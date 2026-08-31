package com.nantaaditya.sotres.model.constant;

/**
 * Attribute keys stamped onto the spring-retry {@code RetryContext} by {@code RestSender} and read
 * back by {@code RestSenderRetryListener} when an outbound call exhausts its retry budget.
 */
public enum RetryConstant {
  CLIENT_NAME("retry.clientName"),
  METHOD("retry.method"),
  PATH("retry.path"),
  HEADERS("retry.headers"),
  REQUEST("retry.request"),
  RESPONSE("retry.response"),
  REQUEST_ID("retry.requestId"),
  PROCESS_TYPE("retry.processType"),
  PROCESS_NAME("retry.processName");

  private final String key;

  RetryConstant(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
