package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoResponseSender;
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
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Forwards an ISO8583 transaction to the downstream REST backend via {@link TransactionClient}
 * (blocking {@code RestClient} on a virtual thread) and maps the JSON reply back onto the 0210.
 * Selected when {@code iso8583.configuration.outgoing-protocol=REST}.
 *
 * <p>Error contract ({@link #handleError}):
 * <ul>
 *   <li><b>timeout</b> (connect/read) — <b>no ISO reply is sent.</b> The downstream may have
 *       processed the request, so the acquirer/switch must drive the reversal rather than the
 *       gateway guessing a decline.</li>
 *   <li><b>other {@link TransactionException}</b> — reply with DE39 = {@code 96}
 *       ({@link IsoResponseCode#SYSTEM_MALFUNCTION}).</li>
 *   <li><b>anything else</b> — logged, no ISO reply.</li>
 * </ul>
 */
@Log4j2
@Component
@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "REST")
public class RestProtocolStrategy implements SenderProtocolStrategy {

  private final SystemPropertiesService systemPropertiesService;
  private final TransactionClient transactionClient;
  private final IsoResponseSender isoResponseSender;
  private final TracerHelper tracerHelper;

  public RestProtocolStrategy(
      SystemPropertiesService systemPropertiesService,
      TransactionClient transactionClient, IsoResponseSender isoResponseSender,
      TracerHelper tracerHelper) {
    this.systemPropertiesService = systemPropertiesService;
    this.transactionClient = transactionClient;
    this.isoResponseSender = isoResponseSender;
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
    } catch (Exception exception) {
      throw translateError(requestContext, exception);
    }
  }

  @Override
  public void handleResponse(ParticipantContext ctx) {
    ResponseContext response = ctx.getResponseContext();
    log.debug(AppLogMessage.message("#Transaction - response from external").additionalData(response));

    try {
      if (response == null) {
        log.error(AppLogMessage.message("#Transaction - no response from host"));
        isoResponseSender.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), null);
      } else {
        // override response before sending ISO message when necessary
        ctx.getTransactionHandler().populateResponse(ctx);
        String responseCode = response.getResponseCode();
        String mappedResponseCode = mappingResponseCode(responseCode);
        log.info(AppLogMessage.message("#Transaction - response code from host: {} to internal response code: {}", responseCode, mappedResponseCode));

        isoResponseSender.sendResponseWithObservation(ctx, mappedResponseCode, null, msg -> {
          IsoFieldHelper.setApprovalCode(msg, response.getApprovalCode());
          msg.setField(39, IsoType.ALPHA.value(mappedResponseCode, 2));
        });
      }

    } catch (Exception e) {
      log.error(AppLogMessage.message("#Transaction - failed write and flush transaction").error(e));
      String responseCode = IsoResponseCode.SYSTEM_MALFUNCTION.getCode();
      isoResponseSender.sendResponseWithObservation(ctx, responseCode, e);
    }
  }

  @Override
  public void handleError(ParticipantContext ctx, Throwable throwable) {
    log.error(AppLogMessage.message("#Transaction - got exception").error(throwable));

    if (!(throwable instanceof TransactionException e)) {
      // unknown error: no ISO response, the acquirer/switch drives the reversal
      log.error(AppLogMessage.message("#Transaction - skipping unknown exception").error(throwable));
      return;
    }

    if (isTimeout(e.getOriginalError())) {
      // no ISO response on timeout: the downstream may have processed the request, so the acquirer must reverse rather than us guessing a decline
      log.error(AppLogMessage.message(
          "#Transaction - timeout for RRN {}, no ISO response sent (acquirer reverses)",
          IsoFieldHelper.getField(ctx.getIsoMessage(), 37)).error(throwable));
      return;
    }

    isoResponseSender.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), e);
  }

  /**
   * Walks the cause chain: a {@code RestClient} timeout surfaces as {@code ResourceAccessException}
   * wrapping {@link HttpTimeoutException} / {@link SocketTimeoutException}, not the bare Netty
   * {@link ReadTimeoutException} the reactive stack used to raise.
   */
  private static boolean isTimeout(Throwable throwable) {
    for (Throwable t = throwable; t != null && t != t.getCause(); t = t.getCause()) {
      if (t instanceof ReadTimeoutException
          || t instanceof TimeoutException
          || t instanceof HttpTimeoutException
          || t instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
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
