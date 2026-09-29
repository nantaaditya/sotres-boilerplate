package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@DisplayName("IsoResponseSender")
@ExtendWith(MockitoExtension.class)
class IsoResponseSenderTest {

  @Mock
  private IsoMessage isoMessage;

  @Nested
  @DisplayName("publishIsoEvent(Observation, IsoMessage, String)")
  class PublishIsoEvent {

    @Mock
    private MessageFactoryHelper messageFactoryHelper;
    @Mock
    private IsoMessageLoggerHelper isoMessageLoggerHelper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Observation observation;
    @Mock
    private JacksonException serializationError;

    private IsoResponseSender isoResponseSender;

    @BeforeEach
    void setUp() throws Exception {
      isoResponseSender = new IsoResponseSender(messageFactoryHelper, isoMessageLoggerHelper, objectMapper);

      lenient().when(isoMessageLoggerHelper.toLogMessage(eq(isoMessage), anyString()))
          .thenReturn(new JsonLogIsoMessage("outgoing", "0200", Map.of()));
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"mti\":\"0200\"}");
      // AppLogMessage.error() reads the stack trace when logging the caught exception
      lenient().when(serializationError.getStackTrace()).thenReturn(new StackTraceElement[0]);
    }

    @Test
    @DisplayName("publishes an event with the given name and the serialized ISO message as value")
    void publishIsoEvent_publishesEventWithSerializedMessage() {
      isoResponseSender.publishIsoEvent(observation, isoMessage, "iso_request", "incoming");

      ArgumentCaptor<Observation.Event> eventCaptor = ArgumentCaptor.forClass(Observation.Event.class);
      verify(observation).event(eventCaptor.capture());
      assertThat(eventCaptor.getValue().getName()).isEqualTo("iso_request");
      assertThat(eventCaptor.getValue().getContextualName()).isEqualTo("{\"mti\":\"0200\"}");
    }

