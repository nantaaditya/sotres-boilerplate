package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

public enum NetworkInformationCode {
  LOGON("001"),
  LOGOFF("002"),
  ECHO("301"),
  CUTOVER("201");

  @Getter
  private String code;

  NetworkInformationCode(String code) {
    this.code = code;
  }
}
