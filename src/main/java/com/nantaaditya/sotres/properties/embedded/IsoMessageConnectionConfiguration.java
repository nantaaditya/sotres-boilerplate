package com.nantaaditya.sotres.properties.embedded;

public record IsoMessageConnectionConfiguration(
    String host,
    int port,
    int workerThreadCount,
    String forwardingInstitutionId
) {

}
