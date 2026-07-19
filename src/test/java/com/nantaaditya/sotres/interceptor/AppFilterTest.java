package com.nantaaditya.sotres.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.EventLogHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("AppFilter")
@ExtendWith(MockitoExtension.class)
class AppFilterTest {

  @Mock
  private EventLogHelper eventLogHelper;
  @Mock
  private ContextHelper contextHelper;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private WebFilterChain chain;

  private AppFilter filter;

  @BeforeEach
  void setUp() {
    filter = new AppFilter();
    ReflectionTestUtils.setField(filter, "contextPath", "/api");
    ReflectionTestUtils.setField(filter, "eventLogHelper", eventLogHelper);
    ReflectionTestUtils.setField(filter, "contextHelper", contextHelper);
    ReflectionTestUtils.setField(filter, "tracerHelper", tracerHelper);
    ReflectionTestUtils.setField(filter, "observationRegistry", ObservationRegistry.NOOP);
  }

  @Test
  @DisplayName("filter with body completes and saves event log")
  void filter_withBody_completesAndSavesEventLog() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/payment")
            .header("x-client-id", "client-001")
            .header("x-request-id", "req-001")
            .body("{\"amount\": 100}")
    );
    when(chain.filter(any())).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    verify(eventLogHelper).save(any(ServerWebExchange.class), any(ContextDTO.class));
  }

  @Test
  @DisplayName("filter with empty body still completes and saves event log")
  void filter_withEmptyBody_completesAndSavesEventLog() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/payment")
            .header("x-request-id", "req-002")
            .build()
    );
    when(chain.filter(any())).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    verify(eventLogHelper).save(any(ServerWebExchange.class), any(ContextDTO.class));
  }

  @Test
  @DisplayName("filter puts ContextDTO in Reactor context under key 'context'")
  void filter_putsContextDtoInReactorContext() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/health")
            .header("x-request-id", "req-003")
            .build()
    );
    when(chain.filter(any())).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain))
        .expectAccessibleContext()
        .hasKey("context")
        .then()
        .verifyComplete();
  }

  @Test
  @DisplayName("filter puts the started Observation in Reactor context under Observation.class")
  void filter_putsObservationInReactorContext() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/health")
            .header("x-request-id", "req-006")
            .build()
    );
    when(chain.filter(any())).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain))
        .expectAccessibleContext()
        .assertThat(ctx -> assertThat(ctx.get(Observation.class)).isNotNull())
        .then()
        .verifyComplete();
  }

  @Test
  @DisplayName("filter copies request headers to response")
  void filter_copiesRequestHeadersToResponse() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/payment")
            .header("x-client-id", "client-001")
            .header("x-request-id", "req-004")
            .build()
    );
    when(chain.filter(any())).thenReturn(Mono.empty());

    StepVerifier.create(filter.filter(exchange, chain))
        .verifyComplete();

    assertThat(exchange.getResponse().getHeaders().getFirst("x-request-id")).isEqualTo("req-004");
    assertThat(exchange.getResponse().getHeaders().getFirst("x-client-id")).isEqualTo("client-001");
  }

  @Test
  @DisplayName("filter when chain errors still saves event log and propagates error")
  void filter_whenChainErrors_savesEventLogAndPropagatesError() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/payment")
            .header("x-request-id", "req-005")
            .build()
    );
    when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("chain error")));

    StepVerifier.create(filter.filter(exchange, chain))
        .expectError(RuntimeException.class)
        .verify();

    verify(eventLogHelper).save(any(ServerWebExchange.class), any(ContextDTO.class));
  }
}
