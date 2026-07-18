package com.nantaaditya.sotres.helper;

import com.github.f4b6a3.tsid.TsidCreator;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.EncodingUtils;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Log4j2
@Component
@RequiredArgsConstructor
public class TracerHelper {

  private final BaggageManager baggageManager;
  @Getter
  private final Tracer tracer;

  public static final String TRACE_ID = "traceId";
  public static final String SPAN_ID = "spanId";

  public TraceContext getTracerContext() {
    return Optional.ofNullable(tracer)
      .map(Tracer::currentSpan)
      .map(Span::context)
      .orElseGet(() -> {
        String parentId = EncodingUtils.fromLong(TsidCreator.getTsid256().toLong());
        return tracer.traceContextBuilder()
          .parentId(parentId)
          .traceId(parentId)
          .spanId(parentId)
          .sampled(true)
          .build();
      });
  }

  public Map<String, String> getBaggages() {
    return baggageManager.getAllBaggage();
  }

  public String getBaggage(HeaderConstant header) {
    return getBaggages().getOrDefault(header.getHeader(), null);
  }

  public void setBaggage(String key, String value) {
    try {
      Baggage baggage = Optional.ofNullable(baggageManager.getBaggage(key))
          .orElseGet(() -> baggageManager.createBaggage(key));
      baggage.makeCurrent(value);
      MDC.put(key, value);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Baggage - failed to set baggage {} with value {}", key, value).error(e));
    }
  }

  public Span startSpan(Tracer tracer, String spanName) {
    return tracer.spanBuilder()
      .setNoParent()
      .name(spanName)
      .start();
  }

  public <T> Mono<T> withSpanScopeAndMDC(Mono<T> mono, Span span, Map<String, String> mdc) {
    return Mono.deferContextual(ctxView ->
        mono.doOnEach(signal -> {
          MDC.setContextMap(mdc);
          MDC.put("traceId", span.context().traceId());
          MDC.put("spanId", span.context().spanId());
        })
    );
  }

  public void createTraceContext(IsoMessage isoMessage) {
    setBaggage(HeaderConstant.REQUEST_ID.getHeader(), IsoFieldHelper.getField(isoMessage, 37));
  }

  public void initiateSpan(IsoMessage isoMessage, Map<String, String> mdc) {
    createTraceContext(isoMessage);
    mdc.putAll(MDC.getCopyOfContextMap());
    MDC.setContextMap(mdc);
  }

  public Context composeTransactionContext(Context context, RequestContext requestContext) {
    Span currentSpan = context.get(Span.class);
    Span nextSpan = tracer.nextSpan(currentSpan);
    return context.put(HeaderConstant.REQUEST_ID.getHeader(), requestContext.getRrn())
        .put(TRACE_ID, nextSpan.context().traceId())
        .put(SPAN_ID, nextSpan.context().spanId());
  }
}