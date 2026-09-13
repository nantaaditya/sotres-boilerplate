package com.nantaaditya.sotres.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.entity.EventLog;
import com.nantaaditya.sotres.repository.EventLogRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the servlet-migration wiring — {@code HeaderFilter} -> {@code CacheBodyRequest} ->
 * {@code EventLogInterceptor} — cooperates through a real {@code DispatcherServlet} dispatch: the
 * {@code ContextDTO} {@code HeaderFilter} attaches to the request is readable by
 * {@code EventLogInterceptor.afterCompletion}, and the resulting {@code EventLog} audit row is
 * persisted (async, off the request thread) with the fields {@code HeaderFilter} derived from the
 * inbound headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Event log wiring (HeaderFilter -> EventLogInterceptor)")
class EventLogWiringE2eTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withInitScript("e2e/init.sql");

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();

  @Autowired
  TestRestTemplate rest;

  @Autowired
  EventLogRepository eventLogRepository;

  @BeforeAll
  static void startIso() throws InterruptedException {
    ISO_HOST.start();
  }

  @AfterAll
  static void stopIso() {
    ISO_HOST.stop();
  }

  @DynamicPropertySource
  static void isoProperties(DynamicPropertyRegistry registry) {
    registry.add("iso8583.configuration.connection.host", () -> "127.0.0.1");
    registry.add("iso8583.configuration.connection.port", ISO_HOST::getPort);
    registry.add("iso8583.configuration.network.reconnect-interval", () -> 2000);
    registry.add("iso8583.configuration.network.scheduled-echo-enabled", () -> false);
  }

  @Test
  @DisplayName("a request through the DispatcherServlet produces a matching event_logs row")
  void requestThroughDispatcher_producesMatchingEventLogRow() {
    String requestId = "evt-" + UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-request-id", requestId);
    headers.set("x-client-id", "wiring-test-client");

    // TestRestTemplate's URI handler already prepends server.servlet.context-path (/sotres)
    ResponseEntity<String> response = rest.exchange(
        "/api/example?name=Wiring", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      List<EventLog> matches = eventLogRepository.findAll().stream()
          .filter(row -> requestId.equals(row.getRequestId()))
          .toList();

      assertThat(matches).as("event_logs row for requestId=%s", requestId).hasSize(1);
      EventLog eventLog = matches.get(0);
      assertThat(eventLog.getClientId()).isEqualTo("wiring-test-client");
      assertThat(eventLog.getMethod()).isEqualTo("GET");
      assertThat(eventLog.getPath()).isEqualTo("/api/example");
      assertThat(eventLog.getResponseCode()).isEqualTo("000");
    });
  }

  @Test
  @DisplayName("a request under an ignored path (/internal-api/**) produces no event_logs row")
  void requestUnderIgnoredPath_producesNoEventLogRow() {
    String requestId = "evt-ignored-" + UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("x-request-id", requestId);
    headers.set("x-client-id", "wiring-test-client");

    rest.exchange("/internal-api/event_log?days=30", HttpMethod.DELETE,
        new HttpEntity<>(headers), String.class);

    // No positive wait possible for "never happens" — the async save (if it happened) would land
    // well within this window given the same executor used by the positive-path test above.
    await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      List<EventLog> matches = eventLogRepository.findAll().stream()
          .filter(row -> requestId.equals(row.getRequestId()))
          .toList();
      assertThat(matches).as("ignored path must not be audited").isEmpty();
    });
  }
}
