package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("participant.clientConfiguration")
public record ParticipantConfigurationProperties(
    Map<ManagerConstant, ParticipantPoolConfiguration> pool
) {

  public ParticipantPoolConfiguration getPool(ManagerConstant poolName) {
    return pool.get(poolName);
  }
}
