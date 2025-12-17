package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.helper.StringHelper;
import java.beans.Transient;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

@ConfigurationProperties(value = "apps.log")
public record LogProperties(
    boolean enableTraceLog,
    boolean enableMetricLog,
    boolean enableApiLog,
    String sensitiveField,
    String logStyle,
    String ignoredPath
) {

  @Transient
  public boolean isSensitiveFields(String key) {
    return getSensitiveFields()
        .stream()
        .anyMatch(field -> field.equals(key));
  }

  @Transient
  public Set<String> getSensitiveFields() {
    return (Set<String>) StringHelper.toCollection(sensitiveField, ",", HashSet.class);
  }

  @Transient
  public boolean isIgnoredPath(String path) {
    AntPathMatcher matcher = new AntPathMatcher();
    boolean isMatch = false;

    for (String pattern : getIgnoredPaths()) {
      isMatch = matcher.match(pattern, path);
      if (isMatch) break;
    }
    return isMatch;
  }

  @Transient
  public Set<String> getIgnoredPaths() {
    return (Set<String>) StringHelper.toCollection(ignoredPath, ",", HashSet.class);
  }
}