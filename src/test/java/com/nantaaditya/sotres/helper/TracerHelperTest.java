package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@DisplayName("TracerHelper")
@ExtendWith(MockitoExtension.class)
class TracerHelperTest {

  @Mock
  private BaggageManager baggageManager;
  @Mock
  private Tracer tracer;
  @Mock
  private Span.Builder spanBuilder;
  @Mock
  private Span span;
  @Mock
  private ObservationRegistry observationRegistry;
  @Mock
  private IsoMessage isoMessage;

  private TracerHelper tracerHelper;

  @BeforeEach
  void setUp() {
    tracerHelper = new TracerHelper(baggageManager, tracer);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  private void stubSpanCreation() {
    lenient().when(tracer.spanBuilder()).thenReturn(spanBuilder);
    lenient().when(spanBuilder.setNoParent()).thenReturn(spanBuilder);
    lenient().when(spanBuilder.name(any())).thenReturn(spanBuilder);
    lenient().when(spanBuilder.start()).thenReturn(span);
  }

  @Nested
  @DisplayName("startIsoObservation(IsoMessage, ObservationRegistry)")
  class StartIsoObservation {

    @Test
    @DisplayName("snapshots the calling thread's MDC before initiateSpan mutates it")
    void startIsoObservation_snapshotsCallerMdcBeforeMutation() {
      stubSpanCreation();
      when(observationRegistry.isNoop()).thenReturn(true);
      when(isoMessage.getField(37))
          .thenReturn(new IsoValue<>(IsoType.ALPHA, "000000000001", 12));
      MDC.put("existing", "value");

      IsoObservationContext ctx = tracerHelper.startIsoObservation(isoMessage, observationRegistry);

      assertThat(ctx.callerMdc()).containsEntry("existing", "value");
    }

    @Test
    @DisplayName("captures an empty callerMdc when the calling thread's MDC was empty")
    void startIsoObservation_emptyCallerMdc_capturesEmpty() {
      stubSpanCreation();
      when(observationRegistry.isNoop()).thenReturn(true);
      when(isoMessage.getField(37))
          .thenReturn(new IsoValue<>(IsoType.ALPHA, "000000000001", 12));

      IsoObservationContext ctx = tracerHelper.startIsoObservation(isoMessage, observationRegistry);

      assertThat(ctx.callerMdc()).isNullOrEmpty();
    }
  }

  @Nested
  @DisplayName("restoreCallerMdc(IsoObservationContext)")
  class RestoreCallerMdc {

    @Test
    @DisplayName("restores a previously captured non-null snapshot")
    void restoreCallerMdc_nonNullSnapshot_restoresIt() {
      MDC.put("dirty", "leaked-value");
      IsoObservationContext ctx = new IsoObservationContext(null, null, null, Map.of("original", "state"));

      tracerHelper.restoreCallerMdc(ctx);

      assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("original", "state"));
    }

    @Test
    @DisplayName("clears MDC when the original snapshot was null (empty)")
    void restoreCallerMdc_nullSnapshot_clearsMdc() {
      MDC.put("dirty", "leaked-value");
      IsoObservationContext ctx = new IsoObservationContext(null, null, null, null);

      tracerHelper.restoreCallerMdc(ctx);

      assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
  }
}
