package com.nantaaditya.sotres.helper;

import com.github.f4b6a3.tsid.TsidCreator;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.internal.EncodingUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

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

  /**
   * Reads back a value set by {@link #setBaggage}. Deliberately reads MDC, not
   * {@code baggageManager.getAllBaggage()}: {@code BaggageManager.getBaggage(name)} builds a
   * fresh, unregistered baggage handle on every call, bound to whatever span happens to be
   * current at that instant — a value written through one such handle is not reliably visible to
   * a different handle created later for the same name, even within the same thread and request
   * (confirmed empirically; see docs/POST_MIGRATION_REMEDIATION_PLAN.md Phase 7). MDC has no such
   * per-call rebinding and is what every caller of {@link #setBaggage} already gets populated for
   * free, so it is the one channel proven to round-trip correctly for this codebase's
   * one-thread-per-request-or-message model.
   */
  public String getBaggage(HeaderConstant header) {
    return MDC.get(header.getHeader());
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

  public void createTraceContext(IsoMessage isoMessage) {
    setBaggage(HeaderConstant.REQUEST_ID.getHeader(), IsoFieldHelper.getField(isoMessage, 37));
  }

  public void initiateSpan(IsoMessage isoMessage, Map<String, String> mdc) {
    createTraceContext(isoMessage);
    mdc.putAll(MDC.getCopyOfContextMap());
    MDC.setContextMap(mdc);
  }

  /**
   * Starts the {@code iso.message} observation + span, and snapshots the calling thread's MDC
   * <em>before</em> {@link #initiateSpan} mutates it. Callers on a thread that must never leak
   * context to the next message it handles (e.g. the Netty event loop) must call
   * {@link #restoreCallerMdc(IsoObservationContext)} with the returned context once the handoff
   * that follows (success or failure) is resolved.
   */
  public IsoObservationContext startIsoObservation(IsoMessage isoMessage, ObservationRegistry registry) {
    Map<String, String> callerMdc = MDC.getCopyOfContextMap();

    Observation observation = Observation.start(ObservationConstant.ISO_MESSAGE.getName(), registry);

    Span span = startSpan(tracer, ObservationConstant.ISO_MESSAGE.getName());
    Map<String, String> mdc = new HashMap<>();
    initiateSpan(isoMessage, mdc);
    return new IsoObservationContext(observation, span, mdc, callerMdc);
  }

  /** Restores the calling thread's MDC to what {@link #startIsoObservation} captured. */
  public void restoreCallerMdc(IsoObservationContext context) {
    if (context.callerMdc() == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(context.callerMdc());
    }
  }
}