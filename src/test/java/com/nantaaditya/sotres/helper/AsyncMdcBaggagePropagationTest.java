package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.nantaaditya.sotres.configuration.JpaAuditorConfiguration;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Proves the fix for the Phase 7 baggage-round-trip finding (see
 * docs/POST_MIGRATION_REMEDIATION_PLAN.md) also holds across an {@code @Async} executor boundary,
 * not just on the request thread {@code EventLogWiringE2eTest} covers: a value written to MDC on
 * the submitting thread (as {@code HeaderFilter} does via {@code TracerHelper.setBaggage}) must
 * still be readable via {@code TracerHelper.getBaggage} after {@link AsyncMDCTaskDecorator} hands
 * the task to a genuinely different thread — the real shape of every
 * {@code @Async("defaultAsyncTaskExecutor")} method (e.g. {@code JpaAuditorConfiguration}
 * populating {@code @CreatedBy}/{@code @LastModifiedBy} during an async entity save).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MDC-backed baggage survives the AsyncMDCTaskDecorator thread handoff")
class AsyncMdcBaggagePropagationTest {

  @Mock
  private BaggageManager baggageManager;
  @Mock
  private Tracer tracer;

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("JpaAuditorConfiguration.getCurrentAuditor sees the submitting thread's client id from a different worker thread")
  void auditorClientId_survivesAsyncExecutorHandoff() throws Exception {
    TracerHelper tracerHelper = new TracerHelper(baggageManager, tracer);
    JpaAuditorConfiguration auditorConfig = new JpaAuditorConfiguration();
    ReflectionTestUtils.setField(auditorConfig, "applicationName", "system-fallback");
    ReflectionTestUtils.setField(auditorConfig, "tracerHelper", tracerHelper);

    // Simulates what HeaderFilter.decorateBaggage ultimately achieves on the request thread.
    MDC.put(HeaderConstant.CLIENT_ID.getHeader(), "wiring-client-42");

    AtomicReference<Optional<String>> capturedAuditor = new AtomicReference<>();
    Runnable decorated = new AsyncMDCTaskDecorator()
        .decorate(() -> capturedAuditor.set(auditorConfig.getCurrentAuditor()));

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // Prove this genuinely runs on a different thread, not just a different MDC snapshot.
      Thread submittingThread = Thread.currentThread();
      Runnable assertingDecorated = () -> {
        assertThat(Thread.currentThread()).isNotSameAs(submittingThread);
        decorated.run();
      };
      executor.submit(assertingDecorated).get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(capturedAuditor.get()).as("auditor resolved on the async worker thread")
        .contains("wiring-client-42");
  }
}
