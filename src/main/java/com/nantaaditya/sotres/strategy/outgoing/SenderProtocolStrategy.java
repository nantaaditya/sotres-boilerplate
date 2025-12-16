package com.nantaaditya.sotres.strategy.outgoing;

import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import reactor.core.publisher.Mono;

public interface SenderProtocolStrategy {
    OutgoingProtocol getProtocol();
    Mono<ResponseContext> send(ChannelHandlerContext ctx, IsoMessage incomingMessage, RequestContext requestContext);
    void handleResponse(ChannelHandlerContext ctx, IsoMessage incomingMessage, ResponseContext responseContext, Observation observation);
    void handleError(ChannelHandlerContext ctx, IsoMessage incomingMessage, Throwable throwable);
}
