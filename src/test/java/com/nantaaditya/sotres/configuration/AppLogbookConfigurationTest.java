package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.properties.LogProperties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.zalando.logbook.Logbook;

@DisplayName("AppLogbookConfiguration")
@ExtendWith(MockitoExtension.class)
class AppLogbookConfigurationTest {

  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private LogProperties logProperties;

  private AppLogbookConfiguration config;
  private Gson gson;

  @BeforeEach
  void setUp() {
    gson = new GsonConfiguration().gson();
    config = new AppLogbookConfiguration();
    ReflectionTestUtils.setField(config, "tracerHelper", tracerHelper);
    ReflectionTestUtils.setField(config, "logProperties", logProperties);
    ReflectionTestUtils.setField(config, "objectMapper", new ObjectMapper());
  }

  @Test
  @DisplayName("logbook bean is created successfully with sensitive field filter")
  void logbook_createsNonNullInstance() {
    when(logProperties.getSensitiveFields()).thenReturn(Set.of("authorization"));

    Logbook result = config.logbook(gson);

    assertThat(result).isNotNull();
  }
}
