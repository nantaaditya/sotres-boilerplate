package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Reversal;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("IsoFieldHelper")
@ExtendWith(MockitoExtension.class)
class IsoFieldHelperTest {

  @Mock
  private IsoMessage isoMessage;

  @SuppressWarnings("unchecked")
  private IsoValue<Object> mockIsoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  @Nested
  @DisplayName("getMTI(int)")
  class GetMTI {

    @Test
    @DisplayName("converts 0x0200 to '0200'")
    void getMTI_purchaseRequest_returns0200() {
      assertThat(IsoFieldHelper.getMTI(0x0200)).isEqualTo("0200");
    }

    @Test
    @DisplayName("converts 0x0210 to '0210'")
    void getMTI_purchaseResponse_returns0210() {
      assertThat(IsoFieldHelper.getMTI(0x0210)).isEqualTo("0210");
    }

    @Test
    @DisplayName("converts 0x0800 to '0800'")
    void getMTI_networkRequest_returns0800() {
      assertThat(IsoFieldHelper.getMTI(0x0800)).isEqualTo("0800");
    }

    @Test
    @DisplayName("converts 0x0810 to '0810'")
    void getMTI_networkResponse_returns0810() {
      assertThat(IsoFieldHelper.getMTI(0x0810)).isEqualTo("0810");
    }

    @Test
    @DisplayName("converts 0x0220 to '0220'")
    void getMTI_adviceRequest_returns0220() {
      assertThat(IsoFieldHelper.getMTI(0x0220)).isEqualTo("0220");
    }
  }

  @Nested
  @DisplayName("isMTIRequest(int)")
  class IsMTIRequest {

