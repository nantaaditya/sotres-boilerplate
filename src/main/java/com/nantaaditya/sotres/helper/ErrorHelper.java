package com.nantaaditya.sotres.helper;

import io.micrometer.core.instrument.util.StringEscapeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorHelper {

  private static final String STACKTRACE_FORMAT = "Root cause: %s at %s:%s";
  private static final String EMPTY_STACKTRACE_FORMAT = "Root cause: %s (no stack trace)";

  private ErrorHelper() {}

  public static String getRootCause(Throwable throwable) {
    Throwable root = throwable;
    while (root.getCause() != null) {
      root = root.getCause();
    }

    String message = root.getMessage();
    StackTraceElement[] stackTrace = root.getStackTrace();
    if (stackTrace.length > 0) {
      StackTraceElement origin = stackTrace[0];
      return StringEscapeUtils.escapeJson(String.format(STACKTRACE_FORMAT,
          message,
          origin.getClassName() + "." + origin.getMethodName(),
          origin.getLineNumber()
      ));
    } else {
      return StringEscapeUtils.escapeJson(String.format(EMPTY_STACKTRACE_FORMAT, message));
    }
  }

  public static void loggingError(String message, Throwable ex) {
    log.error(message, ex.getMessage(), getRootCause(ex));
  }
}
