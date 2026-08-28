package com.nantaaditya.sotres.helper;

import com.github.kpavlov.jreactive8583.ConnectorConfigurer;
import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.IsoClientConfigurationRequest;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EnhancedIsoClient
    extends Iso8583Client<IsoMessage>
    implements IsoCallbackConstant {

  private final CorrelationRegistry correlationRegistry;
  private final IsoFieldHelper isoFieldHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final RegistryType registryType;

  public EnhancedIsoClient(IsoClientConfigurationRequest clientConfiguration) {

    super(clientConfiguration.socketAddress(), clientConfiguration.clientConfiguration(), clientConfiguration.messageFactory());
    this.correlationRegistry = clientConfiguration.correlationRegistry();
    this.isoFieldHelper = clientConfiguration.isoFieldHelper();
    this.isoMessageLoggerHelper = clientConfiguration.isoMessageLoggerHelper();
    this.registryType = clientConfiguration.clientProperties().getRegistryType();
    this.setConfigurer(new ConnectorConfigurer<ClientConfiguration, Bootstrap>() {
      @Override
      public void configurePipeline(ChannelPipeline pipeline, ClientConfiguration configuration) {
        pipeline.addLast(IsoCallbackConstant.CALLBACK_NAME,
            // classify the inbound response (success / late / orphan / external) and deliver
            // it to any waiting CorrelationRegistry future
            new IsoCallbackResponseHandler(
                correlationRegistry,
                clientConfiguration.systemPropertiesService(),
                clientConfiguration.tracerHelper()
            )
        );
      }
    });
  }

  /**
   * CALLBACK mode: register the correlation, flush the write, return once the write
   * completes — the caller does <b>not</b> wait for the ISO response. The response is
   * delivered later to the {@link CorrelationRegistry} future by
   * {@link IsoCallbackResponseHandler}, then handled by {@code TransactionResponseParticipant}.
   *
   * <p>Must run on a worker (virtual) thread — never the Netty event loop.
   */
  public void sendWithCallback(IsoMessage request) {
    requireWritableChannel();
    if (RegistryType.CALLBACK != this.registryType) {
      throw new IllegalStateException("callback mode not enabled (client.registry-type != CALLBACK)");
    }

    String correlationId = IsoFieldHelper.getCorrelationId(request);
    correlationRegistry.register(correlationId);
    try {
      sendAsync(request).sync();
    } catch (InterruptedException e) {
      correlationRegistry.cancel(correlationId);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted sending callback request " + correlationId, e);
    } catch (Exception e) {
      correlationRegistry.cancel(correlationId);
      throw new IllegalStateException("failed sending callback request " + correlationId, e);
    }
  }

  /**
   * RESPONSE mode: register the correlation, flush the write, then wait up to
   * {@code timeout} for the matching inbound response.
   *
   * <p>Must run on a worker (virtual) thread — never the Netty event loop.
   *
   * @return the correlated response, or a synthetic DE39 error response
   *         ({@link IsoResponseCode#SUSPEND_TRANSACTION} on timeout,
   *         {@link IsoResponseCode#SYSTEM_MALFUNCTION} otherwise)
   */
  public IsoMessage send(IsoMessage request, Duration timeout) {
    requireWritableChannel();
    if (RegistryType.RESPONSE != this.registryType) {
      throw new IllegalStateException("response mode not enabled (client.registry-type != RESPONSE)");
    }

    String correlationId = IsoFieldHelper.getCorrelationId(request);
    CompletableFuture<IsoMessage> pending = correlationRegistry.register(correlationId);

    try {
      sendAsync(request).sync();
      return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      correlationRegistry.cancel(correlationId);
      log.error(AppLogMessage.message("#API - no response within {} for key {}", timeout, correlationId).error(e));
      return constructErrorResponse(request, IsoResponseCode.SUSPEND_TRANSACTION);
    } catch (ExecutionException e) {
      correlationRegistry.cancel(correlationId);
      IsoResponseCode code = e.getCause() instanceof TimeoutException
          ? IsoResponseCode.SUSPEND_TRANSACTION : IsoResponseCode.SYSTEM_MALFUNCTION;
      log.error(AppLogMessage.message("#API - response failed for key {}", correlationId).error(e));
      return constructErrorResponse(request, code);
    } catch (InterruptedException e) {
      correlationRegistry.cancel(correlationId);
      Thread.currentThread().interrupt();
      log.error(AppLogMessage.message("#API - interrupted awaiting response for key {}", correlationId).error(e));
      return constructErrorResponse(request, IsoResponseCode.SYSTEM_MALFUNCTION);
    } catch (Exception e) {
      correlationRegistry.cancel(correlationId);
      log.error(AppLogMessage.message("#API - send/response error for key {}", correlationId).error(e));
      return constructErrorResponse(request, IsoResponseCode.SYSTEM_MALFUNCTION);
    }
  }

  private void requireWritableChannel() {
    Channel channel = getChannel();
    if (channel == null || !channel.isWritable()) {
      throw new IllegalStateException("Channel not writable or disconnected");
    }
  }

  private IsoMessage constructErrorResponse(IsoMessage request, IsoResponseCode responseCode) {
    IsoMessage result = isoFieldHelper.createResponse(request);
    result.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode.getCode(), 2));
    return result;
  }
}
