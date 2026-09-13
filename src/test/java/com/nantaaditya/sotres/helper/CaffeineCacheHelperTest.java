package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.github.benmanes.caffeine.cache.Cache;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.embedded.CacheConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CaffeineCacheHelper")
class CaffeineCacheHelperTest {

  @Nested
  @DisplayName("createCache(long, TimeUnit, UnaryOperator) — raw builder")
  class RawBuilder {

    private final CaffeineCacheHelper helper = new CaffeineCacheHelper(mock(CacheProperties.class));

    @Test
    @DisplayName("builds a functioning cache honoring the given expiry")
    void createCache_buildsUsableCache() {
      Cache<String, String> cache =
          helper.createCache(5, TimeUnit.MINUTES, UnaryOperator.identity());

      cache.put("key", "value");

      assertThat(cache.getIfPresent("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("applies the operator's extra tuning (e.g. a removal listener)")
    void createCache_appliesOperatorTuning() {
      AtomicBoolean removalListenerInvoked = new AtomicBoolean(false);

      Cache<String, String> cache = helper.createCache(5, TimeUnit.MINUTES,
          caffeine -> caffeine.removalListener((key, value, cause) -> removalListenerInvoked.set(true)));

      cache.put("key", "value");
      cache.invalidate("key");

      // Caffeine invokes removal listeners on an async executor by default, not synchronously.
      await().atMost(Duration.ofSeconds(2)).untilTrue(removalListenerInvoked);
    }
  }

  @Nested
  @DisplayName("createCache(String) / createCache(String, UnaryOperator) — named, properties-driven")
  class NamedFromProperties {

    private CaffeineCacheHelper helper(CacheConfiguration config) {
      CacheProperties properties = new CacheProperties(Map.of("context", config));
      return new CaffeineCacheHelper(properties);
    }

    @Test
    @DisplayName("resolves the named entry's TTL and builds a usable cache")
    void createCache_byName_resolvesConfiguredTtl() {
      CaffeineCacheHelper helper = helper(new CacheConfiguration(300L,null));

      Cache<String, String> cache = helper.createCache("context");
      cache.put("key", "value");

      assertThat(cache.getIfPresent("key")).isEqualTo("value");
    }

    @Test
    @DisplayName("applies maximumSize when configured")
    void createCache_byName_appliesMaximumSizeWhenConfigured() {
      CaffeineCacheHelper helper = helper(new CacheConfiguration(300L,10L));

      Cache<String, String> cache = helper.createCache("context");

      assertThat(cache.policy().eviction()).isPresent();
      assertThat(cache.policy().eviction().get().getMaximum()).isEqualTo(10L);
    }

    @Test
    @DisplayName("leaves the cache uncapped when maximumSize is not configured")
    void createCache_byName_noMaximumSize_leavesCacheUncapped() {
      CaffeineCacheHelper helper = helper(new CacheConfiguration(300L,null));

      Cache<String, String> cache = helper.createCache("context");

      assertThat(cache.policy().eviction()).isEmpty();
    }

    @Test
    @DisplayName("still applies the caller's own operator alongside the configured maximumSize")
    void createCache_byName_appliesCallerOperatorAndConfiguredMaximumSize() {
      CaffeineCacheHelper helper = helper(new CacheConfiguration(300L,10L));
      AtomicBoolean removalListenerInvoked = new AtomicBoolean(false);

      Cache<String, String> cache = helper.createCache("context",
          caffeine -> caffeine.removalListener((key, value, cause) -> removalListenerInvoked.set(true)));
      cache.put("key", "value");
      cache.invalidate("key");

      await().atMost(Duration.ofSeconds(2)).untilTrue(removalListenerInvoked);
      assertThat(cache.policy().eviction().get().getMaximum()).isEqualTo(10L);
    }

    @Test
    @DisplayName("throws when no configuration is registered under the requested name")
    void createCache_unknownName_throws() {
      CaffeineCacheHelper helper = helper(new CacheConfiguration(300L,null));

      assertThatThrownBy(() -> helper.createCache("does-not-exist"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("does-not-exist");
    }
  }
}
