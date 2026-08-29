package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.RetryProcessorHelper;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService.DeadLetterContext;
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

    List<DeadLetterProcess> toRetry = getDeadLetterProcesses(request, pageRequest).stream()
        .filter(deadLetterProcess -> deadLetterProcess.getRetryCount() < deadLetterProcess.getMaxRetry())
        .toList();

    try {
      executeRetryProcess(request, toRetry);
      log.info(AppLogMessage.message("#Retry - [{}] [{}] total {} retry processed",
          request.processType(), request.processName(), toRetry.size()));
    } catch (Exception error) {
      log.error(AppLogMessage.message("#Retry - [{}] [{}] retry error",
          request.processType(), request.processName()).error(error));
    }
  }

  private List<DeadLetterProcess> getDeadLetterProcesses(RetryDeadLetterProcessRequest request,
      PageRequest pageRequest) {
    return deadLetterProcessRepository.findByProcessTypeAndProcessNameAndStatusIn(
        request.processType(), request.processName(),
        Set.of(RetryStatus.NEW.name(), RetryStatus.FAILED.name()),
        pageRequest);
  }

  public void executeRetryProcess(RetryDeadLetterProcessRequest request, List<DeadLetterProcess> deadLetterProcesses) {
    AbstractRetryProcessorService processor = retryProcessorHelper.getProcessor(request.processType(), request.processName());

    if (processor == null) {
      log.warn(AppLogMessage.message(
          "#DeadLetterProcess - no retry processor handler found with {} - {}",
          request.processType(), request.processName()));
      return;
    }

    processor.resetCounter();

    // Sequential retry (the reactive version fanned out at concurrency 4); a real adopter that
    // wires a producer + concrete RetryProcessorService can parallelise on the async executor.
    for (DeadLetterProcess deadLetterProcess : updateInProgress(deadLetterProcesses)) {
      if (!processor.isEligibleToBeRetried(deadLetterProcess)) {
        handleNotEligibleToBeRetried(deadLetterProcess, processor);
        continue;
      }
      DeadLetterContext result = processor.execute(deadLetterProcess);
      processor.update(deadLetterProcess, result);
    }
  }

  private void handleNotEligibleToBeRetried(DeadLetterProcess deadLetterProcess,
      AbstractRetryProcessorService processor) {
    deadLetterProcess.setStatus(RetryStatus.SUCCESS.name());
    deadLetterProcess.setUpdatedBy("internal-retry-process");
    deadLetterProcess.setUpdatedDate(LocalDateTime.now());
    deadLetterProcessRepository.save(deadLetterProcess);
    processor.getNotEligibleCounter().incrementAndGet();
    log.warn(AppLogMessage.message("#DeadLetterProccess - {} is not eligible to be retried", deadLetterProcess.getId()));
  }

  private List<DeadLetterProcess> updateInProgress(List<DeadLetterProcess> deadLetterProcesses) {
    deadLetterProcesses.forEach(d -> d.setStatus(RetryStatus.RETRYING.name()));
    return deadLetterProcessRepository.saveAll(deadLetterProcesses);
  }
}
