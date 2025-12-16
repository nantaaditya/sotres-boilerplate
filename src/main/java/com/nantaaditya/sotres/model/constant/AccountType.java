package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

public enum AccountType {
  UNSPECIFIED("00"),
  SAVING("10"),
  CHEQUE("20"),
  CREDIT("30"),
  UNIVERSAL("40"),
  INVESTMENT("50"),
  ELECTRONIC_PURSE("60");

  @Getter
  private String code;

  AccountType(String code) {
    this.code = code;
  }

  public static AccountType fromCode(String code) {
    for (AccountType type : AccountType.values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    return null;
  }
}
