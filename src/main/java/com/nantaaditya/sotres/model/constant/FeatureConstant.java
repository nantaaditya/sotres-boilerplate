package com.nantaaditya.sotres.model.constant;

import java.util.Set;
import lombok.Getter;

public enum FeatureConstant {
  AUTHENTICATE_FOR_PAYMENT(Set.of("10.97-E001", "10.97-E002", "10.97-E003"));

  @Getter
  private Set<String> selectors;

  FeatureConstant(Set<String> selectors) {
    this.selectors = selectors;
  }

  public static FeatureConstant getBySelector(String selector) {
    for (FeatureConstant featureConstant : FeatureConstant.values()) {
      if (featureConstant.getSelectors().contains(selector)) {
        return featureConstant;
      }
    }
    return null;
  }
}
