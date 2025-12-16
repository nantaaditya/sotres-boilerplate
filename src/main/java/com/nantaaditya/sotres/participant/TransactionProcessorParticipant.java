package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.RequestContextHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ResponseCode;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Component
public class TransactionProcessorParticipant implements IsoMessageListener<IsoMessage> {

  private final SystemPropertiesService systemPropertiesService;
  private final SenderProtocolStrategy senderProtocolStrategy;
  private final List<AbstractTransactionHandler<RequestContext>> transactionHandlers;
  private final ObservationRegistry observationRegistry;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;
  private final Scheduler scheduler;
  private final IsoMessageProperties isoMessageProperties;

  private static final String PARTICIPANT_NAME = "api.public";

  public TransactionProcessorParticipant(SystemPropertiesService systemPropertiesService,
      List<AbstractTransactionHandler<RequestContext>> transactionHandlers,
      IsoMessageLoggerHelper isoMessageLoggerHelper, IsoFieldHelper isoFieldHelper,
      ObservationRegistry observationRegistry, TracerHelper tracerHelper, Tracer tracer,
      List<SenderProtocolStrategy> senderProtocolStrategies,
      ParticipantConfigurationProperties participantConfigurationProperties,
      IsoMessageProperties isoMessageProperties) {

    this.systemPropertiesService = systemPropertiesService;
    this.transactionHandlers = transactionHandlers;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
    this.observationRegistry = observationRegistry;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
    this.isoMessageProperties = isoMessageProperties;

    this.scheduler = participantConfigurationProperties.getPool(PARTICIPANT_NAME).createScheduler();
    this.senderProtocolStrategy = senderProtocolStrategies.stream()
        .filter(sender -> sender.getProtocol() == isoMessageProperties.outgoingProtocol())
        .findAny()
        .orElse(null);
  }

  @Override
  public boolean applies(@NotNull IsoMessage isoMessage) {
    return isoMessage.getType() != 0x800 && isoMessage.getType() != 0x810;
  }

  @Override
  public boolean onMessage(@NotNull ChannelHandlerContext ctx, @NotNull IsoMessage isoMessage) {
    // start observation
    Observation observation = Observation.start(PARTICIPANT_NAME, observationRegistry);
    Span span = tracerHelper.startSpan(tracer, PARTICIPANT_NAME);
    Map<String, String> mdc = new HashMap<>();

    try (SpanInScope spanInScope = tracer.withSpan(span)) {
      // initiate manual span
      TraceContext traceContext = span.context();
      initiateSpan(isoMessage, mdc);

      Mono.fromCallable(() -> RequestContextHelper.create(isoMessage, systemPropertiesService)) // convert to internal DTO
          .transformDeferred(contextMono -> tracerHelper.withSpanScopeAndMDC(contextMono, span, mdc))
          .filter(Objects::nonNull)
          .map(requestContext -> {
            // log iso message
            ObservationHelper.observeRequest(observation, requestContext.getRrn(), requestContext.getFeatureConstant());
            isoMessageLoggerHelper.logIsoMessage(isoMessage);
            return requestContext;
          })
          // get transaction handler by selector
          .flatMap(requestContext -> composeTransactionHandler(ctx, isoMessage, requestContext))
          // execute transaction handler
          .flatMap(tuples -> tuples.getT2()
              .execute(ctx, isoMessage, tuples.getT1()))
          .flatMap(requestContext -> {
            // process & send message using specific protocol
            log.debug("#Transaction - DTO: {}", requestContext);
            return sendMessage(ctx, isoMessage, requestContext);
          })
          .contextWrite(context -> context // set up span and context
              .put(Span.class, span)
              .put(TraceContext.class, traceContext)
          )
          .subscribeOn(scheduler)
          .subscribe(
              tuples -> handleResponse(ctx, isoMessage, tuples, observation), // handle response
              throwable -> handleError(ctx, isoMessage, throwable), // handle error
              () -> {
                span.end();
                MDC.clear();
              }
          );

      MDC.setContextMap(mdc);
      log.info("#Transaction - message with RRN {} processed", isoMessage.getField(37).toString());
      return false;
    }
  }

  private Mono<Tuple2<RequestContext, ResponseContext>> sendMessage(
      @NotNull ChannelHandlerContext context, @NotNull IsoMessage request,
      RequestContext requestContext) {

    if (senderProtocolStrategy == null) {
      log.error("#Transaction - sender protocol not found: {}", isoMessageProperties.outgoingProtocol());
      isoFieldHelper.sendResponse(context, request, ResponseCode.SYSTEM_MALFUNCTION.getCode());
      return Mono.empty();
    }

    return senderProtocolStrategy.send(context, request, requestContext)
        .map(responseContext -> Tuples.of(requestContext, responseContext));
  }

  private void handleResponse(@NotNull ChannelHandlerContext ctx,
      @NotNull IsoMessage request, Tuple2<RequestContext, ResponseContext> tuples,
      Observation observation) {
    ResponseContext responseContext = tuples.getT2();
    senderProtocolStrategy.handleResponse(ctx, request, responseContext, observation);
  }

  private void handleError(@NotNull ChannelHandlerContext ctx,
      @NotNull IsoMessage request, Throwable throwable) {
    senderProtocolStrategy.handleError(ctx, request, throwable);
  }

  private void initiateSpan(IsoMessage isoMessage, Map<String, String> mdc) {
    tracerHelper.createTraceContext(isoMessage);
    mdc.putAll(MDC.getCopyOfContextMap());
    MDC.setContextMap(mdc);
  }

  private Mono<Tuple2<RequestContext, AbstractTransactionHandler<RequestContext>>> composeTransactionHandler(
      ChannelHandlerContext context, IsoMessage isoMessage, RequestContext requestContext) {

    Optional<AbstractTransactionHandler<RequestContext>> maybeHandler = findTransactionHandler(requestContext);

    if (!maybeHandler.isPresent()) {
      log.warn("#Transaction - skipping unknown transaction handler {}", requestContext.getSelector());
      isoFieldHelper.sendResponse(context, isoMessage, ResponseCode.SYSTEM_MALFUNCTION.getCode());
      return Mono.empty();
    }
    return Mono.fromSupplier(() -> Tuples.of(requestContext, maybeHandler.get()));
  }

  @NotNull
  private Optional<AbstractTransactionHandler<RequestContext>> findTransactionHandler(RequestContext requestContext) {
    return transactionHandlers.stream()
        .filter(handler -> handler.getSelectors().contains(requestContext.getSelector()))
        .findFirst();
  }
}
