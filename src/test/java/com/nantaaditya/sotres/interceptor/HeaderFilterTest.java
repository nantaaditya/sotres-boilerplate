package com.nantaaditya.sotres.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("HeaderFilter")
@ExtendWith(MockitoExtension.class)
class HeaderFilterTest {

  @Mock
  private ContextHelper contextHelper;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private ObservationWrapper observationWrapper;

  private HeaderFilter filter;

  @BeforeEach
  void setUp() {
    filter = new HeaderFilter("/sotres", contextHelper, tracerHelper,
        ObservationRegistry.NOOP, observationWrapper);
  }

  private MockHttpServletRequest request(String method, String uri, byte[] body) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.addHeader("x-client-id", "client-001");
    request.addHeader("x-request-id", "req-001");
    if (body != null) {
      request.setContent(body);
    }
    return request;
  }

  @Test
  @DisplayName("stores ContextDTO on request attribute and in ContextHelper, strips context path")
  void doFilter_storesContextAndStripsContextPath() throws ServletException, IOException {
    MockHttpServletRequest request = request("POST", "/sotres/api/payment", "{\"amount\":100}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    ArgumentCaptor<ContextDTO> captor = ArgumentCaptor.forClass(ContextDTO.class);
    verify(contextHelper).put(captor.capture());
    ContextDTO stored = captor.getValue();
    assertThat(stored.getPath()).isEqualTo("/api/payment");
    assertThat(stored.getRequestId()).isEqualTo("req-001");
    assertThat(request.getAttribute(HeaderFilter.CONTEXT_ATTRIBUTE)).isInstanceOf(ContextDTO.class);
  }

  @Test
  @DisplayName("empty body still completes and stores context")
  void doFilter_emptyBody_completes() throws ServletException, IOException {
    MockHttpServletRequest request = request("POST", "/sotres/api/payment", null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    verify(contextHelper).put(any(ContextDTO.class));
  }

  @Test
  @DisplayName("sets correlation baggage and response headers")
  void doFilter_setsBaggageAndResponseHeaders() throws ServletException, IOException {
    MockHttpServletRequest request = request("GET", "/sotres/api/health", null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    verify(tracerHelper).setBaggage(eq("x-request-id"), eq("req-001"));
    verify(tracerHelper).setBaggage(eq("x-client-id"), eq("client-001"));
    assertThat(response.getHeader("x-request-id")).isEqualTo("req-001");
    assertThat(response.getHeader("x-client-id")).isEqualTo("client-001");
    assertThat(response.getHeader("x-received-time")).isNotBlank();
  }

  @Test
  @DisplayName("starts observation, stores it in the wrapper, clears it afterwards")
  void doFilter_managesObservationLifecycle() throws ServletException, IOException {
    MockHttpServletRequest request = request("GET", "/sotres/api/health", null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    verify(observationWrapper).setObservation(any(), any());
    verify(observationWrapper).clear(any());
  }

  @Test
  @DisplayName("when the chain throws, the error propagates and the observation is still cleared")
  void doFilter_chainThrows_propagatesAndClears() {
    MockHttpServletRequest request = request("POST", "/sotres/api/payment", null);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain throwingChain = new MockFilterChain() {
      @Override
      public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
        throw new RuntimeException("chain error");
      }
    };

    assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("chain error");

    verify(observationWrapper).clear(any());
  }
}
