package com.nantaaditya.sotres.strategy.outgoing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

@DisplayName("RestProtocolStrategy")
@ExtendWith(MockitoExtension.class)
class RestProtocolStrategyTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private TransactionClient transactionClient;
  @Mock
  private IsoFieldHelper isoFieldHelper;
  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private Tracer tracer;
  @Mock
  private ParticipantContext participantCtx;
  @Mock
  private AbstractTransactionHandler transactionHandler;
  @Mock
  private ChannelHandlerContext channelHandlerContext;
  @Mock
  private IsoMessage isoMessage;
  @Mock
  private Observation observation;
  @Mock
  private ResponseContext responseContext;

  private RestProtocolStrategy strategy;
  private RequestContext requestContext;

  @BeforeEach
  void setUp() {
    strategy = new RestProtocolStrategy(
        systemPropertiesService, transactionClient, isoFieldHelper, tracerHelper, tracer);

    requestContext = new RequestContext();
    requestContext.setRrn("rrn-001");

    lenient().when(participantCtx.getChannelHandlerContext()).thenReturn(channelHandlerContext);
    lenient().when(participantCtx.getIsoMessage()).thenReturn(isoMessage);
    lenient().when(participantCtx.getObservation()).thenReturn(observation);
    lenient().when(participantCtx.getTransactionHandler()).thenReturn(transactionHandler);
    lenient().when(tracerHelper.composeTransactionContext(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("getProtocol returns REST")
  void getProtocol_returnsRest() {
    assertThat(strategy.getProtocol()).isEqualTo(OutgoingProtocol.REST);
  }

  @Test
  @DisplayName("send delegates to TransactionClient and emits ResponseContext")
  void send_delegatesToTransactionClient_emitsResponseContext() {
    Span mockSpan = mock(Span.class);
    Span nextSpan = mock(Span.class);
    TraceContext traceCtx = mock(TraceContext.class);
    when(tracer.nextSpan(mockSpan)).thenReturn(nextSpan);
    when(nextSpan.context()).thenReturn(traceCtx);
    when(traceCtx.traceId()).thenReturn("trace-001");
    when(traceCtx.spanId()).thenReturn("span-001");
    when(transactionClient.send(requestContext)).thenReturn(Mono.just(responseContext));

    StepVerifier.create(
            strategy.send(channelHandlerContext, isoMessage, requestContext)
                .contextWrite(Context.of(Span.class, mockSpan))
        )
        .expectNext(responseContext)
        .verifyComplete();
  }

  @Test
  @DisplayName("send wraps TransactionClient error in TransactionException")
  void send_whenTransactionClientErrors_wrapsInTransactionException() {
    Span mockSpan = mock(Span.class);
    Span nextSpan = mock(Span.class);
    TraceContext traceCtx = mock(TraceContext.class);
    when(tracer.nextSpan(mockSpan)).thenReturn(nextSpan);
    when(nextSpan.context()).thenReturn(traceCtx);
    when(traceCtx.traceId()).thenReturn("trace-001");
    when(traceCtx.spanId()).thenReturn("span-001");
    when(transactionClient.send(requestContext))
        .thenReturn(Mono.error(new RuntimeException("network error")));

    StepVerifier.create(
            strategy.send(channelHandlerContext, isoMessage, requestContext)
                .contextWrite(Context.of(Span.class, mockSpan))
        )
        .expectError(TransactionException.class)
        .verify();
  }

  @Test
  @DisplayName("handleResponse with null ResponseContext sends system malfunction code")
  void handleResponse_withNullResponseContext_sendsMalfunctionCode() {
    when(participantCtx.getResponseContext()).thenReturn(null);

    strategy.handleResponse(participantCtx);

    verify(isoFieldHelper).sendResponseWithObservation(participantCtx, "96", null);
    verify(observation).stop();
  }

  @Test
  @DisplayName("handleResponse with valid ResponseContext calls sendResponse with Consumer")
  void handleResponse_withValidResponseContext_callsSendResponseWithConsumer() {
    when(participantCtx.getResponseContext()).thenReturn(responseContext);
    when(responseContext.getResponseCode()).thenReturn("00");

    strategy.handleResponse(participantCtx);

    verify(isoFieldHelper).sendResponse(eq(channelHandlerContext), eq(isoMessage),
        any(Consumer.class));
    verify(observation).stop();
  }

  @Test
  @DisplayName("handleResponse when populateResponse throws sends system malfunction code")
  void handleResponse_whenPopulateResponseThrows_sendsMalfunctionCode() {
    when(participantCtx.getResponseContext()).thenReturn(responseContext);
    doThrow(new RuntimeException("populate failed", new IllegalStateException("cause")))
        .when(transactionHandler).populateResponse(participantCtx);

    strategy.handleResponse(participantCtx);

    verify(isoFieldHelper).sendResponseWithObservation(eq(participantCtx), eq("96"), any(RuntimeException.class));
    verify(observation).stop();
  }

  @Test
  @DisplayName("handleError with ReadTimeoutException does not send response")
  void handleError_withReadTimeoutException_doesNotSendResponse() {
    TransactionException ex = new TransactionException(ReadTimeoutException.INSTANCE,
        requestContext);

    strategy.handleError(participantCtx, ex);

    verify(isoFieldHelper, never()).sendResponse(any(), any(), anyString());
  }

  @Test
  @DisplayName("handleError with non-timeout TransactionException sends system malfunction code")
  void handleError_withOtherTransactionException_sendsMalfunctionCode() {
    TransactionException ex = new TransactionException(new RuntimeException("other"),
        requestContext);

    strategy.handleError(participantCtx, ex);

    verify(isoFieldHelper).sendResponseWithObservation(participantCtx, "96", ex);
  }

  @Test
  @DisplayName("handleError with unknown exception does not send response")
  void handleError_withUnknownException_doesNotSendResponse() {
    strategy.handleError(participantCtx, new IllegalStateException("unknown"));

    verify(isoFieldHelper, never()).sendResponse(any(), any(), anyString());
  }
}
