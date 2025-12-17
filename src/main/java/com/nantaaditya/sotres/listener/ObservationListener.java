package com.nantaaditya.sotres.listener;

import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.LogProperties;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ObservationListener implements ObservationHandler<Context> {

  private final LogProperties logProperties;

  @Override
  public boolean supportsContext(Context context) {
    return logProperties.enableMetricLog() && isEligibleToLog(context);
  }

  @Override
  public void onStart(Context context) {
    log.info(AppLogMessage.message("#Observation - start").additionalData(createContext(context)));
  }

  @Override
  public void onEvent(Event event, Context context) {
    log.info(AppLogMessage.message("#Observation - event").additionalData(event));
  }

  @Override
  public void onError(Context context) {
    log.error(AppLogMessage.message("#Observation - error").additionalData(createContext(context)));
  }

  @Override
  public void onStop(Context context) {
    log.info(AppLogMessage.message("#Observation - stop").additionalData(createContext(context)));
  }

  private boolean isEligibleToLog(Context context) {
    return Set.of(ObservationConstant.values())
        .stream()
        .anyMatch(c -> c.getName().equals(context.getName()));
  }

  private Map<String, Object> createContext(Context context) {
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("name", context.getName());
    ctx.put("lowCardinalityKV", context.getLowCardinalityKeyValues());
    ctx.put("highCardinalityKV", context.getHighCardinalityKeyValues());
    return ctx;
  }
}
