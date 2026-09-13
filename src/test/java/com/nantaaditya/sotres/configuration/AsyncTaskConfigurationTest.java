package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nantaaditya.sotres.helper.AsyncMDCTaskDecorator;
import com.nantaaditya.sotres.model.constant.RejectionPolicy;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("AsyncTaskConfiguration")
class AsyncTaskConfigurationTest {

  private final AsyncTaskConfiguration configuration = new AsyncTaskConfiguration();

  @Test
  @DisplayName("resolveRejectionHandler(ABORT) returns AbortPolicy")
  void resolveRejectionHandler_abort_returnsAbortPolicy() {
    assertThat(configuration.resolveRejectionHandler(RejectionPolicy.ABORT))
        .isInstanceOf(AbortPolicy.class);
  }

  @Test
  @DisplayName("resolveRejectionHandler(CALLER_RUNS) returns CallerRunsPolicy")
  void resolveRejectionHandler_callerRuns_returnsCallerRunsPolicy() {
    assertThat(configuration.resolveRejectionHandler(RejectionPolicy.CALLER_RUNS))
        .isInstanceOf(CallerRunsPolicy.class);
  }

  @Test
  @DisplayName("createAsyncExecutor wires the configuration's rejection policy onto the executor")
  void createAsyncExecutor_abortPolicy_appliedToExecutor() {
    AsyncConfiguration asyncConfig = new AsyncConfiguration(
        5, 10, 5, 30, "test-", false, RejectionPolicy.ABORT);

    ThreadPoolTaskExecutor executor = configuration.createAsyncExecutor(asyncConfig,
        new AsyncMDCTaskDecorator());

    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(AbortPolicy.class);
  }

  @Test
  @DisplayName("createAsyncExecutor applies CallerRunsPolicy when configured")
  void createAsyncExecutor_callerRunsPolicy_appliedToExecutor() {
    AsyncConfiguration asyncConfig = new AsyncConfiguration(
        5, 10, 5, 30, "test-", false, RejectionPolicy.CALLER_RUNS);

    ThreadPoolTaskExecutor executor = configuration.createAsyncExecutor(asyncConfig,
        new AsyncMDCTaskDecorator());

    assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
        .isInstanceOf(CallerRunsPolicy.class);
  }
}
