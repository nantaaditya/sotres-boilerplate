package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.constant.ResponseCode;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.dto.ResponseContext.Response;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "REST")
public class RestProtocolStrategy implements SenderProtocolStrategy {

  private static final String REQUEST_ID = "reqId";
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";

  private final SystemPropertiesService systemPropertiesService;
  private final TransactionClient transactionClient;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;

  public RestProtocolStrategy(List<AbstractTransactionHandler<RequestContext>> transactionHandlers,
      SystemPropertiesService systemPropertiesService,
      TransactionClient transactionClient, IsoFieldHelper isoFieldHelper,
      TracerHelper tracerHelper, Tracer tracer) {
    this.systemPropertiesService = systemPropertiesService;
    this.transactionClient = transactionClient;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
    this.tracer = tracer;
  }

  @Override
  public OutgoingProtocol getProtocol() {
    return OutgoingProtocol.REST;
  }

  @Override
  public Mono<ResponseContext> send(ChannelHandlerContext context, IsoMessage incomingMessage,
      RequestContext requestContext) {
    return transactionClient.send(requestContext) // send message
      .onErrorMap(throwable -> new TransactionException(throwable, requestContext))
      .transformDeferred(result -> Mono.deferContextual(contextView -> {
        Span currentSpan = contextView.get(Span.class);
        Span nextSpan = tracer.nextSpan(currentSpan);
        return result.doOnEach(signal -> {
          tracerHelper.setBaggage(REQUEST_ID, requestContext.getRrn());
          tracerHelper.setBaggage(TRACE_ID, nextSpan.context().traceId());
          tracerHelper.setBaggage(SPAN_ID, nextSpan.context().spanId());
        });
      })).contextWrite(ctx -> {
        Span currentSpan = ctx.get(Span.class);
        Span nextSpan = tracer.nextSpan(currentSpan);
        return ctx.put(REQUEST_ID, requestContext.getRrn())
            .put(TRACE_ID, nextSpan.context().traceId())
            .put(SPAN_ID, nextSpan.context().spanId());
      });
  }

  @Override
  public void handleResponse(ChannelHandlerContext context, IsoMessage request,
      ResponseContext responseContext, Observation observation) {
    log.debug("#Transaction - response from external: {}", responseContext.toString());

    try {
      String responseCode = getResponseCode(responseContext);
      if (responseContext == null) {
        log.error("#Transaction - no response from host");
        isoFieldHelper.sendResponse(context, request, responseCode);
      } else {
        log.info("#Transaction - response code from host: {}", responseCode);
        isoFieldHelper.sendResponse(context, request, response -> {
          setApprovalCode(response, responseContext.getTransaction().getApprovalCode());
          response.setField(39, IsoType.ALPHA.value(mappingResponseCode(responseCode), 2));
        });
      }

      ObservationHelper.observeResponse(observation, responseCode, null);
    } catch (Exception e) {
      String responseCode = ResponseCode.SYSTEM_MALFUNCTION.getCode();
      ErrorHelper.loggingError("#Transaction - failed write and flush transaction. with Message : {} , and root cause : {}", e);
      isoFieldHelper.sendResponse(context, request, responseCode);
      ObservationHelper.observeResponse(observation, responseCode, e);
    } finally {
      observation.stop();
      MDC.clear();
    }
  }

  @Override
  public void handleError(ChannelHandlerContext context, IsoMessage request, Throwable throwable) {
    ErrorHelper.loggingError("#Transaction - got exception {}, with detail {}", throwable);

    if (throwable instanceof TransactionException e) {
      if (e.getOriginalError() instanceof ReadTimeoutException || e.getOriginalError() instanceof TimeoutException) {
        log.error("#Transaction - timeout occurred for RRN {}, skipping response, with error {} and detail {}",
            request.getObjectValue(37), throwable.getMessage(), ErrorHelper.getRootCause(throwable));
      } else {
        isoFieldHelper.sendResponse(context, request, ResponseCode.SYSTEM_MALFUNCTION.getCode());
      }
    } else {
      log.error("#Transaction - skipping unknown exception {}, detail {}",
          throwable.getMessage(), ErrorHelper.getRootCause(throwable));
    }
  }

  private static String getResponseCode(ResponseContext responseContext) {
    return Optional.ofNullable(responseContext)
        .map(ResponseContext::getResponse)
        .map(Response::getCode)
        .orElseGet(() -> ResponseCode.LINK_DOWN.getCode());
  }

  private String mappingResponseCode(String responseCode) {
    Map<String, String> responseCodeMapping = PropertiesGroup.getMap(systemPropertiesService, PropertiesGroup.RESPONSE_MAPPING);
    if (responseCodeMapping == null || responseCodeMapping.isEmpty()) {
      return ResponseCode.SYSTEM_MALFUNCTION.getCode();
    }
    return responseCodeMapping.getOrDefault(responseCode, ResponseCode.SYSTEM_MALFUNCTION.getCode());
  }

  protected void setApprovalCode(IsoMessage isoMessage, String approvalCode) {
    Optional.ofNullable(approvalCode)
      .ifPresent(code -> isoMessage.setField(38, IsoType.ALPHA.value(code.substring(code.length() - 6), 6)));
  }

}
