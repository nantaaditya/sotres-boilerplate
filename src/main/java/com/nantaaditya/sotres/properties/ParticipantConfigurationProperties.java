package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code participant.configuration.pool} — a map of {@link ManagerConstant} to its
 * {@link ParticipantPoolConfiguration}.
 */
@ConfigurationProperties("participant.configuration")
public record ParticipantConfigurationProperties(
    Map<ManagerConstant, ParticipantPoolConfiguration> pool
) {

  /**
   * Resolve and validate the pool for {@code poolName}. Call at wiring time so a bad config fails
   * fast rather than producing a nonsensical correlation window at runtime.
   *
   * @param poolName which pool to resolve
   * @return the validated configuration
   * @throws IllegalArgumentException if the pool is missing, or its grace window
   *     ({@code messageQueueTimeOut}) is shorter than its real-timeout window
   *     ({@code flightQueueTimeOut})
   */
  public ParticipantPoolConfiguration getPool(ManagerConstant poolName) {
    ParticipantPoolConfiguration configuration = pool.get(poolName);

    if (configuration == null) {
      throw new IllegalArgumentException(String.format("pool %s not configured", poolName));
    }

    if (configuration.messageQueueTimeOut() < configuration.flightQueueTimeOut()) {
      throw new IllegalArgumentException(String.format("invalid pool timeout %s configuration", poolName));
    }

    return configuration;
  }
}
