package com.nantaaditya.sotres.api.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.api.ApiExceptionHandler;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("SystemPropertiesController")
class SystemPropertiesControllerTest {

  private SystemPropertiesService systemPropertiesService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    systemPropertiesService = mock(SystemPropertiesService.class);

    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));
    ApiExceptionHandler exceptionHandler =
        new ApiExceptionHandler(new ObjectMapper(), responseHelper,
            mock(ObservationWrapper.class), mock(HttpServletRequest.class));

    SystemPropertiesController controller = new SystemPropertiesController(systemPropertiesService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", mock(ObservationWrapper.class));

    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(exceptionHandler)
        .build();
  }

  @Test
  @DisplayName("PUT /_reload returns 200 and reloads the requested group")
  void reload_returnsSuccessAndReloadsGroup() throws Exception {
    mockMvc.perform(put("/internal-api/configurations/_reload").param("group", "PATH_MAPPING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));

    verify(systemPropertiesService).reload(ConfigGroup.PATH_MAPPING);
  }

  @Test
  @DisplayName("GET / returns 200 with the current property map for the requested key")
  void find_returnsCurrentPropertyMap() throws Exception {
    Map<String, String> properties = Map.of("20.97-E001", "/api/transaction");
    when(systemPropertiesService.getProperty(ConfigGroup.PATH_MAPPING)).thenReturn(properties);

    mockMvc.perform(get("/internal-api/configurations").param("key", "PATH_MAPPING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data['20.97-E001']").value("/api/transaction"));
  }

  @Test
  @DisplayName("an unknown group value falls through to the generic error handler (500 / code 999)")
  void reload_unknownGroup_returnsInternalErrorEnvelope() throws Exception {
    // ApiExceptionHandler has no dedicated handler for enum @RequestParam conversion failures
    // (MethodArgumentTypeMismatchException), so it falls to the catch-all Throwable handler.
    mockMvc.perform(put("/internal-api/configurations/_reload").param("group", "NOT_A_REAL_GROUP"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.response.code").value("999"));
  }
}
