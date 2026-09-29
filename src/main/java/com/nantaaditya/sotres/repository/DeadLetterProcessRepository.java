package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DeadLetterProcessRepository
    extends ListCrudRepository<DeadLetterProcess, Long>,
        PagingAndSortingRepository<DeadLetterProcess, Long> {

  /**
   * Bulk-deletes as a single {@code DELETE} statement (JPA {@code @Modifying} bulk semantics) —
   * see {@link EventLogRepository#deleteByCreatedDateBefore} for why this matters. Returns the
   * number of rows removed.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("delete from DeadLetterProcess d where d.createdDate < :date and d.status = :status")
  int deleteByCreatedDateBeforeAndStatus(@Param("date") LocalDateTime dateTime, @Param("status") String status);

  List<DeadLetterProcess> findByProcessTypeAndProcessNameAndStatusIn(String processType,
      String processName, Set<String> statuses, Pageable pageable);
}
