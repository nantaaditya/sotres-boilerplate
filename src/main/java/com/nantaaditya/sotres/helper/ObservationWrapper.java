package com.nantaaditya.sotres.helper;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import org.springframework.stereotype.Component;

@Component
public class ObservationWrapper {
  private ThreadLocal<Observation> observationThreadLocal = new ThreadLocal<>();

  public void setObservation(Observation observation) {
    observationThreadLocal.set(observation);
  }

  public Observation getObservation() {
    return observationThreadLocal.get();
  }

  public void clear() {
    observationThreadLocal.remove();
  }

  public Runnable wrap(Runnable runnable) {
    ContextSnapshot snapshot = captureSnapshot();
    return () -> {
      try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        runnable.run();
      }
    };
  }

  private ContextSnapshot captureSnapshot() {
    return ContextSnapshot.captureAll();
  }
}
