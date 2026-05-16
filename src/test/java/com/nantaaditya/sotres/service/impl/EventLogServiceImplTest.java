package com.nantaaditya.sotres.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.ReactorHelper;
import com.nantaaditya.sotres.helper.SchedulerHelper;
import com.nantaaditya.sotres.repository.EventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

@DisplayName("EventLogServiceImpl")
@ExtendWith(MockitoExtension.class)
class EventLogServiceImplTest {

  @Mock
  private EventLogRepository eventLogRepository;
  @Mock
  private SchedulerHelper schedulerHelper;
  @Mock
  private ReactorHelper reactorHelper;
  @Mock
  private Scheduler scheduler;

  private EventLogServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new EventLogServiceImpl(eventLogRepository, schedulerHelper, reactorHelper);
  }

  @Test
  @DisplayName("remove_returnsTrueAndSchedulesBackgroundDeletion")
  void remove_returnsTrueAndSchedulesBackgroundDeletion() {
    when(schedulerHelper.from("default-async")).thenReturn(scheduler);

    StepVerifier.create(service.remove(7))
        .expectNext(Boolean.TRUE)
        .verifyComplete();

    verify(reactorHelper).runBackgroundTask(
        eq("remove_obsolete_event_log"),
        any(),
        eq(scheduler)
    );
  }
}
