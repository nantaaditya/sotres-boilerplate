package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.EventLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EventLogRepository extends ListCrudRepository<EventLog, String> {

  /**
   * Bulk-deletes as a single {@code DELETE} statement (JPA {@code @Modifying} bulk semantics) —
   * a derived {@code deleteBy...} method here would instead load every matching row into the
   * persistence context and remove them one at a time, which is unsafe for this high-volume
   * audit table. Returns the number of rows removed.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("delete from EventLog e where e.createdDate < :date")
  int deleteByCreatedDateBefore(@Param("date") LocalDateTime date);
}
