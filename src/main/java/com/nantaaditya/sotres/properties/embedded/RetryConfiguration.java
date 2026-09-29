package com.nantaaditya.sotres.properties.embedded;

import com.nantaaditya.sotres.model.constant.BackoffPolicyConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;

/**
 * One entry under {@code apps.retry.configurations.<name>}. Backs a single
 * {@code org.springframework.retry.support.RetryTemplate} built in {@code RetryTemplateConfiguration}.
 *
 * <p>{@code retryableExceptions} is a comma-separated list of {@code <fqcn>:<boolean>} pairs;
 * {@code true} => whitelist (retry on it), {@code false} => blacklist (never retry on it).
 * Subclass matching is handled by the retry policy, not here.
 *
 * <p>{@code deadLetterEnabled} controls whether an exhausted call is persisted to
 * {@code dead_letter_process} ({@code true}) or only logged ({@code false}).
 */
@Log4j2
public record RetryConfiguration(
    BackoffPolicyConstant type,
    long initialInterval,
    double multiplier,
    long maxInterval,
    int maxAttempt,
    boolean deadLetterEnabled,
    String retryableExceptions
) {

  /** Parsed exception lists are stable for a given spec string; parse (and {@code Class.forName}) once. */
  private static final ConcurrentHashMap<String, List<Class<? extends Throwable>>> PARSE_CACHE =
      new ConcurrentHashMap<>();

  public List<Class<? extends Throwable>> getWhitelistedExceptions() {
    return getRetryableExceptionList(true);
  }

  public List<Class<? extends Throwable>> getBlacklistedExceptions() {
    return getRetryableExceptionList(false);
  }

  private List<Class<? extends Throwable>> getRetryableExceptionList(boolean isRetryable) {
    if (retryableExceptions == null || retryableExceptions.isBlank()) {
      return Collections.emptyList();
    }
    return PARSE_CACHE.computeIfAbsent(isRetryable + "|" + retryableExceptions,
        k -> parse(isRetryable));
  }

  private List<Class<? extends Throwable>> parse(boolean isRetryable) {
    StringTokenizer tokens = new StringTokenizer(retryableExceptions, ",");
    List<Class<? extends Throwable>> list = new ArrayList<>();
    while (tokens.hasMoreTokens()) {
      String[] token = tokens.nextToken().trim().split(":");
      if (token.length != 2) {
        continue;
      }

      try {
        Class<?> clazz = Class.forName(token[0].trim());
        if (!Throwable.class.isAssignableFrom(clazz)) {
          log.info(AppLogMessage.message("#Retry - class not extends Throwable, skipping: {}", clazz));
          continue;
        }

        if (Boolean.parseBoolean(token[1].trim()) == isRetryable) {
          @SuppressWarnings("unchecked")
          Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) clazz;
          list.add(throwableClass);
        }
      } catch (ClassNotFoundException ex) {
        log.error(AppLogMessage.message("#Retry - could not load retry exception {}", ex.getMessage())
            .error(ex));
      }
    }
    return List.copyOf(list);
  }
}
