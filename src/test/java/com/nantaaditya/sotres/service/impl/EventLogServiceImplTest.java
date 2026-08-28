package com.nantaaditya.sotres.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.nantaaditya.sotres.repository.EventLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("EventLogServiceImpl")
@ExtendWith(MockitoExtension.class)
class EventLogServiceImplTest {

  @Mock
  private EventLogRepository eventLogRepository;

  private EventLogServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new EventLogServiceImpl(eventLogRepository);
  }

  @Test
  @DisplayName("remove deletes event logs older than (now - days)")
  void remove_deletesEventLogsOlderThanCutoff() {
    service.remove(7);

    verify(eventLogRepository).deleteByCreatedDateBefore(argThat(cutoff ->
        cutoff.isBefore(LocalDateTime.now().minusDays(6))
            && cutoff.isAfter(LocalDateTime.now().minusDays(8))));
  }
}
