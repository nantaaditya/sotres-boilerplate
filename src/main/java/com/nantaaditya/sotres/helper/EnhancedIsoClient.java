package com.nantaaditya.sotres.helper;

import com.github.kpavlov.jreactive8583.ConnectorConfigurer;
import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.IsoClientConfigurationRequest;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Log4j2
public class EnhancedIsoClient
    extends Iso8583Client<IsoMessage>
    implements IsoCallbackConstant {

  private final IsoCallbackRegistry isoCallbackRegistry;
  private final IsoResponseRegistry isoResponseRegistry;
  private final IsoFieldHelper isoFieldHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final Scheduler scheduler;
  private final RegistryType registryType;

  public EnhancedIsoClient(IsoClientConfigurationRequest clientConfiguration) {

    super(clientConfiguration.socketAddress(), clientConfiguration.clientConfiguration(), clientConfiguration.messageFactory());
    this.isoCallbackRegistry = clientConfiguration.isoCallbackRegistry();
    this.isoResponseRegistry = clientConfiguration.isoResponseRegistry();
    this.isoFieldHelper = clientConfiguration.isoFieldHelper();
    this.isoMessageLoggerHelper = clientConfiguration.isoMessageLoggerHelper();
    this.registryType = clientConfiguration.clientProperties().getRegistryType();
    this.scheduler = clientConfiguration.participantConfigurationProperties()
        .getPool(ManagerConstant.API)
        .createScheduler();
    this.setConfigurer(new ConnectorConfigurer<ClientConfiguration, Bootstrap>() {
      @Override
      public void configurePipeline(ChannelPipeline pipeline, ClientConfiguration configuration) {
        pipeline.addLast(IsoCallbackConstant.CALLBACK_NAME,
            // callback to determine if the response success, late response, orphan, or external request
            new IsoCallbackResponseHandler(
                isoCallbackRegistry,
                clientConfiguration.systemPropertiesService(),
                clientConfiguration.tracerHelper()
            )
        );
      }
    });
  }

  /**
   * new method that provides the async response callback + timeout
   * when the callback received, it will be handled by TransactionProcessorParticipant
   */
  public Mono<Void> sendWithCallback(IsoMessage request) {
    // use the base class channel
    Channel channel = getChannel();

    if (channel == null || !channel.isWritable()) {
      return Mono.error(new IllegalStateException("Channel not writable or disconnected"));
    }

    if (RegistryType.CALLBACK != this.registryType) {
      return Mono.error(new IllegalStateException("callback not applicable"));
    }

    // leverage the base class send method
    String correlationId = IsoFieldHelper.getCorrelationId(request);
    isoCallbackRegistry.register(request);
    return Mono.create(sink -> {
      sendAsync(request)
        .addListener(future -> {
          if (future.isSuccess()) {
            sink.success(); // async ACK
          } else {
            isoCallbackRegistry.remove(IsoFieldHelper.getCorrelationId(request));
            sink.error(future.cause());
          }
        });
    });
  }

  /**
   * new method that provides the sync request response + timeout
   * when the response received, it will be handled by TransactionResponseParticipant
   */
  public Mono<IsoMessage> sendWithResponse(IsoMessage request) {
    // use the base class channel
    Channel channel = getChannel();

    if (channel == null || !channel.isWritable()) {
      return Mono.error(new IllegalStateException("Channel not writable or disconnected"));
    }

    if (RegistryType.RESPONSE != this.registryType) {
      return Mono.error(new IllegalStateException("response not applicable"));
    }

    return Mono.defer(() -> {
          Mono<IsoMessage> responseExpectation = isoResponseRegistry.register(request);

          return Mono.fromRunnable(() -> {
            try {
              send(request);
            } catch (Exception e) {
              log.error(AppLogMessage.message("#API - send with response error").error(e));
              throw new RuntimeException(e);
            }
          })
            .subscribeOn(scheduler)
            .then(responseExpectation);
        })
        .onErrorResume(TimeoutException.class, e -> {
          log.error(AppLogMessage.message("#API - error response").error(e));
          return Mono.just(constructErrorResponse(request, IsoResponseCode.SUSPEND_TRANSACTION));
        })
        .onErrorResume(e -> {
          log.error(AppLogMessage.message("#API - error response").error(e));
          return Mono.just(constructErrorResponse(request, IsoResponseCode.SYSTEM_MALFUNCTION));
        });
  }

  private IsoMessage constructErrorResponse(IsoMessage request, IsoResponseCode responseCode) {
    IsoMessage result =  isoFieldHelper.createResponse(request);
    result.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode.getCode(), 2));
    return result;
  }
}