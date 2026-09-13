package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.embedded.CacheConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContextHelper")
class ContextHelperTest {

  private ContextHelper contextHelper;

  @BeforeEach
  void setUp() {
    // TTL long enough that write-expiry never fires mid-test (mirrors CorrelationRegistryTest).
    CacheConfiguration config = new CacheConfiguration(300L, null);
    CacheProperties cacheProperties = new CacheProperties(Map.of(
        ContextHelper.CONTEXT_CACHE, config,
        ContextHelper.ADDITIONAL_CONTEXT_CACHE, config
    ));
    contextHelper = new ContextHelper(new CaffeineCacheHelper(cacheProperties));
  }

  private ContextDTO buildContext(String requestId, String clientId) {
    ContextDTO ctx = new ContextDTO();
    ctx.setRequestId(requestId);
    ctx.setClientId(clientId);
    return ctx;
  }

  @Nested
  @DisplayName("put(ContextDTO) and get(String)")
  class PutAndGet {

    @Test
    @DisplayName("stores a ContextDTO and retrieves it by requestId")
    void put_thenGet_returnsStoredContext() {
      ContextDTO ctx = buildContext("req-001", "client-A");
      contextHelper.put(ctx);

      ContextDTO result = contextHelper.get("req-001");
      assertThat(result).isNotNull();
      assertThat(result.getClientId()).isEqualTo("client-A");
    }

