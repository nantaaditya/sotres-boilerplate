package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaskingHelper")
class MaskingHelperTest {

  private static final Gson GSON = new Gson();

  @Nested
  @DisplayName("masking(String)")
  class DefaultMasking {

    @Test
    @DisplayName("returns value unchanged when null")
    void masking_null_returnsNull() {
      assertThat(MaskingHelper.masking(null)).isNull();
    }

    @Test
    @DisplayName("returns value unchanged when empty")
    void masking_empty_returnsEmpty() {
      assertThat(MaskingHelper.masking("")).isEqualTo("");
    }

    @Test
    @DisplayName("returns value unchanged when length is 1")
    void masking_singleChar_returnsUnchanged() {
      assertThat(MaskingHelper.masking("a")).isEqualTo("a");
    }

    @Test
    @DisplayName("returns value unchanged when length is 2")
    void masking_twoChars_returnsUnchanged() {
      assertThat(MaskingHelper.masking("ab")).isEqualTo("ab");
    }

    @Test
    @DisplayName("masks middle portion of 8-char string showing 2 start and 2 end chars")
    void masking_eightChars_masksMidPortion() {
      // length=8, half=4, startLen=(8-4)/2=2, masked=4 chars
      String result = MaskingHelper.masking("abcdefgh");
      assertThat(result).startsWith("ab").endsWith("gh").contains("****");
      assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("masks middle portion of 10-char string")
    void masking_tenChars_masksMidPortion() {
      // length=10, half=5, startLen=(10-5)/2=2, masked=5 chars
      String result = MaskingHelper.masking("1234567890");
      assertThat(result).startsWith("12").endsWith("90").contains("*****");
      assertThat(result).hasSize(10);
    }
  }

  @Nested
  @DisplayName("masking(String, int, int)")
  class SpecificMasking {

    @Test
    @DisplayName("masks with specified start and end char counts")
    void masking_specificCounts_masksCorrectPortion() {
      String result = MaskingHelper.masking("1234567890", 3, 3);
      assertThat(result).isEqualTo("123****890");
    }

    @Test
    @DisplayName("returns value unchanged when too short for the given mask counts")
    void masking_valueTooShort_returnsUnchanged() {
      // "abc" length=3, totalLength=2+2=4, 3 <= 4 so returns unchanged
      String result = MaskingHelper.masking("abc", 2, 2);
      assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("returns null for null input")
    void masking_nullInput_returnsNull() {
      assertThat(MaskingHelper.masking(null, 2, 2)).isNull();
    }

    @Test
    @DisplayName("masks exactly one character when value is minimal length for mask")
    void masking_minimalMaskable_masksSingleChar() {
      // "abcde" length=5, totalLength=2+2=4, masks 1 char
      String result = MaskingHelper.masking("abcde", 2, 2);
      assertThat(result).isEqualTo("ab*de");
    }
  }

  @Nested
  @DisplayName("maskingCardNo(String)")
  class CardNoMasking {

    @Test
    @DisplayName("masks 16-digit card keeping 6 prefix and 4 suffix")
    void maskingCardNo_sixteenDigitCard_masksMiddleSixDigits() {
      String result = MaskingHelper.maskingCardNo("1234567890123456");
      assertThat(result).isEqualTo("123456******3456");
      assertThat(result).hasSize(16);
    }

    @Test
    @DisplayName("masks 19-digit card keeping 6 prefix and 4 suffix")
    void maskingCardNo_nineteenDigitCard_masksMiddleNineDigits() {
      String result = MaskingHelper.maskingCardNo("1234567890123456789");
      assertThat(result).startsWith("123456").endsWith("6789");
      assertThat(result).hasSize(19);
    }

    @Test
    @DisplayName("returns card unchanged when length is less than 16")
    void maskingCardNo_shortCard_returnsUnchanged() {
      assertThat(MaskingHelper.maskingCardNo("123456789012345")).isEqualTo("123456789012345");
    }

    @Test
    @DisplayName("returns null for null input")
    void maskingCardNo_null_returnsNull() {
      assertThat(MaskingHelper.maskingCardNo(null)).isNull();
    }

