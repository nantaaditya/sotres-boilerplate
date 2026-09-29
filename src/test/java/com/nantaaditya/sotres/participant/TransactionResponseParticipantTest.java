package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.CorrelationRegistry;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.IsoObservationContext;
import com.nantaaditya.sotres.helper.IsoResponseSender;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@DisplayName("TransactionResponseParticipant")
@ExtendWith(MockitoExtension.class)
class TransactionResponseParticipantTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private CorrelationRegistry correlationRegistry;
  @Mock
  private ObservationRegistry observationRegistry;
  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;
  @Mock
  private IsoResponseSender isoResponseSender;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private Tracer tracer;
  @Mock
  private Span span;
  @Mock
  private Tracer.SpanInScope spanInScope;
  @Mock
  private ClientProperties clientProperties;
  @Mock
  private ChannelHandlerContext ctx;
  @Mock
  private Channel channel;
  @Mock
  @SuppressWarnings("rawtypes")
  private Attribute attribute;
  @Mock
  private IsoMessage msg;

  private TransactionResponseParticipant participant;

  @BeforeEach
  void setUp() {
    // production onMessage() reads the callback classification off the channel attribute
    // (set upstream by IsoCallbackResponseHandler) purely for logging — never null in real Netty.
    lenient().when(ctx.channel()).thenReturn(channel);
    lenient().when(channel.attr(any())).thenReturn(attribute);

    lenient().when(systemPropertiesService.getProperty(
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR.getPropertyId()))
        .thenReturn("21.00-QR");

    lenient().when(tracerHelper.startSpan(any(), any())).thenReturn(span);
    lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
    lenient().when(observationRegistry.isNoop()).thenReturn(true);
    lenient().when(tracerHelper.startIsoObservation(any(), any())).thenAnswer(invocation -> {
      ObservationRegistry registry = invocation.getArgument(1);
      Observation observation = Observation.start(ObservationConstant.ISO_MESSAGE.getName(), registry);
      return new IsoObservationContext(observation, span, new HashMap<>(), MDC.getCopyOfContextMap());
    });
    lenient().doAnswer(invocation -> {
      IsoObservationContext ctx = invocation.getArgument(0);
      if (ctx.callerMdc() == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(ctx.callerMdc());
      }
      return null;
    }).when(tracerHelper).restoreCallerMdc(any(IsoObservationContext.class));

    lenient().when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    lenient().when(msg.getField(3)).thenReturn(isoValue("000000"));
    lenient().when(msg.getField(4)).thenReturn(isoValue("100000"));
    lenient().when(msg.getField(28)).thenReturn(isoValue("C000000"));
    lenient().when(msg.getField(49)).thenReturn(isoValue("360"));
    lenient().when(msg.getField(7)).thenReturn(isoValue("0615103045"));
    lenient().when(msg.getField(11)).thenReturn(isoValue("123456"));
    lenient().when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    lenient().when(msg.hasField(90)).thenReturn(false);
    lenient().when(systemPropertiesService.getProperty(
            ConfigGroup.CURRENCY_FRACTIONS,
            ConfigGroup.CURRENCY_FRACTIONS.getPropertyId()))
        .thenReturn("360:2");

    // executor runs the completion inline
    participant = new TransactionResponseParticipant(systemPropertiesService, correlationRegistry,
        observationRegistry, isoMessageLoggerHelper, isoResponseSender, tracerHelper, tracer,
        clientProperties, Runnable::run);
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
    @DisplayName("returns true when registry=RESPONSE and selector matches")
    void applies_responseRegistryAndMatchingSelector_returnsTrue() {
      when(msg.getType()).thenReturn(528); // 0x0210 → selector "21.00-QR"
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);

      assertThat(participant.applies(msg)).isTrue();
    }

    @Test
    @DisplayName("returns false when registry=CALLBACK regardless of selector")
    void applies_callbackRegistry_returnsFalse() {
      when(msg.getType()).thenReturn(528);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns false when registry=RESPONSE but selector is not eligible")
    void applies_responseRegistryButUnlistedSelector_returnsFalse() {
      when(msg.getType()).thenReturn(512); // 0x0200 → selector "20.00-QR", not in list
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns false for network MTI 0x810")
    void applies_networkMessage_returnsFalse() {
      when(msg.getType()).thenReturn(0x810);

      assertThat(participant.applies(msg)).isFalse();
    }
  }

  @Nested
  @DisplayName("onMessage(ChannelHandlerContext, IsoMessage)")
  class OnMessage {

    @Test
    @DisplayName("completes the correlation future and returns false")
    void onMessage_completesCorrelationAndReturnsFalse() {
      when(correlationRegistry.complete(msg)).thenReturn(IsoCategory.SUCCESS);

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
      verify(correlationRegistry).complete(msg);
    }

    @Test
    @DisplayName("swallows a completion error and still returns false")
    void onMessage_completionThrows_returnsFalse() {
      when(correlationRegistry.complete(msg)).thenThrow(new RuntimeException("boom"));

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
      verify(correlationRegistry).complete(msg);
    }
  }

  @Nested
  @DisplayName("onMessage — MDC hygiene on the calling (event-loop) thread")
  class MdcHygiene {

    @Test
    @DisplayName("restores the calling thread's MDC after a successful completion (happy path)")
    void onMessage_happyPath_restoresCallingThreadMdc() {
      doAnswer(invocation -> {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", "leaked-request-id");
        return new IsoObservationContext(Observation.NOOP, span, new HashMap<>(), callerMdc);
      }).when(tracerHelper).startIsoObservation(any(), any());

      Map<String, String> mdcBefore = MDC.getCopyOfContextMap();

      participant.onMessage(ctx, msg);

      assertThat(MDC.getCopyOfContextMap()).isEqualTo(mdcBefore);
    }

    @Test
    @DisplayName("restores the calling thread's MDC to its pre-call state on rejection")
    void onMessage_executorRejects_restoresCallingThreadMdc() {
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      doAnswer(invocation -> {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", "leaked-request-id");
        return new IsoObservationContext(Observation.NOOP, span, new HashMap<>(), callerMdc);
      }).when(tracerHelper).startIsoObservation(any(), any());
      TransactionResponseParticipant p = new TransactionResponseParticipant(
          systemPropertiesService, correlationRegistry, observationRegistry, isoMessageLoggerHelper,
          isoResponseSender, tracerHelper, tracer, clientProperties, rejectingExecutor);

      Map<String, String> mdcBefore = MDC.getCopyOfContextMap();

      p.onMessage(ctx, msg);

      assertThat(MDC.getCopyOfContextMap()).isEqualTo(mdcBefore);
    }
  }

  @Nested
  @DisplayName("onMessage — executor saturated (RejectedExecutionException)")
  class ExecutorSaturated {

    @Test
    @DisplayName("logs and returns false, but still completes the correlation registry "
        + "(that fast completion happens before the executor hand-off, independent of it)")
    void onMessage_executorRejects_returnsFalseButStillCompletes() {
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      TransactionResponseParticipant p = new TransactionResponseParticipant(
          systemPropertiesService, correlationRegistry, observationRegistry, isoMessageLoggerHelper,
          isoResponseSender, tracerHelper, tracer, clientProperties, rejectingExecutor);

      boolean result = p.onMessage(ctx, msg);

      assertThat(result).isFalse();
      verify(correlationRegistry).complete(msg);
    }

    @Test
    @DisplayName("stops the observation, marked as errored, instead of leaving it open")
    void onMessage_executorRejects_stopsObservationAsErrored() {
      TestObservationRegistry testRegistry = TestObservationRegistry.create();
      Executor rejectingExecutor = mock(Executor.class);
      doThrow(new RejectedExecutionException("pool saturated"))
          .when(rejectingExecutor).execute(any());
      TransactionResponseParticipant p = new TransactionResponseParticipant(
          systemPropertiesService, correlationRegistry, testRegistry, isoMessageLoggerHelper,
          isoResponseSender, tracerHelper, tracer, clientProperties, rejectingExecutor);

      p.onMessage(ctx, msg);

      TestObservationRegistryAssert.assertThat(testRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped()
          .hasError();
    }
  }

  @Nested
  @DisplayName("onMessage — iso.message observation lifecycle (matches EnhancedIsoClient.send())")
  class ObservationLifecycle {

    @Test
    @DisplayName("a normal completion starts AND stops one iso.message observation")
    void onMessage_completes_stopsIsoMessageObservation() {
      TestObservationRegistry testRegistry = TestObservationRegistry.create();
      TransactionResponseParticipant p = new TransactionResponseParticipant(systemPropertiesService,
          correlationRegistry, testRegistry, isoMessageLoggerHelper, isoResponseSender, tracerHelper,
          tracer, clientProperties, Runnable::run);

      p.onMessage(ctx, msg);

      // this is the same assertion EnhancedIsoClientTest makes for its own iso.message
      // observation — proves the two classes' observations are wired the same way.
      TestObservationRegistryAssert.assertThat(testRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped();
    }

    @Test
    @DisplayName("negative: a completion failure still stops the iso.message observation, marked as errored")
    void onMessage_completionThrows_stopsIsoMessageObservationAsErrored() {
      TestObservationRegistry testRegistry = TestObservationRegistry.create();
      TransactionResponseParticipant p = new TransactionResponseParticipant(systemPropertiesService,
          correlationRegistry, testRegistry, isoMessageLoggerHelper, isoResponseSender, tracerHelper,
          tracer, clientProperties, Runnable::run);
      doThrow(new RuntimeException("boom")).when(isoResponseSender).publishIsoEvent(any(), any(), any(), anyString());

      p.onMessage(ctx, msg);

      // completeResponse's catch(Throwable) tags the observation via ObservationHelper
      // .observeResponse(..., throwable) before stopping it — same error-tagging shape
      // EnhancedIsoClient.send()'s catch branches use after the fix.
      TestObservationRegistryAssert.assertThat(testRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped()
          .hasError();
    }
  }
}
