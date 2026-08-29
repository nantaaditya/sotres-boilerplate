package com.nantaaditya.sotres.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.model.response.TemplateResponse;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JsltAdminController")
@ExtendWith(MockitoExtension.class)
class JsltAdminControllerTest {

  @Mock
  private JsltTransformationHelper jsltTransformationHelper;

  @Mock
  private SystemPropertiesService systemPropertiesService;

  @Mock
  private ResponseHelper responseHelper;

  @Mock
  private ObservationWrapper observationWrapper;

  private JsltAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new JsltAdminController(jsltTransformationHelper, systemPropertiesService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", observationWrapper);
  }

  @Nested
  @DisplayName("POST /_reload")
  class Reload {

    @Test
    @DisplayName("returns 200 with template map after evicting and reloading selector")
    void reload_returnsTemplateMap() {
      Map<String, String> templates = Map.of(
          "client_spec_request", "{\"result\": .value}",
          "client_spec_response", "{\"mapped\": .data}"
      );
      when(jsltTransformationHelper.evictAndReload("10.97-E001")).thenReturn(templates);
      when(responseHelper.success(templates)).thenReturn(successResponse(templates));

      ResponseEntity<Response<Map<String, String>>> entity = controller.reload("10.97-E001");

      assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(entity.getBody().getData()).isEqualTo(templates);
      verify(jsltTransformationHelper).evictAndReload("10.97-E001");
    }

    @Test
    @DisplayName("propagates error from helper")
    void reload_propagatesError() {
      when(jsltTransformationHelper.evictAndReload("10.97-E001"))
          .thenThrow(new RuntimeException("DB error"));

      assertThatThrownBy(() -> controller.reload("10.97-E001"))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("GET /templates")
  class Templates {

    @Test
    @DisplayName("returns 200 with current template content for selector")
    void templates_returnsTemplateContent() {
      Map<String, String> templates = Map.of(
          "client_spec_request", "{\"result\": .value}",
          "client_spec_response", ""
      );
      when(jsltTransformationHelper.getTemplates("10.97-E001")).thenReturn(templates);
      when(responseHelper.success(templates)).thenReturn(successResponse(templates));

      ResponseEntity<Response<Map<String, String>>> entity = controller.templates("10.97-E001");

      assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(entity.getBody().getData()).containsKey("client_spec_request");
      verify(jsltTransformationHelper).getTemplates("10.97-E001");
    }

    @Test
    @DisplayName("propagates error from helper")
    void templates_propagatesError() {
      when(jsltTransformationHelper.getTemplates("10.97-E001"))
          .thenThrow(new RuntimeException("service error"));

      assertThatThrownBy(() -> controller.templates("10.97-E001"))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("POST /_reload-all")
  class ReloadAll {

    @Test
    @DisplayName("returns 200 with true after evicting and rewarming all templates")
    void reloadAll_returnsTrue() {
      when(responseHelper.success(Boolean.TRUE)).thenReturn(successResponse(Boolean.TRUE));

      ResponseEntity<Response<Boolean>> entity = controller.reloadAll();

      assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(entity.getBody().getData()).isTrue();
      verify(jsltTransformationHelper).evictAll();
    }

    @Test
    @DisplayName("propagates error from helper")
    void reloadAll_propagatesError() {
      doThrow(new RuntimeException("reload failed")).when(jsltTransformationHelper).evictAll();

      assertThatThrownBy(() -> controller.reloadAll())
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("PUT /template")
  class Save {

    @Test
    @DisplayName("returns 200 with TemplateResponse DTO after upsert and cache eviction")
    void save_returnsTemplateResponseDto() {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();
      TemplateResponse dto = TemplateResponse.from(saved);

      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenReturn(saved);
      when(responseHelper.success(dto)).thenReturn(successResponse(dto));

      ResponseEntity<Response<TemplateResponse>> entity =
          controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}");

      assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(entity.getBody().getData().template()).isEqualTo("{\"result\": .value}");
      assertThat(entity.getBody().getData().selector()).isEqualTo("10.97-E001");
      assertThat(entity.getBody().getData().group()).isEqualTo("client_spec_request");
    }

    @Test
    @DisplayName("propagates error from upsert when DB write fails")
    void save_propagatesError() {
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenThrow(new RuntimeException("DB write failed"));

      assertThatThrownBy(() ->
          controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}"))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("propagates InvalidTemplateException and never calls upsert when template is invalid")
    void save_invalidTemplate_rejectsBeforeUpsert() {
      doThrow(new InvalidTemplateException("invalid JSLT syntax"))
          .when(jsltTransformationHelper).validateTemplate("<<< bad >>>");

      assertThatThrownBy(() ->
          controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "<<< bad >>>"))
          .isInstanceOf(InvalidTemplateException.class);

      verify(systemPropertiesService, never())
          .upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "<<< bad >>>");
    }

    @Test
    @DisplayName("validates before upserting - validateTemplate is called first")
    void save_validTemplate_validatesBeforeUpsert() {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();
      TemplateResponse dto = TemplateResponse.from(saved);

      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenReturn(saved);
      when(responseHelper.success(dto)).thenReturn(successResponse(dto));

      ResponseEntity<Response<TemplateResponse>> entity =
          controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}");

      assertThat(entity.getStatusCode().value()).isEqualTo(200);
      InOrder order = inOrder(jsltTransformationHelper, systemPropertiesService);
      order.verify(jsltTransformationHelper).validateTemplate("{\"result\": .value}");
      order.verify(systemPropertiesService).upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}");
    }
  }

  private <T> Response<T> successResponse(T data) {
    return Response.<T>builder()
        .response(Response.ResponseMetadata.builder()
            .code(ApiResponseCode.SUCCESS.getCode())
            .description(ApiResponseCode.SUCCESS.getMessage())
            .build())
        .data(data)
        .build();
  }
}
