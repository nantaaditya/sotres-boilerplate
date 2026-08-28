package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.solab.iso8583.IsoMessage;
import io.netty.channel.ChannelHandlerContext;

public interface SenderProtocolStrategy {
    OutgoingProtocol getProtocol();
    ResponseContext send(ChannelHandlerContext ctx, IsoMessage incomingMessage, RequestContext requestContext);
    void handleResponse(ParticipantContext ctx);
    void handleError(ParticipantContext ctx, Throwable throwable);
}
