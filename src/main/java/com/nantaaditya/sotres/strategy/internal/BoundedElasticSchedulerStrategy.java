package com.nantaaditya.sotres.strategy.internal;

import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import java.util.Optional;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class BoundedElasticSchedulerStrategy implements SchedulerStrategy {

  @Override
  public Scheduler createScheduler(SchedulerConfiguration schedulerConfiguration) {
    return Optional.ofNullable(schedulerConfiguration)
        .map(SchedulerConfiguration::getBoundedElastic)
        .map(configuration -> Schedulers.newBoundedElastic(
            configuration.corePoolSize(),
            configuration.queueSize(),
            configuration.name(),
            configuration.ttlInSeconds(),
            configuration.daemon()
        ))
        .orElseThrow(() -> new IllegalArgumentException("bounded elastic scheduler configuration is null"));
  }
}