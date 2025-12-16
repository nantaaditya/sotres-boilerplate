package com.nantaaditya.sotres.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dead_letter_process")
@SuppressWarnings("java:S1068")
public class DeadLetterProcess {

  @Id
  private long id;

  private String processType;

  private String processName;

  private String lastError;

  private byte[] payload;

  private boolean processed;

  @CreatedBy
  private String createdBy;

  @LastModifiedBy
  private String updatedBy;

  @CreatedDate
  private LocalDateTime createdDate;

  @LastModifiedDate
  private LocalDateTime updatedDate;

  @Version
  private long version;

  public static DeadLetterProcess create(byte[] request, String lastError, String processName) {
    return DeadLetterProcess.builder()
        .processType("client")
        .processName(processName)
        .payload(request)
        .lastError(lastError)
        .processed(false)
        .build();
  }
}
