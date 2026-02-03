package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.constant.RegistryType;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
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
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Tracer.SpanInScope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Log4j2
@Component
public class TransactionProcessorParticipant
    implements IsoMessageListener<IsoMessage>, IsoCallbackConstant {

  private final SystemPropertiesService systemPropertiesService;
  private final SenderProtocolStrategy senderProtocolStrategy;
  private final List<AbstractTransactionHandler> transactionHandlers;
  private final ObservationRegistry observationRegistry;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Scheduler scheduler;
  private final IsoMessageProperties isoMessageProperties;
  private final ClientProperties clientProperties;
  private final List<String> responseRegistrySelectors;

  private static final Set<Integer> MTIs = Set.of(0x800, 0x810);

  public TransactionProcessorParticipant(SystemPropertiesService systemPropertiesService,
      List<AbstractTransactionHandler> transactionHandlers,
      IsoMessageLoggerHelper isoMessageLoggerHelper, IsoFieldHelper isoFieldHelper,
      ObservationRegistry observationRegistry, TracerHelper tracerHelper, Tracer tracer,
      List<SenderProtocolStrategy> senderProtocolStrategies,
      ParticipantConfigurationProperties participantConfigurationProperties,
      IsoMessageProperties isoMessageProperties, ClientProperties clientProperties) {

    this.systemPropertiesService = systemPropertiesService;
    this.transactionHandlers = transactionHandlers;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
    this.observationRegistry = observationRegistry;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
    this.isoMessageProperties = isoMessageProperties;
    this.clientProperties = clientProperties;

    this.scheduler = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION).createScheduler();
    this.responseRegistrySelectors = PropertiesGroup.getList(
        this.systemPropertiesService,
        PropertiesGroup.REGISTRY_RESPONSE_SELECTOR
    );
    this.senderProtocolStrategy = senderProtocolStrategies.stream()
        .filter(sender -> sender.getProtocol() == isoMessageProperties.outgoingProtocol())
        .findAny()
        .orElse(null);
  }

  @Override
  public boolean applies(@NotNull IsoMessage isoMessage) {
    return !MTIs.contains(isoMessage.getType());
  }

  @Override
  public boolean onMessage(@NotNull ChannelHandlerContext ctx, @NotNull IsoMessage isoMessage) {
    // start observation
    Observation observation = Observation.start(ObservationConstant.API_PUBLIC.getName(), observationRegistry);
    Span span = tracerHelper.startSpan(tracer, ObservationConstant.API_PUBLIC.getName());
    Map<String, String> mdc = new HashMap<>();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      // initiate manual span
      TraceContext traceContext = span.context();
      initiateSpan(isoMessage, mdc);
      IsoCategory isoCategory = (IsoCategory) ctx.channel()
          .attr(AttributeKey.valueOf(CALLBACK_ATTRIBUTE))
          .get();

      ParticipantContext participantContext = new ParticipantContext();
      RequestContext requestContext = RequestContextHelper.create(isoMessage, systemPropertiesService, isoCategory);  // convert to internal DTO

      // propagate to the next participant
      if (isResponseRegistryEnabled(requestContext)) {
        return true;
      }

      Mono.fromCallable(() -> requestContext)
          .transformDeferred(contextMono -> tracerHelper.withSpanScopeAndMDC(contextMono, span, mdc))
          .filter(Objects::nonNull)
          // log & observe iso message
          .map(request -> logAndObserve(isoMessage, request, observation))
          // get transaction handler by selector
          .flatMap(request -> selectTransactionHandler(participantContext, ctx, isoMessage, request, observation))
          // execute transaction handler
          .flatMap(this::executeHandler)
          // process & send message using specific protocol
          .flatMap(this::sendMessage)
          // set up span and context
          .contextWrite(context -> context
              .put(Span.class, span)
              .put(TraceContext.class, traceContext)
          )
          .subscribeOn(scheduler)
          .subscribe(
              senderProtocolStrategy::handleResponse, // handle response
              throwable -> senderProtocolStrategy.handleError(participantContext, throwable), // handle error
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

  private RequestContext logAndObserve(IsoMessage isoMessage,
      RequestContext requestContext, Observation observation) {
    ObservationHelper.observeIsoRequest(observation, requestContext.getRrn(), requestContext.getIsoFeatureConstant());
    isoMessageLoggerHelper.logIsoMessage(isoMessage);
    return requestContext;
  }

  private Mono<ParticipantContext> executeHandler(ParticipantContext participantContext) {
    return participantContext
        .getTransactionHandler()
        .execute(participantContext);
  }

  private Mono<ParticipantContext> sendMessage(ParticipantContext ctx) {

    if (senderProtocolStrategy == null) {
      log.error(AppLogMessage.message("#Transaction - sender protocol not found: {}", isoMessageProperties.outgoingProtocol()));
      isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
      return Mono.empty();
    }

    log.debug(AppLogMessage.message("#Transaction - DTO").additionalData(ctx.getRequestContext()));
    return senderProtocolStrategy.send(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), ctx.getRequestContext())
        .map(responseContext -> ParticipantContext.response(ctx, responseContext));
  }

  private void initiateSpan(IsoMessage isoMessage, Map<String, String> mdc) {
    tracerHelper.createTraceContext(isoMessage);
    mdc.putAll(MDC.getCopyOfContextMap());
    MDC.setContextMap(mdc);
  }

  private Mono<ParticipantContext> selectTransactionHandler(ParticipantContext participantContext,
      ChannelHandlerContext context, IsoMessage isoMessage, RequestContext requestContext,
      Observation observation) {

    Optional<AbstractTransactionHandler> maybeHandler = findTransactionHandler(requestContext);

    if (!maybeHandler.isPresent()) {
      log.warn(AppLogMessage.message("#Transaction - skipping unknown transaction handler {}", requestContext.getSelector()));
      isoFieldHelper.sendResponse(context, isoMessage, IsoResponseCode.UNABLE_TO_ROUTE_TRANSACTION.getCode());
      return Mono.empty();
    }
    return Mono.fromSupplier(() -> ParticipantContext.create(
        participantContext, context, isoMessage, maybeHandler.get(),requestContext, observation
      )
    );
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
