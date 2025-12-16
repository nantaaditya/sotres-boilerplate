package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum ReversalResponseCode {
  SUSPECTED_MALFUNCTION("22"),
  RESPONSE_RECEIVED_LATE("68"),
  UNABLE_TO_DECRYPT_TRACK2("69"),
  DESTINATION_NOT_AVAILABLE("82"),
  TIME_OUT("91");

  private final String code;

  ReversalResponseCode(String code) {
    this.code = code;
  }

  public static ReversalResponseCode fromCode(String code) {
    for (ReversalResponseCode responseCode : ReversalResponseCode.values()) {
      if (responseCode.code.equals(code)) {
        return responseCode;
      }
    }
    return null;
  }
}
