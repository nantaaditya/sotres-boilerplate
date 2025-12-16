package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.helper.RetryProcessorHelper;
import com.nantaaditya.sotres.model.internal.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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
        .delayUntil(deadLetterProcesses -> executeRetryProcess(request, deadLetterProcesses)
            .doOnSuccess(result -> log.info("#Retry - [{}] [{}] total {} retry processed",
                request.processType(), request.processName(), deadLetterProcesses.size())
            )
            .doOnError(error -> log.error("#Retry - [{}] [{}] total {} retry error, {} cause {}",
                request.processType(), request.processName(), error.getMessage(), ErrorHelper.getRootCause(error))
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
