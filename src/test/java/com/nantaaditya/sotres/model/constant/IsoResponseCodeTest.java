package com.nantaaditya.sotres.model.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IsoResponseCode")
class IsoResponseCodeTest {

  @Test
  @DisplayName("fromCode matches the first enum constant checked")
  void fromCode_matchesFirstConstant_returnsIt() {
    assertThat(IsoResponseCode.fromCode(IsoResponseCode.APPROVED.getCode()))
        .isEqualTo(IsoResponseCode.APPROVED);
  }

  @Test
  @DisplayName("fromCode matches a later enum constant after iterating past earlier ones")
  void fromCode_matchesLaterConstant_returnsIt() {
    assertThat(IsoResponseCode.fromCode(IsoResponseCode.SYSTEM_MALFUNCTION.getCode()))
        .isEqualTo(IsoResponseCode.SYSTEM_MALFUNCTION);
  }

  @Test
  @DisplayName("fromCode returns null for an unknown code")
  void fromCode_unknownCode_returnsNull() {
    assertThat(IsoResponseCode.fromCode("not-a-real-code")).isNull();
  }
}
