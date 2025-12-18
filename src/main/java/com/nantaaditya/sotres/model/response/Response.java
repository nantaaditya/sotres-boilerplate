package com.nantaaditya.sotres.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@SuppressWarnings("java:S1068")
public class Response<T> {

  private ResponseMetadata response; //NOSONAR
  private T data;
  private ErrorMetadata error;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(Include.NON_NULL)
  public static class ResponseMetadata {
    private String code;
    private String description;
    private String time;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(Include.NON_NULL)
  public static class ErrorMetadata {
    private Map<String, List<String>> violations;
  }

}
