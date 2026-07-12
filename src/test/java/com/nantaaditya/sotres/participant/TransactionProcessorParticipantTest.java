package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.outgoing.SenderProtocolStrategy;
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

  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(1, 100, 100, 100, 100, 2000, "test");

  private TransactionProcessorParticipant participant;

  @BeforeEach
  void setUp() {
    MDC.put("traceId", "test-trace");

    when(isoMessageProperties.outgoingProtocol()).thenReturn(OutgoingProtocol.REST);
    when(senderProtocolStrategy.getProtocol()).thenReturn(OutgoingProtocol.REST);
    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(
        config);
    when(systemPropertiesService.getProperty(
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR.getPropertyId()))
        .thenReturn("21.00-QR");

    // tracer chain
    lenient().when(tracerHelper.startSpan(any(), any())).thenReturn(span);
    lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
    lenient().when(span.context()).thenReturn(traceContext);
    lenient().when(tracerHelper.withSpanScopeAndMDC(any(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(0));

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

    participant = new TransactionProcessorParticipant(
        systemPropertiesService,
        List.of(),
        isoMessageLoggerHelper,
        isoFieldHelper,
        observationRegistry,
        tracerHelper,
        tracer,
        List.of(senderProtocolStrategy),
        participantConfigurationProperties,
        isoMessageProperties,
        clientProperties
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
      // type=512 (0x0200) → selector="20.00-QR", not in responseRegistrySelectors → pipeline runs
      when(msg.getType()).thenReturn(512);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);

      participant.onMessage(ctx, msg);

      // transactionHandlers is empty — selectTransactionHandler sends "92" asynchronously
      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(isoFieldHelper).sendResponse(ctx, msg, "92"));
    }

    @Test
    @DisplayName("onMessage returns false when the async pipeline is started")
    @SuppressWarnings("unchecked")
    void onMessage_pipelineStarted_returnsFalse() {
      when(msg.getType()).thenReturn(512);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
    }
  }
}
