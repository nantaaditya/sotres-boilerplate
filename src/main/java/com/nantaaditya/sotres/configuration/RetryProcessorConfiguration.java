package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.factory.RetryProcessorHelperFactory;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class RetryProcessorConfiguration {

  private final ApplicationContext applicationContext;

  @Bean
  public RetryProcessorHelperFactory retryProcessorHelperFactory() {
    RetryProcessorHelperFactory factory = new RetryProcessorHelperFactory();

    Map<String, AbstractRetryProcessorService> processors = applicationContext.getBeansOfType(AbstractRetryProcessorService.class)
      .entrySet()
      .stream()
        .collect(Collectors.toMap(
            e -> e.getValue().getProcessType() + "|" + e.getValue().getProcessName(),
            Entry::getValue)
        );

    factory.setRetryProcessorServices(processors);
    return factory;
  }

}
