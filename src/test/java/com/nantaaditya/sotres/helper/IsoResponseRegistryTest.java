package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("IsoResponseRegistry")
@ExtendWith(MockitoExtension.class)
class IsoResponseRegistryTest {

  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;

  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;

  @Mock
  private IsoMessage request;

  @Mock
  private IsoMessage response;

  private IsoResponseRegistry registry;

  // 100ms timeout: short enough to keep tests fast, long enough to not be flaky
  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(1, 100, 100, 100, 100, 2000, "test");

  @BeforeEach
  void setUp() {
    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(
        config);
    when(isoMessageLoggerHelper.toLogMessage(any())).thenReturn(null);
    registry = new IsoResponseRegistry(isoMessageLoggerHelper, participantConfigurationProperties);
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  private void setupIsoMessageFields(IsoMessage msg) {
    when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    when(msg.getField(3)).thenReturn(isoValue("000000"));
    when(msg.getField(11)).thenReturn(isoValue("123456"));
    when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    when(msg.getField(7)).thenReturn(isoValue("0615103045"));
  }

  @Nested
  @DisplayName("register(IsoMessage)")
  class Register {

    @Test
    @DisplayName("returns non-null Mono for a valid request")
    void register_validRequest_returnsNonNullMono() {
      setupIsoMessageFields(request);

      Mono<IsoMessage> mono = registry.register(request);

      assertThat(mono).isNotNull();
    }
  }

  @Nested
  @DisplayName("onResponse(IsoMessage)")
  class OnResponse {

    @Test
    @DisplayName("emits the response IsoMessage to the registered Mono")
    void onResponse_registeredRequest_emitsResponse() {
      setupIsoMessageFields(request);
      setupIsoMessageFields(response);

      Mono<IsoMessage> mono = registry.register(request);
      // Emit before subscribing — Sinks.One stores the value for the first subscriber
      registry.onResponse(response);

      StepVerifier.create(mono)
          .expectNext(response)
          .verifyComplete();
    }

    @Test
    @DisplayName("onResponse with unknown correlationId logs error and does not throw")
    void onResponse_unknownKey_doesNotThrow() {
      setupIsoMessageFields(response);
      // no prior register — sink is absent for this correlationId
      registry.onResponse(response);
    }
  }

  @Nested
  @DisplayName("timeout behavior")
  class Timeout {

    @Test
    @DisplayName("Mono errors with TimeoutException when no response arrives within flightQueueTimeOut")
    void register_noResponse_timesOut() {
      setupIsoMessageFields(request);

      Mono<IsoMessage> mono = registry.register(request);

      StepVerifier.create(mono)
          .expectError(TimeoutException.class)
          .verify(Duration.ofSeconds(2));
    }
  }
}
