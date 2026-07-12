package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "REST")
public class RestProtocolStrategy implements SenderProtocolStrategy {

  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";

  private final SystemPropertiesService systemPropertiesService;
  private final TransactionClient transactionClient;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;
  private final Tracer tracer;

  public RestProtocolStrategy(
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
      .onErrorMap(throwable -> translateError(requestContext, throwable))
      .transformDeferred(result -> Mono.deferContextual(contextView -> {
        Span currentSpan = contextView.get(Span.class);
        Span nextSpan = tracer.nextSpan(currentSpan);
        return result.doOnEach(signal -> {
          tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), requestContext.getRrn());
          tracerHelper.setBaggage(TRACE_ID, nextSpan.context().traceId());
          tracerHelper.setBaggage(SPAN_ID, nextSpan.context().spanId());
        });
      })).contextWrite(ctx -> {
        Span currentSpan = ctx.get(Span.class);
        Span nextSpan = tracer.nextSpan(currentSpan);
        return ctx.put(HeaderConstant.REQUEST_ID.getHeader(), requestContext.getRrn())
            .put(TRACE_ID, nextSpan.context().traceId())
            .put(SPAN_ID, nextSpan.context().spanId());
      });
  }

  @Override
  public void handleResponse(ParticipantContext ctx) {
    ResponseContext response = ctx.getResponseContext();
    log.debug(AppLogMessage.message("#Transaction - response from external").additionalData(response));

    try {
      // override response before sending ISO message when necessary
      ctx.getTransactionHandler().populateResponse(ctx);
      String responseCode = getResponseCode(response);

      if (response == null) {
        log.error(AppLogMessage.message("#Transaction - no response from host"));
        isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), responseCode);
      } else {
        log.info(AppLogMessage.message("#Transaction - response code from host: {}", responseCode));
        isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), msg -> {
          setApprovalCode(msg, response.getApprovalCode());
          msg.setField(39, IsoType.ALPHA.value(mappingResponseCode(responseCode), 2));
        });
      }

      ObservationHelper.observeResponse(ctx.getObservation(), responseCode, null);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Transaction - failed write and flush transaction").error(e));
      String responseCode = IsoResponseCode.SYSTEM_MALFUNCTION.getCode();
      isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), responseCode);
      ObservationHelper.observeResponse(ctx.getObservation(), responseCode, e);
    } finally {
      ctx.getObservation().stop();
      MDC.clear();
    }
  }

  @Override
  public void handleError(ParticipantContext ctx, Throwable throwable) {
    log.error(AppLogMessage.message("#Transaction - got exception").error(throwable));

    if (throwable instanceof TransactionException e) {
      // when timeout do nothing, will be reverse from switcher
      if (e.getOriginalError() instanceof ReadTimeoutException || e.getOriginalError() instanceof TimeoutException) {
        log.error(AppLogMessage.message("#Transaction - timeout occurred for RRN {}, no response",
            IsoFieldHelper.getField(ctx.getIsoMessage(),37)).error(throwable));
      } else {

        isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
      }
    } else {
      // when unknown error do nothing, will be reverse from switcher
      log.error(AppLogMessage.message("#Transaction - skipping unknown exception").error(throwable));
    }
  }

  private String getResponseCode(ResponseContext response) {
    return Optional.ofNullable(response)
        .map(ResponseContext::getResponseCode)
        .orElseGet(() -> IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
  }

  private String mappingResponseCode(String responseCode) {
    Map<String, String> responseCodeMapping = ConfigGroup.getMap(systemPropertiesService, ConfigGroup.RESPONSE_MAPPING);
    if (responseCodeMapping == null || responseCodeMapping.isEmpty()) {
      return IsoResponseCode.SYSTEM_MALFUNCTION.getCode();
    }
    return responseCodeMapping.getOrDefault(responseCode, IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
  }

  private void setApprovalCode(IsoMessage isoMessage, String approvalCode) {
    Optional.ofNullable(approvalCode)
      .ifPresent(code -> isoMessage.setField(38, IsoType.ALPHA.value(IsoFieldHelper.substring(code,code.length() - 6), 6)));
  }

  private Exception translateError(RequestContext requestContext, Throwable throwable) {
    // handle retry PrematureCloseException
    if (throwable instanceof PrematureCloseException p) {
      return p;
    }
    return new TransactionException(throwable, requestContext);
  }
}
