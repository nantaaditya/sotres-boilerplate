package com.nantaaditya.sotres.listener;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared config for the standalone (non-Spring-managed) {@link ObjectMapper} used by the log4j2
 * plugins in this package ({@link TextLogLayout}, {@link JsonLogLayout}).
 */
final class LogObjectMapperFactory {

  private LogObjectMapperFactory() {
  }

  static ObjectMapper create() {
    return JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .changeDefaultPropertyInclusion(v -> v.withValueInclusion(Include.NON_NULL))
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(MapperFeature.USE_ANNOTATIONS)
        .build();
  }
}
