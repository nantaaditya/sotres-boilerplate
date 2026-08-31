package com.nantaaditya.sotres.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.model.constant.RetryConstant;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.model.dto.RetryHistoryContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Handles an outbound call that has exhausted its retry budget. When
 * {@code apps.retry.configurations.<name>.dead-letter-enabled} is {@code true} and
 * {@code max-attempt > 1}, a fresh {@code dead_letter_process} row is persisted
 * ({@code status = NEW}, {@code retryCount = 0}) so the dead-letter processor works from its own
 * {@code maxRetry} budget; otherwise the exhaustion is only logged.
 *
 * <p>One instance per {@code apps.retry.configurations.<name>} entry, registered on that entry's
 * {@code RetryTemplate} in {@code RetryTemplateConfiguration}.
 */
@Log4j2
public class RestSenderRetryListener implements RetryListener {

  private static final String PROCESS_TYPE_CLIENT = "client";

  private final String name;
  private final int maxAttempts;
  private final boolean deadLetterEnabled;
  private final DeadLetterProcessRepository deadLetterProcessRepository;
  private final ObjectMapper objectMapper;

  public RestSenderRetryListener(String name, int maxAttempts, boolean deadLetterEnabled,
      DeadLetterProcessRepository deadLetterProcessRepository, ObjectMapper objectMapper) {
    this.name = name;
    this.maxAttempts = maxAttempts;
    this.deadLetterEnabled = deadLetterEnabled;
    this.deadLetterProcessRepository = deadLetterProcessRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
      Throwable throwable) {
    if (throwable == null || context.getRetryCount() < maxAttempts) {
      return; // succeeded, or stopped before the attempt budget was spent
    }

    int attempts = context.getRetryCount();
    String requestId = str(context, RetryConstant.REQUEST_ID);

    if (maxAttempts <= 1 || !deadLetterEnabled) {
      log.warn(AppLogMessage.message(
          "#Retry - [{}] call failed after {} attempt(s); not dead-lettered "
              + "(maxAttempts={}, deadLetterEnabled={}) requestId={}",
          name, attempts, maxAttempts, deadLetterEnabled, requestId).error(throwable));
      return;
    }

    try {
      deadLetterProcessRepository.save(buildDeadLetter(context, throwable));
      log.warn(AppLogMessage.message(
          "#Retry - [{}] exhausted after {} attempt(s); dead-lettered requestId={}",
          name, attempts, requestId).error(throwable));
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Retry - [{}] failed to persist dead letter", name).error(e));
    }
  }

  private DeadLetterProcess buildDeadLetter(RetryContext context, Throwable throwable) {
    DeadLetterProcess deadLetter = new DeadLetterProcess();
    deadLetter.setProcessType(strOrDefault(context, RetryConstant.PROCESS_TYPE, PROCESS_TYPE_CLIENT));
    deadLetter.setProcessName(str(context, RetryConstant.PROCESS_NAME));
    deadLetter.setIdempotencyKey(str(context, RetryConstant.REQUEST_ID));
    deadLetter.setClientName(strOrDefault(context, RetryConstant.CLIENT_NAME, name));
    deadLetter.setMethod(str(context, RetryConstant.METHOD));
    deadLetter.setPath(str(context, RetryConstant.PATH));
    deadLetter.setHeaders(headersJson(context));
    deadLetter.setPayload(payloadBytes(context));
    deadLetter.setRetryCount(0);
    deadLetter.setMaxRetry(maxAttempts);
    deadLetter.setStatus(RetryStatus.NEW.name());
    deadLetter.setLastError(throwable.getMessage());
    deadLetter.setRetryHistories(retryHistoriesBytes(context, throwable));
    return deadLetter;
  }

  private String headersJson(RetryContext context) {
    Object headers = context.getAttribute(RetryConstant.HEADERS.key());
    if (!(headers instanceof HttpHeaders httpHeaders)) {
      return null;
    }
    try {
      Map<String, List<String>> flat = new LinkedHashMap<>(httpHeaders);
      return objectMapper.writeValueAsString(flat);
    } catch (Exception e) {
      log.warn(AppLogMessage.message("#Retry - [{}] could not serialise headers", name).error(e));
      return null;
    }
  }

  private byte[] payloadBytes(RetryContext context) {
    Object request = context.getAttribute(RetryConstant.REQUEST.key());
    if (request == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsBytes(request);
    } catch (Exception e) {
      log.warn(AppLogMessage.message("#Retry - [{}] could not serialise payload", name).error(e));
      return null;
    }
  }

  private byte[] retryHistoriesBytes(RetryContext context, Throwable throwable) {
    try {
      List<RetryHistoryContext> histories = List.of(new RetryHistoryContext(
          0,
          str(context, RetryConstant.RESPONSE),
          throwable.getMessage()));
      return objectMapper.writeValueAsBytes(histories);
    } catch (Exception e) {
      log.warn(AppLogMessage.message("#Retry - [{}] could not serialise retry histories", name).error(e));
      return null;
    }
  }

  private static String str(RetryContext context, RetryConstant key) {
    Object value = context.getAttribute(key.key());
    return value instanceof String s ? s : null;
  }

  private static String strOrDefault(RetryContext context, RetryConstant key, String fallback) {
    String value = str(context, key);
    return value != null ? value : fallback;
  }
}
