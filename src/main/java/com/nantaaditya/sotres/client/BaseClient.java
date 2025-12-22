package com.nantaaditya.sotres.client;

import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public class BaseClient {

  protected WebClient createWebClient(Logbook logbook, ClientConfiguration clientConfiguration) {
    ConnectionProvider provider = ConnectionProvider.builder("custom-webclient")
        .maxConnections(clientConfiguration.maxConnections())
        .maxIdleTime(Duration.ofMillis(clientConfiguration.maxIdleTime()))
        .maxLifeTime(Duration.ofMillis(clientConfiguration.maxLifeTime()))
        .evictInBackground(Duration.ofMillis(clientConfiguration.evictInBackground()))
        .pendingAcquireTimeout(Duration.ofMillis(clientConfiguration.pendingAcquireTimeOut()))
        .build();

    HttpClient httpClient = HttpClient.create(provider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfiguration.clientConnectTimeOut())
        .responseTimeout(Duration.ofMillis(clientConfiguration.clientReadTimeOut()))
        .doOnConnected(connection -> connection
            .addHandlerFirst(new ReadTimeoutHandler(
                clientConfiguration.clientReadTimeOut(),
                clientConfiguration.timeUnit())
            )
            .addHandlerFirst(new WriteTimeoutHandler(
                clientConfiguration.clientWriteTimeOut(),
                clientConfiguration.timeUnit())
            )
            .addHandlerLast(new LogbookClientHandler(logbook))
        );

    return WebClient.builder()
        .baseUrl(clientConfiguration.hostname())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }

  protected static RetryBackoffSpec getRetryCondition(ClientConfiguration clientConfiguration) {
    RetryConfiguration retryConfiguration = clientConfiguration.retryConfiguration();
    if (retryConfiguration == null) {
      return null;
    }
    return Retry.backoff(retryConfiguration.maxAttempt(), Duration.ofSeconds(retryConfiguration.minBackOff()))
        .filter(throwable -> retryConfiguration.isRetryable(throwable.getClass()));
  }
}
