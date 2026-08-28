package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.CorrelationRegistry;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
 */
@Log4j2
@Component
public class TransactionResponseParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  // registered by AsyncTaskConfiguration from apps.async.configurations.isoTransaction
  static final String ISO_TRANSACTION_EXECUTOR = "isoTransactionAsyncTaskExecutor";

  private final SystemPropertiesService systemPropertiesService;
  private final CorrelationRegistry correlationRegistry;
  private final ObservationRegistry observationRegistry;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Executor isoTransactionExecutor;
  private final ClientProperties clientProperties;
  private final List<String> responseRegistrySelectors;

  public TransactionResponseParticipant(SystemPropertiesService systemPropertiesService,
      CorrelationRegistry correlationRegistry, ObservationRegistry observationRegistry,
      TracerHelper tracerHelper, Tracer tracer, ClientProperties clientProperties,
      @Lazy @Qualifier(ISO_TRANSACTION_EXECUTOR) Executor isoTransactionExecutor) {

    this.systemPropertiesService = systemPropertiesService;
    this.correlationRegistry = correlationRegistry;
    this.observationRegistry = observationRegistry;
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
    Observation observation = Observation.start(ObservationConstant.ISO_MESSAGE.getName(), observationRegistry);
    Span span = tracerHelper.startSpan(tracer, ObservationConstant.ISO_MESSAGE.getName());
    Map<String, String> mdc = new HashMap<>();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      tracerHelper.initiateSpan(isoMessage, mdc);
      isoTransactionExecutor.execute(() -> completeResponse(isoMessage, observation, span, mdc));
    }

    log.info(AppLogMessage.message("#Transaction - message with RRN {} processed", IsoFieldHelper.getField(isoMessage, 37)));
    return false;
  }

  private void completeResponse(IsoMessage isoMessage, Observation observation, Span span,
      Map<String, String> mdc) {

    try (SpanInScope spanInScope = tracer.withSpan(span);
        Observation.Scope scope = observation.openScope()) {
      MDC.setContextMap(mdc);

      IsoCategory category = correlationRegistry.complete(isoMessage);
      log.info(AppLogMessage.message("#Transaction - response registry {} completed [{}]",
          IsoFieldHelper.getCorrelationId(isoMessage), category));
    } catch (Throwable throwable) {
      log.error(AppLogMessage.message("#Transaction - response registry {} completion failed",
          IsoFieldHelper.getCorrelationId(isoMessage)).error(throwable));
    } finally {
      span.end();
      observation.stop();
      MDC.clear();
    }
  }

  private boolean isResponseRegistryEnabled(RequestContext requestContext) {
    return RegistryType.RESPONSE == clientProperties.getRegistryType()
        && responseRegistrySelectors.contains(requestContext.getSelector());
  }
}
