package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.embedded.CacheConfiguration;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaffeineCacheHelper {

  private final CacheProperties cacheProperties;

  public <K, V> Cache<K, V> createCache(long timeOut, TimeUnit timeUnit,
      UnaryOperator<Caffeine<K, V>> operator) {
    return createCache(timeOut, timeUnit, null, operator);
  }

  public <K, V> Cache<K, V> createCache(String name) {
    return createCache(name, UnaryOperator.identity());
  }

  public <K, V> Cache<K, V> createCache(String name, UnaryOperator<Caffeine<K, V>> operator) {
    CacheConfiguration config = resolveConfiguration(name);
    return createCache(
        config.expireAfterWriteSeconds(),
        TimeUnit.SECONDS,
        config.maximumSize(),
        operator
    );
  }

  private <K, V> Cache<K, V> createCache(Long ttl, TimeUnit timeUnit, Long size,
      UnaryOperator<Caffeine<K, V>> operator) {
    Caffeine<K, V> caffeine = (Caffeine<K, V>) (Caffeine<?, ?>) Caffeine.newBuilder();

    if (ttl != null && ttl > 0) {
      caffeine = caffeine.expireAfterWrite(ttl, timeUnit);
    }

    if (size != null && size > 0) {
      caffeine = caffeine.maximumSize(size);
    }

    return operator
        .apply(caffeine)
        .build();
  }


  private CacheConfiguration resolveConfiguration(String name) {
    CacheConfiguration config = cacheProperties.getConfiguration(name);
    if (config == null) {
      throw new IllegalStateException(
          "No cache configuration found for name '" + name + "' under " + CacheProperties.PREFIX + ".configurations");
    }
    return config;
  }
}
