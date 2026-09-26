package com.nantaaditya.sotres.helper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@DisplayName("RestSender")
class RestSenderTest {

  private static final ParameterizedTypeReference<JsonNode> JSON = new ParameterizedTypeReference<>() {};

  private WireMockServer wireMock;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterEach
  void tearDown() {
    wireMock.stop();
  }

  private RestClient restClient(int readTimeoutMillis) {
    HttpClientSettings settings = HttpClientSettings.defaults()
        .withConnectTimeout(Duration.ofSeconds(2))
        .withReadTimeout(Duration.ofMillis(readTimeoutMillis));
    return RestClient.builder()
        .baseUrl("http://localhost:" + wireMock.port())
        .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
            .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1))
            .build(settings))
        .build();
  }

  /** {@code maxAttempts} is total executions; only {@link ResourceAccessException} is retryable. */
  private RetryTemplate retryTemplate(int maxAttempts) {
    return RetryTemplate.builder()
        .maxAttempts(maxAttempts)
        .retryOn(ResourceAccessException.class)
        .fixedBackoff(1)
        .build();
  }

  private RestSender sender(int readTimeoutMillis, RetryTemplate retryTemplate) {
    return new RestSender.Builder("test", restClient(readTimeoutMillis))
        .retryTemplate(retryTemplate)
        .build();
  }

  @Test
  @DisplayName("execute returns the body on 2xx")
  void execute_success_returnsBody() {
    wireMock.stubFor(get(urlPathEqualTo("/x"))
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody("{\"ok\":true}")));

    var response = sender(2000, null)
        .execute(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().get("ok").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("execute maps 4xx body and returns it")
  void execute_clientError_returnsBody() {
    wireMock.stubFor(get(urlPathEqualTo("/x"))
        .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"bad\"}")));

    var response = sender(2000, null)
        .execute(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("error").asText()).isEqualTo("bad");
  }

  @Test
  @DisplayName("execute raises HttpServerErrorException on 5xx")
  void execute_serverError_throws() {
    wireMock.stubFor(get(urlPathEqualTo("/x")).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> sender(2000, null)
        .execute(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON))
        .isInstanceOf(HttpServerErrorException.class);
  }

  @Test
  @DisplayName("executeWithRetry retries a retryable failure then succeeds")
  void executeWithRetry_retryableThenSuccess() {
    wireMock.stubFor(get(urlPathEqualTo("/x")).inScenario("retry")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withFixedDelay(400).withStatus(200))
        .willSetStateTo("second"));
    wireMock.stubFor(get(urlPathEqualTo("/x")).inScenario("retry")
        .whenScenarioStateIs("second")
        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody("{\"ok\":true}")));

    var response = sender(150, retryTemplate(3))
        .executeWithRetry(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON, "test");

    assertThat(response.getBody().get("ok").asBoolean()).isTrue();
    wireMock.verify(2, getRequestedFor(urlPathEqualTo("/x")));
  }

  @Test
  @DisplayName("executeWithRetry does not retry a non-retryable failure")
  void executeWithRetry_nonRetryable_throwsImmediately() {
    wireMock.stubFor(get(urlPathEqualTo("/x")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> sender(2000, retryTemplate(3))
        .executeWithRetry(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON, "test"))
        .isInstanceOf(HttpServerErrorException.class);

    wireMock.verify(1, getRequestedFor(urlPathEqualTo("/x")));
  }

  @Test
  @DisplayName("executeWithRetry exhausts attempts and rethrows the last failure")
  void executeWithRetry_exhausted_rethrows() {
    wireMock.stubFor(get(urlPathEqualTo("/x"))
        .willReturn(aResponse().withFixedDelay(400).withStatus(200)));

    assertThatThrownBy(() -> sender(150, retryTemplate(3))
        .executeWithRetry(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON, "test"))
        .isInstanceOf(ResourceAccessException.class);

    wireMock.verify(3, getRequestedFor(urlPathEqualTo("/x")));
  }

  @Test
  @DisplayName("executeWithRetry with maxAttempts=1 makes a single attempt (no retry)")
  void executeWithRetry_maxAttemptsOne_singleAttempt() {
    wireMock.stubFor(get(urlPathEqualTo("/x"))
        .willReturn(aResponse().withFixedDelay(400).withStatus(200)));

    assertThatThrownBy(() -> sender(150, retryTemplate(1))
        .executeWithRetry(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON, "test"))
        .isInstanceOf(ResourceAccessException.class);

    wireMock.verify(1, getRequestedFor(urlPathEqualTo("/x")));
  }

  @Test
  @DisplayName("executeWithRetry without a RetryTemplate raises IllegalStateException")
  void executeWithRetry_noTemplate_throws() {
    assertThatThrownBy(() -> sender(2000, null)
        .executeWithRetry(HttpMethod.GET, "/x", new HttpHeaders(), null, JSON, "test"))
        .isInstanceOf(IllegalStateException.class);

    wireMock.verify(0, getRequestedFor(urlPathEqualTo("/x")));
  }

  @Test
  @DisplayName("Builder rejects a blank name or null client")
  void builder_validation() {
    assertThatThrownBy(() -> new RestSender.Builder("  ", restClient(2000)).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RestSender.Builder("test", null).build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
