package com.nantaaditya.sotres.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.model.dto.RetryHistoryContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Log4j2
@Getter
public abstract class AbstractRetryProcessorService {

  protected DeadLetterProcessRepository deadLetterProcessRepository;
  protected ObjectMapper objectMapper;

  @Getter
  private final AtomicInteger successCounter = new AtomicInteger(0);
  @Getter
  private final AtomicInteger failedCounter = new AtomicInteger(0);
  @Getter
  private final AtomicInteger notEligibleCounter = new AtomicInteger(0);

  protected AbstractRetryProcessorService(DeadLetterProcessRepository deadLetterProcessRepository,
      ObjectMapper objectMapper) {
    this.deadLetterProcessRepository = deadLetterProcessRepository;
    this.objectMapper = objectMapper;
  }

  public abstract String getProcessType();
  public abstract String getProcessName();
  public abstract boolean isEligibleToBeRetried(DeadLetterProcess deadLetterProcess);
  public abstract Mono<DeadLetterContext> execute(DeadLetterProcess deadLetterProcess);
  public abstract void onSuccess(DeadLetterProcess deadLetterProcess, String response);
  public abstract void onError(DeadLetterProcess deadLetterProcess, Throwable throwable);

  public final void resetCounter() {
    successCounter.setRelease(0);
    failedCounter.setRelease(0);
    notEligibleCounter.setRelease(0);
  }

  public final <T> Mono<DeadLetterProcess> update(DeadLetterProcess deadLetterProcess, DeadLetterContext deadLetterContext) {
    if (deadLetterContext.success()) {
      onSuccess(deadLetterProcess, deadLetterContext.response());
      deadLetterProcess.setStatus(RetryStatus.SUCCESS.name());
      successCounter.incrementAndGet();
    } else {
      onError(deadLetterProcess, deadLetterContext.throwable());
      boolean isMaxRetry = deadLetterProcess.getRetryCount() + 1 >= deadLetterProcess.getMaxRetry();
      deadLetterProcess.setStatus(isMaxRetry ? RetryStatus.EXHAUSTED.name() : RetryStatus.FAILED.name());
      failedCounter.incrementAndGet();
    }

    Optional.ofNullable(deadLetterContext.throwable())
        .ifPresent(t -> deadLetterProcess.setLastError(t.getMessage()));
    updateRetryHistories(deadLetterProcess, deadLetterContext.response(), deadLetterContext.throwable());
    deadLetterProcess.setRetryCount(deadLetterProcess.getRetryCount() + 1);
    deadLetterProcess.setUpdatedBy("internal-retry-process");
    deadLetterProcess.setUpdatedDate(LocalDateTime.now());
    return deadLetterProcessRepository.save(deadLetterProcess);
  }

  private <T> void updateRetryHistories(DeadLetterProcess deadLetterProcess, String response,
      Throwable throwable) {
    try {
      List<RetryHistoryContext> retryHistories = objectMapper.readValue(deadLetterProcess.getRetryHistories(),
          new TypeReference<List<RetryHistoryContext>>(){});
      retryHistories.add(new RetryHistoryContext(
          deadLetterProcess.getRetryCount() + 1,
          Optional.ofNullable(response).orElse(null),
          Optional.ofNullable(throwable).map(Throwable::getMessage).orElse(null)
      ));
      deadLetterProcess.setRetryHistories(objectMapper.writeValueAsBytes(retryHistories));
    } catch (IOException e) {
      log.error(AppLogMessage.message("#Retry - failed to update retry histories {}", deadLetterProcess.getId()).error(e));
    }
  }

  public record DeadLetterContext (
      boolean success,
      String response,
      Throwable throwable
  ) {}
}
