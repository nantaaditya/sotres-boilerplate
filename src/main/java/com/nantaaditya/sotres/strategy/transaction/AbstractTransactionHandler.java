package com.nantaaditya.sotres.strategy.transaction;

import com.nantaaditya.sotres.model.dto.ParticipantContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractTransactionHandler {

  public abstract Set<String> getSelectors();

  protected abstract ParticipantContext validate(ParticipantContext participantContext);

  protected abstract ParticipantContext process(ParticipantContext participantContext);

  public void populateResponse(ParticipantContext participantContext) {
    // override on transaction handler child class
  }

  public ParticipantContext execute(ParticipantContext participantContext) {
    return process(validate(participantContext));
  }
}
