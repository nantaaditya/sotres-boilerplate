package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.IsoObservationContext;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * {@code onMessage} runs on the Netty event loop and must never block. It hands the transaction
 * off to {@link #ISO_TRANSACTION_EXECUTOR} immediately; the executor's {@code apps.async
 * .configurations.isoTransaction.rejection-policy} MUST stay {@code ABORT} (see
 * {@link com.nantaaditya.sotres.properties.embedded.AsyncConfiguration}) — {@code CALLER_RUNS}
 * would run a full transaction (JSLT + JDBC + outbound REST) on the event loop itself once the
 * pool saturates, stalling every ISO8583 connection sharing it. A rejection here is treated as a
 * normal, non-blocking shed (DE39=96), not an error.
 */
@Log4j2
@Component
public class TransactionProcessorParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  static final String ISO_TRANSACTION_EXECUTOR = "isoTransactionAsyncTaskExecutor";
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

    this.inFlightAcquireTimeoutMs = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION)
        .flightQueueTimeOut();

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
    IsoObservationContext isoContext = tracerHelper.startIsoObservation(isoMessage, observationRegistry);
    Observation observation = isoContext.observation();
    Span span = isoContext.span();
    Map<String, String> mdc = isoContext.mdc();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      IsoCategory isoCategory = (IsoCategory) ctx.channel()
          .attr(AttributeKey.valueOf(CALLBACK_ATTRIBUTE))
          .get();

      RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, isoCategory);

      // in response-registry mode this inbound message is the response to one of our own
      // requests — leave it for TransactionResponseParticipant, do not process it as a new txn
      if (isResponseRegistryEnabled(requestContext)) {
        return true;
      }

      ObservationHelper.createTransactionContext(observation, requestContext.getRrn(), requestContext.getIsoFeatureConstant());
      isoFieldHelper.publishIsoEvent(observation, isoMessage, IsoFieldHelper.ISO_REQUEST_EVENT);

      // hand the transaction off the Netty event loop onto a (virtual) worker thread
      isoTransactionExecutor.execute(() -> handleTransaction(ctx, isoMessage, requestContext, observation, span, mdc));
    } catch (RejectedExecutionException rejected) {
      handleRejectedTransaction(ctx, isoMessage, rejected, observation, span);
      return false;
    } finally {
      tracerHelper.restoreCallerMdc(isoContext);
    }

    log.info(AppLogMessage.message("#Transaction - message with RRN {} received", IsoFieldHelper.getField(isoMessage, 37)));
    return false;
  }

  private void handleTransaction(ChannelHandlerContext ctx, IsoMessage isoMessage,
      RequestContext requestContext, Observation observation, Span span, Map<String, String> mdc) {

    ParticipantContext participantContext = new ParticipantContext();
    boolean acquired = false;

    // Propagate the worker thread's base MDC (x-request-id, etc.) BEFORE opening the span/
    // observation scope below: MDCScopeDecorator additively MDC.puts traceId/spanId as a side
    // effect of the scope opening. Doing this the other way around — setContextMap after the
    // scope is already open — replaces the whole context map and discards those keys (see
    // docs/POST_MIGRATION_REMEDIATION_PLAN.md Phase 7D).
    MDC.setContextMap(mdc);
    try (SpanInScope spanInScope = tracer.withSpan(span);
        Observation.Scope scope = observation.openScope()) {

      isoMessageLoggerHelper.logIsoMessage(isoMessage);

      Optional<AbstractTransactionHandler> maybeHandler = findTransactionHandler(requestContext);

      if (maybeHandler.isEmpty()) {
        log.warn(AppLogMessage.message("#Transaction - skipping unknown transaction handler {}", requestContext.getSelector()));
        participantContext.onUpdate(ctx, isoMessage, null, requestContext, observation);
        isoFieldHelper.sendResponseWithObservation(participantContext, IsoResponseCode.UNABLE_TO_ROUTE_TRANSACTION.getCode(), null);
        return;
      }

      AbstractTransactionHandler handler = maybeHandler.get();
      participantContext.onUpdate(ctx, isoMessage, handler, requestContext, observation);

      acquired = inFlightBulkhead.tryAcquire(inFlightAcquireTimeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        log.error(AppLogMessage.message("#Transaction - in-flight bulkhead saturated, shedding RRN {}", requestContext.getRrn()));
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

    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      senderProtocolStrategy.handleError(participantContext, exception);
    } catch (Exception exception) {
      senderProtocolStrategy.handleError(participantContext, exception);
    } finally {
      if (acquired) {
        inFlightBulkhead.release();
      }

      observation.stop();
      span.end();
      MDC.clear();
    }
  }

  // executor pool + queue saturated — shed here, on the event loop, rather than let
  // CallerRunsPolicy (or any blocking fallback) run the transaction on this thread
  private void handleRejectedTransaction(ChannelHandlerContext ctx, IsoMessage isoMessage,
      RejectedExecutionException rejected, Observation observation, Span span) {

    log.error(AppLogMessage.message("#Transaction - executor saturated, shedding RRN {}",
        IsoFieldHelper.getField(isoMessage, 37)).error(rejected));
    ParticipantContext participantContext = new ParticipantContext();
    participantContext.onUpdate(ctx, isoMessage, null, null, observation);
    isoFieldHelper.sendResponseWithObservation(
        participantContext, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), rejected);
    observation.stop();
    span.end();
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
