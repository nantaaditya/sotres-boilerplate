package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code apps.retry.configurations.<name>}. Each entry produces one
 * {@code RetryTemplate} bean named {@code <name>RetryTemplate} in {@code RetryTemplateConfiguration}.
 */
@ConfigurationProperties("apps.retry")
public record RetryProperties(
    Map<String, RetryConfiguration> configurations
) {

  /** @return {@code <name> + postfix} for every configured entry (bean-name helper for logging). */
  public Set<String> getBeanNames(String postfix) {
    Set<String> result = new HashSet<>();
    if (configurations == null || configurations.isEmpty()) {
      return result;
    }
    for (String key : configurations.keySet()) {
      result.add(key + postfix);
    }
    return result;
  }
}
