package com.nantaaditya.sotres.properties.embedded;

/**
 * {@code expireAfterWriteSeconds}: write-expiry TTL — the sole eviction driver by default.
 *
 * <p>{@code maximumSize}: leave unset (null) for a TTL-only cache — appropriate for per-request
 * tracking caches where an early size eviction could drop a still-in-flight entry (mirrors
 * {@code CorrelationRegistry}'s TTL-only design, see its Javadoc). Set it only for a cache where a
 * bounded footprint matters more than never evicting early.
 */
public record CacheConfiguration(
    Long expireAfterWriteSeconds,
    Long maximumSize
) {

}
