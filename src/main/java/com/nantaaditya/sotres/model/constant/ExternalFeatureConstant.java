package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum ExternalFeatureConstant implements FeatureConstant {
  GET_EXAMPLE("GET", "/api/example"),
  POST_EXAMPLE("POST", "/api/example");

  private final String method;
  private final String path;

  ExternalFeatureConstant(String method, String path) {
    this.method = method;
    this.path = path;
  }

  public static String getFeature(String method, String path) {
    return FeatureConstantMatcher.find(values(), method, path)
        .map(ExternalFeatureConstant::name)
        .orElseGet(() -> method + path);
  }
}
