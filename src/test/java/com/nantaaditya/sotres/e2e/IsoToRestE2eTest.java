package com.nantaaditya.sotres.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nantaaditya.sotres.e2e.support.E2eTestConfig;
import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.e2e.support.IsoMessages;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 0 regression oracle for the reactive -> servlet/blocking refactor.
 *
 * <p>Boots the full application against Testcontainers Postgres + a WireMock
 * downstream, drives real ISO8583 traffic through an embedded upstream host
 * ({@link FakeIsoHost}), and freezes the current end-to-end behaviour of the
 * transaction-forwarding pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2eTestConfig.class)
@Testcontainers
class IsoToRestE2eTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withInitScript("e2e/init.sql");

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();
  static final WireMockServer DOWNSTREAM = new WireMockServer(0);

  private final AtomicInteger stanSeq = new AtomicInteger(100000);

  @Autowired
  SystemPropertiesService systemPropertiesService;

  @BeforeAll
  static void startInfra() throws InterruptedException {
    DOWNSTREAM.start();
    ISO_HOST.start();
  }

  @AfterAll
  static void stopInfra() {
    ISO_HOST.stop();
    DOWNSTREAM.stop();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("iso8583.configuration.connection.host", () -> "127.0.0.1");
    registry.add("iso8583.configuration.connection.port", ISO_HOST::getPort);
    registry.add("iso8583.configuration.network.reconnect-interval", () -> 2000);
    registry.add("iso8583.configuration.network.scheduled-echo-enabled", () -> false);

    registry.add("client.configurations.transaction.hostname", DOWNSTREAM::baseUrl);
    registry.add("client.configurations.transaction.client-read-time-out", () -> 1500);
  }

  @BeforeEach
  void ready() {
    await().atMost(Duration.ofSeconds(20)).until(ISO_HOST::isClientConnected);
    for (ConfigGroup group : ConfigGroup.values()) {
      systemPropertiesService.reload(group);
    }
    await().atMost(Duration.ofSeconds(10))
        .until(() -> !systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING).isEmpty());
    DOWNSTREAM.resetAll();
    ISO_HOST.drain();
  }

  private String nextStan() {
    return String.valueOf(stanSeq.incrementAndGet());
  }

  @Test
  @DisplayName("0200 approved: JSLT shapes the REST body, 0210 carries DE39=00 and the approval code")
  void approved() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/transaction"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rc\":\"00\",\"auth\":\"654321\"}")));

    String rrn = "RRN000000001";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "970000", 150000, nextStan(), rrn, "E001"));

    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));

    assertThat(reply).as("0210 reply").isNotNull();
    assertThat(str(reply, 39)).as("DE39 response code").isEqualTo("00");
    assertThat(str(reply, 38)).as("DE38 approval code").contains("654321");

    DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/api/transaction"))
        .withRequestBody(matchingJsonPath("$.pan", equalTo("4111111111111111")))
        .withRequestBody(matchingJsonPath("$.rrn", equalTo(rrn))));
  }

  @Test
  @DisplayName("0200 declined: downstream rc=05 maps to DE39=05 on the 0210")
  void declined() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/transaction"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rc\":\"05\"}")));

    String rrn = "RRN000000002";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "970000", 150000, nextStan(), rrn, "E001"));

    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));

    assertThat(reply).as("0210 reply").isNotNull();
    assertThat(str(reply, 39)).as("DE39 response code").isEqualTo("05");
  }

  @Test
  @DisplayName("0200 downstream timeout: 0210 carries DE39=96 (system malfunction)")
  void downstreamTimeout() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/transaction"))
        .willReturn(aResponse()
            .withFixedDelay(4000)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rc\":\"00\"}")));

    String rrn = "RRN000000003";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "970000", 150000, nextStan(), rrn, "E001"));

    // The RestClient read timeout surfaces as ResourceAccessException (not a raw
    // ReadTimeoutException), so RestProtocolStrategy.handleError takes the
    // non-timeout branch and answers with SYSTEM_MALFUNCTION rather than
    // staying silent for a switch-driven reversal.
    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));

    assertThat(reply).as("0210 reply").isNotNull();
    assertThat(str(reply, 39)).as("DE39 response code").isEqualTo("96");
  }

  @Test
  @DisplayName("0200 with a path mapping but no JSLT template: RequestContext passes through unshaped")
  void passThroughWhenNoTemplate() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/passthrough"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"response\":{\"code\":\"00\"}}")));

    String rrn = "RRN000000006";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "980000", 2500, nextStan(), rrn, "E002"));

    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));

    assertThat(reply).as("0210 reply").isNotNull();
    // pass-through: RequestContext serialised as-is, so DE37/rrn is present in the body
    DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/api/passthrough"))
        .withRequestBody(matchingJsonPath("$.rrn", equalTo(rrn))));
  }

  @Test
  @DisplayName("0200 with an unmapped selector (no handler): 0210 carries DE39=92 (unable to route)")
  void unableToRoute() throws InterruptedException {
    // Phase 2C-3 populates the ParticipantContext before the no-handler branch,
    // so createResponse() no longer NPEs and the intended DE39=92 is written back.
    String rrn = "RRN000000007";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "990000", 1000, nextStan(), rrn, "E999"));

    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));

    assertThat(reply).as("0210 reply").isNotNull();
    assertThat(str(reply, 39)).as("DE39 response code").isEqualTo("92");
  }

  private static String str(IsoMessage message, int field) {
    return message.getField(field) == null ? null : message.getField(field).toString();
  }
}
