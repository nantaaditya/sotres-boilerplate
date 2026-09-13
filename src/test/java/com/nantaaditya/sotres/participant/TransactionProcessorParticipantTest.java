package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.outgoing.SenderProtocolStrategy;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@DisplayName("TransactionProcessorParticipant")
@ExtendWith(MockitoExtension.class)
class TransactionProcessorParticipantTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;
  @Mock
  private IsoFieldHelper isoFieldHelper;
  @Mock
  private ObservationRegistry observationRegistry;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private Tracer tracer;
  @Mock
  private Span span;
  @Mock
  private TraceContext traceContext;
  @Mock
  private Tracer.SpanInScope spanInScope;
  @Mock
  private IsoMessageProperties isoMessageProperties;
  @Mock
  private ClientProperties clientProperties;
  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;
  @Mock
  private SenderProtocolStrategy senderProtocolStrategy;
  @Mock
  private IsoMessage msg;
  @Mock
  private ChannelHandlerContext ctx;
  @Mock
  private Channel channel;
  @SuppressWarnings("rawtypes")
  @Mock
  private Attribute attr;
  @Mock
  private AbstractTransactionHandler routableHandler;

  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(100, 2000);

  private TransactionProcessorParticipant participant;

  @BeforeEach
  void setUp() {
    MDC.put("traceId", "test-trace");

    when(isoMessageProperties.outgoingProtocol()).thenReturn(OutgoingProtocol.REST);
    when(senderProtocolStrategy.getProtocol()).thenReturn(OutgoingProtocol.REST);
    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(
        config);
    lenient().when(systemPropertiesService.getProperty(
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR.getPropertyId()))
        .thenReturn("21.00-QR");

    // tracer chain
    lenient().when(tracerHelper.startSpan(any(), any())).thenReturn(span);
    lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
    lenient().when(span.context()).thenReturn(traceContext);
    // startIsoObservation: build a real Observation against whichever registry is passed
    // (the shared NOOP-configured mock by default, or a TestObservationRegistry when a test
    // constructs its own participant) — mirrors what TracerHelper's real implementation does
    lenient().when(tracerHelper.startIsoObservation(any(), any())).thenAnswer(invocation -> {
      io.micrometer.observation.ObservationRegistry registry = invocation.getArgument(1);
      io.micrometer.observation.Observation observation =
          io.micrometer.observation.Observation.start(
              com.nantaaditya.sotres.model.constant.ObservationConstant.ISO_MESSAGE.getName(), registry);
      return new com.nantaaditya.sotres.helper.IsoObservationContext(
          observation, span, new java.util.HashMap<>(), MDC.getCopyOfContextMap());
    });
    // restoreCallerMdc: perform the real restore-or-clear logic so tests can assert on actual
    // MDC state (TracerHelperTest unit-tests this logic in isolation; this proves the call site
    // wiring — that onMessage() actually invokes it on every exit path — is correct)
    lenient().doAnswer(invocation -> {
      com.nantaaditya.sotres.helper.IsoObservationContext ctx = invocation.getArgument(0);
      if (ctx.callerMdc() == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(ctx.callerMdc());
      }
      return null;
    }).when(tracerHelper).restoreCallerMdc(any(com.nantaaditya.sotres.helper.IsoObservationContext.class));

    // channel attribute chain for isoCategory lookup
    lenient().when(ctx.channel()).thenReturn(channel);
    lenient().when(channel.attr(any())).thenReturn(attr);
    lenient().when(attr.get()).thenReturn(null);

    // Observation.start() returns NOOP when registry.isNoop() is true
    lenient().when(observationRegistry.isNoop()).thenReturn(true);

    // IsoMessage fields needed by RequestContextHelper.create()
    lenient().when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    lenient().when(msg.getField(3)).thenReturn(isoValue("000000"));
    lenient().when(msg.getField(4)).thenReturn(isoValue("100000"));
    lenient().when(msg.getField(28)).thenReturn(isoValue("C000000"));
    lenient().when(msg.getField(49)).thenReturn(isoValue("360"));
    lenient().when(msg.getField(7)).thenReturn(isoValue("0615103045"));
    lenient().when(msg.getField(11)).thenReturn(isoValue("123456"));
    lenient().when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    lenient().when(msg.hasField(90)).thenReturn(false);

    // currency fraction map used inside createTransaction
    lenient().when(systemPropertiesService.getProperty(
            ConfigGroup.CURRENCY_FRACTIONS,
            ConfigGroup.CURRENCY_FRACTIONS.getPropertyId()))
        .thenReturn("360:2");

    participant = buildParticipant(List.of(), new Semaphore(100));
  }

  private TransactionProcessorParticipant buildParticipant(
      List<AbstractTransactionHandler> handlers, Semaphore bulkhead) {
    return buildParticipant(handlers, bulkhead, Runnable::run);
  }

  private TransactionProcessorParticipant buildParticipant(
      List<AbstractTransactionHandler> handlers, Semaphore bulkhead, Executor executor) {
    return new TransactionProcessorParticipant(
        systemPropertiesService,
        handlers,
        isoMessageLoggerHelper,
        isoFieldHelper,
        observationRegistry,
        tracerHelper,
        tracer,
        List.of(senderProtocolStrategy),
        participantConfigurationProperties,
        isoMessageProperties,
        clientProperties,
        executor,
        bulkhead
    );
  }

  private TransactionProcessorParticipant buildParticipant(
      List<AbstractTransactionHandler> handlers, Semaphore bulkhead, Executor executor,
      ObservationRegistry registry) {
    return new TransactionProcessorParticipant(
        systemPropertiesService,
        handlers,
        isoMessageLoggerHelper,
        isoFieldHelper,
        registry,
        tracerHelper,
        tracer,
        List.of(senderProtocolStrategy),
        participantConfigurationProperties,
        isoMessageProperties,
        clientProperties,
        executor,
        bulkhead
    );
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  @Nested
  @DisplayName("applies(IsoMessage)")
  class Applies {

    @Test
    @DisplayName("returns false for MTI 0x800 (network request)")
    void applies_networkRequest_returnsFalse() {
      when(msg.getType()).thenReturn(0x800);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns false for MTI 0x810 (network response)")
    void applies_networkResponse_returnsFalse() {
      when(msg.getType()).thenReturn(0x810);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns true for non-network MTI 0x200")
    void applies_transactionMessage_returnsTrue() {
      when(msg.getType()).thenReturn(0x200);

      assertThat(participant.applies(msg)).isTrue();
    }
  }

  @Nested
  @DisplayName("onMessage — response registry enabled")
  class ResponseRegistryEnabled {

    @Test
    @DisplayName("returns true early when registry=RESPONSE and selector matches, skipping async pipeline")
    @SuppressWarnings("unchecked")
    void onMessage_responseRegistryEnabled_returnsTrueWithoutPipeline() {
      // type=528 (0x0210) → getMTI="0210" → substring(1,3)="21" → selector="21.00-QR"
      when(msg.getType()).thenReturn(528);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isTrue();
    }
  }

  @Nested
  @DisplayName("onMessage — no matching transaction handler")
  class NoMatchingHandler {

    @Test
    @DisplayName("sends UNABLE_TO_ROUTE (92) when no handler matches the selector")
    @SuppressWarnings("unchecked")
    void onMessage_noMatchingHandler_sendsUnableToRoute() {
      // type=512 (0x0200) → selector="20.00-QR", no handler → pipeline runs
      when(msg.getType()).thenReturn(512);

      participant.onMessage(ctx, msg);

      // transactionHandlers is empty — selectTransactionHandler sends "92" asynchronously
      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(isoFieldHelper)
              .sendResponseWithObservation(any(ParticipantContext.class), eq("92"), isNull()));
    }

    @Test
    @DisplayName("onMessage returns false when the async pipeline is started")
    @SuppressWarnings("unchecked")
    void onMessage_pipelineStarted_returnsFalse() {
      when(msg.getType()).thenReturn(512);

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("onMessage — routed transaction")
  class Routed {

    @Test
    @DisplayName("sheds with SYSTEM_MALFUNCTION (96) when the in-flight bulkhead is saturated")
    @SuppressWarnings("unchecked")
    void onMessage_bulkheadSaturated_shedsSystemMalfunction() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(0));

      p.onMessage(ctx, msg);

      verify(isoFieldHelper)
          .sendResponseWithObservation(any(ParticipantContext.class), eq("96"), isNull());
      verify(senderProtocolStrategy, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("routes to handleError when the downstream send throws")
    @SuppressWarnings("unchecked")
    void onMessage_downstreamThrows_handledByHandleError() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      when(routableHandler.execute(any())).thenAnswer(inv -> inv.getArgument(0));
      when(senderProtocolStrategy.send(any(), any(), any()))
          .thenThrow(new TransactionException(new RuntimeException("boom"), new RequestContext()));
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1));

      p.onMessage(ctx, msg);

      verify(senderProtocolStrategy)
          .handleError(any(ParticipantContext.class), any(TransactionException.class));
    }

    @Test
    @DisplayName("routes to handleError when the bulkhead acquire is interrupted")
    @SuppressWarnings("unchecked")
    void onMessage_bulkheadInterrupted_handledByHandleError() throws InterruptedException {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      Semaphore interrupting = mock(Semaphore.class);
      when(interrupting.tryAcquire(anyLong(), any())).thenThrow(new InterruptedException());
      TransactionProcessorParticipant p = buildParticipant(List.of(routableHandler), interrupting);

      try {
        p.onMessage(ctx, msg);

        verify(senderProtocolStrategy)
            .handleError(any(ParticipantContext.class), any(InterruptedException.class));
      } finally {
        Thread.interrupted(); // clear the flag set by handleTransaction so it doesn't leak
      }
    }
  }

  @Nested
  @DisplayName("onMessage — MDC hygiene on the calling (event-loop) thread")
  class MdcHygiene {

    @Test
    @DisplayName("restores the calling thread's MDC after a successful handoff (happy path)")
    @SuppressWarnings("unchecked")
    void onMessage_happyPath_restoresCallingThreadMdc() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      when(routableHandler.execute(any())).thenAnswer(inv -> inv.getArgument(0));
      // simulate the real TracerHelper.startIsoObservation mutating the calling thread's MDC
      // (via its internal initiateSpan call) before snapshotting/returning the context
      doAnswer(invocation -> {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", "leaked-request-id");
        return new com.nantaaditya.sotres.helper.IsoObservationContext(
            io.micrometer.observation.Observation.NOOP, span, new java.util.HashMap<>(), callerMdc);
      }).when(tracerHelper).startIsoObservation(any(), any());
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1));

      Map<String, String> mdcBefore = MDC.getCopyOfContextMap();

      p.onMessage(ctx, msg);

      assertThat(MDC.getCopyOfContextMap()).isEqualTo(mdcBefore);
    }
  }

  @Nested
  @DisplayName("onMessage — executor saturated (RejectedExecutionException)")
  class ExecutorSaturated {

    @Test
    @DisplayName("sheds with SYSTEM_MALFUNCTION (96) without running any work on the calling thread")
    @SuppressWarnings("unchecked")
    void onMessage_executorRejects_shedsWithoutRunningWorkOnCallingThread() {
      when(msg.getType()).thenReturn(512);
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1), rejectingExecutor);

      p.onMessage(ctx, msg);

      verify(isoFieldHelper).sendResponseWithObservation(
          any(ParticipantContext.class), eq("96"), any(RejectedExecutionException.class));
      verify(routableHandler, never()).execute(any());
      verify(senderProtocolStrategy, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("never touches the bulkhead — rejection happens before any permit would be acquired")
    @SuppressWarnings("unchecked")
    void onMessage_executorRejects_bulkheadUntouched() {
      when(msg.getType()).thenReturn(512);
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      Semaphore bulkhead = new Semaphore(1);
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), bulkhead, rejectingExecutor);

      p.onMessage(ctx, msg);

      assertThat(bulkhead.availablePermits()).isEqualTo(1);
    }

    @Test
    @DisplayName("restores the calling thread's MDC to its pre-call state")
    @SuppressWarnings("unchecked")
    void onMessage_executorRejects_restoresCallingThreadMdc() {
      when(msg.getType()).thenReturn(512);
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      // simulate the real TracerHelper.startIsoObservation mutating the calling thread's MDC
      doAnswer(invocation -> {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", "leaked-request-id");
        return new com.nantaaditya.sotres.helper.IsoObservationContext(
            io.micrometer.observation.Observation.NOOP, span, new java.util.HashMap<>(), callerMdc);
      }).when(tracerHelper).startIsoObservation(any(), any());
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1), rejectingExecutor);

      Map<String, String> mdcBefore = MDC.getCopyOfContextMap();

      p.onMessage(ctx, msg);

      assertThat(MDC.getCopyOfContextMap()).isEqualTo(mdcBefore);
    }

  }

  @Nested
  @DisplayName("handleTransaction — observation stopped exactly once on every exit path")
  class ObservationLifecycle {

    private TestObservationRegistry testRegistry;

    @BeforeEach
    void setUpRegistry() {
      testRegistry = TestObservationRegistry.create();
    }

    private void assertObservationStoppedExactlyOnce() {
      TestObservationRegistryAssert.assertThat(testRegistry)
          .hasNumberOfObservationsWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName(), 1);
      TestObservationRegistryAssert.assertThat(testRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped();
    }

    @Test
    @DisplayName("unknown transaction handler")
    @SuppressWarnings("unchecked")
    void handleTransaction_unknownHandler_stopsObservation() {
      when(msg.getType()).thenReturn(512); // selector "20.00-QR", no handler registered
      TransactionProcessorParticipant p =
          buildParticipant(List.of(), new Semaphore(1), Runnable::run, testRegistry);

      p.onMessage(ctx, msg);

      assertObservationStoppedExactlyOnce();
    }

    @Test
    @DisplayName("bulkhead saturated")
    @SuppressWarnings("unchecked")
    void handleTransaction_bulkheadSaturated_stopsObservation() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(0), Runnable::run, testRegistry);

      p.onMessage(ctx, msg);

      assertObservationStoppedExactlyOnce();
    }

    @Test
    @DisplayName("downstream send throws a non-timeout TransactionException, routed to handleError")
    @SuppressWarnings("unchecked")
    void handleTransaction_downstreamThrows_stopsObservation() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      when(routableHandler.execute(any())).thenAnswer(inv -> inv.getArgument(0));
      when(senderProtocolStrategy.send(any(), any(), any()))
          .thenThrow(new TransactionException(new RuntimeException("boom"), new RequestContext()));
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1), Runnable::run, testRegistry);

      p.onMessage(ctx, msg);

      assertObservationStoppedExactlyOnce();
    }

    @Test
    @DisplayName("bulkhead acquire interrupted, routed to handleError as an unknown throwable")
    @SuppressWarnings("unchecked")
    void handleTransaction_bulkheadInterrupted_stopsObservation() throws InterruptedException {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      Semaphore interrupting = mock(Semaphore.class);
      when(interrupting.tryAcquire(anyLong(), any())).thenThrow(new InterruptedException());
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), interrupting, Runnable::run, testRegistry);

      try {
        p.onMessage(ctx, msg);
        assertObservationStoppedExactlyOnce();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("executor rejects (saturated pool)")
    @SuppressWarnings("unchecked")
    void handleTransaction_executorRejects_stopsObservation() {
      when(msg.getType()).thenReturn(512);
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1), rejectingExecutor, testRegistry);

      p.onMessage(ctx, msg);

      assertObservationStoppedExactlyOnce();
    }

    @Test
    @DisplayName("happy path: handler executes, downstream responds, handleResponse writes the reply")
    @SuppressWarnings("unchecked")
    void handleTransaction_happyPath_stopsObservation() {
      when(msg.getType()).thenReturn(512);
      when(routableHandler.getSelectors()).thenReturn(Set.of("20.00-QR"));
      when(routableHandler.execute(any())).thenAnswer(inv -> inv.getArgument(0));
      TransactionProcessorParticipant p =
          buildParticipant(List.of(routableHandler), new Semaphore(1), Runnable::run, testRegistry);

      p.onMessage(ctx, msg);

      assertObservationStoppedExactlyOnce();
    }
  }

}
