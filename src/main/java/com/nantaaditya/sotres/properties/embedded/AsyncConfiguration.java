package com.nantaaditya.sotres.properties.embedded;

public record AsyncConfiguration(
    int corePoolSize,
    int maxPoolSize,
    int queueCapacity,
    int keepAliveSeconds,
    String threadNamePrefix
) {

}
