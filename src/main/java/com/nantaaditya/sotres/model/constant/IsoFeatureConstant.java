package com.nantaaditya.sotres.model.constant;

import java.util.Set;
import lombok.Getter;

public enum IsoFeatureConstant {
  AUTHENTICATE_FOR_PAYMENT(Set.of("10.97-E001", "10.97-E002", "10.97-E003"));

  @Getter
  private Set<String> selectors;

  IsoFeatureConstant(Set<String> selectors) {
    this.selectors = selectors;
  }

  public static IsoFeatureConstant getBySelector(String selector) {
    for (IsoFeatureConstant isoFeatureConstant : IsoFeatureConstant.values()) {
      if (isoFeatureConstant.getSelectors().contains(selector)) {
        return isoFeatureConstant;
      }
    }
    return null;
  }
}
