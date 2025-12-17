package com.nantaaditya.sotres.client;

import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import reactor.core.publisher.Mono;

@Log4j2
@Component
public class TransactionClient extends BaseClient {

  private final SystemPropertiesService systemPropertiesService;
  private final ClientProperties clientProperties;
  private final ClientConfiguration clientConfiguration;
  private final WebClient webClient;

  @Value("${spring.application.name}")
  private String applicationName;

  public TransactionClient(SystemPropertiesService systemPropertiesService, Logbook logbook,
      ClientProperties clientProperties) {

    this.systemPropertiesService = systemPropertiesService;
    this.clientProperties = clientProperties;
    this.clientConfiguration = this.clientProperties.getConfiguration("transaction");
    this.webClient = createWebClient(logbook, this.clientConfiguration);

    log.info(AppLogMessage.message(
        "#Client - create transaction client with configuration: hostname {}, connect time out {}ms, read time out {}ms, write time out {}ms",
        this.clientConfiguration.hostname(), this.clientConfiguration.clientConnectTimeOut(),
        this.clientConfiguration.clientReadTimeOut(),
        this.clientConfiguration.clientWriteTimeOut())
    );
  }

  // TODO: mapping outgoing request from internal DTO to external spec using JOLT
  public Mono<ResponseContext> send(RequestContext requestContext) {
    Mono<ResponseContext> response = webClient.post()
      .uri(uriBuilder -> uriBuilder
          .path(getPath(requestContext))
          .build()
      )
      .headers(httpHeaders -> {
        httpHeaders.set(X_CLIENT_ID, applicationName);
        httpHeaders.set(X_REQUEST_ID, requestContext.getRrn());
        httpHeaders.set(X_REQUEST_TIME, DateTimeHelper.getDateInFormat(ZonedDateTime.now(),
            DateTimeHelper.ISO_8601_GMT7_FORMAT));
      })
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .body(BodyInserters.fromValue(requestContext))
      .exchangeToMono(clientResponse -> {
        if (clientResponse.statusCode().is2xxSuccessful()) {
          return clientResponse.bodyToMono(ResponseContext.class);
        } else if (clientResponse.statusCode().is4xxClientError()) {
          log.error(AppLogMessage.message("#Transaction - got http status {} from client", clientResponse.statusCode()));
          return clientResponse.bodyToMono(ResponseContext.class);
        } else {
          return clientResponse.createException()
              .flatMap(Mono::error);
        }
      });

    if (isNeedRetryable(clientConfiguration)) {
      return response
          .retryWhen(getRetryCondition(clientConfiguration));
    }

    return response;
  }

  private String getPath(RequestContext requestContext) {
    return PropertiesGroup.getMap(systemPropertiesService, PropertiesGroup.PATH_MAPPING)
        .get(requestContext.getSelector());
  }

}
