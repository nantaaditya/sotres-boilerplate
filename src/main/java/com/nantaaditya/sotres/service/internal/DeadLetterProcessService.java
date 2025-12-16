package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.model.internal.RetryDeadLetterProcessRequest;
import reactor.core.publisher.Mono;

public interface DeadLetterProcessService {
    Mono<Void> remove(int days);

    Mono<Void> retry(RetryDeadLetterProcessRequest request);
}
