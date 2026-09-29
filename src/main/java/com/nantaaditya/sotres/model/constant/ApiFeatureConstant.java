package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum ApiFeatureConstant implements FeatureConstant {

  GET_EXAMPLE("GET", "/api/example"),
  POST_EXAMPLE("POST", "/api/example");

  private final String method;
  private final String path;

  ApiFeatureConstant(String method, String path) {
    this.method = method;
    this.path = path;
  }

  public static ApiFeatureConstant get(String method, String path) {
    return FeatureConstantMatcher.find(values(), method, path).orElse(null);
  }
}