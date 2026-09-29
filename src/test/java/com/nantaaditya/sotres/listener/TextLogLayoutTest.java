package com.nantaaditya.sotres.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextLogLayout")
class TextLogLayoutTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final TextLogLayout layout = TextLogLayout.createLayout("test-app");

  @Test
  @DisplayName("formats a plain (non-AppLogMessage) message with application, level, requestId, and trace fields")
  void toSerializable_plainMessage_containsExpectedFields() {
    SortedArrayStringMap contextData = new SortedArrayStringMap();
    contextData.putValue(HeaderConstant.REQUEST_ID.getHeader(), "req-1");
    contextData.putValue("traceId", "trace-1");
    contextData.putValue("spanId", "span-1");

    LogEvent event = Log4jLogEvent.newBuilder()
        .setLoggerName("com.example.Foo")
        .setLevel(Level.INFO)
        .setMessage(new SimpleMessage("hello world"))
        .setThreadName("main")
        .setTimeMillis(System.currentTimeMillis())
        .setContextData(contextData)
        .build();

    String result = layout.toSerializable(event);

    assertThat(result)
        .contains("[test-app]")
        .contains("main")
        .contains("INFO")
        .contains("requestId: [req-1]")
        .contains("trace: [trace-1-span-1]")
        .contains("com.example.Foo")
        .contains("hello world")
        .endsWith(System.lineSeparator());
  }

  @Test
  @DisplayName("formats an AppLogMessage as pretty-printed JSON containing the interpolated message")
  void toSerializable_appLogMessage_prettyPrintsJson() throws Exception {
    LogEvent event = Log4jLogEvent.newBuilder()
        .setLoggerName("com.example.Bar")
        .setLevel(Level.WARN)
        .setMessage(AppLogMessage.message("#Test - {}", "value"))
        .setThreadName("worker-1")
        .setTimeMillis(System.currentTimeMillis())
        .setContextData(new SortedArrayStringMap())
        .build();

    String result = layout.toSerializable(event);
    String json = result.substring(result.indexOf('{'), result.lastIndexOf('}') + 1);
    JsonNode node = MAPPER.readTree(json);

    assertThat(node.get("formattedMessage").asText()).isEqualTo("#Test - value");
    assertThat(result).contains("WARN").contains("worker-1");
  }

  @Test
  @DisplayName("missing MDC keys render as literal null placeholders, not an exception")
  void toSerializable_noMdc_doesNotThrow() {
    LogEvent event = Log4jLogEvent.newBuilder()
        .setLoggerName("com.example.Baz")
        .setLevel(Level.ERROR)
        .setMessage(new SimpleMessage("no context"))
        .setThreadName("main")
        .setTimeMillis(System.currentTimeMillis())
        .setContextData(new SortedArrayStringMap())
        .build();

    String result = layout.toSerializable(event);

    assertThat(result)
        .contains("requestId: [null]")
        .contains("trace: [null-null]");
  }
}
