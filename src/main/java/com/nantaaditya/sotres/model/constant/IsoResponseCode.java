package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum IsoResponseCode {
  APPROVED("00"),
  INVALID_MERCHANT("03"),
  DO_NOT_HONOR("05"),
  INVALID_TRANSACTION("12"),
  INVALID_AMOUNT("13"),
  INVALID_PAN("14"),
  FORMAT_ERROR("30"),
  INSUFFICIENT_FUNDS("51"),
  INVALID_PIN("55"),
  NO_CARD_RECORD("56"),
  TRANSACTION_NOT_PERMITTED_TO_CARDHOLDER_OR_OTT_EXPIRED("57"),
  TRANSACTION_NOT_PERMITTED_TO_TERMINAL_OR_PAYMENT_EXPIRED("58"),
  SUSPECTED_FRAUD("59"),
  EXCEEDS_TRANSACTION_AMOUNT_LIMIT("61"),
  RESTRICTED_CARD("62"),
  EXCEEDS_TRANSACTION_FREQUENCY_LIMIT("65"),
  SUSPEND_TRANSACTION("68"),
  CUT_OFF_IN_PROGRESS("90"),
  LINK_DOWN("91"),
  UNABLE_TO_ROUTE_TRANSACTION("92"),
  DUPLICATE_TRANSACTION_OR_TOKEN("94"),
  SYSTEM_MALFUNCTION("96"),
  MUTATION_DOES_NOT_PERFORM("A0");

  
  private final String code;
  
  IsoResponseCode(String code) {
    this.code = code;
  }

  public static IsoResponseCode fromCode(String code) {
    for (IsoResponseCode isoResponseCode : IsoResponseCode.values()) {
      if (isoResponseCode.code.equals(code)) {
        return isoResponseCode;
      }
    }
    return null;
  }
}
