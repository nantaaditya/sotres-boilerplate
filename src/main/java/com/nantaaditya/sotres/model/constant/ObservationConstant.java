package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

@Getter
public enum ObservationConstant {
  API_PUBLIC("api.public");

  private String name;

  ObservationConstant(String name) {
    this.name = name;
  }
}
