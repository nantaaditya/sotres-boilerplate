package com.nantaaditya.sotres.interceptor;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.EventLogHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.response.Response;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * End-to-end proof that the Observation AppFilter writes into Reactor Context
 * is actually readable inside a controller reached through Spring WebFlux's
 * real WebFilter -> WebHandler dispatch chain — not just a mocked
 * WebFilterChain (AppFilterTest) or a hand-wired Context (BaseControllerTest).
 * This is the one link in the propagation chain those two test classes
 * cannot prove on their own.
 *
 * Uses WebTestClient.bindToWebHandler(...) with a manually-built
 * RouterFunction rather than @WebFluxTest or bindToController(...):
 * - @WebFluxTest bootstraps the full Spring context, which fails to load in
 *   isolation here due to an unrelated pre-existing
 *   ParticipantConfigurationProperties binding issue on the app's main
 *   configuration class.
 * - bindToController(...) re-creates the controller as a Spring bean
 *   internally and reprocesses its @Autowired fields (inherited from
 *   BaseController), discarding any value set via ReflectionTestUtils
 *   beforehand and failing with UnsatisfiedDependencyException.
 * bindToWebHandler bypasses Spring's bean factory entirely, so neither
 * problem applies, while still exercising the real WebFilter -> WebHandler
 * dispatch machinery that AppFilter and BaseController#toResponse rely on.
 */
@DisplayName("AppFilter + real controller dispatch (Observation propagation)")
@ExtendWith(MockitoExtension.class)
class AppFilterObservationIntegrationTest {

  @Mock
  private EventLogHelper eventLogHelper;
  @Mock
  private ContextHelper contextHelper;
  @Mock
  private TracerHelper tracerHelper;

  private TestObservationRegistry observationRegistry;
  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    observationRegistry = TestObservationRegistry.create();

    AppFilter filter = new AppFilter();
    ReflectionTestUtils.setField(filter, "contextPath", "/api");
    ReflectionTestUtils.setField(filter, "eventLogHelper", eventLogHelper);
    ReflectionTestUtils.setField(filter, "contextHelper", contextHelper);
    ReflectionTestUtils.setField(filter, "tracerHelper", tracerHelper);
    ReflectionTestUtils.setField(filter, "observationRegistry", (ObservationRegistry) observationRegistry);

    PingController pingController = new PingController();

    RouterFunction<ServerResponse> route = RouterFunctions.route()
        .GET("/ping", request -> pingController.ping()
            .flatMap(entity -> ServerResponse.status(entity.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity.getBody())))
        .build();

    webTestClient = WebTestClient.bindToWebHandler(RouterFunctions.toWebHandler(route))
        .webFilter(filter)
        .build();
  }

  @Test
  @DisplayName("Observation started by AppFilter is readable inside the controller and tagged with the responseCode")
  void realDispatch_observationWrittenByFilter_isReadableInController() {
    webTestClient.get()
        .uri("/ping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.data").isEqualTo("pong");

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasObservationWithNameEqualTo(ObservationConstant.API_PUBLIC.getName())
        .that()
        .hasLowCardinalityKeyValue("responseCode", ApiResponseCode.SUCCESS.getCode());
  }

  /**
   * Deliberately builds the Response directly instead of depending on
   * ResponseHelper (which itself needs TracerHelper/ContextHelper) — this
   * test's only concern is proving BaseController#toResponse reads the
   * Observation correctly through a real dispatch chain, not exercising
   * ResponseHelper's own logic.
   */
  static class PingController extends BaseController {

    public Mono<org.springframework.http.ResponseEntity<Response<String>>> ping() {
      Response<String> response = Response.<String>builder()
          .response(Response.ResponseMetadata.builder()
              .code(ApiResponseCode.SUCCESS.getCode())
              .description(ApiResponseCode.SUCCESS.getMessage())
              .build())
          .data("pong")
          .build();
      return toResponse(response);
    }
  }
}
