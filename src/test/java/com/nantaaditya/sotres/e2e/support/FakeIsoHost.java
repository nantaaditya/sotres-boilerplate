package com.nantaaditya.sotres.e2e.support;

import com.github.kpavlov.jreactive8583.ConnectorConfigurer;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.github.kpavlov.jreactive8583.server.Iso8583Server;
import com.github.kpavlov.jreactive8583.server.ServerConfiguration;
import com.nantaaditya.sotres.helper.MessageFactoryHelper;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.solab.iso8583.IsoMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Embedded jReactive-8583 server acting as the fake upstream ISO8583 host for the
 * end-to-end regression harness. The application connects to it as a client; this
 * class can push messages to that client and observe what the client sends back.
 *
 * <p>Uses the application's own packager ({@code default-packager.xml}) so wire
 * framing and field encoding match exactly.
 */
public final class FakeIsoHost {

  private final int port;
  private final Iso8583Server<IsoMessage> server;
  private final AtomicReference<Channel> clientChannel = new AtomicReference<>();
  private final BlockingQueue<IsoMessage> received = new LinkedBlockingQueue<>();

  public FakeIsoHost() {
    this.port = freePort();
    MessageFactory<IsoMessage> messageFactory =
        new MessageFactoryHelper(PackagerConstant.DEFAULT).createMessageFactory(PackagerConstant.DEFAULT);

    ServerConfiguration config = ServerConfiguration.newBuilder()
        .addEchoMessageListener(true)   // auto-reply 0810 to the client's scheduled echo
        .addLoggingHandler(true)
        .build();

    this.server = new Iso8583Server<>(port, config, messageFactory);
    this.server.setConfigurer(new ConnectorConfigurer<ServerConfiguration, ServerBootstrap>() {
      @Override
      public void configurePipeline(ChannelPipeline pipeline, ServerConfiguration configuration) {
        pipeline.addLast("e2eCapture", new CaptureHandler());
      }
    });
  }

  public int getPort() {
    return port;
  }

  public void start() throws InterruptedException {
    server.init();
    server.start();
  }

  public void stop() {
    server.shutdown();
  }

  /** True once the application has established its TCP connection. */
  public boolean isClientConnected() {
    Channel ch = clientChannel.get();
    return ch != null && ch.isActive();
  }

  /**
   * Closes the current client connection from the server side, forcing the application's
   * {@code Iso8583Client} auto-reconnect (configured via {@code network.reconnect-interval}) to
   * re-establish it. Useful when a test needs a fresh, per-connection pipeline handler (e.g.
   * {@code IsoCallbackResponseHandler}) built after config that handler reads once, at connect
   * time, has already been loaded -- see {@code CallbackModeE2eTest}.
   */
  public void disconnectClient() {
    Channel ch = clientChannel.get();
    if (ch != null) {
      ch.close();
    }
  }

  /** Pushes a message to the connected application client. */
  public void send(IsoMessage message) throws InterruptedException {
    Channel ch = clientChannel.get();
    if (ch == null) {
      throw new IllegalStateException("no application client connected yet");
    }
    ch.writeAndFlush(message).sync();
  }

  /** Discards anything received so far (e.g. sign-on / echo noise). */
  public void drain() {
    received.clear();
  }

  /**
   * Waits for a received message matching {@code predicate}, discarding
   * non-matching messages (echo/network chatter) along the way.
   *
   * @return the matching message, or {@code null} if none arrived in time
   */
  public IsoMessage awaitMessage(Predicate<IsoMessage> predicate, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      long remainingMs = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
      IsoMessage msg = received.poll(remainingMs, TimeUnit.MILLISECONDS);
      if (msg != null && predicate.test(msg)) {
        return msg;
      }
    }
    return null;
  }

  /** Asserts that no message matching {@code predicate} arrives within {@code timeout}. */
  public boolean noMessage(Predicate<IsoMessage> predicate, Duration timeout)
      throws InterruptedException {
    return awaitMessage(predicate, timeout) == null;
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("cannot allocate a free port", e);
    }
  }

  private final class CaptureHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      clientChannel.set(ctx.channel());
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      clientChannel.compareAndSet(ctx.channel(), null);
      super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof IsoMessage isoMessage) {
        received.offer(isoMessage);
      }
      super.channelRead(ctx, msg);
    }
  }
}
