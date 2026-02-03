package com.nantaaditya.sotres.model.constant;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

public enum ManagerConstant {
  TRANSACTION("transaction"),
  API("api");

  @Getter
  private String pool;

  ManagerConstant(String pool) {
    this.pool =  pool;
  }

  public ManagerConstant getManager(String managerName) {
    ManagerConstant managerConstant = null;
    for (ManagerConstant c: ManagerConstant.values()) {
      if (StringUtils.equals(managerName, c.getPool())) {
        managerConstant = c;
        break;
      }
    }

    return managerConstant;
  }
}
