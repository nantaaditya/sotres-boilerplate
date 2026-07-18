package com.nantaaditya.sotres.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.zalando.logbook.Logbook;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("TransactionClient")
@ExtendWith(MockitoExtension.class)
class TransactionClientTest {

  private WireMockServer wireMockServer;
  private ObjectMapper objectMapper;
  private TestObservationRegistry observationRegistry;

  @Mock
  private SystemPropertiesService systemPropertiesService;

  @Mock
  private JsltTransformationHelper jsltTransformationHelper;

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
    objectMapper = new ObjectMapper();
    observationRegistry = TestObservationRegistry.create();

    transactionClient = new TransactionClient(
        systemPropertiesService, jsltTransformationHelper, objectMapper, logbook, clientProperties,
        observationRegistry);
    ReflectionTestUtils.setField(transactionClient, "applicationName", "test-app");
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  @DisplayName("send receives 200 and emits ResponseContext with response code")
  void send_receives200_emitsResponseContextWithCode() throws Exception {
    JsonNode reqBody = objectMapper.readTree("{}");
    JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\",\"description\":\"approved\"}}");

    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"raw\":\"downstream_response\"}")
            )
    );
    when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
        .thenReturn(Mono.just(reqBody));
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
        .thenReturn(Mono.just(normalizedResp));

    StepVerifier.create(transactionClient.send(buildRequest()))
        .assertNext(ctx -> assertThat(ctx.getResponseCode()).isEqualTo("00"))
        .verifyComplete();
  }

  @Test
  @DisplayName("send receives 4xx and still emits ResponseContext from body")
  void send_receives4xx_emitsResponseContextFromBody() throws Exception {
    JsonNode reqBody = objectMapper.readTree("{}");
    JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"96\",\"description\":\"bad request\"}}");

    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"bad_request\"}")
            )
    );
    when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
        .thenReturn(Mono.just(reqBody));
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
        .thenReturn(Mono.just(normalizedResp));

    StepVerifier.create(transactionClient.send(buildRequest()))
        .assertNext(ctx -> assertThat(ctx.getResponseCode()).isEqualTo("96"))
        .verifyComplete();
  }

  @Test
  @DisplayName("send receives 5xx and propagates error")
  void send_receives5xx_propagatesError() throws Exception {
    JsonNode reqBody = objectMapper.readTree("{}");

    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse().withStatus(500))
    );
    when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
        .thenReturn(Mono.just(reqBody));

    StepVerifier.create(transactionClient.send(buildRequest()))
        .expectError()
        .verify();
  }

  @Test
  @DisplayName("send sets CLIENT_ID and REQUEST_ID headers on the outgoing request")
  void send_setsClientIdAndRequestIdHeaders() throws Exception {
    JsonNode reqBody = objectMapper.readTree("{}");
    JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/payment"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"raw\":\"response\"}")
            )
    );
    when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
        .thenReturn("20.00-NA:/api/payment");
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
        .thenReturn(Mono.just(reqBody));
    when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
        .thenReturn(Mono.just(normalizedResp));

    StepVerifier.create(transactionClient.send(buildRequest()))
        .expectNextCount(1)
        .verifyComplete();

    wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/api/payment"))
            .withHeader(HeaderConstant.CLIENT_ID.getHeader(), equalTo("test-app"))
            .withHeader(HeaderConstant.REQUEST_ID.getHeader(), equalTo("123456789012"))
    );
  }

  @Nested
  @DisplayName("JsltTransformation")
  class JsltTransformation {

    @Test
    @DisplayName("calls CLIENT_SPEC_REQUEST transform before sending request body")
    void requestTransform_calledWithClientSpecRequestGroup() throws Exception {
      JsonNode reqBody = objectMapper.readTree("{\"amount\":\"100\"}");
      JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.just(normalizedResp));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectNextCount(1)
          .verifyComplete();

      verify(jsltTransformationHelper)
          .transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any(RequestContext.class));
    }

    @Test
    @DisplayName("calls CLIENT_SPEC_RESPONSE transform after receiving raw response")
    void responseTransform_calledWithClientSpecResponseGroup() throws Exception {
      JsonNode reqBody = objectMapper.readTree("{}");
      JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"transaction_id\":\"txn-001\",\"status\":\"approved\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.just(normalizedResp));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectNextCount(1)
          .verifyComplete();

      verify(jsltTransformationHelper)
          .transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any(JsonNode.class));
    }

    @Test
    @DisplayName("propagates error when CLIENT_SPEC_REQUEST transform fails")
    void requestTransformError_propagatesToCaller() {
      RuntimeException transformError = new RuntimeException("JSLT compile error");

      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.error(transformError));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectErrorMatches(e -> e.getMessage().equals("JSLT compile error"))
          .verify();
    }

    @Test
    @DisplayName("propagates error when CLIENT_SPEC_RESPONSE transform fails")
    void responseTransformError_propagatesToCaller() throws Exception {
      JsonNode reqBody = objectMapper.readTree("{}");
      RuntimeException transformError = new RuntimeException("JSLT apply error");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.error(transformError));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectErrorMatches(e -> e.getMessage().equals("JSLT apply error"))
          .verify();
    }
  }

  @Nested
  @DisplayName("Observation tracking (API_EXTERNAL)")
  class ObservationTracking {

    @Test
    @DisplayName("tags the observation with requestId and feature, and records the responseCode on success")
    void send_success_tagsRequestIdFeatureAndResponseCode() throws Exception {
      JsonNode reqBody = objectMapper.readTree("{}");
      JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.just(normalizedResp));

      RequestContext request = buildRequest();
      request.setIsoFeatureConstant("20.00-QR");

      StepVerifier.create(transactionClient.send(request))
          .expectNextCount(1)
          .verifyComplete();

      TestObservationRegistryAssert.assertThat(observationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.API_EXTERNAL.getName())
          .that()
          .hasHighCardinalityKeyValue("requestId", "123456789012")
          .hasLowCardinalityKeyValue("feature", "POST/api/payment")
          .hasLowCardinalityKeyValue("responseCode", "00");
    }

    @Test
    @DisplayName("records the error class and does not throw when the downstream call fails without a cause")
    void send_causelessError_recordsErrorClassWithoutThrowing() {
      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse().withStatus(500))
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(objectMapper.createObjectNode()));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectError()
          .verify();

      TestObservationRegistryAssert.assertThat(observationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.API_EXTERNAL.getName())
          .that()
          .hasLowCardinalityKeyValueWithKey("error");
    }

    @Test
    @DisplayName("starts exactly one observation per send() call even when the underlying request retries")
    void send_oneObservationPerCall_notPerRetryAttempt() throws Exception {
      JsonNode reqBody = objectMapper.readTree("{}");
      JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.just(normalizedResp));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectNextCount(1)
          .verifyComplete();

      TestObservationRegistryAssert.assertThat(observationRegistry)
          .hasNumberOfObservationsWithNameEqualTo(ObservationConstant.API_EXTERNAL.getName(), 1);
    }

    @Test
    @DisplayName("read timeout does not throw and records the timeout as the observation error")
    void send_readTimeout_recordsTimeoutErrorWithoutThrowing() throws Exception {
      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withFixedDelay(1000)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(objectMapper.createObjectNode()));

      ClientConfiguration shortReadTimeoutConfig = new ClientConfiguration(
          "http://localhost:" + wireMockServer.port(),
          50, 60000, 60000, 60000, 30000,
          5000, 200, 30000,
          TimeUnit.MILLISECONDS,
          null
      );
      ClientProperties shortReadTimeoutProperties = new ClientProperties();
      Map<String, ClientConfiguration> configs = new HashMap<>();
      configs.put("transaction", shortReadTimeoutConfig);
      shortReadTimeoutProperties.setConfigurations(configs);

      TransactionClient shortTimeoutClient = new TransactionClient(
          systemPropertiesService, jsltTransformationHelper, objectMapper,
          Logbook.builder().build(), shortReadTimeoutProperties, observationRegistry);
      ReflectionTestUtils.setField(shortTimeoutClient, "applicationName", "test-app");

      StepVerifier.create(shortTimeoutClient.send(buildRequest()))
          .expectError()
          .verify();

      TestObservationRegistryAssert.assertThat(observationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.API_EXTERNAL.getName())
          .that()
          .thenError()
          .isInstanceOf(WebClientRequestException.class)
          .hasCauseInstanceOf(ReadTimeoutException.class);
    }

    @Test
    @DisplayName("http 5xx error records the real WebClientResponseException as the observation error")
    void send_http5xxError_recordsWebClientResponseException() throws Exception {
      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse().withStatus(500))
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(objectMapper.createObjectNode()));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectError()
          .verify();

      TestObservationRegistryAssert.assertThat(observationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.API_EXTERNAL.getName())
          .that()
          .thenError()
          .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    @DisplayName("publishes request and response events with the serialized JSON payloads")
    void send_success_publishesRequestAndResponseEvents() throws Exception {
      List<Observation.Event> capturedEvents = new ArrayList<>();
      observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
          capturedEvents.add(event);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
          return true;
        }
      });

      JsonNode reqBody = objectMapper.readTree("{\"amount\":\"100\"}");
      JsonNode normalizedResp = objectMapper.readTree("{\"response\":{\"code\":\"00\"}}");

      wireMockServer.stubFor(
          post(urlPathEqualTo("/api/payment"))
              .willReturn(aResponse()
                  .withStatus(200)
                  .withHeader("Content-Type", "application/json")
                  .withBody("{\"raw\":\"downstream_response\"}")
              )
      );
      when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING, "mapping"))
          .thenReturn("20.00-NA:/api/payment");
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_REQUEST), anyString(), any()))
          .thenReturn(Mono.just(reqBody));
      when(jsltTransformationHelper.transform(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), anyString(), any()))
          .thenReturn(Mono.just(normalizedResp));

      StepVerifier.create(transactionClient.send(buildRequest()))
          .expectNextCount(1)
          .verifyComplete();

      assertThat(capturedEvents).hasSize(2);
      assertThat(capturedEvents).anySatisfy(event -> {
        assertThat(event.getName()).isEqualTo("request");
        assertThat(event.getContextualName()).contains("\"amount\":\"100\"");
      });
      assertThat(capturedEvents).anySatisfy(event -> {
        assertThat(event.getName()).isEqualTo("response");
        assertThat(event.getContextualName()).contains("downstream_response");
      });
    }
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
