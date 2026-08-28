package com.nantaaditya.sotres.helper;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Factory for {@link ThreadPoolTaskExecutor}s used to hand work off the Netty
 * event loop. Bounded queue + caller-runs rejection give backpressure; the
 * {@link AsyncMDCTaskDecorator} carries the logging MDC across the hop.
 */
public final class ExecutorHelper {

  private ExecutorHelper() {}

  public static ThreadPoolTaskExecutor create(String threadNamePrefix, int corePoolSize,
      int maxPoolSize, int queueCapacity, int keepAliveSeconds, boolean virtualThreadEnabled) {
    return create(threadNamePrefix, corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds,
        virtualThreadEnabled, new ThreadPoolExecutor.CallerRunsPolicy());
  }

  public static ThreadPoolTaskExecutor create(String threadNamePrefix, int corePoolSize,
      int maxPoolSize, int queueCapacity, int keepAliveSeconds, boolean virtualThreadEnabled,
      RejectedExecutionHandler rejectedExecutionHandler) {

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setKeepAliveSeconds(keepAliveSeconds);
    executor.setTaskDecorator(new AsyncMDCTaskDecorator());
    executor.setRejectedExecutionHandler(rejectedExecutionHandler);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setVirtualThreads(virtualThreadEnabled);
    executor.initialize();
    return executor;
  }
}
