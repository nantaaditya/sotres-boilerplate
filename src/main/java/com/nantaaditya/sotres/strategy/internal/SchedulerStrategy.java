package com.nantaaditya.sotres.strategy.internal;

import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import reactor.core.scheduler.Scheduler;

public interface SchedulerStrategy {
  Scheduler createScheduler(SchedulerConfiguration schedulerConfiguration);
}