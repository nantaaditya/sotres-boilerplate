package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

}
