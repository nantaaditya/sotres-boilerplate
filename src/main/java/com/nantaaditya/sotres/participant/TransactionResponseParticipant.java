package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.IsoResponseRegistry;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Log4j2
@Component
public class TransactionResponseParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  private final SystemPropertiesService systemPropertiesService;
  private final IsoResponseRegistry isoResponseRegistry;
  private final ObservationRegistry observationRegistry;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Scheduler scheduler;
  private final ClientProperties clientProperties;
  private final List<String> responseRegistrySelectors;

  private static final Set<Integer> MTIs = Set.of(0x800, 0x810);

  public TransactionResponseParticipant(SystemPropertiesService systemPropertiesService,
      List<AbstractTransactionHandler> transactionHandlers,
      IsoResponseRegistry isoResponseRegistry,
      IsoMessageLoggerHelper isoMessageLoggerHelper, IsoFieldHelper isoFieldHelper,
      ObservationRegistry observationRegistry, TracerHelper tracerHelper, Tracer tracer,
      ParticipantConfigurationProperties participantConfigurationProperties,
      ClientProperties clientProperties) {

    this.systemPropertiesService = systemPropertiesService;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
    this.observationRegistry = observationRegistry;
    this.isoResponseRegistry = isoResponseRegistry;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
    this.clientProperties = clientProperties;

    this.scheduler = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION).createScheduler();
    this.responseRegistrySelectors = PropertiesGroup.getList(
        this.systemPropertiesService,
        PropertiesGroup.REGISTRY_RESPONSE_SELECTOR
    );
  }

  /**
   * handle non network message, response registry enabled, and eligible message
   * @param isoMessage
   * @return
   */
  @Override
  public boolean applies(@NonNull IsoMessage isoMessage) {
    RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, null);
    return !MTIs.contains(isoMessage.getType()) && isResponseRegistryEnabled(requestContext);
  }

  @Override
  public boolean onMessage(@NonNull ChannelHandlerContext ctx, @NonNull IsoMessage isoMessage) {
    // start observation
    Observation observation = Observation.start(ObservationConstant.API_PUBLIC.getName(), observationRegistry);
    Span span = tracerHelper.startSpan(tracer, ObservationConstant.API_PUBLIC.getName());
    Map<String, String> mdc = new HashMap<>();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      // initiate manual span
      TraceContext traceContext = span.context();
      initiateSpan(isoMessage, mdc);

      RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, null);

      Mono.fromRunnable(() -> isoResponseRegistry.onResponse(isoMessage))
          .transformDeferred(contextMono -> tracerHelper.withSpanScopeAndMDC(contextMono, span, mdc))
          .contextWrite(context -> context
              .put(Span.class, span)
              .put(TraceContext.class, traceContext)
          )
          .subscribeOn(scheduler)
          .subscribe(
              success -> log.info(AppLogMessage.message("#Transaction - response registry {} processed",
                  IsoFieldHelper.getCorrelationId(isoMessage))),
              error -> log.error(AppLogMessage.message("#Transaction - response registry {} processed with error",
                  IsoFieldHelper.getCorrelationId(isoMessage)).error(error)),
              () -> {
                span.end();
                MDC.clear();
              }
          );

        MDC.setContextMap(mdc);
    }

    log.info(AppLogMessage.message("#Transaction - message with RRN {} processed", IsoFieldHelper.getField(isoMessage, 37)));
    return false;
  }

  private boolean isResponseRegistryEnabled(RequestContext requestContext) {
    return RegistryType.RESPONSE == clientProperties.getRegistryType()
        && responseRegistrySelectors.contains(requestContext.getSelector());
  }

  private void initiateSpan(IsoMessage isoMessage, Map<String, String> mdc) {
    tracerHelper.createTraceContext(isoMessage);
    mdc.putAll(MDC.getCopyOfContextMap());
    MDC.setContextMap(mdc);
  }

}
