package com.nantaaditya.sotres.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nantaaditya.sotres.BaseIntegrationTest;
import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import com.nantaaditya.sotres.e2e.support.IsoMessages;
import com.nantaaditya.sotres.helper.EnhancedIsoClient;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Case 2 (CALLBACK mode) regression test: {@link EnhancedIsoClient#sendWithCallback} sends an
 * ISO8583 request to the switch; the correlated reply arrives back on the same connection and is
 * classified by {@code IsoCallbackResponseHandler} before reaching
 * {@code TransactionProcessorParticipant}.
 *
 * <p>Locks in the fix for the "reply-to-a-reply" bug: a callback-mode correlated response must
 * never receive an ISO reply of its own from {@code IsoResponseSender.sendResponseWithObservation}
 * ({@code RequestContext.callbackResponse}), since there is nobody on the switch side waiting for
 * one.
 *
 * <p>Uses its own init script ({@code e2e/callback-mode-init.sql}) with {@code
 * registry.callback_selector} pre-seeded, because {@code IsoCallbackResponseHandler} captures
 * that selector list once, at client-connect time during context startup -- a
 * {@code systemPropertiesService.reload(...)} call from within the test would not reach the
 * already-constructed handler on the live connection.
 *
 * <p>Declares its own {@code ISO_HOST}/{@code DOWNSTREAM} rather than reusing
 * {@code SharedInfraE2eTestBase}'s: this test forces an ISO reconnect mid-run
 * ({@code ISO_HOST.disconnectClient()}), which -- when merged into a shared Spring context with
 * other e2e classes -- was found to leave the shared HikariCP connection pool broken for whichever
 * class runs next (observed: {@code HikariPool total=0}, cascading into ~50s
 * {@code systemPropertiesService.reload()} stalls in the following class). Keeping its own context
 * avoids that entirely.
 */
class CallbackModeE2eTest extends BaseIntegrationTest {

  static final FakeIsoHost ISO_HOST = new FakeIsoHost();
  static final WireMockServer DOWNSTREAM = new WireMockServer(0);

  private final AtomicInteger stanSeq = new AtomicInteger(200000);

  @Autowired
  SystemPropertiesService systemPropertiesService;
  @Autowired
  EnhancedIsoClient enhancedIsoClient;

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

    // IsoCallbackResponseHandler reads registry.callback_selector once, at pipeline-construction
    // time on connect -- and that first connect happens during context refresh, before
    // SystemPropertiesServiceImpl.onStart() (ApplicationReadyEvent) populates the config cache.
    // Force a reconnect now (config is definitely loaded by this point) so the handler bound to
    // the live connection actually has the seeded selector.
    ISO_HOST.disconnectClient();
    await().atMost(Duration.ofSeconds(10)).until(() -> !ISO_HOST.isClientConnected());
    await().atMost(Duration.ofSeconds(20)).until(ISO_HOST::isClientConnected);

    DOWNSTREAM.resetAll();
    ISO_HOST.drain();
  }

  private String nextStan() {
    return String.valueOf(stanSeq.incrementAndGet());
  }

  @Test
  @DisplayName("callback-mode correlated reply: no ISO reply is sent back to the switch")
  void callbackReply_doesNotReceiveAnIsoReplyBack() throws InterruptedException {
    String rrn = "RRN000000201";
    IsoMessage request = IsoMessages.authRequest(
        "4111111111111111", "970000", 150000, nextStan(), rrn, "E001");

    enhancedIsoClient.sendWithCallback(request);

    // wait for the switch (FakeIsoHost) to actually receive the outbound request, then drain it
    // from the capture queue -- otherwise the request itself (which also carries `rrn` on DE37)
    // would satisfy the "no message with this RRN" predicate below for the wrong reason.
    IsoMessage sentRequest = ISO_HOST.awaitMessage(
        m -> m.getType() == 0x200 && rrn.equals(str(m, 37)), Duration.ofSeconds(10));
    assertThat(sentRequest).as("callback request reached the switch").isNotNull();
    ISO_HOST.drain();

    // simulate the switch's correlated reply -- same DE3/DE7/DE11/DE37/DE48(PI) as the request,
    // so CorrelationRegistry.complete() matches it and IsoCallbackResponseHandler classifies it
    // SUCCESS before handing it to TransactionProcessorParticipant.
    IsoMessage reply = IsoMessages.callbackReply(request, "E001", "00");
    ISO_HOST.send(reply);

    // TransactionProcessorParticipant finds no AbstractTransactionHandler for selector
    // "21.97-E001" and falls into its "no handler" branch, calling
    // IsoResponseSender.sendResponseWithObservation -- which must now skip the write because
    // RequestContext.callbackResponse is true for this message.
    boolean noReplyWrittenBack = ISO_HOST.noMessage(
        m -> rrn.equals(str(m, 37)), Duration.ofSeconds(3));

    assertThat(noReplyWrittenBack)
        .as("no second ISO reply written back for the callback-mode correlated response")
        .isTrue();
  }

  private static String str(IsoMessage message, int field) {
    return message.getField(field) == null ? null : message.getField(field).toString();
  }
}
