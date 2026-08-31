package com.nantaaditya.sotres.helper;

import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Request-scoped holder for the API {@link Observation}. Servlet MVC has no Reactor context, so the
 * observation started by {@code HeaderFilter} is stashed on a request attribute and read back in the
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
}
