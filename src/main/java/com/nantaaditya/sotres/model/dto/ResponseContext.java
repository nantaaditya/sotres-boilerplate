package com.nantaaditya.sotres.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * internal DTO to map from external response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings("squid:S1068")
public class ResponseContext {

  private Response response;
  private Transaction transaction;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @SuppressWarnings("squid:S1068")
  public static class Response {
    private String code;
    private String description;
    private String time;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  @SuppressWarnings("squid:S1068")
  public static class Transaction {
    private String referenceId;
    private String approvalCode;
  }
}

