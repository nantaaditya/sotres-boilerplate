package com.nantaaditya.sotres.configuration;

import brave.baggage.BaggageField;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.nantaaditya.sotres.listener.ObservationListener;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.properties.LogProperties;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfiguration {

  @Bean
  public ObservationRegistry observationRegistry(LogProperties logProperties) {
    ObservationRegistry observationRegistry =  ObservationRegistry.create();
    observationRegistry
        .observationConfig()
        .observationHandler(new ObservationListener(logProperties));
    return observationRegistry;
  }

  @Bean
  public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
    return new ObservedAspect(observationRegistry);
  }

  @Bean
  public CurrentTraceContext currentTraceContext() {
    return ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.get())
        .build();
  }

  @Bean
  public BaggageField requestIdBaggage() {
    return BaggageField.create(HeaderConstant.REQUEST_ID.getHeader());
  }

  @Bean
  public BaggageField clientIdBaggage() {
    return BaggageField.create(HeaderConstant.CLIENT_ID.getHeader());
  }

}
