package com.nantaaditya.sotres.configuration;

import brave.baggage.BaggageField;
import com.nantaaditya.sotres.listener.ObservationListener;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfiguration {

  @Bean
  public ObservationRegistry observationRegistry() {
    ObservationRegistry observationRegistry =  ObservationRegistry.create();
    observationRegistry
        .observationConfig()
        .observationHandler(new ObservationListener());
    return observationRegistry;
  }

  @Bean
  public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
    return new ObservedAspect(observationRegistry);
  }

  @Bean
  public BaggageField requestIdBaggage() {
    return BaggageField.create("reqId");
  }

}
