package com.nantaaditya.sotres.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.nantaaditya.sotres.helper.ReactorHelper;
import com.nantaaditya.sotres.repository.EventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@DisplayName("EventLogServiceImpl")
@ExtendWith(MockitoExtension.class)
class EventLogServiceImplTest {

  @Mock
  private EventLogRepository eventLogRepository;
  @Mock
  private ReactorHelper reactorHelper;

  private EventLogServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new EventLogServiceImpl(eventLogRepository, reactorHelper);
  }

  @Test
  @DisplayName("remove_returnsTrueAndSchedulesBackgroundDeletion")
  void remove_returnsTrueAndSchedulesBackgroundDeletion() {
    StepVerifier.create(service.remove(7))
        .expectNext(Boolean.TRUE)
        .verifyComplete();

    verify(reactorHelper).runBackgroundTask(
        eq("remove_obsolete_event_log"),
        any(),
        eq(Schedulers.boundedElastic())
    );
  }
}
