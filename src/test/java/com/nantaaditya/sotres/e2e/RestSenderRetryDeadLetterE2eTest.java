package com.nantaaditya.sotres.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nantaaditya.sotres.BaseIntegrationTest;
import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.e2e.support.IsoMessages;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Phase 7E — end-to-end: proves that when the downstream call exhausts its retry budget, a real
 * {@code dead_letter_process} row lands in Postgres carrying the exact original request payload,
 * not just that {@code RestSenderRetryListener} behaves correctly in isolation (already
 * unit-tested). The payload is deliberately <b>not</b> masked here — unlike
 * {@code IsoMessageLoggerHelper}/{@code ApiLogbookFormatter}'s log-line masking via
 * {@link com.nantaaditya.sotres.helper.MaskingHelper} — because dead-letter reprocessing needs
 * the exact original request to replay it.
 *
 * <p>Declares its own {@code ISO_HOST}/{@code DOWNSTREAM} (shadowing {@link BaseIntegrationTest}'s
 * shared instances) rather than reusing them: this class needs a distinct
 * {@code client-read-time-out}/retry tuning, which forces its own Spring context and therefore
 * its own {@code EnhancedIsoClient} bean -- sharing the JVM-static shared FakeIsoHost/WireMock
 * with another, concurrently-cached Spring context would mean two app instances connecting to the
 * one fake switch, corrupting its single-client-channel/message-queue model.
 */
class RestSenderRetryDeadLetterE2eTest extends BaseIntegrationTest {

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();
  static final WireMockServer DOWNSTREAM = new WireMockServer(0);

  @Autowired
  SystemPropertiesService systemPropertiesService;

  @Autowired
  DeadLetterProcessRepository deadLetterProcessRepository;

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
    // Short enough that the fixed-delay stub below reliably exceeds it on every attempt.
    registry.add("client.configurations.transaction.client-read-time-out", () -> 300);

    // Exhaust after 2 total attempts (1 retry) with a short backoff, so the test stays fast.
    registry.add("apps.retry.configurations.transaction.max-attempt", () -> 2);
    registry.add("apps.retry.configurations.transaction.initial-interval", () -> 100);
    registry.add("apps.retry.configurations.transaction.max-interval", () -> 200);
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
  }

  @Test
  @DisplayName("downstream times out on every attempt: retry budget exhausts and a"
      + " dead_letter_process row is written with the original, unmasked request")
  void retryExhaustion_writesDeadLetterRowWithOriginalRequest() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/deadletter"))
        .willReturn(aResponse()
            .withFixedDelay(2000)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rc\":\"00\"}")));

    String rrn = "RRN000000901";
    ISO_HOST.send(IsoMessages.authRequest(
        "4111111111111111", "960000", 5000, "900001", rrn, "E003"));

    // Downstream timeout -> RestProtocolStrategy.handleError sends no ISO reply (the acquirer
    // drives the reversal); the dead-lettering happens independently, inside RestSender's retry
    // template, so poll the repository rather than wait for a 0210.
    await().atMost(Duration.ofSeconds(10)).until(() -> findDeadLetter(rrn) != null);

    DeadLetterProcess deadLetter = findDeadLetter(rrn);
    assertThat(deadLetter).as("dead_letter_process row for RRN %s", rrn).isNotNull();
    assertThat(deadLetter.getProcessType()).isEqualTo("client");
    assertThat(deadLetter.getProcessName()).isEqualTo("transaction");
    assertThat(deadLetter.getClientName()).isEqualTo("transaction");
    assertThat(deadLetter.getStatus()).isEqualTo(RetryStatus.NEW.name());
    assertThat(deadLetter.getRetryCount()).isZero();
    assertThat(deadLetter.getMaxRetry()).isEqualTo(2);

    String payload = new String(deadLetter.getPayload(), StandardCharsets.UTF_8);
    assertThat(payload)
        .as("original cardNo preserved unmasked in the persisted payload: %s", payload)
        .contains("4111111111111111");
  }

  private DeadLetterProcess findDeadLetter(String rrn) {
    return deadLetterProcessRepository
        .findByProcessTypeAndProcessNameAndStatusIn(
            "client", "transaction", Set.of(RetryStatus.NEW.name()), Pageable.unpaged())
        .stream()
        .filter(d -> rrn.equals(d.getIdempotencyKey()))
        .findFirst()
        .orElse(null);
  }
}
