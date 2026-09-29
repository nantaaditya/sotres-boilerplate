package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.RetryConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Blocking outbound HTTP sender built on {@link RestClient}. Modelled on the
 * spring-boilerplate-non-reactive {@code RestSender}, trimmed to what sotres needs
 * (single body type, optional {@link RetryTemplate}-backed retry).
 *
 * <p>2xx and 4xx responses are returned to the caller (the body is mapped); any other status
 * raises {@code HttpServerErrorException}. {@link #executeWithRetry} delegates the attempt loop,
 * backoff and exception classification to the configured {@link RetryTemplate}, and seeds the
 * {@link RetryContext} with request metadata so {@code RestSenderRetryListener} can dead-letter an
 * exhausted call.
 */
@Log4j2
public final class RestSender {

  private static final int DEFAULT_MAX_ATTEMPTS = 1;

  private final String name;
  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final int maxAttempts;
  private final boolean deadLetterEnabled;

  private RestSender(Builder builder) {
    this.name = builder.name;
    this.restClient = builder.restClient;
    this.retryTemplate = builder.retryTemplate;
    this.maxAttempts = builder.maxAttempts;
    this.deadLetterEnabled = builder.deadLetterEnabled;
  }

  public <S, T> ResponseEntity<T> execute(HttpMethod method, String apiPath, HttpHeaders headers,
      S body, ParameterizedTypeReference<T> responseType) {
    return call(method, apiPath, headers, body, responseType);
  }

  /**
   * @param processName logical name of the outbound call, used by the retry listener for
   *     dead-letter classification.
   */
  public <S, T> ResponseEntity<T> executeWithRetry(HttpMethod method, String apiPath,
      HttpHeaders headers, S body, ParameterizedTypeReference<T> responseType, String processName) {

    if (retryTemplate == null) {
      throw new IllegalStateException(
          String.format("#Client - [%s] retryTemplate not configured", name));
    }

    RetryCallback<ResponseEntity<T>, RuntimeException> retryable = context -> {
      seedRetryContext(context, method, apiPath, headers, body, processName);
      try {
        return call(method, apiPath, headers, body, responseType);
      } catch (RestClientResponseException e) {
        context.setAttribute(RetryConstant.RESPONSE.key(), e.getResponseBodyAsString());
        throw e;
      }
    };
    return retryTemplate.execute(retryable);
  }

  private void seedRetryContext(RetryContext context, HttpMethod method, String apiPath,
      HttpHeaders headers, Object body, String processName) {
    context.setAttribute(RetryConstant.CLIENT_NAME.key(), name);
    context.setAttribute(RetryConstant.METHOD.key(), method.name());
    context.setAttribute(RetryConstant.PATH.key(), apiPath);
    context.setAttribute(RetryConstant.HEADERS.key(), headers);
    context.setAttribute(RetryConstant.PROCESS_TYPE.key(), "client");
    context.setAttribute(RetryConstant.PROCESS_NAME.key(), processName);
    context.setAttribute(RetryConstant.MAX_ATTEMPTS.key(), maxAttempts);
    context.setAttribute(RetryConstant.DEAD_LETTER_ENABLED.key(), deadLetterEnabled);
    if (body != null) {
      context.setAttribute(RetryConstant.REQUEST.key(), body);
    }
    String requestId = headers == null
        ? null
        : headers.getFirst(HeaderConstant.REQUEST_ID.getHeader());
    if (requestId != null) {
      context.setAttribute(RetryConstant.REQUEST_ID.key(), requestId);
    }
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

  public static final class Builder {
    private final String name;
    private final RestClient restClient;
    private RetryTemplate retryTemplate;
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private boolean deadLetterEnabled;

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

    public Builder retryTemplate(RetryTemplate retryTemplate) {
      this.retryTemplate = retryTemplate;
      return this;
    }

    /**
     * @param maxAttempts total execution count backing the retry-listener's exhaustion check;
     *     stamped onto the {@link RetryContext} on every attempt.
     */
    public Builder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    /** Whether an exhausted call should be dead-lettered rather than only logged. */
    public Builder deadLetterEnabled(boolean deadLetterEnabled) {
      this.deadLetterEnabled = deadLetterEnabled;
      return this;
    }

    public RestSender build() {
      return new RestSender(this);
    }
  }
}
