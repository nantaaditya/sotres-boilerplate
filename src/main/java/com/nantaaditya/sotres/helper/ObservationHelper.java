package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.FeatureConstant;
import com.nantaaditya.sotres.model.dto.TransactionException;
import io.micrometer.observation.Observation;
import java.util.Optional;

public class ObservationHelper {

  private static final String REQUEST_ID = "requestId";
  private static final String FEATURE = "feature";
  private static final String RESPONSE_CODE = "responseCode";
  private static final String ERROR = "error";

  private ObservationHelper() {}

  public static void observeRequest(Observation observation, String rrn, FeatureConstant feature) {
    if (observation == null)
      return;

    Optional.ofNullable(rrn)
      .ifPresent(r -> observation.highCardinalityKeyValue(REQUEST_ID, rrn));

    Optional.ofNullable(feature)
      .ifPresent(r -> observation.lowCardinalityKeyValue(FEATURE, feature.name()));
  }

  public static void observeResponse(Observation observation, String responseCode, Throwable error) {
    if (observation == null)
      return;

    Optional.ofNullable(responseCode)
      .ifPresent(code -> observation.lowCardinalityKeyValue(RESPONSE_CODE, code));

    Optional.ofNullable(error)
      .ifPresent(t -> {
        if (t instanceof TransactionException e) {
          observation.lowCardinalityKeyValue(ERROR, e.getOriginalError().getClass().getName());
        } else {
          observation.lowCardinalityKeyValue(ERROR, t.getCause().getClass().getName());
        }
        observation.error(error);
      });
  }
}
