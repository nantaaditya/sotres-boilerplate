package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.EventLog;
import java.time.LocalDateTime;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EventLogRepository extends ListCrudRepository<EventLog, String> {
  @Transactional
  void deleteByCreatedDateBefore(LocalDateTime date);
}
