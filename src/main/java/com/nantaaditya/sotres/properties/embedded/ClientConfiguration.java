package com.nantaaditya.sotres.properties.embedded;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public record ClientConfiguration(
    String hostname,
    int maxConnections,
    int maxIdleTime,
    int maxLifeTime,
    int evictInBackground,
    int pendingAcquireTimeOut,
    int clientConnectTimeOut,
    int clientReadTimeOut,
    int clientWriteTimeOut,
    TimeUnit timeUnit,
    RetryConfiguration retryConfiguration
) {

  public boolean isNeedRetryable() {
    return Optional.ofNullable(this)
        .map(ClientConfiguration::retryConfiguration)
        .filter(Objects::nonNull)
        .isPresent();
  }
}
