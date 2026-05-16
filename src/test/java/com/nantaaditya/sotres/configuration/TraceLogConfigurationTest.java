package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.properties.LogProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;

@DisplayName("TraceLogConfiguration")
@ExtendWith(MockitoExtension.class)
class TraceLogConfigurationTest {

  @Mock private LogProperties logProperties;
  @Mock private TracerHelper tracerHelper;
  @Mock private HttpExchange trace;
  @Mock private HttpExchange.Request request;
  @Mock private HttpExchange.Response response;

  private TraceLogConfiguration config;

  @BeforeEach
  void setUp() {
    config = new TraceLogConfiguration(logProperties, tracerHelper);
  }

  @Test
  @DisplayName("findAll returns singleton list with null before any trace is added")
  void findAll_returnsListWithNullInitially() {
    assertThat(config.findAll()).hasSize(1).containsOnlyNulls();
  }

  @Test
  @DisplayName("add returns early when trace log is disabled")
  void add_whenTraceLogDisabled_returnsEarly() {
    when(trace.getRequest()).thenReturn(request);
    when(trace.getResponse()).thenReturn(response);
    when(logProperties.enableTraceLog()).thenReturn(false);

    config.add(trace);

    assertThat(config.findAll()).containsOnlyNulls();
  }

  @Test
  @DisplayName("add returns early when path is in ignored list")
  void add_whenPathIgnored_returnsEarly() {
    when(trace.getRequest()).thenReturn(request);
    when(trace.getResponse()).thenReturn(response);
    when(logProperties.enableTraceLog()).thenReturn(true);
    when(request.getUri()).thenReturn(URI.create("http://localhost/actuator/health"));
    when(logProperties.isIgnoredPath("/actuator/health")).thenReturn(true);

    config.add(trace);

    assertThat(config.findAll()).containsOnlyNulls();
  }

  @Test
  @DisplayName("add processes request and response when enabled and path is not ignored")
  void add_whenEnabledAndPathNotIgnored_processesWithoutException() {
    when(trace.getRequest()).thenReturn(request);
    when(trace.getResponse()).thenReturn(response);
    when(trace.getTimeTaken()).thenReturn(Duration.ofMillis(100));
    when(logProperties.enableTraceLog()).thenReturn(true);
    when(request.getUri()).thenReturn(URI.create("http://localhost/api/payment"));
    when(request.getHeaders()).thenReturn(Collections.emptyMap());
    when(request.getMethod()).thenReturn("POST");
    when(response.getStatus()).thenReturn(200);
    when(logProperties.isIgnoredPath("/api/payment")).thenReturn(false);

    config.add(trace);
  }
}
