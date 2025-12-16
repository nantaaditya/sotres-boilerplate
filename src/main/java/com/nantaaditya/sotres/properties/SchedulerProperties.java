package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.SchedulerConfiguration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("apps.scheduler")
public record SchedulerProperties(
    Map<String, SchedulerConfiguration> configurations
) {

}