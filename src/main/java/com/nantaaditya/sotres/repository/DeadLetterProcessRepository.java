package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DeadLetterProcessRepository extends R2dbcRepository<DeadLetterProcess, Long> {
  @Transactional
  Mono<Void> deleteByProcessedIsTrueAndCreatedDateBefore(LocalDateTime dateTime);

  Flux<DeadLetterProcess> findByProcessTypeAndProcessNameAndProcessed(String processType,
      String processName, boolean processed, Pageable pageable);
}
