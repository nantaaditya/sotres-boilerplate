package com.nantaaditya.sotres.model.dto;

import lombok.Getter;

@Getter
public class TransactionException extends RuntimeException {

  private Throwable originalError;
  private RequestContext requestContext;

  public TransactionException(Throwable originalError, RequestContext requestContext) {
    super("transaction exception");
    this.originalError = originalError;
    this.requestContext = requestContext;
  }
}
