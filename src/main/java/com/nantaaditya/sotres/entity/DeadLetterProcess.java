package com.nantaaditya.sotres.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dead_letter_process")
@SuppressWarnings("java:S1068")
public class DeadLetterProcess extends BaseEntity<Long>{

  private String processType;
  private String processName;
  private String idempotencyKey;
  private String clientName;
  private String method;
  private String path;
  private String headers;
  private byte[] payload;
  private int retryCount;
  private int maxRetry;
  private String status;
  private String lastError;
  private byte[] retryHistories;

}
