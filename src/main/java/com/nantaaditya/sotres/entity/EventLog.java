package com.nantaaditya.sotres.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Data
@Entity
@Table(name = "event_logs")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SuppressWarnings("java:S1068")
public class EventLog {
  @Id
  @TimeSeriesId
  private String id;
  private String clientId;
  private String requestId;
  private String method;
  private String path;
  private String responseCode;
  private String responseDescription;
  private byte[] payload;
  private byte[] additionalData;
  @CreatedDate
  private LocalDateTime createdDate;

}
