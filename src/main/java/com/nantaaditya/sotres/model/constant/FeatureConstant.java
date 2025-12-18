package com.nantaaditya.sotres.model.constant;

import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.Getter;
import org.springframework.util.AntPathMatcher;

@Getter
public enum FeatureConstant {

  GET_EXAMPLE("GET", "/api/example"),
  POST_EXAMPLE("POST", "/api/example");

  private String method;
  private String path;

  private static final AntPathMatcher matcher = new AntPathMatcher();

  FeatureConstant(String method, String path) {
    this.method = method;
    this.path = path;
  }

  public static FeatureConstant get(String method, String path) {
    Predicate<FeatureConstant> isMatch = (FeatureConstant item)
        -> item.getMethod().equals(method) && matcher.match(item.getPath(), path);

    return Stream.of(values())
        .filter(isMatch)
        .findFirst()
        .orElse(null);
  }
}