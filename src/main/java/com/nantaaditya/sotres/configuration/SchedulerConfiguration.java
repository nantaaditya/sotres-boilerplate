package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.factory.SchedulerHelperFactoryBean;
import com.nantaaditya.sotres.properties.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SchedulerConfiguration {

  private final SchedulerProperties schedulerProperties;

  @Bean
  public SchedulerHelperFactoryBean schedulerHelper() {
    return new SchedulerHelperFactoryBean(schedulerProperties);
  }
}