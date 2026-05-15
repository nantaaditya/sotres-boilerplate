package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.dto.RegistryContext;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("IsoCallbackRegistry")
@ExtendWith(MockitoExtension.class)
class IsoCallbackRegistryTest {

  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;

  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;

  @Mock
  private IsoMessage request;

  @Mock
  private IsoMessage response;

  private IsoCallbackRegistry registry;

  // flightQueueTimeOut=50ms (short) and messageQueueTimeOut=2000ms (long)
  // so that inFlights can expire while registeredMessages is still present for LATE_RESPONSE testing
  private final ParticipantPoolConfiguration config =
      new ParticipantPoolConfiguration(1, 100, 100, 100, 50, 2000, "test");

  private static final String CORRELATION_ID = "QR.00|123456-000000000001-0615103045";

  @BeforeEach
  void setUp() {
    lenient().when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)).thenReturn(config);
    lenient().when(isoMessageLoggerHelper.toLogMessage(any())).thenReturn(null);
    registry = new IsoCallbackRegistry(isoMessageLoggerHelper, participantConfigurationProperties);
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  private void setupIsoMessageFields(IsoMessage msg) {
    // Fields required by IsoFieldHelper.getCorrelationId():
    // DE48 (TLV: PI=QR), DE3 (processing code), DE11 (STAN), DE37 (RRN), DE7 (date)
    lenient().when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    lenient().when(msg.getField(3)).thenReturn(isoValue("000000"));
    lenient().when(msg.getField(11)).thenReturn(isoValue("123456"));
    lenient().when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    lenient().when(msg.getField(7)).thenReturn(isoValue("0615103045"));
  }

  @Nested
  @DisplayName("register(String, IsoMessage)")
  class Register {

    @Test
    @DisplayName("registers correlationId in both caches, verified by subsequent SUCCESS response")
    void register_validKey_subsequentOnResponseReturnsSuccess() {
      setupIsoMessageFields(request);
      setupIsoMessageFields(response);

      registry.register(CORRELATION_ID, request);

      RegistryContext result = registry.onResponse(response);
      assertThat(result.lateResponse()).isFalse();
      assertThat(result.unknownMatchResponse()).isFalse();
    }
  }

  @Nested
  @DisplayName("onResponse(IsoMessage) — SUCCESS")
  class OnResponseSuccess {

    @Test
    @DisplayName("returns SUCCESS context when correlationId is still in inFlights")
    void onResponse_registeredKey_returnsSuccess() {
      setupIsoMessageFields(request);
      setupIsoMessageFields(response);

      registry.register(CORRELATION_ID, request);
      RegistryContext result = registry.onResponse(response);

      assertThat(result.isoMessage()).isSameAs(response);
      assertThat(result.lateResponse()).isFalse();
      assertThat(result.unknownMatchResponse()).isFalse();
    }
  }

  @Nested
  @DisplayName("onResponse(IsoMessage) — LATE_RESPONSE")
  class OnResponseLateResponse {

    @Test
    @DisplayName("returns lateResponse=true when inFlights expired but registeredMessages still present")
    void onResponse_afterFlightExpiry_returnsLateResponse() throws InterruptedException {
      setupIsoMessageFields(request);
      setupIsoMessageFields(response);

      registry.register(CORRELATION_ID, request);
      // Wait past flightQueueTimeOut (50ms); registeredMessages (2000ms) is still valid
      Thread.sleep(100);

      RegistryContext result = registry.onResponse(response);

      assertThat(result.lateResponse()).isTrue();
      assertThat(result.unknownMatchResponse()).isFalse();
      assertThat(result.isoMessage()).isSameAs(response);
    }
  }

  @Nested
  @DisplayName("remove(String)")
  class Remove {

    @Test
    @DisplayName("remove on registered key does not throw")
    void remove_existingKey_doesNotThrow() {
      registry.register(CORRELATION_ID, request);
      registry.remove(CORRELATION_ID);
    }

    @Test
    @DisplayName("remove on non-existing key does not throw")
    void remove_missingKey_doesNotThrow() {
      registry.remove("non-existent-key");
    }
  }
}
