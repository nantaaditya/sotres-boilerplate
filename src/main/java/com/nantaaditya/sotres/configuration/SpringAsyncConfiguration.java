package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class SpringAsyncConfiguration implements AsyncConfigurer {

  private final AsyncTaskProperties asyncProperties;

  @Override
  public Executor getAsyncExecutor() {
    AsyncConfiguration configuration = asyncProperties.getConfiguration("default");
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(configuration.corePoolSize());
    executor.setMaxPoolSize(configuration.maxPoolSize());
    executor.setQueueCapacity(configuration.queueCapacity());
    executor.setThreadNamePrefix(configuration.threadNamePrefix());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setTaskDecorator(new TaskDecorator() {
      @Override
      public Runnable decorate(Runnable runnable) {
        Map<String, String> currentContext = MDC.getCopyOfContextMap();
        return () -> {
          try {
            MDC.setContextMap(currentContext);
            log.debug(AppLogMessage.message("copy context to async task"));
            runnable.run();
          } catch (Throwable e) {
            log.error(AppLogMessage.message("error in async task {}, {}", e.getMessage()).error(e));
          } finally {
            MDC.clear();
          }
        };
      }
    });
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new AsyncUncaughtExceptionHandler() {
      @Override
      public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error(AppLogMessage.message("#Async - got error {}, method {}, params {}, at {}",
                ex.getMessage(), method.getName(), params).error(ex));
      }
    };
  }

}
