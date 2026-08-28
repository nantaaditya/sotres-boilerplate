package com.nantaaditya.sotres.helper;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Request-scoped holder for the API {@link Observation}. Servlet MVC has no Reactor context, so the
 * observation started in the filter is stashed on a request attribute and read back in the
 * controller / exception handler on the same request thread.
 */
@Component
public class ObservationWrapper {

  private static final String REQUEST_ATTR = "com.nantaaditya.sotres.observation";

  public void setObservation(HttpServletRequest request, Observation observation) {
    if (request != null) {
      request.setAttribute(REQUEST_ATTR, observation);
    }
  }

  public Observation getObservation(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object value = request.getAttribute(REQUEST_ATTR);
    return value instanceof Observation o ? o : null;
  }

  public void clear(HttpServletRequest request) {
    if (request != null) {
      request.removeAttribute(REQUEST_ATTR);
    }
  }

  /**
   * Propagates the current Micrometer tracing context to an async thread. Use for hand-offs with no
   * originating request; the observation is not carried — use {@link #wrap(Runnable,
   * HttpServletRequest)} when a request is available.
   */
  public Runnable wrap(Runnable runnable) {
    ContextSnapshot snapshot = captureSnapshot();
    return () -> {
      try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        runnable.run();
      }
    };
  }

  /**
   * Propagates both the Micrometer tracing context and the request's observation to an async thread,
   * opening the observation as a scope on the worker so metrics/traces stay attributed to the
   * originating request.
   */
  public Runnable wrap(Runnable runnable, HttpServletRequest request) {
    ContextSnapshot snapshot = captureSnapshot();
    Observation observation = getObservation(request);
    return () -> {
      try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
        if (observation != null) {
          try (Observation.Scope obsScope = observation.openScope()) {
            runnable.run();
          }
        } else {
          runnable.run();
        }
      }
    };
  }

  private ContextSnapshot captureSnapshot() {
    return ContextSnapshot.captureAll(); //NOSONAR
  }
}
