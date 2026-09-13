package com.nantaaditya.sotres.model.constant;

import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.util.AntPathMatcher;

final class FeatureConstantMatcher {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  private FeatureConstantMatcher() {
  }

  static <T extends FeatureConstant> Optional<T> find(T[] values, String method, String path) {
    return Stream.of(values)
        .filter(item -> item.getMethod().equals(method) && PATH_MATCHER.match(item.getPath(), path))
        .findFirst();
  }
}
