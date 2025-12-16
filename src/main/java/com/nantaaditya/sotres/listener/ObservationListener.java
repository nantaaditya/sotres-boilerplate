package com.nantaaditya.sotres.listener;

import com.nantaaditya.sotres.model.constant.ObservationConstant;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ObservationListener implements ObservationHandler<Context> {

  @Override
  public boolean supportsContext(Context context) {
    return isEligibleToLog(context);
  }

  @Override
  public void onStart(Context context) {
    log.info("#Observation - start {}", context);
  }

  @Override
  public void onEvent(Event event, Context context) {
    log.info("#Observation - event {}", event);
  }

  @Override
  public void onError(Context context) {
    log.error("#Observation - error {}", context);
  }

  @Override
  public void onStop(Context context) {
    log.info("#Observation - stop {}", context);
  }

  private boolean isEligibleToLog(Context context) {
    return Set.of(ObservationConstant.values())
        .stream()
        .anyMatch(c -> c.getName().equals(context.getName()));
  }
}
