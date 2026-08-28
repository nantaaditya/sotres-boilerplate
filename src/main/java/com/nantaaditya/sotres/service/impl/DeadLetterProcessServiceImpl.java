package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.RetryProcessorHelper;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Log4j2
@Service
@RequiredArgsConstructor
public class DeadLetterProcessServiceImpl implements DeadLetterProcessService {

  private final DeadLetterProcessRepository deadLetterProcessRepository;
  private final RetryProcessorHelper retryProcessorHelper;

  @Async("defaultAsyncTaskExecutor")
  @Override
  public void remove(int days) {
    LocalDateTime now = LocalDateTime.now(DateTimeHelper.ZONE_ID);
    deadLetterProcessRepository.deleteByCreatedDateBeforeAndStatus(
        now.minusDays(days), RetryStatus.EXHAUSTED.name());
  }

  @Async("defaultAsyncTaskExecutor")
  @Override
  public void retry(RetryDeadLetterProcessRequest request) {
    PageRequest pageRequest = PageRequest.of(0, request.size(),
        Sort.by(Direction.ASC, "createdDate"));

    List<DeadLetterProcess> toRetry = getDeadLetterProcesses(request, pageRequest)
        .filter(deadLetterProcess -> deadLetterProcess.getRetryCount() < deadLetterProcess.getMaxRetry())
        .collectList()
        .blockOptional()
        .orElseGet(List::of);

    try {
      executeRetryProcess(request, toRetry).block();
      log.info(AppLogMessage.message("#Retry - [{}] [{}] total {} retry processed",
          request.processType(), request.processName(), toRetry.size()));
    } catch (Exception error) {
      log.error(AppLogMessage.message("#Retry - [{}] [{}] retry error",
          request.processType(), request.processName()).error(error));
    }
  }

  private Flux<DeadLetterProcess> getDeadLetterProcesses(RetryDeadLetterProcessRequest request,
      PageRequest pageRequest) {
    // TODO(refactor): blocking JDBC query wrapped for the still-reactive chain
    return Mono.fromCallable(() -> deadLetterProcessRepository.findByProcessTypeAndProcessNameAndStatusIn(
            request.processType(), request.processName(),
            Set.of(RetryStatus.NEW.name(), RetryStatus.FAILED.name()),
            pageRequest))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
  }

  public Mono<Void> executeRetryProcess(RetryDeadLetterProcessRequest request, List<DeadLetterProcess> deadLetterProcesses) {
    AbstractRetryProcessorService processor = retryProcessorHelper.getProcessor(request.processType(), request.processName());

    if (processor == null) {
      log.warn(AppLogMessage.message(
          "#DeadLetterProcess - no retry processor handler found with {} - {}",
          request.processType(), request.processName()));
      return Mono.empty();
    }

    processor.resetCounter();

    return updateInProgress(deadLetterProcesses)
        .filter(deadLetterProcess -> {
            if (!processor.isEligibleToBeRetried(deadLetterProcess)) {
              handleNotEligibleToBeRetried(deadLetterProcess, processor);
              return false;
            }
            return true;
        })
        .flatMap(
            deadLetterProcess -> processor.execute(deadLetterProcess)
                .flatMap(result -> processor.update(deadLetterProcess, result))
            , 4
        )
        .then();
  }

  private void handleNotEligibleToBeRetried(DeadLetterProcess deadLetterProcess,
      AbstractRetryProcessorService processor) {
    deadLetterProcess.setStatus(RetryStatus.SUCCESS.name());
    deadLetterProcess.setUpdatedBy("internal-retry-process");
    deadLetterProcess.setUpdatedDate(LocalDateTime.now());
    deadLetterProcessRepository.save(deadLetterProcess); // TODO(refactor): blocking JDBC save
    processor.getNotEligibleCounter().incrementAndGet();
    log.warn(AppLogMessage.message("#DeadLetterProccess - {} is not eligible to be retried", deadLetterProcess.getId()));
  }

  private Flux<DeadLetterProcess> updateInProgress(List<DeadLetterProcess> deadLetterProcesses) {
    deadLetterProcesses
        .forEach(d -> d.setStatus(RetryStatus.RETRYING.name()));
    // TODO(refactor): blocking JDBC saveAll wrapped for the still-reactive chain
    return Mono.fromCallable(() -> deadLetterProcessRepository.saveAll(deadLetterProcesses))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
  }
}