    @Test
    @DisplayName("returns null when requestId is not present")
    void get_missingRequestId_returnsNull() {
      assertThat(contextHelper.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("does not throw when null ContextDTO is put")
    void put_null_doesNotThrow() {
      contextHelper.put((ContextDTO) null);
      assertThat(contextHelper.size()).isZero();
    }

    @Test
    @DisplayName("overwrites existing entry when same requestId is put again")
    void put_sameRequestId_overwritesExisting() {
      ContextDTO first = buildContext("req-001", "client-A");
      ContextDTO second = buildContext("req-001", "client-B");
      contextHelper.put(first);
      contextHelper.put(second);

      assertThat(contextHelper.get("req-001").getClientId()).isEqualTo("client-B");
    }
  }

  @Nested
  @DisplayName("update(String, UnaryOperator)")
  class Update {

    @Test
    @DisplayName("applies the operator to the existing context and persists the result")
    void update_existingContext_appliesOperator() {
      ContextDTO ctx = buildContext("req-002", "client-A");
      contextHelper.put(ctx);

      contextHelper.update("req-002", existing -> {
        existing.setResponseCode("00");
        return existing;
      });

      assertThat(contextHelper.get("req-002").getResponseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("does nothing when the requestId is not present")
    void update_missingRequestId_doesNothing() {
      contextHelper.update("nonexistent", c -> c);
      assertThat(contextHelper.size()).isZero();
    }
  }

  @Nested
  @DisplayName("put(String, String) and getAdditionalData(String)")
  class AdditionalData {

    @Test
    @DisplayName("stores additional data and retrieves it as UTF-8 bytes")
    void putAdditional_thenGetAdditionalData_returnsBytes() {
      contextHelper.put("req-003", "{\"field\":\"value\"}");
      byte[] result = contextHelper.getAdditionalData("req-003");

      assertThat(result).isNotNull();
      assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    @DisplayName("returns null when requestId has no additional data stored")
    void getAdditionalData_missingKey_returnsNull() {
      assertThat(contextHelper.getAdditionalData("req-missing")).isNull();
    }
  }

  @Nested
  @DisplayName("list()")
  class ListContexts {

    @Test
    @DisplayName("returns empty list when no contexts are stored")
    void list_empty_returnsEmptyList() {
      assertThat(contextHelper.list()).isEmpty();
    }

    @Test
    @DisplayName("returns all stored contexts")
    void list_multipleContexts_returnsAll() {
      contextHelper.put(buildContext("req-A", "client-A"));
      contextHelper.put(buildContext("req-B", "client-B"));

      List<ContextDTO> result = contextHelper.list();
      assertThat(result).hasSize(2);
    }
  }

  @Nested
  @DisplayName("size()")
  class Size {

    @Test
    @DisplayName("returns 0 when no contexts are stored")
    void size_empty_returnsZero() {
      assertThat(contextHelper.size()).isZero();
    }

    @Test
    @DisplayName("increments with each new distinct context added")
    void size_afterAdding_incrementsCorrectly() {
      contextHelper.put(buildContext("req-1", "client-1"));
      assertThat(contextHelper.size()).isEqualTo(1);
      contextHelper.put(buildContext("req-2", "client-2"));
      assertThat(contextHelper.size()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("cleanUp(String)")
  class CleanUp {

    @Test
    @DisplayName("removes the context entry from the main map")
    void cleanUp_removesContextEntry() {
      contextHelper.put(buildContext("req-X", "client-X"));
      assertThat(contextHelper.get("req-X")).isNotNull();

      contextHelper.cleanUp("req-X");

      assertThat(contextHelper.get("req-X")).isNull();
    }

    @Test
    @DisplayName("removes the additional data entry for the same requestId")
    void cleanUp_removesAdditionalDataEntry() {
      contextHelper.put("req-X", "extra-data");
      assertThat(contextHelper.getAdditionalData("req-X")).isNotNull();

      contextHelper.cleanUp("req-X");

      assertThat(contextHelper.getAdditionalData("req-X")).isNull();
    }

    @Test
    @DisplayName("decrements size after cleanup")
    void cleanUp_decrementsSizeByOne() {
      contextHelper.put(buildContext("req-Y", "client-Y"));
      assertThat(contextHelper.size()).isEqualTo(1);

      contextHelper.cleanUp("req-Y");

      assertThat(contextHelper.size()).isZero();
    }

    @Test
    @DisplayName("does not throw when cleaning up a non-existent requestId")
    void cleanUp_missingKey_doesNotThrow() {
      contextHelper.cleanUp("no-such-key");
      assertThat(contextHelper.size()).isZero();
    }

    @Test
    @DisplayName("does not remove other contexts when cleaning a specific one")
    void cleanUp_onlyRemovesTargetEntry() {
      contextHelper.put(buildContext("req-keep", "client-keep"));
      contextHelper.put(buildContext("req-remove", "client-remove"));

      contextHelper.cleanUp("req-remove");

      assertThat(contextHelper.get("req-keep")).isNotNull();
      assertThat(contextHelper.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("does not touch MDC — HeaderFilter owns the request thread's MDC lifecycle, not this map cleanup")
    void cleanUp_doesNotClearMdc() {
      org.slf4j.MDC.put("traceId", "trace-should-survive");

      try {
        contextHelper.cleanUp("req-X");

        assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("trace-should-survive");
      } finally {
        org.slf4j.MDC.clear();
      }
    }
  }

  @Nested
  @DisplayName("leak detection — removal listener on the backing Caffeine caches")
  class LeakDetection {

    private CapturingAppender appender;
    private Logger contextHelperLogger;

    @BeforeEach
    void attachAppender() {
      appender = new CapturingAppender();
      appender.start();
      contextHelperLogger = (Logger) LogManager.getLogger(ContextHelper.class);
      contextHelperLogger.addAppender(appender);
      // Outside a Spring Boot-bootstrapped context (this is a plain unit test), Log4j2 falls back
      // to its default config, whose root level is ERROR — below that, log.warn(...) never
      // reaches any appender, including this one. Configurator.setLevel (unlike
      // Logger.setLevel, which can leave a stale cached effective level behind) reliably
      // propagates the change immediately.
      Configurator.setLevel(ContextHelper.class, Level.WARN);
    }

    @AfterEach
    void detachAppender() {
      contextHelperLogger.removeAppender(appender);
      Configurator.setLevel(ContextHelper.class, (Level) null);
      appender.stop();
    }

    @Test
    @DisplayName("logs a warning for RemovalCause.EXPIRED (cleanUp was skipped)")
    void logIfLeaked_expiredCause_logsWarning() {
      contextHelper.logIfLeaked(ContextHelper.CONTEXT_CACHE, "req-leaked", RemovalCause.EXPIRED);

      assertThat(appender.messages)
          .anyMatch(m -> m.contains("req-leaked") && m.contains(ContextHelper.CONTEXT_CACHE));
    }

    @Test
    @DisplayName("does not log for RemovalCause.EXPLICIT (the normal cleanUp path)")
    void logIfLeaked_explicitCause_doesNotLog() {
      contextHelper.logIfLeaked(ContextHelper.CONTEXT_CACHE, "req-normal", RemovalCause.EXPLICIT);

      assertThat(appender.messages).noneMatch(m -> m.contains("req-normal"));
    }
  }

  private static final class CapturingAppender extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    CapturingAppender() {
      super("context-helper-leak-capture", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      messages.add(event.getMessage().getFormattedMessage());
    }
  }
}
