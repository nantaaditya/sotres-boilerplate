package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
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
            log.debug("copy context to async task");
            runnable.run();
          } catch (Throwable e) {
            log.error("error in async task {}, {}", e.getMessage(), ErrorHelper.getRootCause(e));
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
        log.error("#Async - got error {}, method {}, params {}, at {}",
                ex.getMessage(), method.getName(), params, ErrorHelper.getRootCause(ex));
      }
    };
  }

}
