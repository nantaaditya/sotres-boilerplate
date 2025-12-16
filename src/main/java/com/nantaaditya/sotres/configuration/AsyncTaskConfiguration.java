package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncTaskConfiguration {

  private final AsyncTaskProperties asyncProperties;
  private final GenericApplicationContext applicationContext;

  private static final String POSTFIX_BEAN_NAME = "AsyncTaskExecutor";

  @EventListener(ApplicationReadyEvent.class)
  public void onStart() {
    if (asyncProperties.configurations() == null || asyncProperties.configurations().isEmpty()) {
      log.warn("#AsyncExecutor - no bean defined");
      return;
    }

    asyncProperties.configurations()
      .forEach((key, value) -> applicationContext.registerBean(
          key + POSTFIX_BEAN_NAME,
          ThreadPoolTaskExecutor.class,
          () -> createAsyncExecutor(asyncProperties.getConfiguration(key)),
          definition -> definition.setLazyInit(true)
          )
      );

    log.debug("#AsyncExecutor - bean {} created", asyncProperties.getBeanNames(POSTFIX_BEAN_NAME));
  }

  private ThreadPoolTaskExecutor createAsyncExecutor(AsyncConfiguration configuration) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(configuration.corePoolSize());
    executor.setMaxPoolSize(configuration.maxPoolSize());
    executor.setQueueCapacity(configuration.queueCapacity());
    executor.setThreadNamePrefix(configuration.threadNamePrefix());
    executor.setKeepAliveSeconds(configuration.keepAliveSeconds());
    executor.initialize();
    return executor;
  }
}
