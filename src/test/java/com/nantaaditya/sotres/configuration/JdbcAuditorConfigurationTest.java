package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JdbcAuditorConfiguration")
@ExtendWith(MockitoExtension.class)
class JdbcAuditorConfigurationTest {

  @Mock
  private TracerHelper tracerHelper;

  private JdbcAuditorConfiguration config;

  @BeforeEach
  void setUp() {
    config = new JdbcAuditorConfiguration();
    ReflectionTestUtils.setField(config, "applicationName", "test-app");
    ReflectionTestUtils.setField(config, "tracerHelper", tracerHelper);
  }

  @Test
  @DisplayName("getCurrentAuditor returns clientId from baggage when present")
  void getCurrentAuditor_withClientIdBaggage_returnsClientId() {
    when(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID)).thenReturn("client-123");

    assertThat(config.getCurrentAuditor()).contains("client-123");
  }

  @Test
  @DisplayName("getCurrentAuditor falls back to applicationName when baggage is absent")
  void getCurrentAuditor_withoutClientIdBaggage_returnsApplicationName() {
    when(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID)).thenReturn(null);

    assertThat(config.getCurrentAuditor()).contains("test-app");
  }
}
