package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "REST")
public class RestProtocolStrategy implements SenderProtocolStrategy {

  private final SystemPropertiesService systemPropertiesService;
  private final TransactionClient transactionClient;
  private final IsoFieldHelper isoFieldHelper;
  private final TracerHelper tracerHelper;

  public RestProtocolStrategy(
      SystemPropertiesService systemPropertiesService,
      TransactionClient transactionClient, IsoFieldHelper isoFieldHelper,
      TracerHelper tracerHelper) {
    this.systemPropertiesService = systemPropertiesService;
    this.transactionClient = transactionClient;
    this.isoFieldHelper = isoFieldHelper;
    this.tracerHelper = tracerHelper;
  }

  @Override
  public OutgoingProtocol getProtocol() {
    return OutgoingProtocol.REST;
  }

  @Override
  public ResponseContext send(ChannelHandlerContext context, IsoMessage incomingMessage, RequestContext requestContext) {
    try {
      ResponseContext responseContext = transactionClient.send(requestContext);
      tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), requestContext.getRrn());
      return responseContext;
    } catch (Throwable throwable) {
      throw translateError(requestContext, throwable);
    }
  }

  @Override
  public void handleResponse(ParticipantContext ctx) {
    ResponseContext response = ctx.getResponseContext();
    log.debug(AppLogMessage.message("#Transaction - response from external").additionalData(response));

    try {
      // override response before sending ISO message when necessary
      ctx.getTransactionHandler().populateResponse(ctx);

      if (response == null) {
        log.error(AppLogMessage.message("#Transaction - no response from host"));
        isoFieldHelper.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), null);
      } else {
        String responseCode = response.getResponseCode();
        log.info(AppLogMessage.message("#Transaction - response code from host: {}", responseCode));
        isoFieldHelper.sendResponse(ctx.getChannelHandlerContext(), ctx.getIsoMessage(), msg -> {
          IsoFieldHelper.setApprovalCode(msg, response.getApprovalCode());
          msg.setField(39, IsoType.ALPHA.value(mappingResponseCode(responseCode), 2));
        });
      }

    } catch (Exception e) {
      log.error(AppLogMessage.message("#Transaction - failed write and flush transaction").error(e));
      String responseCode = IsoResponseCode.SYSTEM_MALFUNCTION.getCode();
      isoFieldHelper.sendResponseWithObservation(ctx, responseCode, e);
    } finally {
      ctx.getObservation().stop();
      MDC.clear();
    }
  }

  @Override
  public void handleError(ParticipantContext ctx, Throwable throwable) {
    log.error(AppLogMessage.message("#Transaction - got exception").error(throwable));

    if (throwable instanceof TransactionException e) {
      // when timeout does nothing, will be reverse from switcher
      if (e.getOriginalError() instanceof ReadTimeoutException || e.getOriginalError() instanceof TimeoutException) {
        log.error(AppLogMessage.message("#Transaction - timeout occurred for RRN {}, no response",
            IsoFieldHelper.getField(ctx.getIsoMessage(),37)).error(throwable));
      } else {
        isoFieldHelper.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), e);
      }
    } else {
      // when unknown error does nothing, will be reverse from switcher
      log.error(AppLogMessage.message("#Transaction - skipping unknown exception").error(throwable));
    }
  }

  private String mappingResponseCode(String responseCode) {
    Map<String, String> responseCodeMapping = ConfigGroup.getMap(systemPropertiesService, ConfigGroup.RESPONSE_MAPPING);
    if (responseCodeMapping == null || responseCodeMapping.isEmpty()) {
      return IsoResponseCode.SYSTEM_MALFUNCTION.getCode();
    }
    return responseCodeMapping.getOrDefault(responseCode, IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
  }

  private TransactionException translateError(RequestContext requestContext, Throwable throwable) {
    if (throwable instanceof TransactionException te) {
      return te;
    }
    return new TransactionException(throwable, requestContext);
  }
}
