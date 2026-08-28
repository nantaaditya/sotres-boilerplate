package com.nantaaditya.sotres.client;

import com.nantaaditya.sotres.helper.RestSender;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

public class BaseClient {

  protected RestSender createRestSender(String name, Logbook logbook,
      ClientConfiguration clientConfiguration) {
    return new RestSender.Builder(name, createRestClient(logbook, clientConfiguration))
        .retryConfiguration(clientConfiguration.retryConfiguration())
        .build();
  }

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
