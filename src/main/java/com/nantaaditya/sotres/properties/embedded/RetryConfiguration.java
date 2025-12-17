package com.nantaaditya.sotres.properties.embedded;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.extern.log4j.Log4j2;

@Log4j2
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
          log.info(AppLogMessage.message("#Retry - class not extends Throwable, skipping: {}", clazz));
          continue;
        }

        Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) clazz; //NOSONAR
        maps.put(throwableClass, Boolean.valueOf(token[1])); //NOSONAR
      } catch (ClassNotFoundException ex) {
        log.error(AppLogMessage.message("#Retry - could not load retry exception map {}",
            ex.getMessage()).error(ex));
      }
    }
    return maps;
  }
}
