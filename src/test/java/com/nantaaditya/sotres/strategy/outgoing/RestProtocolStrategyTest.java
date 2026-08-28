package com.nantaaditya.sotres.strategy.outgoing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.client.TransactionClient;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.ResponseContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import com.solab.iso8583.IsoMessage;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        systemPropertiesService, transactionClient, isoFieldHelper, tracerHelper);

    requestContext = new RequestContext();
    requestContext.setRrn("rrn-001");

    lenient().when(participantCtx.getChannelHandlerContext()).thenReturn(channelHandlerContext);
    lenient().when(participantCtx.getIsoMessage()).thenReturn(isoMessage);
    lenient().when(participantCtx.getObservation()).thenReturn(observation);
    lenient().when(participantCtx.getTransactionHandler()).thenReturn(transactionHandler);
  }

  @Test
  @DisplayName("getProtocol returns REST")
  void getProtocol_returnsRest() {
    assertThat(strategy.getProtocol()).isEqualTo(OutgoingProtocol.REST);
  }

  @Test
  @DisplayName("send delegates to TransactionClient and returns ResponseContext")
  void send_delegatesToTransactionClient_returnsResponseContext() {
    when(transactionClient.send(requestContext)).thenReturn(responseContext);

    ResponseContext result = strategy.send(channelHandlerContext, isoMessage, requestContext);

    assertThat(result).isSameAs(responseContext);
    verify(tracerHelper).setBaggage(HeaderConstant.REQUEST_ID.getHeader(), "rrn-001");
  }

  @Test
  @DisplayName("send wraps TransactionClient error in TransactionException")
  void send_whenTransactionClientErrors_wrapsInTransactionException() {
    when(transactionClient.send(requestContext))
        .thenThrow(new RuntimeException("network error"));

    assertThatThrownBy(() -> strategy.send(channelHandlerContext, isoMessage, requestContext))
        .isInstanceOf(TransactionException.class);
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
