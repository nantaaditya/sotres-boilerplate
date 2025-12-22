package com.nantaaditya.sotres.helper;

import com.github.kpavlov.jreactive8583.ConnectorConfigurer;
import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.net.SocketAddress;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Log4j2
public class EnhancedIsoClient
    extends Iso8583Client<IsoMessage>
    implements IsoCallbackConstant {

  private final IsoMessageRegistry isoMessageRegistry;

  public EnhancedIsoClient(
      SocketAddress socketAddress, ClientConfiguration config, MessageFactory<IsoMessage> factory,
      IsoMessageRegistry isoMessageRegistry, SystemPropertiesService systemPropertiesService,
      TracerHelper tracerHelper) {

    super(socketAddress, config, factory);
    this.isoMessageRegistry = isoMessageRegistry;
    this.setConfigurer(new ConnectorConfigurer<ClientConfiguration, Bootstrap>() {
      @Override
      public void configurePipeline(ChannelPipeline pipeline, ClientConfiguration configuration) {
        pipeline.addLast(IsoCallbackConstant.CALLBACK_NAME,
            // callback to determine if the response success, late response, orphan, or external request
            new IsoCallbackResponseHandler(
                isoMessageRegistry,
                systemPropertiesService,
                tracerHelper
            )
        );
      }
    });
  }

  /**
   * new method that provides the async response callback + timeout
   * when the callback received, it will be handled by TransactionResponseParticipant
   */
  public Mono<Void> sendWithCallback(IsoMessage request) {
    // use the base class channel
    Channel channel = getChannel();

    if (channel == null || !channel.isWritable()) {
      return Mono.error(new IllegalStateException("Channel not writable or disconnected"));
    }

    // leverage the base class send method
    String correlationId = IsoFieldHelper.getCorrelationId(request);
    isoMessageRegistry.put(request);
    return Mono.create(sink -> {
      sendAsync(request)
        .addListener(future -> {
          if (future.isSuccess()) {
            sink.success(); // async ACK
          } else {
            isoMessageRegistry.remove(IsoFieldHelper.getCorrelationId(request));
            sink.error(future.cause());
          }
        });
    });
  }

}