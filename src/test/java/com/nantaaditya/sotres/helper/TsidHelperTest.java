package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TsidHelper")
class TsidHelperTest {

  @Nested
  @DisplayName("generateStringId()")
  class GenerateStringId {

    @Test
    @DisplayName("returns non-null non-empty string")
    void generateStringId_returnsNonEmpty() {
      String id = TsidHelper.generateStringId();
      assertThat(id).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("returns lowercase string")
    void generateStringId_returnsLowercase() {
      String id = TsidHelper.generateStringId();
      assertThat(id).isEqualTo(id.toLowerCase());
    }

    @Test
    @DisplayName("generates 100 unique IDs with no collisions")
    void generateStringId_hundredCalls_allUnique() {
      Set<String> ids = new HashSet<>();
      for (int i = 0; i < 100; i++) {
        ids.add(TsidHelper.generateStringId());
      }
      assertThat(ids).hasSize(100);
    }
  }

  @Nested
  @DisplayName("generateLongId()")
  class GenerateLongId {

    @Test
    @DisplayName("returns a positive long value")
    void generateLongId_returnsPositive() {
      long id = TsidHelper.generateLongId();
      assertThat(id).isPositive();
    }

    @Test
    @DisplayName("generates unique values across 100 calls")
    void generateLongId_hundredCalls_allUnique() {
      Set<Long> ids = new HashSet<>();
      for (int i = 0; i < 100; i++) {
        ids.add(TsidHelper.generateLongId());
      }
      assertThat(ids).hasSize(100);
    }
  }
}
