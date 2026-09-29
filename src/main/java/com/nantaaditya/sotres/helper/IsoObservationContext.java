package com.nantaaditya.sotres.helper;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import java.util.Map;

/**
 * Bundle produced by {@link TracerHelper#startIsoObservation(com.solab.iso8583.IsoMessage,
 * io.micrometer.observation.ObservationRegistry)}: the started {@code observation}/{@code span}
 * plus the merged context map ({@code mdc}) a worker thread should adopt, and a snapshot of the
 * calling thread's MDC ({@code callerMdc}) taken before it was mutated — pass this back to
 * {@link TracerHelper#restoreCallerMdc(IsoObservationContext)} once the calling thread is done
 * (handoff succeeded or failed) so it never carries another transaction's context forward.
 */
public record IsoObservationContext(
    Observation observation,
    Span span,
    Map<String, String> mdc,
    Map<String, String> callerMdc
) {

}
