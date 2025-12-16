package com.nantaaditya.sotres.properties.embedded;

import com.nantaaditya.sotres.helper.ErrorHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record RetryConfiguration(
    int maxAttempt,
    int minBackOff,
    String retryableExceptions
) {

  public boolean isRetryable(Class<? extends Throwable> throwable) {
    return getRetryableExceptionMaps().getOrDefault(throwable, false);
  }

  public Map<Class<? extends Throwable>, Boolean> getRetryableExceptionMaps() {
    if (retryableExceptions == null) {
      return new HashMap<>();
    }

    StringTokenizer tokens = new StringTokenizer(retryableExceptions, ",");
    Map<Class<? extends Throwable>, Boolean> maps = new HashMap<>();

    while (tokens.hasMoreTokens()) {
      String[] token = tokens.nextToken().split(":");
      try {
        Class<?> clazz = Class.forName(token[0]);
        if (!Throwable.class.isAssignableFrom(clazz)) {
          log.info("#Retry - class not extends Throwable, skipping: {}", clazz);
          continue;
        }

        Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) clazz; //NOSONAR
        maps.put(throwableClass, Boolean.valueOf(token[1])); //NOSONAR
      } catch (ClassNotFoundException ex) {
        log.error("#Retry - could not load retry exception map, error {}, {}",
            ex.getMessage(), ErrorHelper.getRootCause(ex));
      }
    }
    return maps;
  }
}
