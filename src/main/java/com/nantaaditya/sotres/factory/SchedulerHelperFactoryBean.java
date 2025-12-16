package com.nantaaditya.sotres.factory;

import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.helper.SchedulerHelper;
import com.nantaaditya.sotres.model.constant.SchedulerType;
import com.nantaaditya.sotres.properties.SchedulerProperties;
import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import com.nantaaditya.sotres.strategy.internal.BoundedElasticSchedulerStrategy;
import com.nantaaditya.sotres.strategy.internal.ExecutorSchedulerStrategy;
import com.nantaaditya.sotres.strategy.internal.ParallelSchedulerStrategy;
import com.nantaaditya.sotres.strategy.internal.SchedulerStrategy;
import com.nantaaditya.sotres.strategy.internal.SingleSchedulerStrategy;
import com.nantaaditya.sotres.strategy.internal.ThreadPoolSchedulerStrategy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class SchedulerHelperFactoryBean implements FactoryBean<SchedulerHelper> {

  private final SchedulerProperties schedulerProperties;
  private Map<SchedulerType, SchedulerStrategy> schedulerStrategies = new HashMap<>();
  
  public SchedulerHelperFactoryBean(SchedulerProperties schedulerProperties) {
    this.schedulerProperties = schedulerProperties;
    loadSchedulerStrategies();
  }

  private void loadSchedulerStrategies() {
    for (SchedulerType schedulerType : SchedulerType.values()) {
      SchedulerStrategy schedulerStrategy = switch (schedulerType) {
        case SINGLE -> new SingleSchedulerStrategy();
        case PARALLEL -> new ParallelSchedulerStrategy();
        case BOUNDED_ELASTIC -> new BoundedElasticSchedulerStrategy();
        case EXECUTOR -> new ExecutorSchedulerStrategy();
        case THREAD_POOL -> new ThreadPoolSchedulerStrategy();
      };
      schedulerStrategies.put(schedulerType, schedulerStrategy);
    }
  }

  @Override
  public Class<?> getObjectType() {
    return SchedulerHelper.class;
  }

  @Override
  public SchedulerHelper getObject() throws Exception {
    if (schedulerProperties.configurations() == null) {
      return new SchedulerHelperImpl(Collections.emptyMap());
    }

    Map<String, Scheduler> schedulers = new HashMap<>();
    schedulerProperties.configurations()
        .forEach((name, configurations) ->
            schedulers.put(name, buildScheduler(name, configurations))
        );

    return new SchedulerHelperImpl(schedulers);
  }

  private record SchedulerHelperImpl(Map<String, Scheduler> schedulers) implements SchedulerHelper {
      @Override
      public Scheduler from(String name) {
        return schedulers.getOrDefault(name, Schedulers.immediate());
      }
    }

  private Scheduler buildScheduler(String name, SchedulerConfiguration configuration) {
    Scheduler scheduler = null;
    try {
      SchedulerStrategy schedulerStrategy = schedulerStrategies.get(configuration.getSchedulerType());
      scheduler = schedulerStrategy.createScheduler(configuration);
    } catch (Exception e) {
      log.error("#Scheduler - failed to build scheduler {}, error {}, cause {}",
          name, e.getMessage(), ErrorHelper.getRootCause(e));
    } finally {
      return scheduler;
    }
  }
}