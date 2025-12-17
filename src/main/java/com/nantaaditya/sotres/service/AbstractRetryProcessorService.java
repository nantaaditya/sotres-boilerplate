package com.nantaaditya.sotres.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.SchedulerHelper;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Getter
public abstract class AbstractRetryProcessorService {

  protected DeadLetterProcessRepository deadLetterProcessRepository;
  protected ObjectMapper objectMapper;
  protected Scheduler scheduler;

  protected AbstractRetryProcessorService(DeadLetterProcessRepository deadLetterProcessRepository,
      ObjectMapper objectMapper, SchedulerHelper schedulerHelper) {
    this.deadLetterProcessRepository = deadLetterProcessRepository;
    this.objectMapper = objectMapper;
    this.scheduler = schedulerHelper.from("retry-processor");
  }

  public abstract String getProcessType();
  public abstract String getProcessName();
  protected abstract Mono<DeadLetterProcess> doProcess(DeadLetterProcess deadLetterProcess);

  protected int getParallelism() {
    return 10;
  }

  public Mono<Void> execute(List<DeadLetterProcess> deadLetterProcesses) {
    return Flux.fromIterable(deadLetterProcesses)
      .flatMap(deadLetterProcess -> update(deadLetterProcess), getParallelism())
      .flatMap(deadLetterProcess -> doProcess(deadLetterProcess), getParallelism())
      .subscribeOn(scheduler)
      .then();
  }

  private Mono<DeadLetterProcess> update(DeadLetterProcess deadLetterProcess) {
    deadLetterProcess.setProcessed(true);
    deadLetterProcess.setUpdatedBy("internal-retry-process");
    deadLetterProcess.setUpdatedDate(LocalDateTime.now());
    return deadLetterProcessRepository.save(deadLetterProcess);
  }

}
