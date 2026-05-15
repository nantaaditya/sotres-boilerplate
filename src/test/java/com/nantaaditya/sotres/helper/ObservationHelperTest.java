package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.dto.TransactionException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ObservationHelper")
@ExtendWith(MockitoExtension.class)
class ObservationHelperTest {

  @Mock
  private Observation observation;

  @Nested
  @DisplayName("observeIsoRequest(Observation, String, String)")
  class ObserveIsoRequest {

    @Test
    @DisplayName("does nothing when observation is null")
    void observeIsoRequest_nullObservation_doesNotThrow() {
      ObservationHelper.observeIsoRequest(null, "rrn-001", "feature-A");
    }

    @Test
    @DisplayName("sets highCardinality requestId when rrn is present")
    void observeIsoRequest_withRrn_setsHighCardinalityRequestId() {
      ObservationHelper.observeIsoRequest(observation, "rrn-001", null);

      verify(observation).highCardinalityKeyValue("requestId", "rrn-001");
    }

    @Test
    @DisplayName("sets lowCardinality feature when feature is present")
    void observeIsoRequest_withFeature_setsLowCardinalityFeature() {
      ObservationHelper.observeIsoRequest(observation, null, "purchase");

      verify(observation).lowCardinalityKeyValue("feature", "purchase");
    }

    @Test
    @DisplayName("sets both highCardinality and lowCardinality when both are present")
    void observeIsoRequest_bothPresent_setsBothKeyValues() {
      ObservationHelper.observeIsoRequest(observation, "rrn-001", "purchase");

      verify(observation).highCardinalityKeyValue("requestId", "rrn-001");
      verify(observation).lowCardinalityKeyValue("feature", "purchase");
    }

    @Test
    @DisplayName("does not set highCardinality requestId when rrn is null")
    void observeIsoRequest_nullRrn_doesNotSetRequestId() {
      ObservationHelper.observeIsoRequest(observation, null, "purchase");

      verify(observation, never()).highCardinalityKeyValue(eq("requestId"), any());
    }
  }

  @Nested
  @DisplayName("publishEvent(Observation, String, String)")
  class PublishEvent {

    @Test
    @DisplayName("logs warn and does not throw when observation is null")
    void publishEvent_nullObservation_doesNotThrow() {
      ObservationHelper.publishEvent(null, "key", "value");
    }

    @Test
    @DisplayName("calls observation.event() with the given key and value")
    void publishEvent_withObservation_callsEvent() {
      ObservationHelper.publishEvent(observation, "txn.processed", "ok");

      verify(observation).event(any());
    }
  }

  @Nested
  @DisplayName("observeResponse(Observation, String, Throwable)")
  class ObserveResponse {

    @Test
    @DisplayName("does nothing when observation is null")
    void observeResponse_nullObservation_doesNotThrow() {
      ObservationHelper.observeResponse(null, "00", null);
    }

    @Test
    @DisplayName("sets lowCardinality responseCode when present")
    void observeResponse_withResponseCode_setsResponseCode() {
      ObservationHelper.observeResponse(observation, "00", null);

      verify(observation).lowCardinalityKeyValue("responseCode", "00");
    }

    @Test
    @DisplayName("does not set error keys when error is null")
    void observeResponse_nullError_doesNotSetErrorKeys() {
      ObservationHelper.observeResponse(observation, "00", null);

      verify(observation, never()).lowCardinalityKeyValue(eq("error"), any());
      verify(observation, never()).error(any());
    }

    @Test
    @DisplayName("uses originalError class name for TransactionException")
    void observeResponse_transactionException_usesOriginalErrorClass() {
      IllegalArgumentException originalError = new IllegalArgumentException("root cause");
      TransactionException txException = new TransactionException(originalError, null);

      ObservationHelper.observeResponse(observation, "96", txException);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalArgumentException");
      verify(observation).event(any());
      verify(observation).error(txException);
    }

    @Test
    @DisplayName("uses getCause() class name for non-TransactionException")
    void observeResponse_genericException_usesCauseClass() {
      IllegalStateException cause = new IllegalStateException("cause");
      RuntimeException wrapper = new RuntimeException("wrapper", cause);

      ObservationHelper.observeResponse(observation, "99", wrapper);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalStateException");
      verify(observation).event(any());
      verify(observation).error(wrapper);
    }
  }

  @Nested
  @DisplayName("createApiContext(ContextDTO)")
  class CreateApiContext {

    private ContextDTO buildContext(String method, String path, String requestId) {
      ContextDTO dto = new ContextDTO();
      dto.setMethod(method);
      dto.setPath(path);
      dto.setRequestId(requestId);
      return dto;
    }

    @Test
    @DisplayName("adds feature=POST_EXAMPLE for POST /api/example")
    void createApiContext_knownPostFeature_addsPostExampleFeatureName() {
      ContextDTO dto = buildContext("POST", "/api/example", "req-001");

      Context ctx = ObservationHelper.createApiContext(dto);

      assertThat(ctx.getLowCardinalityKeyValues())
          .anySatisfy(kv -> {
            assertThat(kv.getKey()).isEqualTo("feature");
            assertThat(kv.getValue()).isEqualTo("POST_EXAMPLE");
          });
    }

    @Test
    @DisplayName("adds feature=GET_EXAMPLE for GET /api/example")
    void createApiContext_knownGetFeature_addsGetExampleFeatureName() {
      ContextDTO dto = buildContext("GET", "/api/example", "req-002");

      Context ctx = ObservationHelper.createApiContext(dto);

      assertThat(ctx.getLowCardinalityKeyValues())
          .anySatisfy(kv -> {
            assertThat(kv.getKey()).isEqualTo("feature");
            assertThat(kv.getValue()).isEqualTo("GET_EXAMPLE");
          });
    }

    @Test
    @DisplayName("adds feature=method_path when path does not match any ApiFeatureConstant")
    void createApiContext_unknownPath_addsUnknownFeature() {
      ContextDTO dto = buildContext("DELETE", "/api/unknown", "req-003");

      Context ctx = ObservationHelper.createApiContext(dto);

      assertThat(ctx.getLowCardinalityKeyValues())
          .anySatisfy(kv -> {
            assertThat(kv.getKey()).isEqualTo("feature");
            assertThat(kv.getValue()).isEqualTo("DELETE_/api/unknown");
          });
    }

    @Test
    @DisplayName("adds highCardinality requestId when requestId is set")
    void createApiContext_withRequestId_addsHighCardinalityRequestId() {
      ContextDTO dto = buildContext("POST", "/api/example", "req-123");

      Context ctx = ObservationHelper.createApiContext(dto);

      assertThat(ctx.getHighCardinalityKeyValues())
          .anySatisfy(kv -> {
            assertThat(kv.getKey()).isEqualTo("requestId");
            assertThat(kv.getValue()).isEqualTo("req-123");
          });
    }
  }
}
