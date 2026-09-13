package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.CorrelationRegistry;
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
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.netty.channel.ChannelHandlerContext;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Completes the {@link CorrelationRegistry} future for an inbound ISO response when the
 * client runs in {@link RegistryType#RESPONSE} mode (an application flow sent a request via
 * {@code EnhancedIsoClient.send(...)} and is blocked waiting for the matching response).
 *
 * <p>{@link TransactionProcessorParticipant} yields these messages via
 * {@code isResponseRegistryEnabled}, so exactly one participant acts on them.
 *
 * <p>{@code onMessage} runs on the Netty event loop, same as {@link TransactionProcessorParticipant}
 * — the same {@code ABORT} rejection-policy requirement applies here too. A rejection has no ISO
 * reply to send (this class only completes futures), so it just logs and stops the observation
 * rather than erroring.
 *
 * <p>Deliberately uses its own executor ({@link #ISO_TRANSACTION_EXECUTOR}), separate from
 * {@link TransactionProcessorParticipant}'s: a fast completion (just resolving a
 * {@code CompletableFuture}) must never queue behind a slow transaction (JSLT + JDBC + outbound
 * REST) on a shared pool — that starves any {@code EnhancedIsoClient.send()} caller blocked
 * waiting for the reply into a false timeout.
 */
@Log4j2
@Component
public class TransactionResponseParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  // registered by AsyncTaskConfiguration from apps.async.configurations.isoTransactionResponse
  static final String ISO_TRANSACTION_EXECUTOR = "isoTransactionResponseAsyncTaskExecutor";

  private final SystemPropertiesService systemPropertiesService;
  private final CorrelationRegistry correlationRegistry;
  private final ObservationRegistry observationRegistry;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Executor isoTransactionExecutor;
  private final ClientProperties clientProperties;
  private final List<String> responseRegistrySelectors;

  public TransactionResponseParticipant(SystemPropertiesService systemPropertiesService,
      CorrelationRegistry correlationRegistry, ObservationRegistry observationRegistry,
      IsoMessageLoggerHelper isoMessageLoggerHelper, IsoFieldHelper isoFieldHelper, TracerHelper tracerHelper,
      Tracer tracer, ClientProperties clientProperties,
      @Lazy @Qualifier(ISO_TRANSACTION_EXECUTOR) Executor isoTransactionExecutor) {

    this.systemPropertiesService = systemPropertiesService;
    this.correlationRegistry = correlationRegistry;
    this.observationRegistry = observationRegistry;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
    this.clientProperties = clientProperties;
    this.isoTransactionExecutor = isoTransactionExecutor;

    this.responseRegistrySelectors = ConfigGroup.getList(
        this.systemPropertiesService,
        ConfigGroup.REGISTRY_RESPONSE_SELECTOR
    );
  }

  /**
   * Handle non-network messages when response-registry mode is enabled and the selector is
   * eligible.
   */
  @Override
  public boolean applies(@NotNull IsoMessage isoMessage) {
    RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, null);
    return !MTIs.contains(isoMessage.getType()) && isResponseRegistryEnabled(requestContext);
  }

  @Override
  public boolean onMessage(@NotNull ChannelHandlerContext ctx, @NotNull IsoMessage isoMessage) {
    // start observation + manual span on the event loop; hand the completion to a worker thread
    IsoObservationContext isoContext = tracerHelper.startIsoObservation(isoMessage, observationRegistry);
    Observation observation = isoContext.observation();
    Span span = isoContext.span();
    Map<String, String> mdc = isoContext.mdc();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      IsoCategory isoCategory = completeCorrelation(isoMessage);
      isoMessageLoggerHelper.logIsoMessage(isoMessage);
      isoTransactionExecutor.execute(() -> completeResponse(isoMessage, isoCategory, observation, span, mdc));
    } catch (RejectedExecutionException rejected) {
      handleRejectedTransaction(isoMessage, rejected, observation, span);
      return false;
    } finally {
      tracerHelper.restoreCallerMdc(isoContext);
    }

    log.info(AppLogMessage.message("#Transaction - response message with RRN {} received", IsoFieldHelper.getField(isoMessage, 37)));
    return false;
  }

  // resolves the waiting CorrelationRegistry future for this response and classifies the
  // outcome. A completion failure must never abort onMessage — there is no ISO reply to send
  // here regardless (this class only completes futures) — so it is logged and swallowed.
  //
  // NOTE: IsoCallbackResponseHandler also calls correlationRegistry.complete(...), gated by the
  // registry.callback_selector config, upstream in the same pipeline. If that selector list ever
  // overlaps with registry.response_selector for a given message, this call becomes a harmless
  // second completion (CorrelationRegistry.complete is idempotent-safe — see its javadoc) except
  // for a misleading ORPHAN log line from whichever call runs second. Keep the two selector
  // configs disjoint to avoid that log noise.
  private IsoCategory completeCorrelation(IsoMessage isoMessage) {
    try {
      return correlationRegistry.complete(isoMessage);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Transaction - correlation completion failed for RRN {}",
          IsoFieldHelper.getField(isoMessage, 37)).error(e));
      return null;
    }
  }

  private void completeResponse(IsoMessage isoMessage, IsoCategory category, Observation observation,
      Span span, Map<String, String> mdc) {

    // See TransactionProcessorParticipant.handleTransaction: propagate the base MDC BEFORE
    // opening the span/observation scope, so MDCScopeDecorator's traceId/spanId writes land on
    // top instead of being wiped by a subsequent full-map setContextMap.
    MDC.setContextMap(mdc);
    try (SpanInScope spanInScope = tracer.withSpan(span);
        Observation.Scope scope = observation.openScope()) {

      String de37 = IsoFieldHelper.getField(isoMessage, 37);
      String de39 = IsoFieldHelper.getField(isoMessage, 39);
      String isoFeatureConstant = IsoFieldHelper.getIsoFeature(isoMessage);
      ObservationHelper.createTransactionContext(observation, de37, isoFeatureConstant);
      isoFieldHelper.publishIsoEvent(observation, isoMessage, IsoFieldHelper.ISO_RESPONSE_EVENT);
      ObservationHelper.observeResponse(observation, de39, null);
      log.info(AppLogMessage.message("#Transaction - response registry {} completed [{}]",
          IsoFieldHelper.getCorrelationId(isoMessage), category));
    } catch (Throwable throwable) {
      log.error(AppLogMessage.message("#Transaction - response registry {} completion failed",
          IsoFieldHelper.getCorrelationId(isoMessage)).error(throwable));
      ObservationHelper.observeResponse(observation, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), throwable);
    } finally {
      span.end();
      observation.stop();
      MDC.clear();
    }
  }

  // executor pool + queue saturated — no ISO reply is possible for a response message,
  // so just record the loss and shed here rather than block or run on this thread
  private void handleRejectedTransaction(IsoMessage isoMessage, RejectedExecutionException rejected,
      Observation observation, Span span) {
    log.error(AppLogMessage.message("#Transaction - executor saturated, dropping response completion for RRN {}",
        IsoFieldHelper.getField(isoMessage, 37)).error(rejected));
    isoFieldHelper.publishIsoEvent(observation, isoMessage, IsoFieldHelper.ISO_RESPONSE_EVENT);
    ObservationHelper.observeResponse(observation, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), rejected);
    observation.stop();
    span.end();
  }

  private boolean isResponseRegistryEnabled(RequestContext requestContext) {
    return RegistryType.RESPONSE == clientProperties.getRegistryType()
        && responseRegistrySelectors.contains(requestContext.getSelector());
  }
}
