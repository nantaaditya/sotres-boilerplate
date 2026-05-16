package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.EnhancedIsoClient;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@DisplayName("CustomHealthConfiguration")
@ExtendWith(MockitoExtension.class)
class CustomHealthConfigurationTest {

  @Mock
  private EnhancedIsoClient enhancedIsoClient;

  private HealthCheckHelper healthCheckHelper;
  private CustomHealthConfiguration config;

  @BeforeEach
  void setUp() {
    healthCheckHelper = new HealthCheckHelper();
    config = new CustomHealthConfiguration(enhancedIsoClient, healthCheckHelper);
  }

  @Test
  @DisplayName("health returns UP when client is connected")
  void health_whenConnected_returnsUp() {
    when(enhancedIsoClient.isConnected()).thenReturn(true);

    Health health = config.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("connected", true);
  }

  @Test
  @DisplayName("health returns DOWN when client is not connected")
  void health_whenNotConnected_returnsDown() {
    when(enhancedIsoClient.isConnected()).thenReturn(false);

    Health health = config.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("connected", false);
  }

  @Test
  @DisplayName("health includes healthy and signOn details reflecting HealthCheckHelper state")
  void health_includesHealthyAndSignOnDetails() {
    when(enhancedIsoClient.isConnected()).thenReturn(true);
    healthCheckHelper.setIsHealthy(true);
    healthCheckHelper.setIsSignedOn(true);

    Health health = config.health();

    assertThat(health.getDetails()).containsEntry("healthy", true);
    assertThat(health.getDetails()).containsEntry("signOn", true);
  }

  @Test
  @DisplayName("health reflects false healthy and signOn when not set")
  void health_whenHealthyAndSignOnFalse_reflectsInDetails() {
    when(enhancedIsoClient.isConnected()).thenReturn(true);

    Health health = config.health();

    assertThat(health.getDetails()).containsEntry("healthy", false);
    assertThat(health.getDetails()).containsEntry("signOn", false);
  }
}
