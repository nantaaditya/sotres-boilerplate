package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum ObservationConstant {
  API_PUBLIC("api.public"),
  API_EXTERNAL("api.external"),
  ISO_MESSAGE("iso.message");

  private String name;

  ObservationConstant(String name) {
    this.name = name;
  }
}
