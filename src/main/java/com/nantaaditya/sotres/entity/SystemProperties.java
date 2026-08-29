package com.nantaaditya.sotres.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_properties")
@SuppressWarnings("java:S1068")
public class SystemProperties {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;
  private String groupId;
  private String propertyId;
  private String propertyValue;
}
