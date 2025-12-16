package com.nantaaditya.sotres.configuration;

import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import com.solab.iso8583.IsoMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomHealthConfiguration implements HealthIndicator {

  private final Iso8583Client<IsoMessage> iso8583Client;
  private final HealthCheckHelper healthCheckHelper;

  @Override
  public Health health() {
    Health.Builder status = iso8583Client.isConnected() ? Health.up() : Health.down();

    return status
        .withDetail("healthy", healthCheckHelper.isHealthy())
        .withDetail("signOn", healthCheckHelper.isSignedOn())
        .withDetail("connected", iso8583Client.isConnected())
        .build();
  }
}
