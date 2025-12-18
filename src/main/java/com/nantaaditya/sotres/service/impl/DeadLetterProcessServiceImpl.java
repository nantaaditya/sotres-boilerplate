package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.RetryProcessorHelper;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
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

  @Override
  public Mono<Void> remove(int days) {
    LocalDateTime now = LocalDateTime.now(DateTimeHelper.ZONE_ID);
    return deadLetterProcessRepository.deleteByProcessedIsTrueAndCreatedDateBefore(now.minusDays(days));
  }

  @Override
  public Mono<Void> retry(RetryDeadLetterProcessRequest request) {
    PageRequest pageRequest = PageRequest.of(0, request.size(),
        Sort.by(Direction.ASC, "createdDate"));

    return getDeadLetterProcesses(request, pageRequest)
        .collectList()
        .doOnSuccess(deadLetterProcesses -> executeRetryProcess(request, deadLetterProcesses)
            .doOnSuccess(result -> log.info(AppLogMessage.message(
                "#Retry - [{}] [{}] total {} retry processed",
                request.processType(), request.processName(), deadLetterProcesses.size()))
            )
            .doOnError(error -> log.error(AppLogMessage.message("#Retry - [{}] [{}] total {} retry error",
                request.processType(), request.processName(), error.getMessage()).error(error))
            )
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.debug(AppLogMessage.message("#Retry - dead letter process [{}] [{}] success",
                    request.processType(), request.processName())),
                error -> log.error(AppLogMessage.message("#Retry - dead letter process [{}] [{}] error",
                    request.processType(), request.processName())
                    .error(error)),
                () -> log.info(AppLogMessage.message("#Retry - dead letter process [{}] [{}] done",
                    request.processType(), request.processName()))
            )
        )
        .then();
  }

  private Flux<DeadLetterProcess> getDeadLetterProcesses(RetryDeadLetterProcessRequest request,
      PageRequest pageRequest) {
    return deadLetterProcessRepository.findByProcessTypeAndProcessNameAndProcessed(
        request.processType(), request.processName(), false, pageRequest);
  }

  public Mono<Void> executeRetryProcess(RetryDeadLetterProcessRequest request, List<DeadLetterProcess> deadLetterProcesses) {
    AbstractRetryProcessorService processor = retryProcessorHelper.getProcessor(request.processType(), request.processName());
    return processor.execute(deadLetterProcesses);
  }
}
