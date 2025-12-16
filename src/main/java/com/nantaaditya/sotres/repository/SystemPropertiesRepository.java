package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.SystemProperties;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SystemPropertiesRepository extends R2dbcRepository<SystemProperties, Long> {
  Flux<SystemProperties> findByGroupId(String groupId);
}
