package com.nantaaditya.sotres.model.logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonLogError(
    @JsonProperty("error_message")
    String errorMessage,
    @JsonProperty("error_stacktrace")
    String [] errorStackTrace
) {

  public static JsonLogError create(Throwable throwable) {
    return new JsonLogError(throwable.getMessage(), getStackTrace(throwable));
  }

  private static String[] getStackTrace(Throwable throwable) {
    return Arrays.stream(throwable.getStackTrace())
        .skip(1)
        .map(StackTraceElement::toString)
        .toArray(String[]::new);
  }
}