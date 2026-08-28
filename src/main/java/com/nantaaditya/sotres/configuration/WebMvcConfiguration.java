package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.interceptor.EventLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

  private final EventLogInterceptor eventLogInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(eventLogInterceptor).order(Ordered.LOWEST_PRECEDENCE);
  }
}
