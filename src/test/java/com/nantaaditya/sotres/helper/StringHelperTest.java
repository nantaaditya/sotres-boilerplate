package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StringHelper")
class StringHelperTest {

  @Nested
  @DisplayName("toCollection(String, String, Class<Collection>)")
  class ToCollectionTests {

    @Test
    @DisplayName("splits comma-delimited string into ordered list")
    void toCollection_commaSeparated_returnsList() {
      Collection<String> result = StringHelper.toCollection("a,b,c", ",", ArrayList.class);
      assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("returns empty collection for null input")
    void toCollection_nullInput_returnsEmpty() {
      Collection<String> result = StringHelper.toCollection(null, ",", ArrayList.class);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty collection for empty string")
    void toCollection_emptyInput_returnsEmpty() {
      Collection<String> result = StringHelper.toCollection("", ",", ArrayList.class);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("uses whitespace as delimiter when delimiter is null")
    void toCollection_nullDelimiter_splitsOnWhitespace() {
      Collection<String> result = StringHelper.toCollection("a b c", null, ArrayList.class);
      assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("uses whitespace as delimiter when delimiter is empty")
    void toCollection_emptyDelimiter_splitsOnWhitespace() {
      Collection<String> result = StringHelper.toCollection("a b c", "", ArrayList.class);
      assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("trims whitespace from each token")
    void toCollection_tokensWithSpaces_trimsTokens() {
      Collection<String> result = StringHelper.toCollection("a , b , c", ",", ArrayList.class);
      assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("splits single token into one-element collection")
    void toCollection_singleToken_returnsOneElement() {
      Collection<String> result = StringHelper.toCollection("only", ",", ArrayList.class);
      assertThat(result).containsExactly("only");
    }
  }

  @Nested
  @DisplayName("toCollection(String, String, String, Class<Map>)")
  class ToMapCollectionTests {

    @Test
    @DisplayName("parses key-value pairs separated by row and column delimiters")
    void toCollection_keyValuePairs_returnsMap() {
      Map<String, String> result = StringHelper.toCollection("k1:v1,k2:v2", ",", ":", HashMap.class);
      assertThat(result).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    @DisplayName("returns empty map for empty input string")
    void toCollection_emptyInput_returnsEmptyMap() {
      Map<String, String> result = StringHelper.toCollection("", ",", ":", HashMap.class);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty map for null input string")
    void toCollection_nullInput_returnsEmptyMap() {
      Map<String, String> result = StringHelper.toCollection(null, ",", ":", HashMap.class);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("append(String, char, int)")
  class AppendTests {

    @Test
    @DisplayName("appends pad chars to reach target count")
    void append_shortString_padded() {
      assertThat(StringHelper.append("abc", '0', 6)).isEqualTo("abc000");
    }

    @Test
    @DisplayName("returns original when already longer than count")
    void append_longerThanCount_returnsOriginal() {
      assertThat(StringHelper.append("abcdefg", '0', 5)).isEqualTo("abcdefg");
    }

    @Test
    @DisplayName("returns original when exactly at count")
    void append_exactLength_returnsOriginal() {
      assertThat(StringHelper.append("abc", '0', 3)).isEqualTo("abc");
    }

    @Test
    @DisplayName("appends space characters when pad char is space")
    void append_spaceChar_appendsSpaces() {
      assertThat(StringHelper.append("ab", ' ', 5)).isEqualTo("ab   ");
    }
  }

  @Nested
  @DisplayName("prepend(String, char, int)")
  class PrependTests {

    @Test
    @DisplayName("prepends pad chars to reach target count")
    void prepend_shortString_padded() {
      assertThat(StringHelper.prepend("abc", '0', 6)).isEqualTo("000abc");
    }

    @Test
    @DisplayName("returns original when already longer than count")
    void prepend_longerThanCount_returnsOriginal() {
      assertThat(StringHelper.prepend("abcdefg", '0', 5)).isEqualTo("abcdefg");
    }

    @Test
    @DisplayName("returns original when exactly at count")
    void prepend_exactLength_returnsOriginal() {
      assertThat(StringHelper.prepend("abc", '0', 3)).isEqualTo("abc");
    }

    @Test
    @DisplayName("prepends space characters when pad char is space")
    void prepend_spaceChar_prependsSpaces() {
      assertThat(StringHelper.prepend("ab", ' ', 5)).isEqualTo("   ab");
    }
  }

  @Nested
  @DisplayName("unPrepend(String, char)")
  class UnPrependTests {

    @Test
    @DisplayName("removes leading pad characters")
    void unPrepend_leadingPads_removed() {
      assertThat(StringHelper.unPrepend("000abc", '0')).isEqualTo("abc");
    }

    @Test
    @DisplayName("removes all leading pad characters when entire string is pad char")
    void unPrepend_allPads_returnsEmpty() {
      assertThat(StringHelper.unPrepend("000", '0')).isEqualTo("");
    }

    @Test
    @DisplayName("returns string unchanged when no leading pad chars")
    void unPrepend_noPads_returnsUnchanged() {
      assertThat(StringHelper.unPrepend("abc000", '0')).isEqualTo("abc000");
    }

    @Test
    @DisplayName("returns null for null input")
    void unPrepend_null_returnsNull() {
      assertThat(StringHelper.unPrepend(null, '0')).isNull();
    }

    @Test
    @DisplayName("returns empty for empty input")
    void unPrepend_empty_returnsEmpty() {
      assertThat(StringHelper.unPrepend("", '0')).isEqualTo("");
    }

    @Test
    @DisplayName("removes only the leading pad chars leaving embedded non-pad chars intact")
    void unPrepend_mixedContent_removesOnlyLeadingPads() {
      assertThat(StringHelper.unPrepend("00a00b", '0')).isEqualTo("a00b");
    }
  }

  @Nested
  @DisplayName("unAppend(String, char)")
  class UnAppendTests {

    @Test
    @DisplayName("removes trailing pad characters")
    void unAppend_trailingPads_removed() {
      assertThat(StringHelper.unAppend("abc000", '0')).isEqualTo("abc");
    }

    @Test
    @DisplayName("removes all trailing pad characters when entire string is pad char")
    void unAppend_allPads_returnsEmpty() {
      assertThat(StringHelper.unAppend("000", '0')).isEqualTo("");
    }

    @Test
    @DisplayName("returns string unchanged when no trailing pad chars")
    void unAppend_noPads_returnsUnchanged() {
      assertThat(StringHelper.unAppend("000abc", '0')).isEqualTo("000abc");
    }

    @Test
    @DisplayName("returns null for null input")
    void unAppend_null_returnsNull() {
      assertThat(StringHelper.unAppend(null, '0')).isNull();
    }

    @Test
    @DisplayName("returns empty for empty input")
    void unAppend_empty_returnsEmpty() {
      assertThat(StringHelper.unAppend("", '0')).isEqualTo("");
    }

    @Test
    @DisplayName("removes only the trailing pad chars leaving embedded non-pad chars intact")
    void unAppend_mixedContent_removesOnlyTrailingPads() {
      assertThat(StringHelper.unAppend("a00b00", '0')).isEqualTo("a00b");
    }
  }

  @Nested
  @DisplayName("toMaps(String)")
  class ToMapsTests {

    @Test
    @DisplayName("parses comma-and-colon-delimited string to map")
    void toMaps_commaSeparatedColonPairs_returnsMap() {
      Map<String, String> result = StringHelper.toMaps("k1:v1,k2:v2");
      assertThat(result).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    @DisplayName("single pair parses correctly")
    void toMaps_singlePair_returnsOneEntryMap() {
      Map<String, String> result = StringHelper.toMaps("key:value");
      assertThat(result).containsEntry("key", "value").hasSize(1);
    }
  }

  @Nested
  @DisplayName("toSet(String)")
  class ToSetTests {

    @Test
    @DisplayName("parses comma-separated string to insertion-ordered set")
    void toSet_commaSeparated_returnsLinkedHashSet() {
      Set<String> result = StringHelper.toSet("a,b,c");
      assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("single token becomes a one-element set")
    void toSet_singleToken_returnsOneElementSet() {
      Set<String> result = StringHelper.toSet("only");
      assertThat(result).containsExactly("only");
    }
  }

  @Nested
  @DisplayName("logPrepend(String, int, char)")
  class LogPrependTests {

    @Test
    @DisplayName("prepends character when value is shorter than maxLength")
    void logPrepend_shortValue_prepended() {
      assertThat(StringHelper.logPrepend("abc", 6, '0')).isEqualTo("000abc");
    }

    @Test
    @DisplayName("truncates from left when value is longer than maxLength")
    void logPrepend_longValue_truncatedFromLeft() {
      assertThat(StringHelper.logPrepend("abcdefgh", 5, '0')).isEqualTo("defgh");
    }

    @Test
    @DisplayName("returns null when input is null")
    void logPrepend_null_returnsNull() {
      assertThat(StringHelper.logPrepend(null, 5, '0')).isNull();
    }

    @Test
    @DisplayName("returns string unchanged when it is exactly maxLength")
    void logPrepend_exactLength_returnsUnchanged() {
      assertThat(StringHelper.logPrepend("abcde", 5, '0')).isEqualTo("abcde");
    }
  }
}
