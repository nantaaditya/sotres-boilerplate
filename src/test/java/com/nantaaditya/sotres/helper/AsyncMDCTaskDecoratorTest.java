package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("AsyncMDCTaskDecorator")
class AsyncMDCTaskDecoratorTest {

  private AsyncMDCTaskDecorator decorator;

  @BeforeEach
  void setUp() {
    decorator = new AsyncMDCTaskDecorator();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("decorate copies MDC context map into wrapped runnable")
  void decorate_withMdcContext_copiesMdcIntoWrappedRunnable() {
    MDC.put("traceId", "test-trace-123");
    AtomicReference<String> capturedValue = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> capturedValue.set(MDC.get("traceId")));
    MDC.clear();
    decorated.run();

    assertThat(capturedValue.get()).isEqualTo("test-trace-123");
  }

  @Test
  @DisplayName("decorate clears MDC after wrapped runnable completes")
  void decorate_alwaysClearsMdcAfterExecution() {
    MDC.put("traceId", "test-trace-123");

    Runnable decorated = decorator.decorate(() -> {
    });
    MDC.clear();
    decorated.run();

    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName("decorate with null MDC context does not set MDC in wrapped runnable")
  void decorate_whenMdcContextIsNull_doesNotSetMdc() {
    AtomicReference<String> capturedValue = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> capturedValue.set(MDC.get("traceId")));
    decorated.run();

    assertThat(capturedValue.get()).isNull();
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName("decorate copies multiple MDC entries into wrapped runnable")
  void decorate_withMultipleMdcEntries_copiesAllEntries() {
    MDC.put("traceId", "trace-abc");
    MDC.put("spanId", "span-xyz");
    AtomicReference<String> capturedTrace = new AtomicReference<>();
    AtomicReference<String> capturedSpan = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> {
      capturedTrace.set(MDC.get("traceId"));
      capturedSpan.set(MDC.get("spanId"));
    });
    MDC.clear();
    decorated.run();

    assertThat(capturedTrace.get()).isEqualTo("trace-abc");
    assertThat(capturedSpan.get()).isEqualTo("span-xyz");
  }
}
