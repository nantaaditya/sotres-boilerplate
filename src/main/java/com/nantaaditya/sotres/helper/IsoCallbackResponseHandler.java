package com.nantaaditya.sotres.helper;

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
  private final IsoCallbackRegistry isoCallbackRegistry;
  private final List<String> registryCallbackSelectors;

  private static final AttributeKey<IsoCategory> CALLBACK_KEY = AttributeKey.valueOf(CALLBACK_ATTRIBUTE);

  public IsoCallbackResponseHandler(IsoCallbackRegistry isoCallbackRegistry,
      SystemPropertiesService systemPropertiesService,
      TracerHelper tracerHelper) {

    this.isoCallbackRegistry = isoCallbackRegistry;
    this.tracerHelper = tracerHelper;
    this.registryCallbackSelectors = PropertiesGroup.getList(
        systemPropertiesService,
        PropertiesGroup.REGISTRY_CALLBACK_SELECTOR
    );

  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, IsoMessage msg) {
    String correlationId = IsoFieldHelper.getCorrelationId(msg);
    String selector = IsoFieldHelper.createSelector(msg);
    IsoCategory isoCategory = null;

    Span span = tracerHelper.getTracer().nextSpan().name(CALLBACK_NAME).start();
    try (Tracer.SpanInScope ws = tracerHelper.getTracer().withSpan(span)) {
      tracerHelper.createTraceContext(msg);

      // match is response callback
      if (this.registryCallbackSelectors != null && this.registryCallbackSelectors.contains(selector)) {
        RegistryContext registryContext = isoCallbackRegistry.onResponse(msg);
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
      ctx.channel().attr(CALLBACK_KEY).set(isoCategory);
      // pass everything to the TransactionProcessorParticipant
      ctx.fireChannelRead(msg);
    } finally {
      span.end();
    }
  }
}