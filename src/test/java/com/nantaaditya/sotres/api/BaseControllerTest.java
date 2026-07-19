package com.nantaaditya.sotres.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.response.Response;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Verifies the Observation set into Reactor Context by AppFilter (see
 * AppFilter#filter) is correctly readable inside toResponse() even after a
 * real thread hop — the exact scenario the old ThreadLocal-based
 * ObservationWrapper could not guarantee.
 */
@DisplayName("BaseController")
class BaseControllerTest {

  private final BaseController controller = new BaseController();

  @Test
  @DisplayName("observation set upstream via contextWrite survives a real thread hop and gets tagged with the responseCode")
  void toResponse_successAfterThreadHop_tagsObservationWithResponseCode() {
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    Observation observation = Observation.start("test.observation", observationRegistry);

    String callingThread = Thread.currentThread().getName();
    AtomicReference<String> executionThread = new AtomicReference<>();

    Response<String> successResponse = Response.<String>builder()
        .response(Response.ResponseMetadata.builder()
            .code(ApiResponseCode.SUCCESS.getCode())
            .description(ApiResponseCode.SUCCESS.getMessage())
            .build())
        .data("hello")
        .build();

    Mono<ResponseEntity<Response<String>>> result = Mono.just(successResponse)
        // force a real thread hop, mirroring what an R2DBC/WebClient async boundary does in production
        .publishOn(Schedulers.boundedElastic())
        .flatMap(response -> {
          executionThread.set(Thread.currentThread().getName());
          return controller.toResponse(response);
        })
        .contextWrite(ctx -> ctx.put(Observation.class, observation));

    StepVerifier.create(result)
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();

    observation.stop();

    assertThat(executionThread.get())
        .as("toResponse must actually execute on a different thread than the caller for this test to be meaningful")
        .isNotEqualTo(callingThread);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasObservationWithNameEqualTo("test.observation")
        .that()
        .hasLowCardinalityKeyValue("responseCode", ApiResponseCode.SUCCESS.getCode());
  }

  @Test
  @DisplayName("observation set upstream via contextWrite survives a real thread hop and records the error for a non-success response")
  void toResponse_errorAfterThreadHop_tagsObservationWithError() {
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    Observation observation = Observation.start("test.observation", observationRegistry);

    Response<String> failedResponse = Response.<String>builder()
        .response(Response.ResponseMetadata.builder()
            .code(ApiResponseCode.BAD_REQUEST.getCode())
            .description(ApiResponseCode.BAD_REQUEST.getMessage())
            .build())
        .build();

    Mono<ResponseEntity<Response<String>>> result = Mono.just(failedResponse)
        .publishOn(Schedulers.boundedElastic())
        .flatMap(controller::toResponse)
        .contextWrite(ctx -> ctx.put(Observation.class, observation));

    StepVerifier.create(result)
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
        .verifyComplete();

    observation.stop();

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasObservationWithNameEqualTo("test.observation")
        .that()
        .hasLowCardinalityKeyValueWithKey("error")
        .hasError();
  }

  @Test
  @DisplayName("does not throw when no observation was written into the Reactor context")
  void toResponse_noObservationInContext_doesNotThrow() {
    Response<String> successResponse = Response.<String>builder()
        .response(Response.ResponseMetadata.builder()
            .code(ApiResponseCode.SUCCESS.getCode())
            .description(ApiResponseCode.SUCCESS.getMessage())
            .build())
        .data("hello")
        .build();

    StepVerifier.create(controller.toResponse(successResponse))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();
  }
}
