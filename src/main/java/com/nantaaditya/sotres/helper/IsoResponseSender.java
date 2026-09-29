package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Log4j2
@Component
@RequiredArgsConstructor
public class IsoResponseSender {

  public static final String ISO_REQUEST_EVENT = "iso_request";
  public static final String ISO_RESPONSE_EVENT = "iso_response";

  private final MessageFactoryHelper messageFactoryHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final ObjectMapper objectMapper;

  public void sendResponse(ChannelHandlerContext context, IsoMessage request, String responseCode) {
    IsoMessage response = createResponse(request);
    setResponseCode(response, responseCode);
    isoMessageLoggerHelper.logIsoMessage(response, IsoMessageLoggerHelper.OUTGOING_ISO);
    context.writeAndFlush(response);
  }

  public void sendResponseWithObservation(ParticipantContext context, String responseCode, Throwable throwable,
      Consumer<IsoMessage> responseConsumer) {
    RequestContext requestContext = context.getRequestContext();
    if (requestContext != null && requestContext.isCallbackResponse()) {
      // this inbound message was already a reply to something we sent via EnhancedIsoClient
      // (CALLBACK mode) — there is no one on the switch side waiting for a reply-to-a-reply.
      log.debug(AppLogMessage.message("#Transaction - callback-mode response, skipping ISO reply"));
      ObservationHelper.observeResponse(context.getObservation(), responseCode, throwable);
      return;
    }

    IsoMessage request = context.getIsoMessage();
    IsoMessage response = createResponse(request);

    responseConsumer.accept(response);

    Observation observation = context.getObservation();
    publishIsoEvent(observation, response, ISO_RESPONSE_EVENT, IsoMessageLoggerHelper.OUTGOING_ISO);

    isoMessageLoggerHelper.logIsoMessage(response, IsoMessageLoggerHelper.OUTGOING_ISO);

    ChannelHandlerContext channelHandlerContext = context.getChannelHandlerContext();
    channelHandlerContext.writeAndFlush(response);

    ObservationHelper.observeResponse(context.getObservation(), responseCode, throwable);
  }

  public void sendResponseWithObservation(ParticipantContext context, String responseCode, Throwable throwable) {
    sendResponseWithObservation(context, responseCode, throwable, isoMessage -> {
      setResponseCode(isoMessage, responseCode);
    });
  }

  public IsoMessage createResponse(IsoMessage request) {
    return messageFactoryHelper.getDefaultMessageFactory().createResponse(request);
  }

  public void publishIsoEvent(Observation observation, IsoMessage isoMessage, String event, String direction) {
    try {
      JsonLogIsoMessage jsonLogIsoMessage = isoMessageLoggerHelper.toLogMessage(isoMessage, direction);
      String isoRequest = objectMapper.writeValueAsString(jsonLogIsoMessage);
      ObservationHelper.publishEvent(observation, event, isoRequest);
    } catch (JacksonException e) {
      log.error(AppLogMessage.message("#IsoField - failed to log iso message").error(e));
    }
  }

  private void setResponseCode(IsoMessage isoMessage, String responseCode) {
    isoMessage.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode, 2));
  }
}
