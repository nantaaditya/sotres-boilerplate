package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GsonHelper")
class GsonHelperTest {

  private static final Gson GSON = new Gson();

  @Nested
  @DisplayName("cleanJson(String, Gson)")
  class CleanJson {

    @Test
    @DisplayName("normalizes a flat JSON object")
    void cleanJson_flatObject_returnsNormalized() {
      String json = "{\"key\":\"value\"}";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).isNotEmpty().contains("key").contains("value");
    }

    @Test
    @DisplayName("normalizes a JSON array of objects")
    void cleanJson_jsonArray_returnsNormalized() {
      String json = "[{\"key\":\"v1\"},{\"key\":\"v2\"}]";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).startsWith("[").endsWith("]").contains("v1").contains("v2");
    }

    @Test
    @DisplayName("normalizes a nested JSON object")
    void cleanJson_nestedObject_returnsNormalized() {
      String json = "{\"outer\":{\"inner\":\"value\"}}";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).contains("outer").contains("inner").contains("value");
    }

    @Test
    @DisplayName("removes escaped tab characters from JSON string values")
    void cleanJson_withTabEscape_removesEscapedTab() {
      String json = "{\"key\":\"val\\tue\"}";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).doesNotContain("\\t");
    }

    @Test
    @DisplayName("removes escaped newline characters from JSON string values")
    void cleanJson_withNewlineEscape_removesEscapedNewline() {
      String json = "{\"key\":\"line1\\nline2\"}";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).doesNotContain("\\n");
    }

    @Test
    @DisplayName("returns empty string for input that is neither JSON object nor array")
    void cleanJson_nonJson_returnsEmpty() {
      String result = GsonHelper.cleanJson("not json at all", GSON);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("handles JSON object with numeric values")
    void cleanJson_objectWithNumericValues_returnsNormalized() {
      String json = "{\"count\":42,\"amount\":100.50}";
      String result = GsonHelper.cleanJson(json, GSON);
      assertThat(result).contains("count").contains("42");
    }

    @Test
    @DisplayName("handles empty JSON object")
    void cleanJson_emptyObject_returnsEmptyObject() {
      String result = GsonHelper.cleanJson("{}", GSON);
      assertThat(result).isEqualTo("{}");
    }

    @Test
    @DisplayName("handles empty JSON array")
    void cleanJson_emptyArray_returnsEmptyArray() {
      String result = GsonHelper.cleanJson("[]", GSON);
      assertThat(result).isEqualTo("[]");
    }
  }
}
