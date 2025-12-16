package com.nantaaditya.sotres.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class R2DBCAuditorConfiguration implements ReactiveAuditorAware<String> {

  @Value("${spring.application.name}")
  private String applicationName;

  @Override
  public Mono<String> getCurrentAuditor() {
    return Mono.just(applicationName);
  }
}
