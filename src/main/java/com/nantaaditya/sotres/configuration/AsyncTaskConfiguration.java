package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.AsyncMDCTaskDecorator;
import com.nantaaditya.sotres.model.constant.RejectionPolicy;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Log4j2
@Configuration
public class AsyncTaskConfiguration {

  @Autowired
  private AsyncTaskProperties asyncProperties;
  @Autowired
  private GenericApplicationContext applicationContext;

  private static final String POSTFIX_BEAN_NAME = "AsyncTaskExecutor";

  @EventListener(ApplicationReadyEvent.class)
  public void onStart() {
    if (asyncProperties.configurations() == null || asyncProperties.configurations().isEmpty()) {
      log.warn(AppLogMessage.message("#AsyncExecutor - no bean defined"));
      return;
    }

    AsyncMDCTaskDecorator asyncMDCTaskDecorator = new AsyncMDCTaskDecorator();
    asyncProperties.configurations()
      .forEach((key, value) -> applicationContext.registerBean(
          key + POSTFIX_BEAN_NAME,
          ThreadPoolTaskExecutor.class,
          () -> createAsyncExecutor(asyncProperties.getConfiguration(key), asyncMDCTaskDecorator),
          definition -> definition.setLazyInit(true)
          )
      );

    log.debug(AppLogMessage.message("#AsyncExecutor - bean {} created", asyncProperties.getBeanNames(POSTFIX_BEAN_NAME)));
  }

  ThreadPoolTaskExecutor createAsyncExecutor(AsyncConfiguration configuration,
      AsyncMDCTaskDecorator asyncMDCTaskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(configuration.corePoolSize());
    executor.setMaxPoolSize(configuration.maxPoolSize());
    executor.setQueueCapacity(configuration.queueCapacity());
    executor.setThreadNamePrefix(configuration.threadNamePrefix());
    executor.setKeepAliveSeconds(configuration.keepAliveSeconds());
    executor.setTaskDecorator(asyncMDCTaskDecorator);
    executor.setVirtualThreads(configuration.virtualThreadEnabled());
    executor.setRejectedExecutionHandler(resolveRejectionHandler(configuration.rejectionPolicy()));
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }

  RejectedExecutionHandler resolveRejectionHandler(RejectionPolicy policy) {
    return switch (policy) {
      case ABORT -> new AbortPolicy();
      case CALLER_RUNS -> new CallerRunsPolicy();
    };
  }
}
