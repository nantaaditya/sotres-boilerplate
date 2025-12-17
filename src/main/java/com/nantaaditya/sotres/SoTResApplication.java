package com.nantaaditya.sotres;

import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.SchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableConfigurationProperties(value = {
    AsyncTaskProperties.class,
    ClientProperties.class,
    IsoMessageProperties.class,
    LogProperties.class,
    ParticipantConfigurationProperties.class,
    SchedulerProperties.class
})
@EnableScheduling
@EnableAsync
@EnableR2dbcAuditing
public class SoTResApplication {

  public static void main(String[] args) {
    Hooks.enableAutomaticContextPropagation();
    SpringApplication.run(SoTResApplication.class, args);
  }

}
