package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class R2DBCAuditorConfiguration implements ReactiveAuditorAware<String> {

  @Value("${spring.application.name}")
  private String applicationName;

  @Autowired
  private TracerHelper tracerHelper;

  @Override
  public Mono<String> getCurrentAuditor() {
    String auditor = Optional.ofNullable(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID))
        .orElse(applicationName);
    return Mono.just(auditor);
  }
}
