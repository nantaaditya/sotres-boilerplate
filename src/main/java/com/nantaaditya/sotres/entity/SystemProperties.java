package com.nantaaditya.sotres.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "system_properties")
@SuppressWarnings("java:S1068")
public class SystemProperties {
  @Id
  private long id;
  private String groupId;
  private String propertyId;
  private String propertyValue;
}