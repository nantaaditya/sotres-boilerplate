package com.nantaaditya.sotres.properties.embedded;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public record ParticipantPoolConfiguration(
    int corePoolSize,
    int queueSize,
    String prefix
) {

  public Scheduler createScheduler() {
    return Schedulers.newBoundedElastic(corePoolSize, queueSize, prefix);
  }
}
