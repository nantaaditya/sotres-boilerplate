package com.nantaaditya.sotres.strategy.transaction;

import com.nantaaditya.sotres.model.constant.FeatureConstant;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.solab.iso8583.IsoMessage;
import io.netty.channel.ChannelHandlerContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTransactionHandler<S extends RequestContext> {

  public abstract Set<String> getSelectors();

  protected abstract Mono<S> validate(ChannelHandlerContext context, IsoMessage isoMessage,
      S requestContext);

  protected abstract Mono<S> process(ChannelHandlerContext context, IsoMessage isoMessage,
      S requestContext);

  public Mono<S> execute(ChannelHandlerContext context, IsoMessage isoMessage,
      S requestContext) {
    return validate(context, isoMessage, requestContext)
        .flatMap(request -> process(context, isoMessage, request));
  }
}
