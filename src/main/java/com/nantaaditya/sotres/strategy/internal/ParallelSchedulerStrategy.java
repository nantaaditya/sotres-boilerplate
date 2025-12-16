package com.nantaaditya.sotres.strategy.internal;

import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import java.util.Optional;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class ParallelSchedulerStrategy implements SchedulerStrategy {

  @Override
  public Scheduler createScheduler(SchedulerConfiguration schedulerConfiguration) {
    return Optional.ofNullable(schedulerConfiguration)
        .map(SchedulerConfiguration::getParallel)
        .map(configuration -> Schedulers.newParallel(configuration.name(), configuration.corePoolSize(), configuration.daemon()))
        .orElseThrow(() -> new IllegalArgumentException("parallel Scheduler not found!"));
  }
}