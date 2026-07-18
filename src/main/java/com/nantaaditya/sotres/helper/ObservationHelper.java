package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.ApiFeatureConstant;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.dto.TransactionException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ObservationHelper {

  private static final String REQUEST_ID = "requestId";
  private static final String FEATURE = "feature";
  private static final String RESPONSE_CODE = "responseCode";
  private static final String ERROR = "error";

  private ObservationHelper() {}

  public static void createIsoContext(Observation observation, String rrn, String feature) {
    if (observation == null)
      return;

    Optional.ofNullable(rrn)
      .ifPresent(r -> observation.highCardinalityKeyValue(REQUEST_ID, rrn));

    Optional.ofNullable(feature)
      .ifPresent(r -> observation.lowCardinalityKeyValue(FEATURE, feature));
  }

  public static void publishEvent(Observation observation, String key, String value) {
    if (observation == null) {
      log.warn(AppLogMessage.message("#Observation - no current observation"));
      return;
    }
    observation.event(Event.of(key, value));
  }

  public static void observeResponse(Observation observation, String responseCode, Throwable error) {
    if (observation == null) {
      log.warn(AppLogMessage.message("#Observation - no current observation"));
      return;
    }

    Optional.ofNullable(responseCode)
      .ifPresent(code -> observation.lowCardinalityKeyValue(RESPONSE_CODE, code));

    Optional.ofNullable(error)
      .ifPresent(t -> {
        String exceptionClass = getExceptionClass(t);
        observation.lowCardinalityKeyValue(ERROR, exceptionClass);
        publishEvent(observation, ERROR, error.getMessage());
        observation.error(t);
      });
  }

  private static String getExceptionClass(Throwable t) {
    if (t instanceof TransactionException e) {
      return e.getOriginalError().getClass().getName();
    }

    return Optional.ofNullable(t.getCause())
      .map(cause -> cause.getClass().getName())
      .orElseGet(() -> t.getClass().getName());
  }

  public static Context createApiContext(ContextDTO contextDTO) {
    Context observationContext = new Context();

    ApiFeatureConstant feature = ApiFeatureConstant.get(contextDTO.getMethod(), contextDTO.getPath());
    if (feature != null) {
      observationContext.addLowCardinalityKeyValue(KeyValue.of("feature", feature.name()));
    } else {
      observationContext.addLowCardinalityKeyValue(KeyValue.of("feature", contextDTO.getUnknownFeature()));
    }

    if (contextDTO.getRequestId() != null) {
      observationContext.addHighCardinalityKeyValue(KeyValue.of("requestId", contextDTO.getRequestId()));
    }

    return observationContext;
  }
}
