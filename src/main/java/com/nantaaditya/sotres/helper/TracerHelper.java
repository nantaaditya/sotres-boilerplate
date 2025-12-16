package com.nantaaditya.sotres.helper;

import com.github.f4b6a3.tsid.TsidCreator;
import com.solab.iso8583.IsoMessage;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.EncodingUtils;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TracerHelper {

  private final BaggageManager baggageManager;
  private final Tracer tracer;

  public TraceContext getTracer() {
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

  public void setBaggage(String key, String value) {
    try {
      Baggage baggage = Optional.ofNullable(baggageManager.getBaggage(key))
          .orElseGet(() -> baggageManager.createBaggage(key));
      baggage.makeCurrent(value);
      MDC.put(key, value);
    } catch (Exception e) {
      log.error("#Baggage - failed to set baggage {} with value {}, error {} cause {}", key, value,
          e.getMessage(), ErrorHelper.getRootCause(e));
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
    setBaggage("reqId", String.valueOf(isoMessage.getField(37)));
  }
}