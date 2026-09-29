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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
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
  private final IsoResponseSender isoResponseSender;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final RegistryType registryType;
  private final ObservationRegistry observationRegistry;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;

  public EnhancedIsoClient(IsoClientConfigurationRequest clientConfiguration) {

    super(clientConfiguration.socketAddress(), clientConfiguration.clientConfiguration(), clientConfiguration.messageFactory());
    this.correlationRegistry = clientConfiguration.correlationRegistry();
    this.isoResponseSender = clientConfiguration.isoResponseSender();
    this.isoMessageLoggerHelper = clientConfiguration.isoMessageLoggerHelper();
    this.registryType = clientConfiguration.clientProperties().getRegistryType();
    this.observationRegistry = clientConfiguration.observationRegistry();
    this.tracerHelper = clientConfiguration.tracerHelper();
    this.tracer = tracerHelper.getTracer();
    this.setConfigurer(new ConnectorConfigurer<ClientConfiguration, Bootstrap>() {
      @Override
      public void configurePipeline(ChannelPipeline pipeline, ClientConfiguration configuration) {
        // Iso8583ChannelInitializer.initChannel() adds the framework's own message-listener
        // dispatcher (messageHandler -- fans out to TransactionProcessorParticipant et al.) via
        // addLast BEFORE calling this configurer. addLast here would therefore run this handler
        // AFTER that dispatcher for every inbound message: TransactionProcessorParticipant would
        // read CALLBACK_ATTRIBUTE one message too late (stale/empty), never the current message's
        // real classification. Insert right after the decoder instead, so classification always
        // happens before any participant sees the message. "iso8583Decoder" is the fixed name
        // Iso8583ChannelInitializer registers the decoder under.
        pipeline.addAfter("iso8583Decoder", IsoCallbackConstant.CALLBACK_NAME,
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

    IsoObservationContext isoContext = tracerHelper.startIsoObservation(request, observationRegistry);
    Observation observation = isoContext.observation();
    Span span = isoContext.span();

    try (SpanInScope spanInScope = tracer.withSpan(span);
        Observation.Scope scope = observation.openScope()) {

      String de37 = IsoFieldHelper.getField(request, 37);
      ObservationHelper.createTransactionContext(observation, de37, IsoFieldHelper.getIsoFeature(request));
      isoResponseSender.publishIsoEvent(observation, request,
          IsoResponseSender.ISO_REQUEST_EVENT, IsoMessageLoggerHelper.OUTGOING_ISO);

      sendAsync(request).sync();
      IsoMessage response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return response;
    } catch (TimeoutException e) {
      correlationRegistry.cancel(correlationId);
      log.error(AppLogMessage.message("#API - no response within {} for key {}", timeout, correlationId).error(e));
      ObservationHelper.observeResponse(observation, IsoResponseCode.SUSPEND_TRANSACTION.getCode(), e);
      return constructErrorResponse(request, IsoResponseCode.SUSPEND_TRANSACTION);
    } catch (ExecutionException e) {
      correlationRegistry.cancel(correlationId);
      IsoResponseCode code = e.getCause() instanceof TimeoutException
          ? IsoResponseCode.SUSPEND_TRANSACTION : IsoResponseCode.SYSTEM_MALFUNCTION;
      log.error(AppLogMessage.message("#API - response failed for key {}", correlationId).error(e));
      ObservationHelper.observeResponse(observation, code.getCode(), e);
      return constructErrorResponse(request, code);
    } catch (InterruptedException e) {
      correlationRegistry.cancel(correlationId);
      Thread.currentThread().interrupt();
      log.error(AppLogMessage.message("#API - interrupted awaiting response for key {}", correlationId).error(e));
      ObservationHelper.observeResponse(observation, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), e);
      return constructErrorResponse(request, IsoResponseCode.SYSTEM_MALFUNCTION);
    } catch (Exception e) {
      correlationRegistry.cancel(correlationId);
      log.error(AppLogMessage.message("#API - send/response error for key {}", correlationId).error(e));
      ObservationHelper.observeResponse(observation, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), e);
      return constructErrorResponse(request, IsoResponseCode.SYSTEM_MALFUNCTION);
    } finally {
      span.end();
      observation.stop();
      tracerHelper.restoreCallerMdc(isoContext);
    }
  }

  private void requireWritableChannel() {
    Channel channel = getChannel();
    if (channel == null || !channel.isWritable()) {
      throw new IllegalStateException("Channel not writable or disconnected");
    }
  }

  private IsoMessage constructErrorResponse(IsoMessage request, IsoResponseCode responseCode) {
    IsoMessage result = isoResponseSender.createResponse(request);
    result.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode.getCode(), 2));
    return result;
  }
}
