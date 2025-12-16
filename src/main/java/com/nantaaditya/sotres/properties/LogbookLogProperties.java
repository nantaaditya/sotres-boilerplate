package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.helper.StringHelper;
import java.beans.Transient;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "apps.log")
public record LogbookLogProperties(
    String sensitiveField
) {
  @Transient
  public Set<String> getSensitiveFields() {
    return (Set<String>) StringHelper.toCollection(sensitiveField, ",", HashSet.class);
  }
}