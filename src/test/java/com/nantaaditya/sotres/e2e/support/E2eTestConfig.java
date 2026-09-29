package com.nantaaditya.sotres.e2e.support;

import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.strategy.transaction.AbstractTransactionHandler;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies a concrete {@link AbstractTransactionHandler} for the E2E harness.
 * The shipped codebase has none — {@code transactionHandlers} is injected empty —
 * so without this the forwarding pipeline is unreachable and every 0200 is
 * answered with DE39 92 (unable to route).
 */
@TestConfiguration
public class E2eTestConfig {

  public static final String SELECTOR_JSLT = "20.97-E001";
  public static final String SELECTOR_PASSTHROUGH = "20.98-E002";
  public static final String SELECTOR_DEAD_LETTER = "20.96-E003";

  @Bean
  AbstractTransactionHandler e2ePassThroughHandler() {
    return new PassThroughHandler();
  }

  private static final class PassThroughHandler extends AbstractTransactionHandler {

    @Override
    public Set<String> getSelectors() {
      return Set.of(SELECTOR_JSLT, SELECTOR_PASSTHROUGH, SELECTOR_DEAD_LETTER);
    }

    @Override
    protected ParticipantContext validate(ParticipantContext participantContext) {
      return participantContext;
    }

    @Override
    protected ParticipantContext process(ParticipantContext participantContext) {
      return participantContext;
    }
  }
}
