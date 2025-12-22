package com.nantaaditya.sotres.model.dto;

import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;

@Getter
public class ParticipantContext {

  private IsoMessage isoMessage;
  private RequestContext requestContext;
  private ResponseContext responseContext;
  private AbstractTransactionHandler transactionHandler;
  private ChannelHandlerContext channelHandlerContext;
  private Observation observation;

  public static ParticipantContext create(ParticipantContext participantContext,
      ChannelHandlerContext ctx, IsoMessage isoMessage,
      AbstractTransactionHandler handler, RequestContext request, Observation observation) {

    participantContext.channelHandlerContext = ctx;
    participantContext.isoMessage = isoMessage;
    participantContext.requestContext = request;
    participantContext.observation = observation;

    return participantContext;
  }

  public static ParticipantContext response(ParticipantContext ctx, ResponseContext response) {
    ctx.responseContext = response;
    return ctx;
  }
}