    @Test
    @DisplayName("returns empty string for empty input")
    void maskingCardNo_empty_returnsEmpty() {
      assertThat(MaskingHelper.maskingCardNo("")).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("maskingJson(Gson, Set, String)")
  class JsonMasking {

    @Test
    @DisplayName("masks target key value in flat JSON object")
    void maskingJson_flatObjectWithTargetKey_masksValue() {
      String json = "{\"username\":\"john\",\"password\":\"secret123\"}";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).contains("\"username\":\"john\"");
      assertThat(result).doesNotContain("secret123");
      assertThat(result).contains("password");
    }

    @Test
    @DisplayName("masks cardNo field using card masking rules regardless of target keys")
    void maskingJson_objectWithCardNo_appliesCardMasking() {
      String json = "{\"cardNo\":\"1234567890123456\"}";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).contains("123456").contains("3456");
      assertThat(result).doesNotContain("1234567890123456");
    }

    @Test
    @DisplayName("masks field '2' using card masking rules")
    void maskingJson_objectWithField2_appliesCardMasking() {
      String json = "{\"2\":\"1234567890123456\"}";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).doesNotContain("1234567890123456");
    }

    @Test
    @DisplayName("recursively masks target key in nested JSON object")
    void maskingJson_nestedObject_masksNestedValue() {
      String json = "{\"outer\":{\"password\":\"secret\"}}";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).doesNotContain("secret");
    }

    @Test
    @DisplayName("masks target key in each element of a JSON array")
    void maskingJson_arrayOfObjects_masksAllElements() {
      String json = "[{\"password\":\"secret1\"},{\"password\":\"secret2\"}]";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).doesNotContain("secret1").doesNotContain("secret2");
    }

    @Test
    @DisplayName("returns null for null input")
    void maskingJson_nullInput_returnsNull() {
      assertThat(MaskingHelper.maskingJson(GSON, Set.of("key"), null)).isNull();
    }

    @Test
    @DisplayName("returns empty string for empty input")
    void maskingJson_emptyInput_returnsEmpty() {
      assertThat(MaskingHelper.maskingJson(GSON, Set.of("key"), "")).isEqualTo("");
    }

    @Test
    @DisplayName("returns 'not a json' for invalid JSON input")
    void maskingJson_invalidJson_returnsErrorString() {
      String result = MaskingHelper.maskingJson(GSON, Set.of("key"), "not-valid-json");
      assertNull(result);
    }

    @Test
    @DisplayName("handles object with no matching keys without modification")
    void maskingJson_noMatchingKeys_returnsObjectUnmodified() {
      String json = "{\"name\":\"alice\",\"age\":\"30\"}";
      String result = MaskingHelper.maskingJson(GSON, Set.of("password"), json);
      assertThat(result).contains("alice").contains("30");
    }
  }

  @Nested
  @DisplayName("maskHeaders(Map, Predicate)")
  class HeaderMasking {

    @Test
    @DisplayName("masks values of headers the predicate flags as sensitive")
    void maskHeaders_sensitiveHeader_masksValues() {
      Map<String, List<String>> headers = Map.of(
          "authorization", List.of("Bearer abcdefghijklmnop"),
          "content-type", List.of("application/json"));

      Map<String, List<String>> result =
          MaskingHelper.maskHeaders(headers, key -> key.equalsIgnoreCase("authorization"));

      assertThat(result.get("authorization").get(0)).doesNotContain("abcdefghijklmnop");
      assertThat(result.get("content-type")).isEqualTo(List.of("application/json"));
    }

    @Test
    @DisplayName("leaves non-sensitive headers untouched")
    void maskHeaders_noSensitiveHeaders_returnsUnchanged() {
      Map<String, List<String>> headers = Map.of("x-request-id", List.of("RRN-1"));

      Map<String, List<String>> result = MaskingHelper.maskHeaders(headers, key -> false);

      assertThat(result).isEqualTo(headers);
    }

    @Test
    @DisplayName("masks every value in a multi-valued sensitive header")
    void maskHeaders_multiValuedSensitiveHeader_masksAllValues() {
      Map<String, List<String>> headers = Map.of(
          "x-api-key", List.of("key-1234567890", "key-0987654321"));

      Map<String, List<String>> result = MaskingHelper.maskHeaders(headers, key -> true);

      assertThat(result.get("x-api-key").get(0)).doesNotContain("1234567890");
      assertThat(result.get("x-api-key").get(1)).doesNotContain("0987654321");
    }
  }

}
