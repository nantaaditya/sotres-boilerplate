package com.nantaaditya.sotres.strategy.transaction;

import com.nantaaditya.sotres.model.dto.ParticipantContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public abstract class AbstractTransactionHandler {

  public abstract Set<String> getSelectors();

  protected abstract Mono<ParticipantContext> validate(ParticipantContext participantContext);

  protected abstract Mono<ParticipantContext> process(ParticipantContext participantContext);

  public Mono<ParticipantContext> execute(ParticipantContext participantContext) {
    return validate(participantContext)
        .flatMap(this::process);
  }
}
