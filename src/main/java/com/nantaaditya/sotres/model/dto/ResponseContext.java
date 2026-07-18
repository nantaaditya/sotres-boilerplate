package com.nantaaditya.sotres.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import java.beans.Transient;
import java.util.Optional;
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
  private ResponseData data;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class ResponseData {
    private String cpan;
    private Transaction transaction;
    private Customer customer;
  }

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
    private String paymentId;
    private String referenceId;
    private String rrn;
    private String approvalCode;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  private static class Customer {
    private String customerName;
  }

  @Transient
  public String getApprovalCode() {
    return Optional.ofNullable(data)
        .map(ResponseData::getTransaction)
        .map(Transaction::getApprovalCode)
        .orElse(null);
  }

  @Transient
  public String getResponseCode() {
    return Optional.ofNullable(response)
        .map(Response::getCode)
        .orElseGet(() -> IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
  }
}

