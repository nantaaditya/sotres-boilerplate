package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.schibsted.spt.data.jslt.JsltException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DisplayName("JsltTransformationHelper")
@ExtendWith(MockitoExtension.class)
class JsltTransformationHelperTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;

  private JsltTransformationHelper helper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String SELECTOR = "10.97-E001";
  private static final String REQ_TEMPLATE = "{\"result\": .input}";
  private static final String RESP_TEMPLATE = "{\"mapped\": .output}";

  private static SystemProperties sp(String groupId, String propertyId, String value) {
    return SystemProperties.builder()
        .groupId(groupId)
        .propertyId(propertyId)
        .propertyValue(value)
        .build();
  }

  @BeforeEach
  void setUp() {
    helper = new JsltTransformationHelper(systemPropertiesService, objectMapper);
  }

  @Nested
  @DisplayName("transform")
  class Transform {

    @Test
    @DisplayName("fetches, compiles, and applies template on cache miss")
    void cacheMiss_fetchesCompiles_andApplies() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));

      JsonNode json = helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "hello"));

      assertThat(json.get("result").asText()).isEqualTo("hello");
    }

    @Test
    @DisplayName("uses cached expression on second call, skipping service lookup")
    void cacheHit_skipsServiceLookup() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));

      Map<String, String> input = Map.of("input", "hello");
      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);

      JsonNode json = helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);

      assertThat(json.get("result").asText()).isEqualTo("hello");
      verify(systemPropertiesService, times(1))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_REQUEST), eq(SELECTOR));
    }

    @Test
    @DisplayName("passes through input as JsonNode when template not found in repository")
    void cacheMiss_noTemplate_passesThroughAsJsonNode() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.empty());

      Map<String, String> input = Map.of("cardNo", "4111111111111111", "amount", "10000");

      JsonNode json = helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);

      assertThat(json.get("cardNo").asText()).isEqualTo("4111111111111111");
      assertThat(json.get("amount").asText()).isEqualTo("10000");
    }

    @Test
    @DisplayName("propagates JsltException when template is syntactically invalid")
    void cacheMiss_invalidTemplate_propagatesJsltException() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just("<<< this is not valid JSLT >>>"));

      assertThatThrownBy(() -> helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of()))
          .isInstanceOf(JsltException.class);
    }

    @Test
    @DisplayName("negative-caches the pass-through: a missing template is not re-fetched on the next call")
    void cacheMiss_noTemplate_isNegativeCached() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.empty());

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "hello"));
      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "hello"));

      verify(systemPropertiesService, times(1)).getRawProperty(any(), any());
    }

    @Test
    @DisplayName("does not cache a compilation error — a subsequent call retries from DB")
    void compilationError_notCached_subsequentCallRetries() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just("<<< invalid JSLT >>>"))
          .thenReturn(Mono.just(REQ_TEMPLATE));

      assertThatThrownBy(() -> helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of()))
          .isInstanceOf(JsltException.class);

      JsonNode json = helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "hello"));

      assertThat(json.get("result").asText()).isEqualTo("hello");
      verify(systemPropertiesService, times(2)).getRawProperty(any(), any());
    }
  }

  @Nested
  @DisplayName("validateTemplate")
  class ValidateTemplate {

    @Test
    @DisplayName("does not throw for a syntactically valid template")
    void validTemplate_doesNotThrow() {
      assertThatCode(() -> helper.validateTemplate(REQ_TEMPLATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throws InvalidTemplateException wrapping JsltException for invalid template")
    void invalidTemplate_throwsInvalidTemplateException() {
      assertThatThrownBy(() -> helper.validateTemplate("<<< not valid jslt >>>"))
          .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    @DisplayName("throws InvalidTemplateException for null template")
    void nullTemplate_throwsInvalidTemplateException() {
      assertThatThrownBy(() -> helper.validateTemplate(null))
          .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    @DisplayName("throws InvalidTemplateException for blank template")
    void blankTemplate_throwsInvalidTemplateException() {
      assertThatThrownBy(() -> helper.validateTemplate("   "))
          .isInstanceOf(InvalidTemplateException.class);
    }
  }

  @Nested
  @DisplayName("evictExpression")
  class EvictExpression {

    @Test
    @DisplayName("forces re-fetch from service on subsequent transform after eviction")
    void evict_forcesRefetchOnNextTransform() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));

      Map<String, String> input = Map.of("input", "hello");
      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);

      helper.evictExpression(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR);

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);

      verify(systemPropertiesService, times(2))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_REQUEST), eq(SELECTOR));
    }

    @Test
    @DisplayName("evicting one direction does not evict the other")
    void evict_onlyTargetedDirection() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.just(RESP_TEMPLATE));

      Map<String, Object> input = Map.of("input", "x", "output", "y");
      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);
      helper.transform(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR, input);

      helper.evictExpression(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR);

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, input);
      helper.transform(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR, input);

      verify(systemPropertiesService, times(2))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_REQUEST), eq(SELECTOR));
      verify(systemPropertiesService, times(1))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_RESPONSE), eq(SELECTOR));
    }
  }

  @Nested
  @DisplayName("evictAndReload")
  class EvictAndReload {

    @Test
    @DisplayName("returns template texts for both directions after reload")
    void returnsBothTemplates_afterReload() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.just(RESP_TEMPLATE));

      Map<String, String> map = helper.evictAndReload(SELECTOR);

      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), REQ_TEMPLATE);
      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), RESP_TEMPLATE);
    }

    @Test
    @DisplayName("returns empty string for a direction with no template in repository")
    void returnsEmptyString_whenDirectionHasNoTemplate() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.empty());
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.just(RESP_TEMPLATE));

      Map<String, String> map = helper.evictAndReload(SELECTOR);

      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), "");
      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), RESP_TEMPLATE);
    }

    @Test
    @DisplayName("swallows a compilation failure during reload and still returns the template text")
    void reload_invalidTemplate_swallowed_returnsText() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just("<<< bad jslt >>>"));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.empty());

      Map<String, String> map = helper.evictAndReload(SELECTOR);

      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), "<<< bad jslt >>>");
      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), "");
    }

    @Test
    @DisplayName("subsequent transform hits pre-warmed cache after evictAndReload")
    void subsequentTransform_hitsCache_afterEvictAndReload() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.empty());

      helper.evictAndReload(SELECTOR);

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "x"));

      verify(systemPropertiesService, times(1))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_REQUEST), eq(SELECTOR));
    }
  }

  @Nested
  @DisplayName("evictAll")
  class EvictAll {

    @Test
    @DisplayName("rewarms cache from getByGroupId for both directions")
    void rewarms_cachesAllTemplates() {
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST))
          .thenReturn(Flux.just(sp("client_spec_request", SELECTOR, REQ_TEMPLATE)));
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE))
          .thenReturn(Flux.empty());

      helper.evictAll();

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "hi"));

      verify(systemPropertiesService, never()).getRawProperty(any(), any());
    }

    @Test
    @DisplayName("completes successfully when all groups are empty")
    void completesSuccessfully_whenGroupsEmpty() {
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST))
          .thenReturn(Flux.empty());
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE))
          .thenReturn(Flux.empty());

      assertThatCode(() -> helper.evictAll()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("swallows a compilation failure during rewarm and negative-caches that selector")
    void rewarm_invalidTemplate_swallowedAndNegativeCached() {
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST))
          .thenReturn(Flux.just(sp("client_spec_request", SELECTOR, "<<< bad jslt >>>")));
      when(systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE))
          .thenReturn(Flux.empty());

      assertThatCode(() -> helper.evictAll()).doesNotThrowAnyException();

      // the bad entry is cached as a pass-through; transform does not re-fetch or throw
      JsonNode json = helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "x"));
      assertThat(json.get("input").asText()).isEqualTo("x");
      verify(systemPropertiesService, never()).getRawProperty(any(), any());
    }
  }

  @Nested
  @DisplayName("getTemplates")
  class GetTemplates {

    @Test
    @DisplayName("returns both request and response templates from service")
    void returnsBothTemplates() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.just(RESP_TEMPLATE));

      Map<String, String> map = helper.getTemplates(SELECTOR);

      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), REQ_TEMPLATE);
      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), RESP_TEMPLATE);
    }

    @Test
    @DisplayName("returns empty strings when templates are absent from repository")
    void returnsEmptyStrings_whenTemplatesAbsent() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.empty());
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.empty());

      Map<String, String> map = helper.getTemplates(SELECTOR);

      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), "");
      assertThat(map).containsEntry(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), "");
    }

    @Test
    @DisplayName("does not populate expression cache — transform re-fetches from service")
    void doesNotPopulateCache_transformRefetches() {
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR))
          .thenReturn(Mono.just(REQ_TEMPLATE));
      when(systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, SELECTOR))
          .thenReturn(Mono.just(RESP_TEMPLATE));

      helper.getTemplates(SELECTOR);

      helper.transform(TemplateGroup.CLIENT_SPEC_REQUEST, SELECTOR, Map.of("input", "x"));

      verify(systemPropertiesService, times(2))
          .getRawProperty(eq(TemplateGroup.CLIENT_SPEC_REQUEST), eq(SELECTOR));
    }
  }
}
