package com.nantaaditya.sotres.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.helper.ApiLogbookWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end check that an inbound HTTP request to this app's own endpoint is captured by Logbook
 * (servlet {@code LogbookFilter} -> custom sink -> {@link ApiLogbookWriter}) when
 * {@code apps.log.enable-inbound-api-log} is on (its default). The disabled case is covered by
 * {@code AppLogbookConfigurationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@DisplayName("Inbound API logging (Logbook)")
class InboundApiLoggingE2eTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withInitScript("e2e/init.sql");

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();

  @Autowired
  TestRestTemplate rest;

  private LoggerContext loggerContext;
  private CapturingAppender appender;

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

  @BeforeEach
  void attachAppender() {
    // Attach to the ROOT LoggerConfig, not via Logger.addAppender(ApiLogbookWriter.class) — the
    // latter creates a dedicated private LoggerConfig entry for that exact logger name the first
    // time it's called, which then persists for the rest of the JVM's life even after
    // removeAppender: every later test's ApiLogbookWriter log calls get routed into that
    // now-appender-less orphaned config instead of root's real appenders, silently losing output
    // (found via ObservabilityE2eTest going flaky only when run after this test).
    loggerContext = (LoggerContext) LogManager.getContext(false);
    appender = new CapturingAppender();
    appender.start();
    loggerContext.getConfiguration().getRootLogger().addAppender(appender, null, null);
    loggerContext.updateLoggers();
  }

  @AfterEach
  void detachAppender() {
    loggerContext.getConfiguration().getRootLogger().removeAppender(appender.getName());
    loggerContext.updateLoggers();
    appender.stop();
  }

  @Test
  @DisplayName("logs the inbound request and the outgoing response")
  void inboundRequestAndResponseAreLogged() {
    // TestRestTemplate's URI handler already prepends server.servlet.context-path (/sotres)
    ResponseEntity<String> response =
        rest.getForEntity("/api/example?name=Log", String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(appender.messages)
        .as("Logbook request line")
        .anyMatch(m -> m.contains("#API: request"));
    assertThat(appender.messages)
        .as("Logbook response line")
        .anyMatch(m -> m.contains("#API: response"));
  }

  private static final class CapturingAppender extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    CapturingAppender() {
      super("inbound-log-capture", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      messages.add(event.getMessage().getFormattedMessage());
    }
  }
}
