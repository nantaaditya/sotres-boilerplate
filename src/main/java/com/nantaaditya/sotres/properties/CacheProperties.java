package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.CacheConfiguration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Named Caffeine cache configurations, the same "configure once under a key, look up by name"
 * shape as {@link AsyncTaskProperties}/{@link BulkheadProperties} — a component wanting a cache
 * declares which named entry under {@code apps.cache.configurations} it uses instead of hardcoding
 * TTL/size values, so tuning stays in one place ({@code application.yml}) for every cache in the
 * app.
 */
@ConfigurationProperties(CacheProperties.PREFIX)
public record CacheProperties(
    Map<String, CacheConfiguration> configurations
) {

  public static final String PREFIX = "apps.cache";

  public CacheConfiguration getConfiguration(String name) {
    return configurations.get(name);
  }
}
