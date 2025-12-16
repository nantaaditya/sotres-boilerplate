package com.nantaaditya.sotres.strategy.internal;

import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import java.util.Optional;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class SingleSchedulerStrategy implements SchedulerStrategy {

  @Override
  public Scheduler createScheduler(SchedulerConfiguration schedulerConfiguration) {
    return Optional.ofNullable(schedulerConfiguration)
        .map(SchedulerConfiguration::getSingle)
        .map(configuration -> Schedulers.newSingle(configuration.name(), configuration.daemon()))
        .orElseThrow(() -> new IllegalArgumentException("scheduler configuration is null"));
  }
}