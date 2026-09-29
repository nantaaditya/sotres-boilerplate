package com.nantaaditya.sotres.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nantaaditya.sotres.BaseIntegrationTest;
import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.e2e.support.IsoMessages;
import com.nantaaditya.sotres.listener.JsonLogLayout;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Phase 7D — proves the observability contract the migration depends on actually holds
 * end-to-end, not just at the unit level: a real 0200-&gt;REST-&gt;0210 round trip must leave
 * {@code trace_id}/{@code span_id}/{@code request_id} populated on both the ISO-direction log
 * lines ({@link com.nantaaditya.sotres.helper.IsoMessageLoggerHelper}) and the outbound-API log
 * lines ({@link com.nantaaditya.sotres.helper.ApiLogbookWriter}), and must record the
 * {@code iso.message} / {@code api.external} Micrometer observations with the same correlating
 * {@code requestId}.
 *
 * <p>Log verification runs the real {@link JsonLogLayout} against captured {@link LogEvent}s —
 * the same serialization the app's file/console appenders use — rather than re-implementing the
 * field extraction, so a regression in the layout itself would also fail this test.
 *
 * <p>Declares its own {@code ISO_HOST}/{@code DOWNSTREAM} (shadowing {@link BaseIntegrationTest}'s
 * shared instances) because {@link TestObservationConfig} swaps in a {@code @Primary}
 * {@link TestObservationRegistry} -- sharing that across every other e2e class would make their
 * observation assertions depend on execution order, so this class necessarily keeps its own
 * Spring context, and therefore its own {@code EnhancedIsoClient} bean: sharing the JVM-static
 * shared FakeIsoHost/WireMock with another, concurrently-cached Spring context would mean two app
 * instances connecting to the one fake switch, corrupting its single-client-channel/message-queue
 * model.
 */
@Import(ObservabilityE2eTest.TestObservationConfig.class)
class ObservabilityE2eTest extends BaseIntegrationTest {

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();
  static final WireMockServer DOWNSTREAM = new WireMockServer(0);

  private final AtomicInteger stanSeq = new AtomicInteger(500000);

  @Autowired
  SystemPropertiesService systemPropertiesService;

  @Autowired
  TestObservationRegistry observationRegistry;

  private LoggerContext loggerContext;
  private CapturingLogAppender appender;

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

  @BeforeEach
  void attachLogCapture() {
    loggerContext = (LoggerContext) LogManager.getContext(false);
    appender = new CapturingLogAppender();
    appender.start();
    loggerContext.getConfiguration().getRootLogger().addAppender(appender, Level.INFO, null);
    loggerContext.updateLoggers();
  }

  @AfterEach
  void detachLogCapture() {
    loggerContext.getConfiguration().getRootLogger().removeAppender(appender.getName());
    loggerContext.updateLoggers();
    appender.stop();
  }

  @Test
  @DisplayName("approved transaction: trace_id/span_id/request_id populate real ISO and outbound-API"
      + " log lines, and iso.message/api.external observations correlate on the same requestId")
  void approvedTransaction_populatesTraceContextOnLogsAndObservations() throws InterruptedException {
    DOWNSTREAM.stubFor(post(urlEqualTo("/api/transaction"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"rc\":\"00\",\"auth\":\"654321\"}")));

    String rrn = "RRN000000501";
    ISO_HOST.send(IsoMessages.authRequest("4111111111111111", "970000", 150000,
        String.valueOf(stanSeq.incrementAndGet()), rrn, "E001"));

    IsoMessage reply = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x210 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));
    assertThat(reply).as("0210 reply").isNotNull();

    // ISO-direction log lines ("#ISO" — IsoMessageLoggerHelper) must carry a populated trace
    // context, and the request id must be this transaction's RRN. Log4j2's Disruptor delivers to
    // appenders asynchronously, so the 0210 reply above can arrive before these lines are
    // captured — poll instead of asserting immediately.
    await().atMost(Duration.ofSeconds(15)).until(() -> appender.matching("#ISO").size() >= 2);
    List<JsonNode> isoLogs = appender.matching("#ISO");
    isoLogs.forEach(node -> assertTraceContext(node, rrn));

    // Outbound-API log lines ("#API: request"/"#API: response" — ApiLogbookWriter, driven by
    // Logbook wrapping the RestClient call to the downstream) must carry the same trace context.
    await().atMost(Duration.ofSeconds(15)).until(() -> appender.matching("#API:").size() >= 2);
    List<JsonNode> apiLogs = appender.matching("#API:");
    apiLogs.forEach(node -> assertTraceContext(node, rrn));

    // The ISO side and the API side must correlate on the same trace/span — proves the context
    // survived the isoTransactionAsyncTaskExecutor virtual-thread handoff intact.
    String isoTraceId = isoLogs.get(0).path("trace_id").asText();
    String apiTraceId = apiLogs.get(0).path("trace_id").asText();
    assertThat(apiTraceId).as("API log trace_id matches ISO log trace_id").isEqualTo(isoTraceId);

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasObservationWithNameEqualTo("iso.message")
        .that()
        .hasHighCardinalityKeyValue("requestId", rrn)
        .hasLowCardinalityKeyValueWithKey("feature")
        .hasLowCardinalityKeyValue("responseCode", "00");

    TestObservationRegistryAssert.assertThat(observationRegistry)
        .hasObservationWithNameEqualTo("api.external")
        .that()
        .hasHighCardinalityKeyValue("requestId", rrn)
        .hasLowCardinalityKeyValueWithKey("feature")
        .hasLowCardinalityKeyValue("responseCode", "00");
  }

  private static void assertTraceContext(JsonNode logLine, String expectedRequestId) {
    assertThat(logLine.path("trace_id").asText(null))
        .as("trace_id on log line %s", logLine).isNotBlank();
    assertThat(logLine.path("span_id").asText(null))
        .as("span_id on log line %s", logLine).isNotBlank();
    assertThat(logLine.path("request_id").asText(null))
        .as("request_id on log line %s", logLine).isEqualTo(expectedRequestId);
  }

  private static String str(IsoMessage message, int field) {
    return message.getField(field) == null ? null : message.getField(field).toString();
  }

  @TestConfiguration
  static class TestObservationConfig {

    @Bean
    @Primary
    TestObservationRegistry testObservationRegistry() {
      return TestObservationRegistry.create();
    }
  }

  /**
   * Captures every {@link LogEvent} the app logs during a test and serializes it through the
   * real {@link JsonLogLayout} — the same layout the app's file/console appenders use — so
   * assertions read the actual {@code trace_id}/{@code span_id}/{@code request_id} fields a
   * production log line would carry, instead of re-deriving them from MDC directly.
   */
  private static final class CapturingLogAppender extends AbstractAppender {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonLogLayout jsonLayout = JsonLogLayout.createLayout("e2e-test");
    private final List<String> lines = new CopyOnWriteArrayList<>();

    CapturingLogAppender() {
      super("observability-e2e-capture", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      lines.add(jsonLayout.toSerializable(event.toImmutable()));
    }

    List<JsonNode> matching(String messagePrefix) {
      return lines.stream()
          .map(CapturingLogAppender::readTree)
          .filter(node -> node.path("context").path("message").asText("").startsWith(messagePrefix))
          .toList();
    }

    private static JsonNode readTree(String json) {
      try {
        return MAPPER.readTree(json);
      } catch (Exception e) {
        throw new IllegalStateException("captured log line is not valid JSON: " + json, e);
      }
    }
  }
}
