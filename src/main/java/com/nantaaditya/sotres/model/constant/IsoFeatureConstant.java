package com.nantaaditya.sotres.model.constant;

import java.util.Set;
import lombok.Getter;

public enum IsoFeatureConstant {
  AUTHENTICATE_FOR_PAYMENT(
      Set.of("10.97-E001", "10.97-E002", "10.97-E003"),
      Set.of("11.97-E001", "11.97-E002", "11.97-E003")
  );

  @Getter
  private Set<String> requests;
  @Getter
  private Set<String> responses;

  IsoFeatureConstant(Set<String> requests, Set<String> responses) {
    this.requests = requests;
    this.responses = responses;
  }

  public static String getBySelector(String selector) {
    for (IsoFeatureConstant isoFeatureConstant : IsoFeatureConstant.values()) {
      if (isoFeatureConstant.getRequests().contains(selector)
          || isoFeatureConstant.getResponses().contains(selector)) {
        return isoFeatureConstant.name();
      }
    }
    return selector;
  }
}
