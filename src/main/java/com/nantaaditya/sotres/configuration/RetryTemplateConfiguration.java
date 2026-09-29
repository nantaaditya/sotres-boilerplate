package com.nantaaditya.sotres.configuration;

import com.google.gson.Gson;
import com.nantaaditya.sotres.factory.RetryTemplateHelperFactory;
import com.nantaaditya.sotres.listener.RestSenderRetryListener;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.properties.RetryProperties;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds one {@link RetryTemplate} per {@code apps.retry.configurations.<name>} entry and exposes
 * them through {@link RetryTemplateHelperFactory} keyed as {@code <name>RetryTemplate}.
 *
 * <p>{@code max-attempt} is the <em>total</em> number of executions: {@code 1} means one call and
 * no retry, {@code 3} means the initial call plus two retries. The dead-letter {@code RetryListener}
 * is attached in a later phase.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class RetryTemplateConfiguration {

  private final RetryProperties retryProperties;
  private final DeadLetterProcessRepository deadLetterProcessRepository;
  private final ObjectMapper objectMapper;
  private final Gson gson;
  private final LogProperties logProperties;

  private static final String POSTFIX_BEAN_NAME = "RetryTemplate";

  @Bean
  public RestSenderRetryListener restSenderRetryListener() {
    return new RestSenderRetryListener(deadLetterProcessRepository, objectMapper, gson, logProperties);
  }

  @Bean
  public RetryTemplateHelperFactory retryTemplateHelperFactory() {
    RetryTemplateHelperFactory factory = new RetryTemplateHelperFactory();
    Map<String, RetryTemplate> retryTemplates = new HashMap<>();

    if (retryProperties.configurations() == null || retryProperties.configurations().isEmpty()) {
      log.warn(AppLogMessage.message("#Retry - no retry template configured"));
      factory.setRetryTemplates(retryTemplates);
      factory.setRetryConfigurations(Map.of());
      return factory;
    }

    retryTemplates.putAll(retryProperties.configurations()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey() + POSTFIX_BEAN_NAME,
            entry -> createRetryTemplate(entry.getKey(), entry.getValue())
        ))
    );
    factory.setRetryTemplates(retryTemplates);
    factory.setRetryConfigurations(Map.copyOf(retryProperties.configurations()));
    log.info(AppLogMessage.message("#Retry - retry templates created {}",
        retryProperties.getBeanNames(POSTFIX_BEAN_NAME)));
    return factory;
  }

  private RetryTemplate createRetryTemplate(String name, RetryConfiguration configuration) {
    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(createPolicy(configuration));
    retryTemplate.setBackOffPolicy(createBackOffPolicy(configuration));
    retryTemplate.setThrowLastExceptionOnExhausted(true);
    retryTemplate.registerListener(restSenderRetryListener());
    log.debug(AppLogMessage.message("#Retry - [{}] template built type={} maxAttempt={}",
        name, configuration.type(), configuration.maxAttempt()));
    return retryTemplate;
  }

  private SimpleRetryPolicy createPolicy(RetryConfiguration configuration) {
    List<Class<? extends Throwable>> whitelist = configuration.getWhitelistedExceptions();
    List<Class<? extends Throwable>> blacklist = configuration.getBlacklistedExceptions();

    Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
    whitelist.forEach(clazz -> retryableExceptions.put(clazz, true));
    blacklist.forEach(clazz -> retryableExceptions.put(clazz, false));

    int maxAttempts = Math.max(1, configuration.maxAttempt());
    boolean retryByDefault = whitelist.isEmpty();

    return new SimpleRetryPolicy(maxAttempts, retryableExceptions, true, retryByDefault);
  }

  private BackOffPolicy createBackOffPolicy(RetryConfiguration configuration) {
    return switch (configuration.type()) {
      case FIXED -> {
        FixedBackOffPolicy policy = new FixedBackOffPolicy();
        policy.setBackOffPeriod(configuration.initialInterval());
        yield policy;
      }
      case EXPONENTIAL -> exponential(new ExponentialBackOffPolicy(), configuration);
      case EXPONENTIAL_RANDOM -> exponential(new ExponentialRandomBackOffPolicy(), configuration);
      case UNIFORM_RANDOM -> {
        UniformRandomBackOffPolicy policy = new UniformRandomBackOffPolicy();
        policy.setMinBackOffPeriod(configuration.initialInterval());
        policy.setMaxBackOffPeriod(configuration.maxInterval());
        yield policy;
      }
    };
  }

  private ExponentialBackOffPolicy exponential(ExponentialBackOffPolicy policy,
      RetryConfiguration configuration) {
    policy.setInitialInterval(configuration.initialInterval());
    policy.setMultiplier(configuration.multiplier());
    policy.setMaxInterval(configuration.maxInterval());
    return policy;
  }
}
