package com.nantaaditya.sotres.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.schibsted.spt.data.jslt.JsltException;
import org.mockito.InOrder;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.model.response.TemplateResponse;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import io.micrometer.observation.Observation;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

  @Mock
  private Observation observation;

  private JsltAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new JsltAdminController(jsltTransformationHelper, systemPropertiesService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", observationWrapper);
    lenient().when(observationWrapper.getObservation()).thenReturn(observation);
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
      Response<Map<String, String>> successResp = successResponse(templates);

      when(jsltTransformationHelper.evictAndReload("10.97-E001")).thenReturn(Mono.just(templates));
      when(responseHelper.success(templates)).thenReturn(successResp);

      StepVerifier.create(controller.reload("10.97-E001"))
          .assertNext(entity -> {
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entity.getBody().getData()).isEqualTo(templates);
          })
          .verifyComplete();

      verify(jsltTransformationHelper).evictAndReload("10.97-E001");
    }

    @Test
    @DisplayName("propagates error from helper")
    void reload_propagatesError() {
      when(jsltTransformationHelper.evictAndReload("10.97-E001"))
          .thenReturn(Mono.error(new RuntimeException("DB error")));

      StepVerifier.create(controller.reload("10.97-E001"))
          .expectError(RuntimeException.class)
          .verify();
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
      Response<Map<String, String>> successResp = successResponse(templates);

      when(jsltTransformationHelper.getTemplates("10.97-E001")).thenReturn(Mono.just(templates));
      when(responseHelper.success(templates)).thenReturn(successResp);

      StepVerifier.create(controller.templates("10.97-E001"))
          .assertNext(entity -> {
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entity.getBody().getData()).containsKey("client_spec_request");
          })
          .verifyComplete();

      verify(jsltTransformationHelper).getTemplates("10.97-E001");
    }

    @Test
    @DisplayName("propagates error from helper")
    void templates_propagatesError() {
      when(jsltTransformationHelper.getTemplates("10.97-E001"))
          .thenReturn(Mono.error(new RuntimeException("service error")));

      StepVerifier.create(controller.templates("10.97-E001"))
          .expectError(RuntimeException.class)
          .verify();
    }
  }

  @Nested
  @DisplayName("POST /_reload-all")
  class ReloadAll {

    @Test
    @DisplayName("returns 200 with true after evicting and rewarming all templates")
    void reloadAll_returnsTrue() {
      Response<Boolean> successResp = successResponse(Boolean.TRUE);

      when(jsltTransformationHelper.evictAll()).thenReturn(Mono.empty());
      when(responseHelper.success(Boolean.TRUE)).thenReturn(successResp);

      StepVerifier.create(controller.reloadAll())
          .assertNext(entity -> {
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entity.getBody().getData()).isTrue();
          })
          .verifyComplete();

      verify(jsltTransformationHelper).evictAll();
    }

    @Test
    @DisplayName("propagates error from helper")
    void reloadAll_propagatesError() {
      when(jsltTransformationHelper.evictAll())
          .thenReturn(Mono.error(new RuntimeException("reload failed")));

      StepVerifier.create(controller.reloadAll())
          .expectError(RuntimeException.class)
          .verify();
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
      Response<TemplateResponse> successResp = successResponse(dto);

      when(jsltTransformationHelper.validateTemplate("{\"result\": .value}"))
          .thenReturn(Mono.empty());
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenReturn(Mono.just(saved));
      when(responseHelper.success(dto)).thenReturn(successResp);

      StepVerifier.create(controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}"))
          .assertNext(entity -> {
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entity.getBody().getData().template()).isEqualTo("{\"result\": .value}");
            assertThat(entity.getBody().getData().selector()).isEqualTo("10.97-E001");
            assertThat(entity.getBody().getData().group()).isEqualTo("client_spec_request");
          })
          .verifyComplete();
    }

    @Test
    @DisplayName("propagates error from upsert when DB write fails")
    void save_propagatesError() {
      when(jsltTransformationHelper.validateTemplate("{\"result\": .value}"))
          .thenReturn(Mono.empty());
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenReturn(Mono.error(new RuntimeException("DB write failed")));

      StepVerifier.create(controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}"))
          .expectError(RuntimeException.class)
          .verify();
    }

    @Test
    @DisplayName("propagates JsltException and never calls upsert when template is invalid")
    void save_invalidTemplate_rejectsBeforeUpsert() {
      when(jsltTransformationHelper.validateTemplate("<<< bad >>>"))
          .thenReturn(Mono.error(new JsltException("unexpected token")));

      StepVerifier.create(controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "<<< bad >>>"))
          .expectError(JsltException.class)
          .verify();

      verify(systemPropertiesService, never())
          .upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "<<< bad >>>");
    }

    @Test
    @DisplayName("validates before upserting — validateTemplate is called first")
    void save_validTemplate_validatesBeforeUpsert() {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();
      TemplateResponse dto = TemplateResponse.from(saved);
      Response<TemplateResponse> successResp = successResponse(dto);

      when(jsltTransformationHelper.validateTemplate("{\"result\": .value}"))
          .thenReturn(Mono.empty());
      when(systemPropertiesService.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .thenReturn(Mono.just(saved));
      when(responseHelper.success(dto)).thenReturn(successResp);

      StepVerifier.create(controller.save("10.97-E001", TemplateGroup.CLIENT_SPEC_REQUEST, "{\"result\": .value}"))
          .assertNext(entity -> assertThat(entity.getStatusCode().value()).isEqualTo(200))
          .verifyComplete();

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
