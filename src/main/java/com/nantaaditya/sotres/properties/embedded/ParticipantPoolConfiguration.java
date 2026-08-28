package com.nantaaditya.sotres.properties.embedded;

public record ParticipantPoolConfiguration(
    int corePoolSize,
    int queueSize,
    int flightPool,
    int messagePool,
    int flightQueueTimeOut,
    int messageQueueTimeOut,
    String prefix
) {

}
