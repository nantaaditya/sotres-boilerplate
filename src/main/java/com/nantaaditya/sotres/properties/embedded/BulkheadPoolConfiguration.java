package com.nantaaditya.sotres.properties.embedded;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code permits}: explicit permit count. Leave unset (null) to derive it instead from the
 * {@link AsyncConfiguration} sharing this bulkhead's key — {@code maxPoolSize + queueCapacity +
 * headroom} — so the bulkhead can never itself be the bottleneck ahead of the executor's own
 * admission control, and the two never need to be hand-tuned in sync.
 *
 * <p>{@code headroom}: extra permits added on top of the derived pool capacity. Ignored when
 * {@code permits} is set explicitly.
 */
public record BulkheadPoolConfiguration(
    Integer permits,
    @DefaultValue("0") int headroom,
    boolean fair
) {

}
