package com.nantaaditya.sotres.properties.embedded;

public record BulkheadPoolConfiguration(
    int permits,
    boolean fair
) {

}
