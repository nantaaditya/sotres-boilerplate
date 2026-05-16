package com.nantaaditya.sotres.configuration;

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
import reactor.test.StepVerifier;

@DisplayName("R2DBCAuditorConfiguration")
@ExtendWith(MockitoExtension.class)
class R2DBCAuditorConfigurationTest {

  @Mock
  private TracerHelper tracerHelper;

  private R2DBCAuditorConfiguration config;

  @BeforeEach
  void setUp() {
    config = new R2DBCAuditorConfiguration();
    ReflectionTestUtils.setField(config, "applicationName", "test-app");
    ReflectionTestUtils.setField(config, "tracerHelper", tracerHelper);
  }

  @Test
  @DisplayName("getCurrentAuditor returns clientId from baggage when present")
  void getCurrentAuditor_withClientIdBaggage_returnsClientId() {
    when(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID)).thenReturn("client-123");

    StepVerifier.create(config.getCurrentAuditor())
        .expectNext("client-123")
        .verifyComplete();
  }

  @Test
  @DisplayName("getCurrentAuditor falls back to applicationName when baggage is absent")
  void getCurrentAuditor_withoutClientIdBaggage_returnsApplicationName() {
    when(tracerHelper.getBaggage(HeaderConstant.CLIENT_ID)).thenReturn(null);

    StepVerifier.create(config.getCurrentAuditor())
        .expectNext("test-app")
        .verifyComplete();
  }
}
