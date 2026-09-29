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

  public void onUpdate(
      ChannelHandlerContext ctx, IsoMessage isoMessage,
      AbstractTransactionHandler handler, RequestContext request, Observation observation) {

    this.channelHandlerContext = ctx;
    this.isoMessage = isoMessage;
    this.transactionHandler = handler;
    this.requestContext = request;
    this.observation = observation;
  }

  public void onResponse(ResponseContext response) {
    this.responseContext = response;
  }
}
