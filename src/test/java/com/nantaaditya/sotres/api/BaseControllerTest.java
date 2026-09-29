package com.nantaaditya.sotres.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.response.Response;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("BaseController")
@ExtendWith(MockitoExtension.class)
class BaseControllerTest {

  @Mock
  private ObservationWrapper observationWrapper;
  @Mock
  private HttpServletRequest request;

  private final BaseController controller = new BaseController();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "observationWrapper", observationWrapper);
    ReflectionTestUtils.setField(controller, "request", request);
  }

  private <T> Response<T> response(String code, T data) {
    return Response.<T>builder()
        .response(Response.ResponseMetadata.builder()
            .code(code)
            .description(code)
            .build())
        .data(data)
        .build();
  }

  @Test
  @DisplayName("success response returns 200 and tags the request observation with the responseCode")
  void toResponse_success_tagsObservationWithResponseCode() {
    TestObservationRegistry registry = TestObservationRegistry.create();
    Observation observation = Observation.start("test.observation", registry);
    when(observationWrapper.getObservation(request)).thenReturn(observation);

    ResponseEntity<Response<String>> entity =
        controller.toResponse(response(ApiResponseCode.SUCCESS.getCode(), "hello"));

    observation.stop();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo("test.observation")
        .that()
        .hasLowCardinalityKeyValue("responseCode", ApiResponseCode.SUCCESS.getCode());
  }

  @Test
  @DisplayName("non-success response returns 400 and records the error on the observation")
  void toResponse_error_recordsError() {
    TestObservationRegistry registry = TestObservationRegistry.create();
    Observation observation = Observation.start("test.observation", registry);
    when(observationWrapper.getObservation(request)).thenReturn(observation);

    ResponseEntity<Response<String>> entity =
        controller.toResponse(response(ApiResponseCode.BAD_REQUEST.getCode(), null));

    observation.stop();

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo("test.observation")
        .that()
        .hasLowCardinalityKeyValueWithKey("error")
        .hasError();
  }

  @Test
  @DisplayName("does not throw when no observation is present for the request")
  void toResponse_noObservation_doesNotThrow() {
    when(observationWrapper.getObservation(request)).thenReturn(null);

    ResponseEntity<Response<String>> entity =
        controller.toResponse(response(ApiResponseCode.SUCCESS.getCode(), "hello"));

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
