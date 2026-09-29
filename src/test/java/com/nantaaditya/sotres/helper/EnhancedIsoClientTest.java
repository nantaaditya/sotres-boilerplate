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
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.IsoClientConfigurationRequest;
import com.nantaaditya.sotres.participant.TransactionResponseParticipant;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
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
  private IsoResponseSender isoResponseSender;
  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;
  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private ObservationRegistry observationRegistry;
  @Mock
  private Tracer tracer;
  @Mock
  private Span span;
  @Mock
  private Tracer.SpanInScope spanInScope;
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
  @Mock
  private ChannelHandlerContext ctx;
  @Mock
  @SuppressWarnings("rawtypes")
  private Attribute callbackAttribute;

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
        isoResponseSender,
        isoMessageLoggerHelper,
        systemPropertiesService,
        tracerHelper,
        participantConfigurationProperties,
        clientProperties,
        observationRegistry
    ));
  }

  @BeforeEach
  void setUp() {
    lenient().when(correlationRegistry.register(any())).thenReturn(new CompletableFuture<>());
    lenient().when(isoResponseSender.createResponse(request)).thenReturn(errorResponse);
    // send() calls the static IsoFieldHelper.getIsoFeature(request), which unpacks DE48 (TLV) —
    // an unstubbed getField(48) returns null and NPEs inside unpackTLV's raw.length() call.
    lenient().when(request.getField(48)).thenReturn(isoValue("PI02QR"));

    // send() must read a real Tracer/Span pair off tracerHelper — same collaborators
    // TransactionResponseParticipant uses — so its iso.message observation is wired the same way.
    lenient().when(tracerHelper.getTracer()).thenReturn(tracer);
    lenient().when(tracer.withSpan(any())).thenReturn(spanInScope);
    lenient().when(observationRegistry.isNoop()).thenReturn(true);
    lenient().when(tracerHelper.startIsoObservation(any(), any())).thenAnswer(invocation -> {
      ObservationRegistry registry = invocation.getArgument(1);
      Observation observation = Observation.start(ObservationConstant.ISO_MESSAGE.getName(), registry);
      return new IsoObservationContext(observation, span, new HashMap<>(), null);
    });

    // for CrossClassCorrelation: TransactionResponseParticipant.onMessage() reads the callback
    // classification off ctx.channel().attr(...) — set upstream by IsoCallbackResponseHandler.
    lenient().when(ctx.channel()).thenReturn(channel);
    lenient().when(channel.attr(any())).thenReturn(callbackAttribute);
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

  private CorrelationRegistry realRegistry(int flightMs, int graceMs) {
    when(participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION))
        .thenReturn(new ParticipantPoolConfiguration(flightMs, graceMs));
    return new CorrelationRegistry(participantConfigurationProperties, new SimpleMeterRegistry(),
        new CaffeineCacheHelper(mock(CacheProperties.class)));
  }

  @Nested
  @DisplayName("RESPONSE-mode response delivery (real CorrelationRegistry)")
  class ResponseDelivery {

    private TestClient responseClient(CorrelationRegistry registry) {
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      TestClient c = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, registry, isoResponseSender, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties,
          observationRegistry));
      when(channel.isWritable()).thenReturn(true);
      lenient().when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      c.useChannel(channel);
      return c;
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
      when(isoResponseSender.createResponse(req)).thenReturn(synthetic);

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
      lenient().when(isoResponseSender.createResponse(any())).thenReturn(mock(IsoMessage.class));

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
      CorrelationRegistry registry = new CorrelationRegistry(participantConfigurationProperties,
          new SimpleMeterRegistry(), new CaffeineCacheHelper(mock(CacheProperties.class)));
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.CALLBACK);
      TestClient client = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, registry, isoResponseSender, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties,
          observationRegistry));
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
  @DisplayName("send() — iso.message observation lifecycle (matches TransactionResponseParticipant.onMessage())")
  class ObservationLifecycle {

    private TestObservationRegistry testObservationRegistry;

    /** same wiring as ResponseDelivery.responseClient(), but with a real, assertable ObservationRegistry. */
    private TestClient observedClient(CorrelationRegistry registry) {
      testObservationRegistry = TestObservationRegistry.create();
      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      TestClient c = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, registry, isoResponseSender, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties,
          testObservationRegistry));
      when(channel.isWritable()).thenReturn(true);
      lenient().when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      c.useChannel(channel);
      return c;
    }

    @Test
    @DisplayName("a successful round trip starts AND stops one iso.message observation")
    void send_success_stopsIsoMessageObservation() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = observedClient(registry);
      when(channelFuture.sync()).thenReturn(channelFuture);

      IsoMessage req = isoMsg("990001");
      IsoMessage resp = isoMsg("990001");

      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<IsoMessage> sending = pool.submit(() -> client.send(req, Duration.ofSeconds(3)));
        await().pollDelay(Duration.ofMillis(60)).atMost(Duration.ofSeconds(1)).until(() -> true);
        registry.complete(resp);
        sending.get(3, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }

      // this is the same assertion TransactionResponseParticipantTest makes for its own
      // iso.message observation — proves the two classes' observations are wired the same way.
      TestObservationRegistryAssert.assertThat(testObservationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped();
    }

    @Test
    @DisplayName("negative: a timeout still stops the iso.message observation, marked as errored")
    void send_timeout_stopsIsoMessageObservationAsErrored() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = observedClient(registry);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      lenient().when(channelFuture.sync()).thenReturn(channelFuture);
      lenient().when(isoResponseSender.createResponse(any())).thenReturn(mock(IsoMessage.class));

      IsoMessage req = isoMsg("990002");

      client.send(req, Duration.ofMillis(80)); // no response ever delivered → caller's own get() times out

      TestObservationRegistryAssert.assertThat(testObservationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped()
          .hasError();
    }

    @Test
    @DisplayName("negative: a downstream write failure still stops the iso.message observation, marked as errored")
    void send_writeFails_stopsIsoMessageObservationAsErrored() throws Exception {
      CorrelationRegistry registry = realRegistry(5000, 5000);
      TestClient client = observedClient(registry);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenThrow(new RuntimeException("boom"));
      lenient().when(isoResponseSender.createResponse(any())).thenReturn(mock(IsoMessage.class));

      client.send(isoMsg("990003"), Duration.ofMillis(80));

      TestObservationRegistryAssert.assertThat(testObservationRegistry)
          .hasObservationWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName())
          .that()
          .hasBeenStopped()
          .hasError();
    }
  }

  @Nested
  @DisplayName("EnhancedIsoClient + TransactionResponseParticipant — iso.message observations correlate")
  class CrossClassCorrelation {

    private static final String RRN = "770099000001";

    @Test
    @DisplayName("client-side send() and participant-side onMessage() tag their iso.message "
        + "observation with the same requestId (DE37/RRN) on the one app-wide ObservationRegistry")
    void send_and_onMessage_shareSameRequestIdOnSharedRegistry() throws Exception {
      // one shared TestObservationRegistry, exactly like the single ObservationRegistry bean
      // both EnhancedIsoClient and TransactionResponseParticipant are wired to in production.
      TestObservationRegistry sharedRegistry = TestObservationRegistry.create();
      CorrelationRegistry correlation = realRegistry(5000, 5000);

      when(clientProperties.getRegistryType()).thenReturn(RegistryType.RESPONSE);
      TestClient client = new TestClient(new IsoClientConfigurationRequest(
          new InetSocketAddress("localhost", 5000),
          ClientConfiguration.newBuilder().build(),
          messageFactory, correlation, isoResponseSender, isoMessageLoggerHelper,
          systemPropertiesService, tracerHelper, participantConfigurationProperties, clientProperties,
          sharedRegistry));
      when(channel.isWritable()).thenReturn(true);
      when(channel.writeAndFlush(any())).thenReturn(channelFuture);
      when(channelFuture.sync()).thenReturn(channelFuture);
      client.useChannel(channel);

      TransactionResponseParticipant participant = new TransactionResponseParticipant(
          systemPropertiesService, correlation, sharedRegistry, isoMessageLoggerHelper,
          isoResponseSender, tracerHelper, tracer, clientProperties, Runnable::run);

      IsoMessage req = isoMsgWithRrn(RRN);
      IsoMessage resp = isoMsgWithRrn(RRN);

      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<IsoMessage> sending = pool.submit(() -> client.send(req, Duration.ofSeconds(3)));
        await().pollDelay(Duration.ofMillis(60)).atMost(Duration.ofSeconds(1)).until(() -> true);

        // NOTE: TransactionResponseParticipant.onMessage() does NOT itself resolve the
        // CorrelationRegistry future (see the separately-flagged finding on
        // TransactionResponseParticipantTest.onMessage_completesCorrelationAndReturnsFalse) — in
        // production that's done by IsoCallbackResponseHandler, upstream in the same Netty
        // pipeline, before onMessage() ever runs. Reproduce that ordering explicitly here so
        // this test verifies observation correlation, not the separate completion gap.
        correlation.complete(resp);
        participant.onMessage(ctx, resp);

        sending.get(3, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }

      // two DISTINCT iso.message observations (one per class) that correlate on the same
      // business key — this codebase's actual correlation mechanism (ObservationHelper
      // .createTransactionContext tags "requestId" = DE37/RRN), not a shared trace/span: each
      // side calls TracerHelper.startIsoObservation independently with .setNoParent(), so the
      // two observations are NOT part of the same trace.
      TestObservationRegistryAssert.assertThat(sharedRegistry)
          .hasNumberOfObservationsWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName(), 2)
          .forAllObservationsWithNameEqualTo(ObservationConstant.ISO_MESSAGE.getName(),
              context -> context.hasHighCardinalityKeyValue("requestId", RRN));
    }

    /** request/response echo DE48/3/11/37/7 → same correlation id, same RRN tag, distinct objects. */
    private IsoMessage isoMsgWithRrn(String rrn) {
      IsoMessage m = mock(IsoMessage.class);
      lenient().when(m.getField(48)).thenReturn(isoValue("PI02QR"));
      lenient().when(m.getField(3)).thenReturn(isoValue("000000"));
      lenient().when(m.getField(11)).thenReturn(isoValue("770099"));
      lenient().when(m.getField(37)).thenReturn(isoValue(rrn));
      lenient().when(m.getField(7)).thenReturn(isoValue("0615103045"));
      return m;
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

  @Nested
  @DisplayName("pipeline wiring")
  class PipelineWiring {

    @Test
    @DisplayName("configurePipeline inserts IsoCallbackResponseHandler right after the decoder, "
        + "ahead of the framework's message-listener dispatcher")
    void configurePipeline_insertsCallbackHandlerBeforeMessageDispatcher() {
      TestClient client = buildClient();
      io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
      // mirrors Iso8583ChannelInitializer.initChannel(): decoder added first, then (eventually)
      // the framework's own messageHandler dispatcher, THEN this configurer runs.
      channel.pipeline().addLast("iso8583Decoder", new io.netty.channel.ChannelInboundHandlerAdapter());
      channel.pipeline().addLast("messageHandler", new io.netty.channel.ChannelInboundHandlerAdapter());

      client.getConfigurer().configurePipeline(channel.pipeline(), ClientConfiguration.newBuilder().build());

      java.util.List<String> names = channel.pipeline().names();
      int decoderIndex = names.indexOf("iso8583Decoder");
      int callbackIndex = names.indexOf(IsoCallbackConstant.CALLBACK_NAME);
      int dispatcherIndex = names.indexOf("messageHandler");

      assertThat(callbackIndex).as("callback handler position").isEqualTo(decoderIndex + 1);
      assertThat(callbackIndex).as("callback handler must run before the message dispatcher")
          .isLessThan(dispatcherIndex);
    }
  }
}
