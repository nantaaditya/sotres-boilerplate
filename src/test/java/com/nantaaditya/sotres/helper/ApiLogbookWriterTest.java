package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.Precorrelation;

@DisplayName("ApiLogbookWriter")
@ExtendWith(MockitoExtension.class)
class ApiLogbookWriterTest {

  @Mock
  private Precorrelation precorrelation;
  @Mock
  private Correlation correlation;

  private ApiLogbookWriter writer;

  @BeforeEach
  void setUp() {
    writer = new ApiLogbookWriter(new ObjectMapper());
  }

  @Test
  @DisplayName("isActive always returns true")
  void isActive_alwaysReturnsTrue() {
    assertThat(writer.isActive()).isTrue();
  }

  @Test
  @DisplayName("write request with valid JSON does not throw")
  void write_request_withValidJson_doesNotThrow() {
    when(precorrelation.getId()).thenReturn("corr-req-001");

    String requestJson = """
        {
          "method": "POST",
          "uri": "http://localhost/api/payment",
          "headers": {"content-type": ["application/json"]},
          "body": null,
          "protocol": "HTTP/1.1"
        }
        """;

    assertThatCode(() -> writer.write(precorrelation, requestJson))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("write response with valid JSON does not throw")
  void write_response_withValidJson_doesNotThrow() {
    when(correlation.getId()).thenReturn("corr-resp-001");

    String responseJson = """
        {
          "status": 200,
          "duration": 123,
          "headers": {"content-type": ["application/json"]},
          "body": null,
          "protocol": "HTTP/1.1"
        }
        """;

    assertThatCode(() -> writer.write(correlation, responseJson))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("write request with JSON body does not throw")
  void write_request_withBodyJson_doesNotThrow() {
    when(precorrelation.getId()).thenReturn("corr-body-001");

    String requestJson = """
        {
          "method": "POST",
          "uri": "http://localhost/api/payment",
          "headers": {"content-type": ["application/json"]},
          "body": {"amount": 10000, "currency": "IDR"},
          "protocol": "HTTP/1.1"
        }
        """;

    assertThatCode(() -> writer.write(precorrelation, requestJson))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("write request with invalid JSON does not throw due to internal error catch")
  void write_request_withInvalidJson_doesNotThrowDueToInternalCatch() throws IOException {
    assertThatCode(() -> writer.write(precorrelation, "not-valid-json"))
        .doesNotThrowAnyException();
  }
}
