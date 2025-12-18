package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.helper.ReactorHelper;
import com.nantaaditya.sotres.helper.SchedulerHelper;
import com.nantaaditya.sotres.repository.EventLogRepository;
import com.nantaaditya.sotres.service.internal.EventLogService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
@RequiredArgsConstructor
public class EventLogServiceImpl implements EventLogService {

  private final EventLogRepository eventLogRepository;
  private final SchedulerHelper schedulerHelper;
  private final ReactorHelper reactorHelper;

  @Override
  public Mono<Boolean> remove(int days) {
    return Mono.just(Boolean.TRUE)
        .doOnNext(result -> reactorHelper.runBackgroundTask(
            "remove_obsolete_event_log",
            () -> eventLogRepository.deleteByCreatedDateBefore(LocalDateTime.now().minusDays(days)),
            schedulerHelper.from("default-async")
            )
        );
  }
}
