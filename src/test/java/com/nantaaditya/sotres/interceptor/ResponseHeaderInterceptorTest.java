package com.nantaaditya.sotres.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

@DisplayName("ResponseHeaderInterceptor")
class ResponseHeaderInterceptorTest {

  private final ResponseHeaderInterceptor advice = new ResponseHeaderInterceptor();

  @Test
  @DisplayName("supports every controller return type")
  void supports_returnsTrue() {
    assertThat(advice.supports(null, null)).isTrue();
  }

  @Test
  @DisplayName("adds the x-response-time header and returns the body unchanged")
  void beforeBodyWrite_addsResponseTimeHeader_andPassesBodyThrough() {
    HttpHeaders headers = new HttpHeaders();
    ServerHttpResponse response = mock(ServerHttpResponse.class);
    when(response.getHeaders()).thenReturn(headers);
    Object body = new Object();

    Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null,
        mock(ServerHttpRequest.class), response);

    assertThat(result).isSameAs(body);
    assertThat(headers.getFirst("x-response-time")).isNotBlank();
  }
}