    @ParameterizedTest(name = "MTI {0} is request: {1}")
    @CsvSource({
        "512, true",    // 0x0200 - function digit '0' = request
        "528, false",   // 0x0210 - function digit '1' = response
        "544, true",    // 0x0220 - function digit '2' = advice
        "560, false",   // 0x0230 - function digit '3' = advice response
        "2048, true",   // 0x0800 - function digit '0' = network request
        "2064, false",  // 0x0810 - function digit '1' = network response
    })
    @DisplayName("correctly identifies request MTI by function digit")
    void isMTIRequest_variousMTIs_returnsCorrectResult(int mti, boolean expected) {
      assertThat(IsoFieldHelper.isMTIRequest(mti)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("isMTIResponse(int)")
  class IsMTIResponse {

    @ParameterizedTest(name = "MTI {0} is response: {1}")
    @CsvSource({
        "512, false",   // 0x0200 - function digit '0' = request
        "528, true",    // 0x0210 - function digit '1' = response
        "544, false",   // 0x0220 - function digit '2' = advice
        "560, true",    // 0x0230 - function digit '3' = advice response
        "2048, false",  // 0x0800 - network request
        "2064, true",   // 0x0810 - network response
    })
    @DisplayName("correctly identifies response MTI by function digit")
    void isMTIResponse_variousMTIs_returnsCorrectResult(int mti, boolean expected) {
      assertThat(IsoFieldHelper.isMTIResponse(mti)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("convertAmount(double, int)")
  class ConvertAmount {

    @Test
    @DisplayName("moves decimal left by fractionDigit for a standard amount")
    void convertAmount_twoFractionDigits_movesDecimalLeft2() {
      BigDecimal result = IsoFieldHelper.convertAmount(100000.0, 2);
      assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("moves decimal left by 3 for currencies with 3 fraction digits")
    void convertAmount_threeFractionDigits_movesDecimalLeft3() {
      BigDecimal result = IsoFieldHelper.convertAmount(1000000.0, 3);
      assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("returns zero when amount is 0")
    void convertAmount_zeroAmount_returnsZero() {
      BigDecimal result = IsoFieldHelper.convertAmount(0.0, 2);
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("returns zero when fractionDigit is less than 1")
    void convertAmount_zeroFractionDigit_returnsZero() {
      BigDecimal result = IsoFieldHelper.convertAmount(50000.0, 0);
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("result always has scale of 2 after HALF_UP rounding")
    void convertAmount_anyValidInput_scaleIsTwo() {
      BigDecimal result = IsoFieldHelper.convertAmount(100001.0, 2);
      assertThat(result.scale()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("generateNumeric(int)")
  class GenerateNumeric {

    @Test
    @DisplayName("generates numeric string of the requested length containing only digits")
    void generateNumeric_length6_returns6Digits() {
      String result = IsoFieldHelper.generateNumeric(6);
      assertThat(result).hasSize(6).matches("[0-9]{6}");
    }

    @Test
    @DisplayName("generates numeric string of length 12")
    void generateNumeric_length12_returns12Digits() {
      String result = IsoFieldHelper.generateNumeric(12);
      assertThat(result).hasSize(12).matches("[0-9]{12}");
    }
  }

  @Nested
  @DisplayName("unpackTLV / packTLV")
  class TLVOperations {

    @Test
    @DisplayName("unpackTLV parses a single TLV entry into a map")
    void unpackTLV_singleEntry_returnsMap() {
      String raw = "PI02QR";
      Map<String, String> result = IsoFieldHelper.unpackTLV(raw, 2, 2);
      assertThat(result).containsEntry("PI", "QR");
    }

    @Test
    @DisplayName("unpackTLV parses multiple consecutive TLV entries")
    void unpackTLV_multipleEntries_returnsAllEntries() {
      String raw = "PI02QRAT04PURC";
      Map<String, String> result = IsoFieldHelper.unpackTLV(raw, 2, 2);
      assertThat(result).containsEntry("PI", "QR").containsEntry("AT", "PURC");
    }

    @Test
    @DisplayName("packTLV formats a single entry with zero-padded length")
    void packTLV_singleEntry_returnsRaw() {
      Map<String, String> tlv = new LinkedHashMap<>();
      tlv.put("PI", "QR");
      assertThat(IsoFieldHelper.packTLV(tlv, 2, 2)).isEqualTo("PI02QR");
    }

    @Test
    @DisplayName("packTLV pads value length to specified digit count")
    void packTLV_longerValue_lengthPaddedCorrectly() {
      Map<String, String> tlv = new LinkedHashMap<>();
      tlv.put("AT", "PURCHASE");
      assertThat(IsoFieldHelper.packTLV(tlv, 2, 2)).isEqualTo("AT08PURCHASE");
    }

    @Test
    @DisplayName("pack then unpack round-trip preserves original map contents and order")
    void packUnpack_roundTrip_preservesData() {
      Map<String, String> original = new LinkedHashMap<>();
      original.put("PI", "QR");
      original.put("AT", "PURC");
      String packed = IsoFieldHelper.packTLV(original, 2, 2);
      Map<String, String> unpacked = IsoFieldHelper.unpackTLV(packed, 2, 2);
      assertThat(unpacked).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("getField(IsoMessage, int)")
  class GetField {

    @Test
    @DisplayName("returns the field value string when field exists")
    void getField_existingField_returnsValue() {
      when(isoMessage.getField(4)).thenReturn(mockIsoValue("100000"));
      assertThat(IsoFieldHelper.getField(isoMessage, 4)).isEqualTo("100000");
    }

    @Test
    @DisplayName("returns null when the message itself is null")
    void getField_nullMessage_returnsNull() {
      assertThat(IsoFieldHelper.getField(null, 4)).isNull();
    }

    @Test
    @DisplayName("returns null when the field is not present in the message")
    void getField_missingField_returnsNull() {
      when(isoMessage.getField(99)).thenReturn(null);
      assertThat(IsoFieldHelper.getField(isoMessage, 99)).isNull();
    }
  }

  @Nested
  @DisplayName("substring(String, int, int)")
  class Substring {

    @Test
    @DisplayName("returns substring for valid start and end indices")
    void substring_validRange_returnsSubstring() {
      assertThat(IsoFieldHelper.substring("abcdefgh", 2, 5)).isEqualTo("cde");
    }

    @Test
    @DisplayName("returns null for null input")
    void substring_nullInput_returnsNull() {
      assertThat(IsoFieldHelper.substring(null, 0, 3)).isNull();
    }

    @Test
    @DisplayName("returns blank string unchanged when input is blank")
    void substring_blankInput_returnsBlank() {
      assertThat(IsoFieldHelper.substring("   ", 0, 2)).isEqualTo("   ");
    }

    @Test
    @DisplayName("returns original string when start equals end")
    void substring_startEqualsEnd_returnsOriginal() {
      assertThat(IsoFieldHelper.substring("abcde", 3, 3)).isEqualTo("abcde");
    }

    @Test
    @DisplayName("returns original string when start is greater than end")
    void substring_startGreaterThanEnd_returnsOriginal() {
      assertThat(IsoFieldHelper.substring("abcde", 4, 2)).isEqualTo("abcde");
    }
  }

  @Nested
  @DisplayName("substring(String, int)")
  class SubstringFromStart {

    @Test
    @DisplayName("returns from start index to end of string")
    void substring_zeroEnd_returnsFromStartToEnd() {
      assertThat(IsoFieldHelper.substring("abcde", 2)).isEqualTo("cde");
    }
  }

  @Nested
  @DisplayName("parse(String)")
  class Parse {

    @Test
    @DisplayName("parses valid integer string to double")
    void parse_integerString_returnsDouble() {
      assertThat(IsoFieldHelper.parse("100000")).isEqualTo(100000.0);
    }

    @Test
    @DisplayName("parses decimal string to double")
    void parse_decimalString_returnsDouble() {
      assertThat(IsoFieldHelper.parse("1000.50")).isEqualTo(1000.50);
    }

    @Test
    @DisplayName("returns Double.MIN_VALUE for non-numeric string")
    void parse_invalidString_returnsMinValue() {
      assertThat(IsoFieldHelper.parse("not-a-number")).isEqualTo(Double.MIN_VALUE);
    }

    @Test
    @DisplayName("returns Double.MIN_VALUE for empty string")
    void parse_emptyString_returnsMinValue() {
      assertThat(IsoFieldHelper.parse("")).isEqualTo(Double.MIN_VALUE);
    }
  }

  @Nested
  @DisplayName("createSelector(String, String, Map)")
  class CreateSelector {

    @Test
    @DisplayName("builds selector using MTI, processingCode first 2 chars, and PI from TLV")
    void createSelector_allFields_returnsSelector() {
      // mti="0200": substring(1,3)="20"; processingCode="000000": substring(0,2)="00"
      Map<String, String> tlv = Map.of("PI", "QR");
      String result = IsoFieldHelper.createSelector("0200", "000000", tlv);
      assertThat(result).isEqualTo("20.00-QR");
    }

    @Test
    @DisplayName("uses 'NA' for processing code segment when processingCode is blank")
    void createSelector_blankProcessingCode_usesNA() {
      Map<String, String> tlv = Map.of("PI", "QR");
      String result = IsoFieldHelper.createSelector("0200", "", tlv);
      assertThat(result).isEqualTo("20.NA-QR");
    }

    @Test
    @DisplayName("uses 'NA' for PI segment when PI is absent from TLV")
    void createSelector_missingPI_usesNA() {
      Map<String, String> tlv = Map.of("AT", "PURC");
      String result = IsoFieldHelper.createSelector("0200", "000000", tlv);
      assertThat(result).isEqualTo("20.00-NA");
    }
  }

  @Nested
  @DisplayName("createMerchant(IsoMessage)")
  class CreateMerchant {

    @Test
    @DisplayName("extracts MCC from DE18 and merchant name/city/country from DE43")
    void createMerchant_validFields_returnsMerchant() {
      when(isoMessage.getField(18)).thenReturn(mockIsoValue("5411"));
      // DE43 format: name(25 chars) + city(13 chars) + country code(2 chars) = 40 chars total
      when(isoMessage.getField(43)).thenReturn(
          mockIsoValue("GROCERY STORE NAME       JAKARTA      ID"));

      Merchant result = IsoFieldHelper.createMerchant(isoMessage);

      assertThat(result).isNotNull();
      assertThat(result.getMerchantCategoryCode()).isEqualTo("5411");
      assertThat(result.getMerchantName()).isEqualTo("GROCERY STORE NAME       ");
      assertThat(result.getMerchantCountryCode()).isEqualTo("ID");
    }
  }

  @Nested
  @DisplayName("createReversal(IsoMessage)")
  class CreateReversal {

    @Test
    @DisplayName("returns null when DE90 is not present")
    void createReversal_noDE90_returnsNull() {
      when(isoMessage.hasField(90)).thenReturn(false);
      assertThat(IsoFieldHelper.createReversal(isoMessage)).isNull();
    }

    @Test
    @DisplayName("extracts reversal fields from DE90 when present")
    void createReversal_withDE90_returnsReversal() {
      when(isoMessage.hasField(90)).thenReturn(true);
      // DE90: originalMti(4) + originalStan(6) + originalDateTime(10) + acquirer(11) + forwarding(11) = 42 chars
      when(isoMessage.getField(90))
          .thenReturn(mockIsoValue("020012345606151030450000000001100000000012"));

      Reversal result = IsoFieldHelper.createReversal(isoMessage);

      assertThat(result).isNotNull();
      assertThat(result.getOriginalMti()).isEqualTo("0200");
      assertThat(result.getOriginalStan()).isEqualTo("123456");
      assertThat(result.getOriginalTransmissionDateTime()).isEqualTo("0615103045");
      assertThat(result.getOriginalAcquiringInstitutionId()).isEqualTo("00000000011");
      assertThat(result.getOriginalForwardingInstitutionId()).isEqualTo("00000000012");
    }
  }

  @Nested
  @DisplayName("logAndObserve(IsoMessage, RequestContext, Observation)")
  class LogAndObserve {

    @Mock
    private MessageFactoryHelper messageFactoryHelper;
    @Mock
    private IsoMessageLoggerHelper isoMessageLoggerHelper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private Observation observation;
    @Mock
    private JsonProcessingException serializationError;

    private IsoFieldHelper isoFieldHelper;
    private RequestContext requestContext;

    @BeforeEach
    void setUp() throws Exception {
      isoFieldHelper = new IsoFieldHelper(messageFactoryHelper, isoMessageLoggerHelper, objectMapper);

      requestContext = new RequestContext();
      requestContext.setRrn("000000000001");
      requestContext.setIsoFeatureConstant("20.00-QR");

      lenient().when(isoMessageLoggerHelper.toLogMessage(isoMessage))
          .thenReturn(new JsonLogIsoMessage("outgoing", "0200", Map.of()));
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"mti\":\"0200\"}");
      // AppLogMessage.error() reads the stack trace when logging the caught exception
      lenient().when(serializationError.getStackTrace()).thenReturn(new StackTraceElement[0]);
    }

    @Test
    @DisplayName("sets highCardinality requestId and lowCardinality feature on the observation")
    void logAndObserve_populatesObservationKeyValues() {
      isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      verify(observation).highCardinalityKeyValue("requestId", "000000000001");
      verify(observation).lowCardinalityKeyValue("feature", "20.00-QR");
    }

    @Test
    @DisplayName("publishes an 'iso_request' event with the serialized ISO message")
    void logAndObserve_publishesIsoRequestEvent() {
      isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      ArgumentCaptor<Observation.Event> eventCaptor = ArgumentCaptor.forClass(Observation.Event.class);
      verify(observation).event(eventCaptor.capture());
      assertThat(eventCaptor.getValue().getName()).isEqualTo("iso_request");
    }

    @Test
    @DisplayName("logs the ISO message via IsoMessageLoggerHelper")
    void logAndObserve_logsIsoMessage() {
      isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      verify(isoMessageLoggerHelper).logIsoMessage(isoMessage);
    }

    @Test
    @DisplayName("returns the same RequestContext instance unchanged")
    void logAndObserve_returnsSameRequestContext() {
      RequestContext result = isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      assertThat(result).isSameAs(requestContext);
    }

    @Test
    @DisplayName("swallows serialization failure, skips the event, but still logs the message")
    void logAndObserve_serializationFails_swallowsErrorAndSkipsEvent() throws Exception {
      when(objectMapper.writeValueAsString(any())).thenThrow(serializationError);

      RequestContext result = isoFieldHelper.logAndObserve(isoMessage, requestContext, observation);

      assertThat(result).isSameAs(requestContext);
      verify(observation, never()).event(any());
      verify(isoMessageLoggerHelper).logIsoMessage(isoMessage);
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

    private IsoFieldHelper isoFieldHelper;
    private ParticipantContext participantContext;

    @BeforeEach
    void setUp() throws Exception {
      isoFieldHelper = new IsoFieldHelper(messageFactoryHelper, isoMessageLoggerHelper, objectMapper);

      participantContext = new ParticipantContext();
      participantContext.onUpdate(channelHandlerContext, isoMessage, null, null, observation);

      lenient().when(messageFactoryHelper.getDefaultMessageFactory()).thenReturn(j8583MessageFactory);
      lenient().when(j8583MessageFactory.createResponse(isoMessage)).thenReturn(response);
      lenient().when(isoMessageLoggerHelper.toLogMessage(response))
          .thenReturn(new JsonLogIsoMessage("outgoing", "0210", Map.of()));
      lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"mti\":\"0210\"}");
    }

    @Test
    @DisplayName("sets field 39 on the response to the given response code")
    void sendResponseWithObservation_setsResponseCodeField() {
      isoFieldHelper.sendResponseWithObservation(participantContext, "96", null);

      verify(response).setField(eq(39), any(IsoValue.class));
    }

    @Test
    @DisplayName("logs and writes/flushes the response to the channel")
    void sendResponseWithObservation_writesAndFlushesResponse() {
      isoFieldHelper.sendResponseWithObservation(participantContext, "96", null);

      verify(isoMessageLoggerHelper).logIsoMessage(response);
      verify(channelHandlerContext).writeAndFlush(response);
    }

    @Test
    @DisplayName("publishes an 'iso_response' event")
    void sendResponseWithObservation_publishesIsoResponseEvent() {
      isoFieldHelper.sendResponseWithObservation(participantContext, "96", null);

      ArgumentCaptor<Observation.Event> eventCaptor = ArgumentCaptor.forClass(Observation.Event.class);
      verify(observation).event(eventCaptor.capture());
      assertThat(eventCaptor.getValue().getName()).isEqualTo("iso_response");
    }

    @Test
    @DisplayName("sets lowCardinality responseCode and records no error when throwable is null")
    void sendResponseWithObservation_nullThrowable_setsResponseCodeNoError() {
      isoFieldHelper.sendResponseWithObservation(participantContext, "00", null);

      verify(observation).lowCardinalityKeyValue("responseCode", "00");
      verify(observation, never()).lowCardinalityKeyValue(eq("error"), any());
      verify(observation, never()).error(any());
    }

    @Test
    @DisplayName("records the original error class for a TransactionException")
    void sendResponseWithObservation_transactionException_recordsOriginalErrorClass() {
      IllegalArgumentException originalError = new IllegalArgumentException("root cause");
      TransactionException txException = new TransactionException(originalError, null);

      isoFieldHelper.sendResponseWithObservation(participantContext, "96", txException);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalArgumentException");
      verify(observation).error(txException);
    }

    @Test
    @DisplayName("records the cause's error class for a generic wrapped exception")
    void sendResponseWithObservation_genericException_recordsCauseErrorClass() {
      IllegalStateException cause = new IllegalStateException("cause");
      RuntimeException wrapper = new RuntimeException("wrapper", cause);

      isoFieldHelper.sendResponseWithObservation(participantContext, "99", wrapper);

      verify(observation).lowCardinalityKeyValue("error", "java.lang.IllegalStateException");
      verify(observation).error(wrapper);
    }
  }
}
