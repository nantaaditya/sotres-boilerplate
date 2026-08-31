package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.outgoing.SenderProtocolStrategy;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class TransactionProcessorParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  // registered by AsyncTaskConfiguration from apps.async.configurations.isoTransaction
  static final String ISO_TRANSACTION_EXECUTOR = "isoTransactionAsyncTaskExecutor";
  // registered by BulkheadConfiguration from apps.bulkhead.configurations.isoTransaction
  static final String ISO_TRANSACTION_BULKHEAD = "isoTransactionBulkhead";

  private final SystemPropertiesService systemPropertiesService;
  private final SenderProtocolStrategy senderProtocolStrategy;
  private final List<AbstractTransactionHandler> transactionHandlers;
  private final ObservationRegistry observationRegistry;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Executor isoTransactionExecutor;
  private final Semaphore inFlightBulkhead;
  // How long a transaction waits at the door for a bulkhead permit before it is shed with DE39=96.
  // Set to flightQueueTimeOut (the transaction's whole life budget): waiting longer is pointless
  // (it would be dead anyway), waiting less would shed transactions that still had time to finish.
  private final long inFlightAcquireTimeoutMs;
  private final IsoMessageProperties isoMessageProperties;
  private final ClientProperties clientProperties;
  private final List<String> responseRegistrySelectors;

  public TransactionProcessorParticipant(SystemPropertiesService systemPropertiesService,
      List<AbstractTransactionHandler> transactionHandlers,
      IsoMessageLoggerHelper isoMessageLoggerHelper, IsoFieldHelper isoFieldHelper,
      ObservationRegistry observationRegistry, TracerHelper tracerHelper, Tracer tracer,
      List<SenderProtocolStrategy> senderProtocolStrategies,
      ParticipantConfigurationProperties participantConfigurationProperties,
      IsoMessageProperties isoMessageProperties, ClientProperties clientProperties,
      @Lazy @Qualifier(ISO_TRANSACTION_EXECUTOR) Executor isoTransactionExecutor,
      @Qualifier(ISO_TRANSACTION_BULKHEAD) Semaphore inFlightBulkhead) {

    this.systemPropertiesService = systemPropertiesService;
    this.transactionHandlers = transactionHandlers;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
    this.observationRegistry = observationRegistry;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
    this.isoMessageProperties = isoMessageProperties;
    this.clientProperties = clientProperties;
    this.isoTransactionExecutor = isoTransactionExecutor;
    this.inFlightBulkhead = inFlightBulkhead;
    this.inFlightAcquireTimeoutMs = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION).flightQueueTimeOut();

    this.responseRegistrySelectors = ConfigGroup.getList(
        this.systemPropertiesService,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR
    );
    this.senderProtocolStrategy = senderProtocolStrategies.stream()
        .filter(sender -> sender.getProtocol() == isoMessageProperties.outgoingProtocol())
        .findAny()
        .orElseThrow(() -> new IllegalStateException("no sender protocol strategy found: "+isoMessageProperties.outgoingProtocol()));
  }

  @Override
  public boolean applies(@NotNull IsoMessage isoMessage) {
    return !MTIs.contains(isoMessage.getType());
  }

  @Override
  public boolean onMessage(@NotNull ChannelHandlerContext ctx, @NotNull IsoMessage isoMessage) {
    // start observation + manual span on the event loop; do NOT block here
    Observation observation = Observation.start(ObservationConstant.ISO_MESSAGE.getName(), observationRegistry);
    Span span = tracerHelper.startSpan(tracer, ObservationConstant.ISO_MESSAGE.getName());
    Map<String, String> mdc = new HashMap<>();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      tracerHelper.initiateSpan(isoMessage, mdc);
      IsoCategory isoCategory = (IsoCategory) ctx.channel()
          .attr(AttributeKey.valueOf(CALLBACK_ATTRIBUTE))
          .get();

      RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, isoCategory);

      // in response-registry mode this inbound message is the response to one of our own
      // requests — leave it for TransactionResponseParticipant, do not process it as a new txn
      if (isResponseRegistryEnabled(requestContext)) {
        return true;
      }

      // hand the transaction off the Netty event loop onto a (virtual) worker thread
      isoTransactionExecutor.execute(() -> handleTransaction(ctx, isoMessage, requestContext, observation, span, mdc));
    }

    log.info(AppLogMessage.message("#Transaction - message with RRN {} received", IsoFieldHelper.getField(isoMessage, 37)));
    return false;
  }

  private void handleTransaction(ChannelHandlerContext ctx, IsoMessage isoMessage,
      RequestContext requestContext, Observation observation, Span span, Map<String, String> mdc) {

    ParticipantContext participantContext = new ParticipantContext();
    boolean acquired = false;

    try (SpanInScope spanInScope = tracer.withSpan(span);
        Observation.Scope scope = observation.openScope()) {
      MDC.setContextMap(mdc);

      isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      Optional<AbstractTransactionHandler> maybeHandler = findTransactionHandler(requestContext);

      if (maybeHandler.isEmpty()) {
        log.warn(AppLogMessage.message("#Transaction - skipping unknown transaction handler {}", requestContext.getSelector()));
        participantContext.onUpdate(ctx, isoMessage, null, requestContext, observation);
        isoFieldHelper.sendResponseWithObservation(participantContext, IsoResponseCode.UNABLE_TO_ROUTE_TRANSACTION.getCode(), null);
        return;
      }

      participantContext.onUpdate(ctx, isoMessage, maybeHandler.get(), requestContext, observation);

      acquired = inFlightBulkhead.tryAcquire(inFlightAcquireTimeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        log.error(AppLogMessage.message("#Transaction - in-flight bulkhead saturated, shedding RRN {}",
            requestContext.getRrn()));
        isoFieldHelper.sendResponseWithObservation(participantContext, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), null);
        return;
      }

      participantContext.getTransactionHandler().execute(participantContext);            // blocking validate + process
      ResponseContext responseContext = senderProtocolStrategy.send(                     // blocking downstream call
          participantContext.getChannelHandlerContext(),
          participantContext.getIsoMessage(),
          participantContext.getRequestContext()
      );
      participantContext.onResponse(responseContext);
      senderProtocolStrategy.handleResponse(participantContext);                         // writes ISO response

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      senderProtocolStrategy.handleError(participantContext, e);
    } catch (Exception exception) {
      senderProtocolStrategy.handleError(participantContext, exception);
    } finally {
      if (acquired) {
        inFlightBulkhead.release();
      }
      span.end();
      MDC.clear();
    }
  }

  private Optional<AbstractTransactionHandler> findTransactionHandler(RequestContext requestContext) {
    return transactionHandlers.stream()
        .filter(handler -> handler.getSelectors().contains(requestContext.getSelector()))
        .findFirst();
  }

  private boolean isResponseRegistryEnabled(RequestContext requestContext) {
    return RegistryType.RESPONSE == clientProperties.getRegistryType()
        && responseRegistrySelectors.contains(requestContext.getSelector());
  }
}
