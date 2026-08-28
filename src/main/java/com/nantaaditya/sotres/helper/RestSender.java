package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Blocking outbound HTTP sender built on {@link RestClient}. Modelled on the
 * spring-boilerplate-non-reactive {@code RestSender}, trimmed to what sotres
 * needs (single body type, optional bounded retry).
 *
 * <p>2xx and 4xx responses are returned to the caller (the body is mapped); any
 * other status raises {@code res.createException()}.
 */
@Log4j2
public final class RestSender {

  private final String name;
  private final RestClient restClient;
  private final RetryConfiguration retryConfiguration;

  private RestSender(Builder builder) {
    this.name = builder.name;
    this.restClient = builder.restClient;
    this.retryConfiguration = builder.retryConfiguration;
  }

  public <S, T> ResponseEntity<T> execute(HttpMethod method, String apiPath, HttpHeaders headers,
      S body, ParameterizedTypeReference<T> responseType) {
    return call(method, apiPath, headers, body, responseType);
  }

  public <S, T> ResponseEntity<T> executeWithRetry(HttpMethod method, String apiPath,
      HttpHeaders headers, S body, ParameterizedTypeReference<T> responseType) {

    int totalAttempts = retryConfiguration == null
        ? 1
        : Math.max(1, retryConfiguration.maxAttempt()) + 1;

    RuntimeException last = null;
    for (int attempt = 1; attempt <= totalAttempts; attempt++) {
      try {
        return call(method, apiPath, headers, body, responseType);
      } catch (RuntimeException e) {
        last = e;
        boolean canRetry = retryConfiguration != null
            && attempt < totalAttempts
            && retryConfiguration.isRetryable(e.getClass());
        if (!canRetry) {
          throw e;
        }
        log.warn(AppLogMessage.message("#Client - [{}] attempt {}/{} failed, retrying: {}",
            name, attempt, totalAttempts, e.getMessage()));
        sleep(retryConfiguration.minBackOff());
      }
    }
    throw last;
  }

  private <S, T> ResponseEntity<T> call(HttpMethod method, String apiPath, HttpHeaders headers,
      S body, ParameterizedTypeReference<T> responseType) {

    RestClient.RequestBodySpec spec = restClient
        .method(method)
        .uri(uriBuilder -> uriBuilder.path(apiPath).build())
        .headers(h -> h.addAll(headers));

    if (body != null) {
      spec = spec.body(body);
    }

    return spec.exchange((request, response) -> {
      HttpStatusCode status = response.getStatusCode();
      if (status.is2xxSuccessful() || status.is4xxClientError()) {
        if (status.is4xxClientError()) {
          log.error(AppLogMessage.message("#Client - [{}] got http status {}", name, status.value()));
        }
        return ResponseEntity.status(status).body(response.bodyTo(responseType));
      }
      byte[] errorBody = response.bodyTo(byte[].class);
      log.error(AppLogMessage.message("#Client - [{}] got http status {}", name, status.value()));
      throw HttpServerErrorException.create(status, status.toString(), response.getHeaders(),
          errorBody != null ? errorBody : new byte[0], StandardCharsets.UTF_8);
    });
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(Math.max(0, millis));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("retry backoff interrupted", e);
    }
  }

  public static final class Builder {
    private final String name;
    private final RestClient restClient;
    private RetryConfiguration retryConfiguration;

    public Builder(String name, RestClient restClient) {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("#Client - name must not be blank");
      }
      if (restClient == null) {
        throw new IllegalArgumentException("#Client - restClient must not be null");
      }
      this.name = name;
      this.restClient = restClient;
    }

    public Builder retryConfiguration(RetryConfiguration retryConfiguration) {
      this.retryConfiguration = retryConfiguration;
      return this;
    }

    public RestSender build() {
      return new RestSender(this);
    }
  }
}
