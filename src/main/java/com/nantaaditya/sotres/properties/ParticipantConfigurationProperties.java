package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("participant.configuration")
public record ParticipantConfigurationProperties(
    Map<String, ParticipantPoolConfiguration> pool
) {

  public ParticipantPoolConfiguration getPool(String poolName) {
    return pool.get(poolName);
  }
}
