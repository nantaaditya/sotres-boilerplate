package com.nantaaditya.sotres.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dead_letter_process")
@SuppressWarnings("java:S1068")
public class DeadLetterProcess extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;
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
