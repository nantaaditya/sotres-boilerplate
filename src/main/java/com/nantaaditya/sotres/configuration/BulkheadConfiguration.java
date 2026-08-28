package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.BulkheadProperties;
import com.nantaaditya.sotres.properties.embedded.BulkheadPoolConfiguration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Factory for {@link Semaphore} bulkhead beans — one per
 * {@code apps.bulkhead.configurations} entry, named {@code <key>Bulkhead} —
 * mirroring {@link AsyncTaskConfiguration}'s executor factory.
 *
 * <p>Runs as a {@link BeanDefinitionRegistryPostProcessor} (before any component is
 * instantiated) rather than on {@code ApplicationReadyEvent}: {@link Semaphore} is a
 * JDK class that cannot be CGLIB-proxied under JPMS, so it can't be consumed through a
 * {@code @Lazy} proxy the way the executors are — the definitions must exist up front
 * so consumers can inject them directly by {@code @Qualifier}.
 */
@Log4j2
@Component
public class BulkheadConfiguration
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  private static final String POSTFIX_BEAN_NAME = "Bulkhead";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    BulkheadProperties properties = Binder.get(environment)
        .bind(BulkheadProperties.PREFIX, BulkheadProperties.class)
        .orElseGet(() -> new BulkheadProperties(Map.of()));

    if (properties.configurations() == null || properties.configurations().isEmpty()) {
      log.warn(AppLogMessage.message("#Bulkhead - no bean defined"));
      return;
    }

    properties.configurations().forEach((key, config) -> registry.registerBeanDefinition(
        key + POSTFIX_BEAN_NAME, bulkheadDefinition(config)));

    log.debug(AppLogMessage.message("#Bulkhead - bean {} created",
        properties.getBeanNames(POSTFIX_BEAN_NAME)));
  }

  private static RootBeanDefinition bulkheadDefinition(BulkheadPoolConfiguration config) {
    return new RootBeanDefinition(Semaphore.class,
        () -> new Semaphore(config.permits(), config.fair()));
  }
}
