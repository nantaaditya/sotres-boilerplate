package com.nantaaditya.sotres.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("IsoCallbackResponseHandler")
@ExtendWith(MockitoExtension.class)
class IsoCallbackResponseHandlerTest {

  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private CorrelationRegistry correlationRegistry;
  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private Tracer tracer;
  @Mock
  private Span span;
  @Mock
  private Tracer.SpanInScope spanInScope;
  @Mock
  private ChannelHandlerContext ctx;
  @Mock
  private Channel channel;
  @SuppressWarnings("rawtypes")
  @Mock
  private Attribute attr;
  @Mock
  private IsoMessage msg;

  private IsoCallbackResponseHandler handler;

  // selector = "21.00-QR" when: MTI=0x0210 (528) → substring(1,3)="21", DE3[0-1]="00", PI="QR"
  private static final String MATCHING_SELECTOR = "21.00-QR";

  @BeforeEach
  void setUp() {
    when(systemPropertiesService.getProperty(
        ConfigGroup.REGISTRY_CALLBACK_SELECTOR,
        ConfigGroup.REGISTRY_CALLBACK_SELECTOR.getPropertyId()))
        .thenReturn(MATCHING_SELECTOR);

    // Tracer chain for span creation used inside channelRead0
    when(tracerHelper.getTracer()).thenReturn(tracer);
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(any())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(spanInScope);

    // Netty channel attribute chain
    when(ctx.channel()).thenReturn(channel);
    when(channel.attr(any())).thenReturn(attr);

    // fields accessed by createSelector / getCorrelationId / createTraceContext
    when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    when(msg.getType()).thenReturn(0);
    when(msg.getField(3)).thenReturn(isoValue("000000"));
    when(msg.getField(48)).thenReturn(isoValue("PI02QR"));

    handler = new IsoCallbackResponseHandler(correlationRegistry, systemPropertiesService,
        tracerHelper);
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  private void setupMatchingMessage() {
    // MTI 0x0210 (528) → getMTI → "0210" → chars[1-2]="21", DE3[0-1]="00", PI="QR" → "21.00-QR"
    when(msg.getType()).thenReturn(528);
    when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    when(msg.getField(3)).thenReturn(isoValue("000000"));
    when(msg.getField(11)).thenReturn(isoValue("123456"));
    when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    when(msg.getField(7)).thenReturn(isoValue("0615103045"));
  }

  private void setupNonMatchingMessage() {
    // MTI 0x0200 (512) → getMTI → "0200" → chars[1-2]="20" → selector="20.00-QR" NOT in list
    when(msg.getType()).thenReturn(512);
    when(msg.getField(48)).thenReturn(isoValue("PI02QR"));
    when(msg.getField(3)).thenReturn(isoValue("000000"));
    when(msg.getField(11)).thenReturn(isoValue("123456"));
    when(msg.getField(37)).thenReturn(isoValue("000000000001"));
    when(msg.getField(7)).thenReturn(isoValue("0615103045"));
  }

  @Nested
  @DisplayName("channelRead0 — selector in registryCallbackSelectors")
  class SelectorMatchedPath {

    @Test
    @DisplayName("sets the IsoCategory returned by CorrelationRegistry.complete on the channel attribute")
    @SuppressWarnings("unchecked")
    void channelRead0_selectorMatchedAndSuccess_setsSuccessCategory() {
      setupMatchingMessage();
      when(correlationRegistry.complete(msg)).thenReturn(IsoCategory.SUCCESS);

      handler.channelRead0(ctx, msg);

      verify(attr).set(IsoCategory.SUCCESS);
      verify(ctx).fireChannelRead(msg);
    }

    @Test
    @DisplayName("propagates LATE_RESPONSE from CorrelationRegistry.complete")
    @SuppressWarnings("unchecked")
    void channelRead0_selectorMatchedAndLateResponse_setsLateResponseCategory() {
      setupMatchingMessage();
      when(correlationRegistry.complete(msg)).thenReturn(IsoCategory.LATE_RESPONSE);

      handler.channelRead0(ctx, msg);

      verify(attr).set(IsoCategory.LATE_RESPONSE);
      verify(ctx).fireChannelRead(msg);
    }

    @Test
    @DisplayName("propagates ORPHAN from CorrelationRegistry.complete")
    @SuppressWarnings("unchecked")
    void channelRead0_selectorMatchedAndOrphan_setsOrphanCategory() {
      setupMatchingMessage();
      when(correlationRegistry.complete(msg)).thenReturn(IsoCategory.ORPHAN);

      handler.channelRead0(ctx, msg);

      verify(attr).set(IsoCategory.ORPHAN);
      verify(ctx).fireChannelRead(msg);
    }
  }

  @Nested
  @DisplayName("channelRead0 — selector not in registryCallbackSelectors")
  class SelectorNotMatchedPath {

    @Test
    @DisplayName("sets IsoCategory.EXTERNAL_REQUEST without touching CorrelationRegistry")
    @SuppressWarnings("unchecked")
    void channelRead0_selectorNotMatched_setsExternalRequestCategory() {
      setupNonMatchingMessage();

      handler.channelRead0(ctx, msg);

      verify(attr).set(IsoCategory.EXTERNAL_REQUEST);
      verify(ctx).fireChannelRead(msg);
    }
  }
}
