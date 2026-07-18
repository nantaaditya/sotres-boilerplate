package com.nantaaditya.sotres.model.constant;

import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.Getter;
import org.springframework.util.AntPathMatcher;

@Getter
public enum ExternalFeatureConstant {
  GET_EXAMPLE("GET", "/api/example"),
  POST_EXAMPLE("POST", "/api/example");

  private String method;
  private String path;

  private static final AntPathMatcher matcher = new AntPathMatcher();

  ExternalFeatureConstant(String method, String path) {
    this.method = method;
    this.path = path;
  }

  public static ExternalFeatureConstant get(String method, String path) {
    Predicate<ExternalFeatureConstant> isMatch = (ExternalFeatureConstant item)
        -> item.getMethod().equals(method) && matcher.match(item.getPath(), path);

    return Stream.of(values())
        .filter(isMatch)
        .findFirst()
        .orElse(null);
  }
}
