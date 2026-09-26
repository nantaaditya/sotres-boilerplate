package com.nantaaditya.sotres.client;

import com.nantaaditya.sotres.helper.RestSender;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import java.time.Duration;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

/**
 * Shared factory for outbound blocking HTTP clients. Concrete clients (e.g. {@link TransactionClient})
 * extend this and call {@link #createRestSender} once in their constructor.
 */
public class BaseClient {

  /**
   * @param name          logical client name (also the {@code apps.retry.configurations.<name>} key)
   * @param retryTemplate the per-client {@code RetryTemplate}, or {@code null} for a client that
   *     only ever calls {@code RestSender.execute(...)} (no retry)
   * @param retryConfiguration the source config backing {@code retryTemplate} ({@code maxAttempt}
   *     and {@code deadLetterEnabled} are stamped onto the {@code RetryContext} on every attempt
   *     for the shared {@code RestSenderRetryListener} to read back), or {@code null} when
   *     {@code retryTemplate} is {@code null}
   */
  protected RestSender createRestSender(String name, Logbook logbook,
      ClientConfiguration clientConfiguration, RetryTemplate retryTemplate,
      RetryConfiguration retryConfiguration) {
    RestSender.Builder builder = new RestSender.Builder(
          name,
          createRestClient(logbook, clientConfiguration)
        )
        .retryTemplate(retryTemplate);

    if (retryConfiguration != null) {
      builder
          .maxAttempts(Math.max(1, retryConfiguration.maxAttempt()))
          .deadLetterEnabled(retryConfiguration.deadLetterEnabled());
    }
    return builder.build();
  }

  protected RestClient createRestClient(Logbook logbook, ClientConfiguration clientConfiguration) {
    HttpClientSettings settings = getSettings(clientConfiguration);

    ClientHttpRequestFactory requestFactory = getRequestFactory(clientConfiguration, settings);

    return RestClient.builder()
        .baseUrl(clientConfiguration.hostname())
        .requestFactory(requestFactory)
        .requestInterceptor(new LogbookClientHttpRequestInterceptor(logbook))
        .build();
  }

  private HttpComponentsClientHttpRequestFactory getRequestFactory(
      ClientConfiguration clientConfiguration, HttpClientSettings settings) {
    return ClientHttpRequestFactoryBuilder.httpComponents()
        .withConnectionManagerCustomizer(manager -> manager
            .setMaxConnTotal(clientConfiguration.maxConnections())
            .setMaxConnPerRoute(clientConfiguration.maxConnections())
            .setConnectionTimeToLive(TimeValue.ofMilliseconds(clientConfiguration.maxLifeTime()))
        )
        .withSocketConfigCustomizer(socketConfig -> socketConfig
            .setSoTimeout(Timeout.ofMilliseconds(clientConfiguration.clientReadTimeOut()))
        )
        .withDefaultRequestConfigCustomizer(requestConfig -> requestConfig
            .setConnectionRequestTimeout(
                Timeout.ofMilliseconds(clientConfiguration.pendingAcquireTimeOut())
            )
        )
        .withHttpClientCustomizer(builder -> builder
            .evictIdleConnections(TimeValue.ofMilliseconds(clientConfiguration.maxIdleTime()))
            .evictExpiredConnections()
        )
        .build(settings);
  }

  private HttpClientSettings getSettings(
      ClientConfiguration clientConfiguration) {
    return HttpClientSettings.defaults()
        .withConnectTimeout(Duration.ofMillis(clientConfiguration.clientConnectTimeOut()))
        .withReadTimeout(Duration.ofMillis(clientConfiguration.clientReadTimeOut()));
  }
}
