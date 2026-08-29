package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditorConfiguration implements AuditorAware<String> {

  @Value("${spring.application.name}")
  private String applicationName;

  @Autowired
  private TracerHelper tracerHelper;

  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.of(
        Optional.ofNullable(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID))
            .orElse(applicationName));
  }
}
