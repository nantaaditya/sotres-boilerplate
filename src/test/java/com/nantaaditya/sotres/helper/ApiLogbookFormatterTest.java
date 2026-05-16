package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.nantaaditya.sotres.configuration.GsonConfiguration;
import com.nantaaditya.sotres.properties.LogProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Origin;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.attributes.HttpAttributes;

@DisplayName("ApiLogbookFormatter")
@ExtendWith(MockitoExtension.class)
class ApiLogbookFormatterTest {

  @Mock
  private LogProperties logProperties;
  @Mock
  private Precorrelation precorrelation;
  @Mock
  private Correlation correlation;
  @Mock
  private HttpRequest request;
  @Mock
  private HttpResponse response;
  @Mock
  private HttpHeaders headers;

  private ApiLogbookFormatter formatter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    Gson gson = new GsonConfiguration().gson();
    formatter = new ApiLogbookFormatter(objectMapper, gson, logProperties);
  }

  @Test
  @DisplayName("format request returns JSON with origin, type, correlation, method, and uri")
  void format_request_returnsJsonWithRequiredFields() throws IOException {
    when(precorrelation.getId()).thenReturn("corr-123");
    when(request.getOrigin()).thenReturn(Origin.LOCAL);
    when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(request.getRemote()).thenReturn("127.0.0.1");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestUri()).thenReturn("http://localhost/api/payment");
    when(request.getHost()).thenReturn("localhost");
    when(request.getPath()).thenReturn("/api/payment");
    when(request.getScheme()).thenReturn("http");
    when(request.getPort()).thenReturn(Optional.of(8080));
    when(request.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBodyAsString()).thenReturn("");
    when(request.getContentType()).thenReturn("application/json");

    String result = formatter.format(precorrelation, request);

    Map<String, Object> parsed = objectMapper.readValue(result, Map.class);
    assertThat(parsed)
        .containsEntry("origin", "local")
        .containsEntry("type", "request")
        .containsEntry("correlation", "corr-123")
        .containsEntry("method", "POST")
        .containsEntry("uri", "http://localhost/api/payment")
        .containsEntry("port", "8080");
  }

  @Test
  @DisplayName("format request with no port produces null port in JSON")
  void format_request_withNoPort_producesNullPort() throws IOException {
    when(precorrelation.getId()).thenReturn("corr-no-port");
    when(request.getOrigin()).thenReturn(Origin.LOCAL);
    when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(request.getRemote()).thenReturn("127.0.0.1");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestUri()).thenReturn("https://localhost/health");
    when(request.getHost()).thenReturn("localhost");
    when(request.getPath()).thenReturn("/health");
    when(request.getScheme()).thenReturn("https");
    when(request.getPort()).thenReturn(Optional.empty());
    when(request.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBodyAsString()).thenReturn("");
    when(request.getContentType()).thenReturn("application/json");

    String result = formatter.format(precorrelation, request);

    Map<String, Object> parsed = objectMapper.readValue(result, Map.class);
    assertThat(parsed).containsEntry("port", null);
  }

  @Test
  @DisplayName("format request with non-JSON body includes raw body string")
  void format_request_withNonJsonBody_includesRawBodyString() throws IOException {
    when(precorrelation.getId()).thenReturn("corr-text");
    when(request.getOrigin()).thenReturn(Origin.LOCAL);
    when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(request.getRemote()).thenReturn("127.0.0.1");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestUri()).thenReturn("http://localhost/text");
    when(request.getHost()).thenReturn("localhost");
    when(request.getPath()).thenReturn("/text");
    when(request.getScheme()).thenReturn("http");
    when(request.getPort()).thenReturn(Optional.empty());
    when(request.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBodyAsString()).thenReturn("plain text body");
    when(request.getContentType()).thenReturn("text/plain");

    String result = formatter.format(precorrelation, request);

    assertThat(result).contains("plain text body");
  }

  @Test
  @DisplayName("format request with JSON body includes body field")
  void format_request_withJsonBody_includesBodyField() throws IOException {
    when(precorrelation.getId()).thenReturn("corr-json");
    when(request.getOrigin()).thenReturn(Origin.REMOTE);
    when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(request.getRemote()).thenReturn("10.0.0.1");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestUri()).thenReturn("http://localhost/api");
    when(request.getHost()).thenReturn("localhost");
    when(request.getPath()).thenReturn("/api");
    when(request.getScheme()).thenReturn("http");
    when(request.getPort()).thenReturn(Optional.empty());
    when(request.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBodyAsString()).thenReturn("{\"amount\":100}");
    when(request.getContentType()).thenReturn("application/json");
    when(logProperties.getSensitiveFields()).thenReturn(Set.of());

    String result = formatter.format(precorrelation, request);

    assertThat(result).contains("body");
  }

  @Test
  @DisplayName("format response returns JSON with status and duration")
  void format_response_returnsJsonWithStatusAndDuration() throws IOException {
    when(correlation.getId()).thenReturn("corr-resp");
    when(correlation.getDuration()).thenReturn(Duration.ofMillis(150));
    when(response.getOrigin()).thenReturn(Origin.LOCAL);
    when(response.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(response.getStatus()).thenReturn(200);
    when(response.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(response.getHeaders()).thenReturn(headers);
    when(response.getBodyAsString()).thenReturn("");
    when(response.getContentType()).thenReturn("application/json");

    String result = formatter.format(correlation, response);

    Map<String, Object> parsed = objectMapper.readValue(result, Map.class);
    assertThat(parsed)
        .containsEntry("type", "response")
        .containsEntry("correlation", "corr-resp")
        .containsEntry("status", 200)
        .containsEntry("duration", 150);
  }

  @Test
  @DisplayName("format request with sensitive header masks header value")
  void format_request_withSensitiveHeader_masksHeaderValue() throws IOException {
    when(headers.entrySet())
        .thenReturn(Set.of(Map.entry("authorization", List.of("Bearer super-secret-token"))));
    when(headers.isEmpty()).thenReturn(false);

    when(precorrelation.getId()).thenReturn("corr-mask");
    when(request.getOrigin()).thenReturn(Origin.LOCAL);
    when(request.getProtocolVersion()).thenReturn("HTTP/1.1");
    when(request.getRemote()).thenReturn("127.0.0.1");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestUri()).thenReturn("http://localhost/api");
    when(request.getHost()).thenReturn("localhost");
    when(request.getPath()).thenReturn("/api");
    when(request.getScheme()).thenReturn("http");
    when(request.getPort()).thenReturn(Optional.empty());
    when(request.getAttributes()).thenReturn(HttpAttributes.EMPTY);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getBodyAsString()).thenReturn("");
    when(request.getContentType()).thenReturn("application/json");
    when(logProperties.isSensitiveFields("authorization")).thenReturn(true);

    String result = formatter.format(precorrelation, request);

    assertThat(result).doesNotContain("super-secret-token");
    assertThat(result).contains("authorization");
  }
}
