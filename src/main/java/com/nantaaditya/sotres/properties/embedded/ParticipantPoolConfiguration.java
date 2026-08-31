package com.nantaaditya.sotres.properties.embedded;

/**
 * One entry under {@code participant.configuration.pool.<name>} — the timing windows for
 * ISO8583 request/response correlation. See {@code CorrelationRegistry}. Both windows are TTL-only
 * (no size cap); concurrency is bounded by the {@code isoTransaction} bulkhead {@code Semaphore}.
 *
 * @param flightQueueTimeOut   real-timeout window (ms): how long a caller waits for the matching
 *                             reply before it is a timeout; also the bulkhead acquire timeout
 * @param messageQueueTimeOut  grace window (ms): a reply after {@code flightQueueTimeOut} but
 *                             within this window is {@code LATE_RESPONSE}, not {@code ORPHAN}.
 *                             Must be {@code >= flightQueueTimeOut}
 */
public record ParticipantPoolConfiguration(
    int flightQueueTimeOut,
    int messageQueueTimeOut
) {

}