    @Test
    @DisplayName("swallows serialization failure and skips the event")
    void publishIsoEvent_serializationFails_swallowsErrorAndSkipsEvent() throws Exception {
      when(objectMapper.writeValueAsString(any())).thenThrow(serializationError);

      isoResponseSender.publishIsoEvent(observation, isoMessage, "iso_request", "incoming");

      verify(observation, never()).event(any());
    }
  }

  @Nested
  @DisplayName("sendResponseWithObservation(ParticipantContext, String, Throwable)")
  class SendResponseWithObservation {

    @Mock
    private MessageFactoryHelper messageFactoryHelper;
    @Mock
    private IsoMessageLoggerHelper isoMessageLoggerHelper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private J8583MessageFactory j8583MessageFactory;
    @Mock
    private Observation observation;
    @Mock
    private ChannelHandlerContext channelHandlerContext;
    @Mock
    private IsoMessage response;

    private IsoResponseSender isoResponseSender;
    private ParticipantContext participantContext;

    @BeforeEach
    void setUp() throws Exception {
      isoResponseSender = new IsoResponseSender(messageFactoryHelper, isoMessageLoggerHelper, objectMapper);

      participantContext = new ParticipantContext();
      participantContext.onUpdate(channelHandlerContext, isoMessage, null, null, observation);

      lenient().when(messageFactoryHelper.getDefaultMessageFactory()).thenReturn(j8583MessageFactory);
      lenient().when(j8583MessageFactory.createResponse(isoMessage)).thenReturn(response);
      lenient().when(isoMessageLoggerHelper.toLogMessage(response, "outgoing"))
          .thenReturn(new JsonLogIsoMessage("outgoing", "0210", Map.of()));
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"mti\":\"0210\"}");
    }

    @Test
    @DisplayName("sets field 39 on the response to the given response code")
    void sendResponseWithObservation_setsResponseCodeField() {
      isoResponseSender.sendResponseWithObservation(participantContext, "96", null);

      verify(response).setField(eq(39), any(IsoValue.class));
    }

    @Test
    @DisplayName("logs and writes/flushes the response to the channel")
    void sendResponseWithObservation_writesAndFlushesResponse() {
      isoResponseSender.sendResponseWithObservation(participantContext, "96", null);

      verify(isoMessageLoggerHelper).logIsoMessage(response, "outgoing");
      verify(channelHandlerContext).writeAndFlush(response);
    }

    @Test
    @DisplayName("publishes an 'iso_response' event")
    void sendResponseWithObservation_publishesIsoResponseEvent() {
      isoResponseSender.sendResponseWithObservation(participantContext, "96", null);

      ArgumentCaptor<Observation.Event> eventCaptor = ArgumentCaptor.forClass(Observation.Event.class);
      verify(observation).event(eventCaptor.capture());
      assertThat(eventCaptor.getValue().getName()).isEqualTo("iso_response");
    }

    @Test
    @DisplayName("sets lowCardinality responseCode and records no error when throwable is null")
    void sendResponseWithObservation_nullThrowable_setsResponseCodeNoError() {
      isoResponseSender.sendResponseWithObservation(participantContext, "00", null);

      verify(observation).lowCardinalityKeyValue("responseCode", "00");
      verify(observation, never()).lowCardinalityKeyValue(eq("error"), any());
      verify(observation, never()).error(any());
    }

    @Test
    @DisplayName("records the original error class for a TransactionException")
    void sendResponseWithObservation_transactionException_recordsOriginalErrorClass() {
      IllegalArgumentException originalError = new IllegalArgumentException("root cause");
      TransactionException txException = new TransactionException(originalError, null);

      isoResponseSender.sendResponseWithObservation(participantContext, "96", txException);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalArgumentException");
      verify(observation).error(txException);
    }

    @Test
    @DisplayName("records the cause's error class for a generic wrapped exception")
    void sendResponseWithObservation_genericException_recordsCauseErrorClass() {
      IllegalStateException cause = new IllegalStateException("cause");
      RuntimeException wrapper = new RuntimeException("wrapper", cause);

      isoResponseSender.sendResponseWithObservation(participantContext, "99", wrapper);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalStateException");
      verify(observation).error(wrapper);
    }

    @Test
    @DisplayName("callback-mode response: skips writing an ISO reply back to the channel")
    void sendResponseWithObservation_callbackResponse_skipsWriteAndFlush() {
      RequestContext callbackRequestContext = new RequestContext();
      callbackRequestContext.setCallbackResponse(true);
      participantContext.onUpdate(channelHandlerContext, isoMessage, null, callbackRequestContext, observation);

      isoResponseSender.sendResponseWithObservation(participantContext, "00", null);

      verify(channelHandlerContext, never()).writeAndFlush(any());
      verify(isoMessageLoggerHelper, never()).logIsoMessage(any(), anyString());
      verify(j8583MessageFactory, never()).createResponse(any());
    }

    @Test
    @DisplayName("callback-mode response: still records the observation outcome")
    void sendResponseWithObservation_callbackResponse_stillRecordsObservation() {
      RequestContext callbackRequestContext = new RequestContext();
      callbackRequestContext.setCallbackResponse(true);
      participantContext.onUpdate(channelHandlerContext, isoMessage, null, callbackRequestContext, observation);

      isoResponseSender.sendResponseWithObservation(participantContext, "00", null);

      verify(observation).lowCardinalityKeyValue("responseCode", "00");
    }

    @Test
    @DisplayName("non-callback request context: writes ISO reply as before")
    void sendResponseWithObservation_nonCallbackResponse_writesAndFlushesResponse() {
      RequestContext plainRequestContext = new RequestContext();
      participantContext.onUpdate(channelHandlerContext, isoMessage, null, plainRequestContext, observation);

      isoResponseSender.sendResponseWithObservation(participantContext, "96", null);

      verify(channelHandlerContext).writeAndFlush(response);
    }
  }
}
