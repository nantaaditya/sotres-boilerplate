package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.EventLog;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.EventLogRepository;
import com.nantaaditya.sotres.service.internal.EventLogService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class EventLogServiceImpl implements EventLogService {

  private final EventLogRepository eventLogRepository;

  @Async("defaultAsyncTaskExecutor")
  @Override
  public void save(EventLog eventLog) {
    try {
      eventLogRepository.save(eventLog);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#EventLog - failed save event log").error(e));
    }
  }

  @Async("defaultAsyncTaskExecutor")
  @Override
  public void remove(int days) {
    int deleted = eventLogRepository.deleteByCreatedDateBefore(LocalDateTime.now().minusDays(days));
    log.info(AppLogMessage.message("#EventLog - removed {} obsolete event log(s) older than {} days", deleted, days));
  }
}
