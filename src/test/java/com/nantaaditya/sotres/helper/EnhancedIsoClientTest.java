package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.IsoClientConfigurationRequest;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("EnhancedIsoClient")
@ExtendWith(MockitoExtension.class)
class EnhancedIsoClientTest {

  @Mock
  private CorrelationRegistry correlationRegistry;
  @Mock
  private IsoFieldHelper isoFieldHelper;
  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;
  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private ParticipantConfigurationProperties participantConfigurationProperties;
  @Mock
  private ClientProperties clientProperties;
  @Mock
  private MessageFactory<IsoMessage> messageFactory;
  @Mock
  private IsoMessage request;
  @Mock
  private IsoMessage errorResponse;
  @Mock
  private Channel channel;
  @Mock
  private ChannelFuture channelFuture;

  /** exposes {@code setChannel} so tests can inject a (non-)writable channel without a real connection. */
  private static final class TestClient extends EnhancedIsoClient {
    TestClient(IsoClientConfigurationRequest configuration) {
      super(configuration);
    }

    void useChannel(Channel channel) {
      setChannel(channel);
    }
  }

  /** builds a client that reads {@code clientProperties.getRegistryType()} at construction. */
  private TestClient buildClient() {
    return new TestClient(new IsoClientConfigurationRequest(
        new InetSocketAddress("localhost", 5000),
        ClientConfiguration.newBuilder().build(),
        messageFactory,
        correlationRegistry,
        isoFieldHelper,
        isoMessageLoggerHelper,
        systemPropertiesService,
        tracerHelper,
        participantConfigurationProperties,
        clientProperties
    ));
  }

  @BeforeEach
  void setUp() {
    lenient().when(correlationRegistry.register(any())).thenReturn(new CompletableFuture<>());
    lenient().when(isoFieldHelper.createResponse(request)).thenReturn(errorResponse);
  }

  @Nested
  @DisplayName("send(IsoMessage, Duration) — RESPONSE mode")
  class SendResponse {

    private TestClient client;

    @BeforeEach
    void responseMode() {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      client = buildClient();
    }

