package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("CorrelationRegistry")
@ExtendWith(MockitoExtension.class)
class CorrelationRegistryTest {

  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;

  private CorrelationRegistry registry;

  // flightPool=100 (pending capacity), flightQueueTimeOut=5000 / messageQueueTimeOut=5000 —
  // long enough that write-expiry never fires mid-test
  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(1, 100, 100, 100, 5000, 5000, "test");

  @BeforeEach
  void setUp() {
    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(config);
    registry = new CorrelationRegistry(participantConfigurationProperties);
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  /** Correlation id = PI + "." + procCode + "|" + STAN + "-" + RRN + "-" + date. */
  private IsoMessage message(String stan) {
    IsoMessage msg = org.mockito.Mockito.mock(IsoMessage.class);
    lenient().when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    lenient().when(msg.getField(3)).thenReturn(isoValue("000000"));
    lenient().when(msg.getField(11)).thenReturn(isoValue(stan));
    lenient().when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    lenient().when(msg.getField(7)).thenReturn(isoValue("0615103045"));
    return msg;
  }

  private String correlationId(String stan) {
    return IsoFieldHelper.getCorrelationId(message(stan));
  }

  @Nested
  @DisplayName("register + complete")
  class RegisterComplete {

    @Test
    @DisplayName("register returns a non-null incomplete future")
    void register_returnsIncompleteFuture() {
      CompletableFuture<IsoMessage> future = registry.register(correlationId("100001"));

      assertThat(future).isNotNull();
      assertThat(future).isNotDone();
    }

    @Test
    @DisplayName("complete resolves the registered future and classifies SUCCESS")
    void complete_registeredKey_completesFutureAndReturnsSuccess() throws Exception {
      IsoMessage response = message("100002");
      CompletableFuture<IsoMessage> future = registry.register(correlationId("100002"));

      IsoCategory category = registry.complete(response);

      assertThat(category).isEqualTo(IsoCategory.SUCCESS);
      assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(response);
    }
  }

  @Nested
  @DisplayName("classification of unmatched responses")
  class Unmatched {

    @Test
    @DisplayName("complete with no prior register returns ORPHAN")
    void complete_unknownKey_returnsOrphan() {
      assertThat(registry.complete(message("200001"))).isEqualTo(IsoCategory.ORPHAN);
    }

    @Test
    @DisplayName("a duplicate response after SUCCESS returns ORPHAN (both windows cleared)")
    void complete_duplicateAfterSuccess_returnsOrphan() {
      registry.register(correlationId("200002"));
      registry.complete(message("200002")); // SUCCESS clears pending + registered

      assertThat(registry.complete(message("200002"))).isEqualTo(IsoCategory.ORPHAN);
    }

    @Test
    @DisplayName("a response after the real-timeout window lapses, still within grace, returns LATE_RESPONSE")
    void complete_afterFlightWindowLapses_returnsLateResponse() {
      // short real-timeout window (30ms), long grace window (5s), no cancel
      ParticipantPoolConfiguration shortFlight =
          new ParticipantPoolConfiguration(1, 100, 100, 100, 30, 5000, "test");
      when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION))
          .thenReturn(shortFlight);
      CorrelationRegistry reg = new CorrelationRegistry(participantConfigurationProperties);

      reg.register(correlationId("200003"));
      // let the 30ms flight window lapse without a cancel
      await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofSeconds(1)).until(() -> true);

      assertThat(reg.complete(message("200003"))).isEqualTo(IsoCategory.LATE_RESPONSE);
    }
  }

  @Nested
  @DisplayName("cancel")
  class Cancel {

    @Test
    @DisplayName("cancel removes the pending future and marks it cancelled")
    void cancel_pendingKey_cancelsFuture() {
      CompletableFuture<IsoMessage> future = registry.register(correlationId("300001"));

      registry.cancel(correlationId("300001"));

      assertThat(future).isCancelled();
    }

    @Test
    @DisplayName("complete after cancel returns ORPHAN — the key was never seen successfully")
    void complete_afterCancel_returnsOrphan() {
      registry.register(correlationId("300002"));
      registry.cancel(correlationId("300002"));

      assertThat(registry.complete(message("300002"))).isEqualTo(IsoCategory.ORPHAN);
    }

    @Test
    @DisplayName("cancel of an unknown key is a no-op")
    void cancel_unknownKey_isNoop() {
      registry.cancel(correlationId("300003"));
    }
  }

  @Nested
  @DisplayName("capacity backstop")
  class CapacityBackstop {

    @Test
    @DisplayName("evicts an un-answered pending future exceptionally when flight capacity is exceeded")
    void register_overCapacity_evictsPendingExceptionally() {
      // flightPool = 100 → registering well past it forces window-TinyLFU eviction
      CompletableFuture<IsoMessage> first = registry.register(correlationId("K00000"));
      for (int i = 1; i <= 400; i++) {
        registry.register(correlationId(String.format("K%05d", i)));
      }

      await().atMost(Duration.ofSeconds(3))
          .untilAsserted(() -> assertThat(first).isCompletedExceptionally());
    }
  }

  @Nested
  @DisplayName("concurrency")
  class Concurrency {

    @Test
    @DisplayName("100 concurrent register/complete pairs each resolve to SUCCESS")
    void concurrentRegisterComplete_allSucceed() throws Exception {
      int n = 100;
      ExecutorService pool = Executors.newFixedThreadPool(16);
      CountDownLatch registered = new CountDownLatch(n);
      AtomicInteger successes = new AtomicInteger();
      try {
        for (int i = 0; i < n; i++) {
          String stan = String.format("9%05d", i);
          CompletableFuture<IsoMessage> future = registry.register(correlationId(stan));
          pool.execute(() -> {
            registered.countDown();
            if (registry.complete(message(stan)) == IsoCategory.SUCCESS && future.isDone()) {
              successes.incrementAndGet();
            }
          });
        }
        assertThat(registered.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        pool.shutdownNow();
      }

      assertThat(successes.get()).isEqualTo(n);
    }
  }
}
