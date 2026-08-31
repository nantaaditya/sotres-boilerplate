package com.nantaaditya.sotres.client;

import com.nantaaditya.sotres.helper.RestSender;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
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
   */
  protected RestSender createRestSender(String name, Logbook logbook,
      ClientConfiguration clientConfiguration, RetryTemplate retryTemplate) {
    return new RestSender.Builder(name, createRestClient(logbook, clientConfiguration))
        .retryTemplate(retryTemplate)
        .build();
  }

  /** JDK {@code HttpClient} (HTTP/1.1) behind a {@code RestClient}, with the logbook interceptor for I/O logging. */
  protected RestClient createRestClient(Logbook logbook, ClientConfiguration clientConfiguration) {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(Duration.ofMillis(clientConfiguration.clientConnectTimeOut()))
        .withReadTimeout(Duration.ofMillis(clientConfiguration.clientReadTimeOut()));

    ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
        .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
        .build(settings);

    return RestClient.builder()
        .baseUrl(clientConfiguration.hostname())
        .requestFactory(requestFactory)
        .requestInterceptor(new LogbookClientHttpRequestInterceptor(logbook))
        .build();
  }
}