    @Test
    @DisplayName("throws IllegalStateException when no channel is connected")
    void send_noChannel_throwsIllegalState() {
      assertThatThrownBy(() -> client.send(request, Duration.ofMillis(50)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("throws IllegalStateException when the channel is not writable")
    void send_channelNotWritable_throwsIllegalState() {
      when(channel.isWritable()).thenReturn(false);
      client.useChannel(channel);

      assertThatThrownBy(() -> client.send(request, Duration.ofMillis(50)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancels the correlation and returns a DE39 error response when the write fails")
    void send_writeFails_cancelsAndReturnsErrorResponse() throws Exception {
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenThrow(new RuntimeException("boom"));
      client.useChannel(channel);

      IsoMessage result = client.send(request, Duration.ofMillis(50));

      assertThat(result).isSameAs(errorResponse);
      verify(errorResponse).setField(eq(39), any());
      verify(correlationRegistry).cancel(any());
    }

    @Test
    @DisplayName("cancels the correlation and returns a DE39 error response when no response arrives in time")
    void send_noResponseInTime_cancelsAndReturnsErrorResponse() throws Exception {
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenReturn(channelFuture);
      client.useChannel(channel);

      IsoMessage result = client.send(request, Duration.ofMillis(50));

      assertThat(result).isSameAs(errorResponse);
      verify(errorResponse).setField(eq(39), any());
      verify(correlationRegistry).cancel(any());
    }
  }

  @Nested
  @DisplayName("sendWithCallback(IsoMessage) — CALLBACK mode")
  class SendWithCallback {

    private TestClient client;

    @BeforeEach
    void callbackMode() {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);
      client = buildClient();
    }

    @Test
    @DisplayName("registers the correlation and returns once the write flushes")
    void sendWithCallback_writeSucceeds_registersAndReturns() throws Exception {
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenReturn(channelFuture);
      client.useChannel(channel);

      client.sendWithCallback(request);

      verify(correlationRegistry).register(any());
      verify(correlationRegistry, org.mockito.Mockito.never()).cancel(any());
    }

    @Test
    @DisplayName("cancels the correlation and rethrows when the write fails")
    void sendWithCallback_writeFails_cancelsAndThrows() throws Exception {
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenThrow(new RuntimeException("boom"));
      client.useChannel(channel);

      assertThatThrownBy(() -> client.sendWithCallback(request))
          .isInstanceOf(IllegalStateException.class);
      verify(correlationRegistry).cancel(any());
    }

    @Test
    @DisplayName("throws when the channel is not connected")
    void sendWithCallback_noChannel_throwsIllegalState() {
      assertThatThrownBy(() -> client.sendWithCallback(request))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("RESPONSE-mode response delivery (real CorrelationRegistry)")
  class ResponseDelivery {

    @SuppressWarnings("unchecked")
    private IsoValue<Object> isoValue(String value) {
      return new IsoValue<>(IsoType.ALPHA, value, value.length());
    }

    /** request/response echo DE48/3/11/37/7 → same correlation id, distinct objects. */
    private IsoMessage isoMsg(String stan) {
      IsoMessage m = mock(IsoMessage.class);
      lenient().when(m.getField(48)).thenReturn(isoValue("PI02QR"));
      lenient().when(m.getField(3)).thenReturn(isoValue("000000"));
      lenient().when(m.getField(11)).thenReturn(isoValue(stan));
      lenient().when(m.getField(37)).thenReturn(isoValue("000000000009"));
      lenient().when(m.getField(7)).thenReturn(isoValue("0615103045"));
      return m;
    }

    private TestClient responseClient(CorrelationRegistry registry) {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      TestClient c = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, registry, isoFieldHelper, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties));
      when(channel.isWritable()).thenReturn(true);
      lenient().when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      c.useChannel(channel);
      return c;
    }

    private CorrelationRegistry realRegistry(int flightMs, int graceMs) {
      when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION))
          .thenReturn(new ParticipantPoolConfiguration(flightMs, graceMs));
      return new CorrelationRegistry(participantConfigurationProperties);
    }

    @Test
    @DisplayName("returns the delivered response IsoMessage — not the request")
    void send_responseDelivered_returnsResponse() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = responseClient(registry);
      when(channelFuture.sync()).thenReturn(channelFuture);

      IsoMessage req = isoMsg("770001");
      IsoMessage resp = isoMsg("770001");

      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<IsoMessage> sending = pool.submit(() -> client.send(req, Duration.ofSeconds(3)));

        // deliver the response as TransactionResponseParticipant would, once send() has registered
        await().pollDelay(Duration.ofMillis(60)).atMost(Duration.ofSeconds(1)).until(() -> true);
        assertThat(registry.complete(resp)).isEqualTo(IsoCategory.SUCCESS);

        IsoMessage returned = sending.get(3, TimeUnit.SECONDS);
        assertThat(returned).isSameAs(resp);
        assertThat(returned).isNotSameAs(req);
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    @DisplayName("returns a synthetic DE39 error response — not the request — when no response arrives")
    void send_noResponse_returnsSyntheticError() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = responseClient(registry);
      lenient().when(channelFuture.sync()).thenReturn(channelFuture);

      IsoMessage req = isoMsg("770002");
      IsoMessage synthetic = mock(IsoMessage.class);
      when(isoFieldHelper.createResponse(req)).thenReturn(synthetic);

      IsoMessage returned = client.send(req, Duration.ofMillis(120));

      assertThat(returned).isSameAs(synthetic);
      assertThat(returned).isNotSameAs(req);
      verify(synthetic).setField(eq(39), any());
    }

    @Test
    @DisplayName("a response landing after send() timed out is ORPHAN — send() cancelled both windows")
    void send_responseAfterTimeout_isOrphan() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = responseClient(registry);
      lenient().when(channelFuture.sync()).thenReturn(channelFuture);
      lenient().when(isoFieldHelper.createResponse(any())).thenReturn(mock(IsoMessage.class));

      IsoMessage req = isoMsg("770003");
      IsoMessage resp = isoMsg("770003");

      IsoMessage returned = client.send(req, Duration.ofMillis(120)); // caller's own get() times out → cancel()
      assertThat(returned).isNotSameAs(resp);

      assertThat(registry.complete(resp)).isEqualTo(IsoCategory.ORPHAN);
    }

    @Test
    @DisplayName("CALLBACK: a response after the flight window lapses (no cancel) is LATE_RESPONSE")
    void sendWithCallback_lateResponse_isLate() throws Exception {
      when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION))
          .thenReturn(new ParticipantPoolConfiguration(40, 5000));
      CorrelationRegistry registry = new CorrelationRegistry(participantConfigurationProperties);
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);
      TestClient client = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, registry, isoFieldHelper, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties));
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      lenient().when(channelFuture.sync()).thenReturn(channelFuture);
      client.useChannel(channel);

      IsoMessage req = isoMsg("880001");
      IsoMessage resp = isoMsg("880001");

      client.sendWithCallback(req); // returns immediately, never cancels

      await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofSeconds(1)).until(() -> true);
      assertThat(registry.complete(resp)).isEqualTo(IsoCategory.LATE_RESPONSE);
    }
  }

  @Nested
  @DisplayName("mode guard")
  class ModeGuard {

    @Test
    @DisplayName("send() rejects CALLBACK-mode clients")
    void send_inCallbackMode_throws() {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);
      TestClient client = buildClient();
      when(channel.isWritable()).thenReturn(true);
      client.useChannel(channel);

      assertThatThrownBy(() -> client.send(request, Duration.ofMillis(50)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sendWithCallback() rejects RESPONSE-mode clients")
    void sendWithCallback_inResponseMode_throws() {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      TestClient client = buildClient();
      when(channel.isWritable()).thenReturn(true);
      client.useChannel(channel);

      assertThatThrownBy(() -> client.sendWithCallback(request))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
