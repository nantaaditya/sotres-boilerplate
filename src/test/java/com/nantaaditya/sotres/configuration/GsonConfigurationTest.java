package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GsonConfiguration")
class GsonConfigurationTest {

  private Gson gson;

  @BeforeEach
  void setUp() {
    gson = new GsonConfiguration().gson();
  }

  @Test
  @DisplayName("Double serializes to two decimal places")
  void double_serializesToTwoDecimalPlaces() {
    assertThat(gson.toJson(1.567)).isEqualTo("1.57");
  }

  @Test
  @DisplayName("Float serializes to two decimal places")
  void float_serializesToTwoDecimalPlaces() {
    assertThat(gson.toJson(1.567f)).isEqualTo("1.57");
  }

  @Test
  @DisplayName("LocalDateTime serializes using ISO_DATE_TIME format")
  void localDateTime_serializesUsingIsoDateTimeFormat() {
    LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
    assertThat(gson.toJson(dt)).isEqualTo("\"2024-06-15T10:30:00\"");
  }

  @Test
  @DisplayName("LocalDateTime deserializes from ISO_DATE_TIME string")
  void localDateTime_deserializesFromIsoDateTimeString() {
    LocalDateTime result = gson.fromJson("\"2024-06-15T10:30:00\"", LocalDateTime.class);
    assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
  }

  @Test
  @DisplayName("Long field serializes as JSON string")
  void long_fieldSerializesAsJsonString() {
    record Wrapper(long id) {

    }
    String json = gson.toJson(new Wrapper(123456789L));
    assertThat(json).isEqualTo("{\"id\":\"123456789\"}");
  }
}
