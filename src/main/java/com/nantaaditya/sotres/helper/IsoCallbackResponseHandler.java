package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.IsoCallbackConstant;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.dto.RegistryContext;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.util.List;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class IsoCallbackResponseHandler
    extends SimpleChannelInboundHandler<IsoMessage>
    implements IsoCallbackConstant {

  private final TracerHelper tracerHelper;
  private final IsoMessageRegistry isoMessageRegistry;
  private final List<String> responseCallbackSelectors;

  public IsoCallbackResponseHandler(IsoMessageRegistry isoMessageRegistry,
      SystemPropertiesService systemPropertiesService,
      TracerHelper tracerHelper) {

    this.isoMessageRegistry = isoMessageRegistry;
    this.tracerHelper = tracerHelper;
    this.responseCallbackSelectors = PropertiesGroup.getList(
        systemPropertiesService,
        PropertiesGroup.RESPONSE_CALLBACK_SELECTOR
    );

  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, IsoMessage msg) {
    String correlationId = IsoFieldHelper.getCorrelationId(msg);
    String selector = IsoFieldHelper.createSelector(msg);
    IsoCategory isoCategory = null;

    Span span = tracerHelper.getTracer().nextSpan().name(CALLBACK_NAME).start();
    try (Tracer.SpanInScope ws = tracerHelper.getTracer().withSpan(span)) {
      tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), IsoFieldHelper.getField(msg, 37));

      // match is response callback
      if (this.responseCallbackSelectors.contains(selector)) {
        RegistryContext registryContext = isoMessageRegistry.onResponse(msg);
        if (registryContext.lateResponse()) {
          isoCategory = IsoCategory.LATE_RESPONSE;
        } else if (registryContext.unknownMatchResponse()) {
          isoCategory = IsoCategory.ORPHAN;
        } else {
          isoCategory = IsoCategory.SUCCESS;
        }
      } else {
        isoCategory = IsoCategory.EXTERNAL_REQUEST;
      }

      // attach the classification to the context so the Participant can read it
      ctx.channel().attr(AttributeKey.valueOf(CALLBACK_ATTRIBUTE)).set(isoCategory);
      // pass everything to the TransactionProcessorParticipant
      ctx.fireChannelRead(msg);
    } finally {
      span.end();
    }
  }
}