package com.nantaaditya.sotres.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.zalando.logbook.Logbook;
import reactor.test.StepVerifier;

@DisplayName("TransactionClient")
@ExtendWith(MockitoExtension.class)
class TransactionClientTest {

  private WireMockServer wireMockServer;

  @Mock
  private SystemPropertiesService systemPropertiesService;

  private TransactionClient transactionClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();

    ClientConfiguration clientConfig = new ClientConfiguration(
        "http://localhost:" + wireMockServer.port(),
        50, 60000, 60000, 60000, 30000,
        5000, 30000, 30000,
        TimeUnit.MILLISECONDS,
        null
    );

    ClientProperties clientProperties = new ClientProperties();
    Map<String, ClientConfiguration> configs = new HashMap<>();
    configs.put("transaction", clientConfig);
    clientProperties.setConfigurations(configs);

    Logbook logbook = Logbook.builder().build();

    transactionClient = new TransactionClient(systemPropertiesService, logbook, clientProperties);
    ReflectionTestUtils.setField(transactionClient, "applicationName", "test-app");
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  @DisplayName("send receives 200 and emits ResponseContext with response code")
  void send_receives200_emitsResponseContextWithCode() {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"response\":{\"code\":\"00\",\"description\":\"approved\"}}")
            )
    );
    when(systemPropertiesService.getProperty(PropertiesGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");

    StepVerifier.create(transactionClient.send(buildRequest()))
        .assertNext(ctx -> assertThat(ctx.getResponseCode()).isEqualTo("00"))
        .verifyComplete();
  }

  @Test
  @DisplayName("send receives 4xx and still emits ResponseContext from body")
  void send_receives4xx_emitsResponseContextFromBody() {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"response\":{\"code\":\"96\",\"description\":\"bad request\"}}")
            )
    );
    when(systemPropertiesService.getProperty(PropertiesGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");

    StepVerifier.create(transactionClient.send(buildRequest()))
        .assertNext(ctx -> assertThat(ctx.getResponseCode()).isEqualTo("96"))
        .verifyComplete();
  }

  @Test
  @DisplayName("send receives 5xx and propagates error")
  void send_receives5xx_propagatesError() {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse().withStatus(500))
    );
    when(systemPropertiesService.getProperty(PropertiesGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");

    StepVerifier.create(transactionClient.send(buildRequest()))
        .expectError()
        .verify();
  }

  @Test
  @DisplayName("send sets CLIENT_ID and REQUEST_ID headers on the outgoing request")
  void send_setsClientIdAndRequestIdHeaders() {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"response\":{\"code\":\"00\"}}")
            )
    );
    when(systemPropertiesService.getProperty(PropertiesGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");

    StepVerifier.create(transactionClient.send(buildRequest()))
        .expectNextCount(1)
        .verifyComplete();

    wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/api/payment"))
            .withHeader(HeaderConstant.CLIENT_ID.getHeader(), equalTo("test-app"))
            .withHeader(HeaderConstant.REQUEST_ID.getHeader(), equalTo("123456789012"))
    );
  }

  private RequestContext buildRequest() {
    RequestContext request = new RequestContext();
    request.setMti("0200");
    request.setProcessingCode("000000");
    request.setAdditionalData("");
    request.setRrn("123456789012");
    return request;
  }
}
