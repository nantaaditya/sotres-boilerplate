package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.BulkheadPoolConfiguration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(BulkheadProperties.PREFIX)
public record BulkheadProperties(
    Map<String, BulkheadPoolConfiguration> configurations
) {

  public static final String PREFIX = "apps.bulkhead";

  public BulkheadPoolConfiguration getConfiguration(String name) {
    return configurations.get(name);
  }

  public Set<String> getBeanNames(String postfix) {
    Set<String> result = new HashSet<>();
    if (configurations == null || configurations.isEmpty()) return result;

    for (Map.Entry<String, BulkheadPoolConfiguration> entry : configurations.entrySet()) {
      result.add(entry.getKey() + postfix);
    }
    return result;
  }
}
