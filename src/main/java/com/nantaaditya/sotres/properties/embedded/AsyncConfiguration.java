package com.nantaaditya.sotres.properties.embedded;

import com.nantaaditya.sotres.model.constant.RejectionPolicy;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rejectionPolicy}: what happens once {@code maxPoolSize + queueCapacity} is exhausted.
 * {@link RejectionPolicy#CALLER_RUNS} (the default) guarantees the submitted task still runs —
 * safe for executors whose callers can tolerate stalling (e.g. an async audit-log write; losing
 * one is worse than a slow request). {@link RejectionPolicy#ABORT} rejects immediately instead —
 * required for any executor fed directly from a thread that must never block, such as the Netty
 * event loop (see {@code TransactionProcessorParticipant}/{@code TransactionResponseParticipant},
 * which both consume the {@code isoTransaction} executor and treat a rejection as a normal,
 * non-blocking shed path rather than an error).
 */
public record AsyncConfiguration(
    int corePoolSize,
    int maxPoolSize,
    int queueCapacity,
    int keepAliveSeconds,
    String threadNamePrefix,
    boolean virtualThreadEnabled,
    @DefaultValue("CALLER_RUNS") RejectionPolicy rejectionPolicy
) {

}
