package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

public enum ApiResponseCode {
  SUCCESS("000", "success"),
  INVALID_PARAMS("900", "invalid parameters"),
  BAD_REQUEST("998", "bad request"),
  INTERNAL_ERROR("999", "internal error"),;

  @Getter
  private String code;
  @Getter
  private String message;

  ApiResponseCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public static ApiResponseCode fromCode(String code) {
    for (ApiResponseCode responseCode : ApiResponseCode.values()) {
      if (responseCode.getCode().equals(code)) {
        return responseCode;
      }
    }
    return null;
  }
}
