package com.nantaaditya.sotres.model.error;

import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
@SuppressWarnings("java:S1068")
public class GeneralFlowException extends RuntimeException {

  private final ApiResponseCode response;
  private final Map<String, List<String>> violations = new HashMap<>();

  public GeneralFlowException(ApiResponseCode apiResponseCode) {
    super(apiResponseCode.getMessage());
    this.response = apiResponseCode;
  }

  public GeneralFlowException(ApiResponseCode responseCode, Map<String, List<String>> violations) {
    super(responseCode.getMessage());
    this.response = responseCode;
    this.violations.putAll(violations);
  }

  public GeneralFlowException(String message, ApiResponseCode responseCode) {
    super(message);
    this.response = responseCode;
  }

  public GeneralFlowException(String message, ApiResponseCode responseCode, Map<String, List<String>> violations) {
    super(message);
    this.response = responseCode;
    this.violations.putAll(violations);
  }
}
