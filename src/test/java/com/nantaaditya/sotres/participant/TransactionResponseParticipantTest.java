package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.IsoResponseRegistry;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
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

@DisplayName("TransactionResponseParticipant")
@ExtendWith(MockitoExtension.class)
class TransactionResponseParticipantTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private IsoResponseRegistry isoResponseRegistry;
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
  private ClientProperties clientProperties;
  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;
  @Mock
  private IsoMessage msg;
  @Mock
  private ChannelHandlerContext ctx;

  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(1, 100, 100, 100, 100, 2000, "test");

  private TransactionResponseParticipant participant;

  @BeforeEach
  void setUp() {
    MDC.put("traceId", "test-trace");

    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(config);
    when(systemPropertiesService.getProperty(
        PropertiesGroup.REGISTRY_RESPONSE_SELECTOR,
        PropertiesGroup.REGISTRY_RESPONSE_SELECTOR.getPropertyId()))
        .thenReturn("21.00-QR");

    // tracer chain
    lenient().when(tracerHelper.startSpan(any(), any())).thenReturn(span);
    lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
    lenient().when(span.context()).thenReturn(traceContext);
    lenient().when(tracerHelper.withSpanScopeAndMDC(any(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(0));

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
        PropertiesGroup.CURRENCY_FRACTIONS,
        PropertiesGroup.CURRENCY_FRACTIONS.getPropertyId()))
        .thenReturn("360:2");

    participant = new TransactionResponseParticipant(
        systemPropertiesService,
        List.of(),
        isoResponseRegistry,
        isoMessageLoggerHelper,
        isoFieldHelper,
        observationRegistry,
        tracerHelper,
        tracer,
        participantConfigurationProperties,
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
  @DisplayName("applies(IsoMessage) — network MTI excluded")
  class AppliesNetworkMti {

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
  }

  @Nested
  @DisplayName("applies(IsoMessage) — registry type check")
  class AppliesRegistryType {

    @Test
    @DisplayName("returns false when registryType=CALLBACK regardless of selector")
    void applies_callbackRegistry_returnsFalse() {
      // type=528 → selector="21.00-QR" which is in responseRegistrySelectors
      when(msg.getType()).thenReturn(528);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns false when registryType=RESPONSE but selector not in list")
    void applies_responseRegistryNonMatchingSelector_returnsFalse() {
      // type=512 (0x0200) → getMTI="0200" → substring(1,3)="20" → selector="20.00-QR" not in list
      when(msg.getType()).thenReturn(512);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);

      assertThat(participant.applies(msg)).isFalse();
    }

    @Test
    @DisplayName("returns true when registryType=RESPONSE and selector matches")
    void applies_responseRegistryMatchingSelector_returnsTrue() {
      // type=528 (0x0210) → getMTI="0210" → substring(1,3)="21" → selector="21.00-QR" matches
      when(msg.getType()).thenReturn(528);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);

      assertThat(participant.applies(msg)).isTrue();
    }
  }

  @Nested
  @DisplayName("onMessage — delegates to isoResponseRegistry")
  class OnMessage {

    @Test
    @DisplayName("calls isoResponseRegistry.onResponse(isoMessage) asynchronously")
    void onMessage_registryEnabled_callsOnResponse() {
      when(msg.getType()).thenReturn(528);

      participant.onMessage(ctx, msg);

      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(isoResponseRegistry).onResponse(msg));
    }

    @Test
    @DisplayName("returns false after starting the async pipeline")
    void onMessage_always_returnsFalse() {
      when(msg.getType()).thenReturn(528);

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
    }
  }
}
