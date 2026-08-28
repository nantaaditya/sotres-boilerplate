package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.CorrelationRegistry;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
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
  private IsoMessage msg;

  private TransactionResponseParticipant participant;

  @BeforeEach
  void setUp() {
    lenient().when(systemPropertiesService.getProperty(
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR.getPropertyId()))
        .thenReturn("21.00-QR");

    lenient().when(tracerHelper.startSpan(any(), any())).thenReturn(span);
    lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
    lenient().when(observationRegistry.isNoop()).thenReturn(true);

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
        observationRegistry, tracerHelper, tracer, clientProperties, Runnable::run);
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
}
