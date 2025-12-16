package com.nantaaditya.sotres.properties.embedded;

public record IsoMessageLogConfiguration(
    boolean defaultLogHandlerEnabled,
    boolean maskingEnabled,
    boolean fieldDescriptionEnabled
) {

}
