package com.nantaaditya.sotres.model.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ManagerConstant")
class ManagerConstantTest {

  @Test
  @DisplayName("getManager matches TRANSACTION by its pool name")
  void getManager_transactionPool_returnsTransaction() {
    assertThat(ManagerConstant.TRANSACTION.getManager("transaction")).isEqualTo(ManagerConstant.TRANSACTION);
  }

  @Test
  @DisplayName("getManager matches API by its pool name")
  void getManager_apiPool_returnsApi() {
    assertThat(ManagerConstant.TRANSACTION.getManager("api")).isEqualTo(ManagerConstant.API);
  }

  @Test
  @DisplayName("getManager returns null for an unknown pool name")
  void getManager_unknownPool_returnsNull() {
    assertThat(ManagerConstant.TRANSACTION.getManager("unknown")).isNull();
  }
}
