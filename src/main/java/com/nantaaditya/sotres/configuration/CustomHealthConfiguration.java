package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.EnhancedIsoClient;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomHealthConfiguration implements HealthIndicator {

  private final EnhancedIsoClient enhancedIsoClient;
  private final HealthCheckHelper healthCheckHelper;

  @Override
  public Health health() {
    Health.Builder status = enhancedIsoClient.isConnected() ? Health.up() : Health.down();

    return status
        .withDetail("healthy", healthCheckHelper.isHealthy())
        .withDetail("signOn", healthCheckHelper.isSignedOn())
        .withDetail("connected", enhancedIsoClient.isConnected())
        .build();
  }
}
