package com.nantaaditya.sotres.properties.embedded;

public record IsoMessageNetworkConfiguration(
    int reconnectInterval,
    int timeOut,
    boolean scheduledEchoEnabled,
    int echoInterval
) {
}
