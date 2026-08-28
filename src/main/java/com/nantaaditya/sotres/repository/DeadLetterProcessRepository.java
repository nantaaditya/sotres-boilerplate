package com.nantaaditya.sotres.repository;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DeadLetterProcessRepository
    extends ListCrudRepository<DeadLetterProcess, Long>,
        PagingAndSortingRepository<DeadLetterProcess, Long> {

  @Transactional
  void deleteByCreatedDateBeforeAndStatus(LocalDateTime dateTime, String status);

  List<DeadLetterProcess> findByProcessTypeAndProcessNameAndStatusIn(String processType,
      String processName, Set<String> statuses, Pageable pageable);
}
