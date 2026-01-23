package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.MessageFactoryHelper;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class PackagerConfiguration {

  @Bean
  public MessageFactoryHelper messageFactoryHelper() {
    return new MessageFactoryHelper(PackagerConstant.DEFAULT);
  }
}
