package com.nantaaditya.sotres.service.internal;

import reactor.core.publisher.Mono;

public interface EventLogService {
  Mono<Boolean> remove(int days);
}
