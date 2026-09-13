package com.nantaaditya.sotres.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.entity.EventLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the retention-delete repository methods behave correctly against real Postgres: only
 * rows matching the cutoff are removed, non-matching rows survive, and the returned count is
 * accurate. {@code @Modifying @Query("delete from ...")} is a JPA bulk statement by specification
 * (unlike a derived {@code deleteBy...} method under plain JPA, which loads matches into the
 * persistence context and removes them one at a time) — see docs/POST_MIGRATION_REMEDIATION_PLAN.md
 * Phase 4.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PersistenceBulkDeleteTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("e2e/init.sql");

  @Autowired
  private EventLogRepository eventLogRepository;
  @Autowired
  private DeadLetterProcessRepository deadLetterProcessRepository;
  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("EventLogRepository.deleteByCreatedDateBefore removes only older rows and returns the count")
  void eventLogRepository_deleteByCreatedDateBefore_removesOnlyOlderRows() {
    LocalDateTime cutoff = LocalDateTime.now();
    entityManager.persist(eventLog(cutoff.minusDays(1)));
    entityManager.persist(eventLog(cutoff.minusHours(1)));
    EventLog survivor = entityManager.persist(eventLog(cutoff.plusDays(1)));
    entityManager.flush();

    int deleted = eventLogRepository.deleteByCreatedDateBefore(cutoff);

    assertThat(deleted).isEqualTo(2);
    assertThat(eventLogRepository.findAll())
        .extracting(EventLog::getId)
        .containsExactly(survivor.getId());
  }

  @Test
  @DisplayName("DeadLetterProcessRepository.deleteByCreatedDateBeforeAndStatus removes only matching rows")
  void deadLetterProcessRepository_deleteByCreatedDateBeforeAndStatus_removesOnlyMatchingRows() {
    LocalDateTime cutoff = LocalDateTime.now();
    entityManager.persist(deadLetter(cutoff.minusDays(1), "DONE"));
    entityManager.persist(deadLetter(cutoff.minusDays(1), "NEW")); // older but wrong status
    entityManager.persist(deadLetter(cutoff.plusDays(1), "DONE")); // right status, too new
    entityManager.flush();

    int deleted = deadLetterProcessRepository.deleteByCreatedDateBeforeAndStatus(cutoff, "DONE");

    assertThat(deleted).isEqualTo(1);
    assertThat(deadLetterProcessRepository.findAll())
        .extracting(DeadLetterProcess::getStatus)
        .containsExactlyInAnyOrder("NEW", "DONE");
  }

  private EventLog eventLog(LocalDateTime createdDate) {
    // id intentionally left null — @TimeSeriesId's TsidGenerator assigns it on persist
    return EventLog.builder().createdDate(createdDate).method("GET").path("/x").build();
  }

  private DeadLetterProcess deadLetter(LocalDateTime createdDate, String status) {
    DeadLetterProcess d = new DeadLetterProcess();
    d.setCreatedDate(createdDate);
    d.setStatus(status);
    d.setProcessType("client");
    d.setProcessName("test");
    d.setMaxRetry(1);
    return d;
  }
}
