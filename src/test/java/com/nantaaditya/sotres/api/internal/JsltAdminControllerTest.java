package com.nantaaditya.sotres.api.internal;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.api.ApiExceptionHandler;
import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.model.response.TemplateResponse;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("JsltAdminController")
class JsltAdminControllerTest {

  private static final String SELECTOR = "10.97-E001";

  private JsltTransformationHelper jsltTransformationHelper;
  private SystemPropertiesService systemPropertiesService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jsltTransformationHelper = mock(JsltTransformationHelper.class);
    systemPropertiesService = mock(SystemPropertiesService.class);

    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));
    ApiExceptionHandler exceptionHandler =
        new ApiExceptionHandler(new ObjectMapper(), responseHelper,
            mock(ObservationWrapper.class), mock(HttpServletRequest.class));

    JsltAdminController controller =
        new JsltAdminController(jsltTransformationHelper, systemPropertiesService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", mock(ObservationWrapper.class));

    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(exceptionHandler)
        .build();
  }

  @Nested
  @DisplayName("POST /_reload")
  class Reload {

    @Test
    @DisplayName("returns 200 with template map after evicting and reloading selector")
    void reload_returnsTemplateMap() throws Exception {
      Map<String, String> templates = Map.of(
          "client_spec_request", "{\"result\": .value}",
          "client_spec_response", "{\"mapped\": .data}"
      );
      when(jsltTransformationHelper.evictAndReload(SELECTOR)).thenReturn(templates);

      mockMvc.perform(post("/internal-api/jslt/_reload").param("selector", SELECTOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.response.code").value("000"))
          .andExpect(jsonPath("$.data.client_spec_request").value("{\"result\": .value}"));

      verify(jsltTransformationHelper).evictAndReload(SELECTOR);
    }

    @Test
    @DisplayName("propagates error from helper as a 500 / code 999 envelope")
    void reload_propagatesError() throws Exception {
      when(jsltTransformationHelper.evictAndReload(SELECTOR))
          .thenThrow(new RuntimeException("DB error"));

      mockMvc.perform(post("/internal-api/jslt/_reload").param("selector", SELECTOR))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.response.code").value("999"));
    }
  }

  @Nested
  @DisplayName("GET /templates")
  class Templates {

    @Test
    @DisplayName("returns 200 with current template content for selector")
    void templates_returnsTemplateContent() throws Exception {
      Map<String, String> templates = Map.of(
          "client_spec_request", "{\"result\": .value}",
          "client_spec_response", ""
      );
      when(jsltTransformationHelper.getTemplates(SELECTOR)).thenReturn(templates);

      mockMvc.perform(get("/internal-api/jslt/templates").param("selector", SELECTOR))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.client_spec_request").exists());

      verify(jsltTransformationHelper).getTemplates(SELECTOR);
    }

    @Test
    @DisplayName("propagates error from helper as a 500 / code 999 envelope")
    void templates_propagatesError() throws Exception {
      when(jsltTransformationHelper.getTemplates(SELECTOR))
          .thenThrow(new RuntimeException("service error"));

      mockMvc.perform(get("/internal-api/jslt/templates").param("selector", SELECTOR))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.response.code").value("999"));
    }
  }

  @Nested
  @DisplayName("POST /_reload-all")
  class ReloadAll {

    @Test
    @DisplayName("returns 200 with a per-selector compile-status map after evicting and rewarming all templates")
    void reloadAll_returnsPerSelectorStatus() throws Exception {
      Map<String, Boolean> results = Map.of("client_spec_request:10.97-E001", true);
      when(jsltTransformationHelper.evictAll()).thenReturn(results);

      mockMvc.perform(post("/internal-api/jslt/_reload-all"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data['client_spec_request:10.97-E001']").value(true));
    }

    @Test
    @DisplayName("propagates error from helper as a 500 / code 999 envelope")
    void reloadAll_propagatesError() throws Exception {
      doThrow(new RuntimeException("reload failed")).when(jsltTransformationHelper).evictAll();

      mockMvc.perform(post("/internal-api/jslt/_reload-all"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.response.code").value("999"));
    }
  }

  @Nested
  @DisplayName("PUT /template")
  class Save {

    @Test
    @DisplayName("returns 200 with TemplateResponse DTO after upsert")
    void save_returnsTemplateResponseDto() throws Exception {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId(SELECTOR)
          .propertyValue("{\"result\": .value}").build();
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, "{\"result\": .value}"))
          .thenReturn(saved);

      mockMvc.perform(put("/internal-api/jslt/template")
              .param("selector", SELECTOR)
              .param("group", "CLIENT_SPEC_REQUEST")
              .contentType(MediaType.TEXT_PLAIN)
              .content("{\"result\": .value}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.template").value("{\"result\": .value}"))
          .andExpect(jsonPath("$.data.selector").value(SELECTOR))
          .andExpect(jsonPath("$.data.group").value("client_spec_request"));

      // validation + cache eviction now happen inside SystemPropertiesServiceImpl.upsert()
      // (already unit-tested there) — the controller just delegates to it in one call.
      verify(systemPropertiesService).upsert(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, "{\"result\": .value}");
    }

    @Test
    @DisplayName("propagates error from upsert when DB write fails as a 500 / code 999 envelope")
    void save_propagatesError() throws Exception {
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, "{\"result\": .value}"))
          .thenThrow(new RuntimeException("DB write failed"));

      mockMvc.perform(put("/internal-api/jslt/template")
              .param("selector", SELECTOR)
              .param("group", "CLIENT_SPEC_REQUEST")
              .contentType(MediaType.TEXT_PLAIN)
              .content("{\"result\": .value}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.response.code").value("999"));
    }

    @Test
    @DisplayName("rejects an invalid template as a 400 / code 900 envelope")
    void save_invalidTemplate_rejectsBeforeUpsert() throws Exception {
      // validation now happens inside SystemPropertiesServiceImpl.upsert() (already unit-tested
      // there via jsltTransformationHelper.validateTemplate) — the controller-mocked service
      // simulates that same failure.
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, "<<< bad >>>"))
          .thenThrow(new InvalidTemplateException("invalid JSLT syntax"));

      mockMvc.perform(put("/internal-api/jslt/template")
              .param("selector", SELECTOR)
              .param("group", "CLIENT_SPEC_REQUEST")
              .contentType(MediaType.TEXT_PLAIN)
              .content("<<< bad >>>"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.response.code").value("900"))
          .andExpect(jsonPath("$.error.violations.template").exists());
    }
  }
}
