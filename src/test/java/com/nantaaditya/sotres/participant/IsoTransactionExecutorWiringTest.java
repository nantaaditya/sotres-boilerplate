package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code TransactionProcessorParticipant} and {@code TransactionResponseParticipant} must not
 * share an executor bean — a slow transaction (JSLT + JDBC + outbound REST) queued ahead of a
 * fast response completion can starve {@code EnhancedIsoClient.send()} callers into a false
 * timeout. See docs/POST_MIGRATION_REMEDIATION_PLAN.md Phase 3C.
 */
@DisplayName("ISO transaction executor wiring")
class IsoTransactionExecutorWiringTest {

  @Test
  @DisplayName("TransactionProcessorParticipant and TransactionResponseParticipant use distinct executor beans")
  void processorAndResponseParticipants_useDistinctExecutorBeans() {
    assertThat(TransactionProcessorParticipant.ISO_TRANSACTION_EXECUTOR)
        .isNotEqualTo(TransactionResponseParticipant.ISO_TRANSACTION_EXECUTOR);
  }
}
