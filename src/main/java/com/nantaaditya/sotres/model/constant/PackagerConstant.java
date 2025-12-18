package com.nantaaditya.sotres.model.constant;

import lombok.Getter;

public enum PackagerConstant {
  DEFAULT("default-packager.xml");

  @Getter
  private String path;

  PackagerConstant(String path) {
    this.path = path;
  }
}
