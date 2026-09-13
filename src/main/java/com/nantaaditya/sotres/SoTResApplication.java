package com.nantaaditya.sotres;

import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.BulkheadProperties;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.RetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(value = {
    AsyncTaskProperties.class,
    BulkheadProperties.class,
    CacheProperties.class,
    ClientProperties.class,
    IsoMessageProperties.class,
    LogProperties.class,
    ParticipantConfigurationProperties.class,
    RetryProperties.class
})
@EnableScheduling
@EnableAsync
public class SoTResApplication {

  public static void main(String[] args) {
    SpringApplication.run(SoTResApplication.class, args);
  }

}
