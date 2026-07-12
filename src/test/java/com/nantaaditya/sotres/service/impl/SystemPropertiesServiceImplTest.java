package com.nantaaditya.sotres.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("SystemPropertiesServiceImpl")
@ExtendWith(MockitoExtension.class)
class SystemPropertiesServiceImplTest {

  @Mock
  private SystemPropertiesRepository systemPropertiesRepository;

  private SystemPropertiesServiceImpl service;

  private static SystemProperties prop(String groupId, String propertyId, String value) {
    return SystemProperties.builder()
        .groupId(groupId)
        .propertyId(propertyId)
        .propertyValue(value)
        .build();
  }

  @BeforeEach
  void setUp() {
    when(systemPropertiesRepository.findAll()).thenReturn(Flux.empty());
    service = new SystemPropertiesServiceImpl(systemPropertiesRepository);
  }

  @Nested
  @DisplayName("getProperty(group)")
  class GetPropertyByGroup {

    @Test
    @DisplayName("returns empty map when group not loaded")
    void returnsEmptyMap_whenGroupNotLoaded() {
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS)).isEmpty();
    }

    @Test
    @DisplayName("returns map after reload")
    void returnsMap_afterReload() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);

      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS))
          .containsEntry("fractions", "360:2");
    }
  }

  @Nested
  @DisplayName("getProperty(group, propertyId)")
  class GetPropertyByGroupAndId {

    @Test
    @DisplayName("returns null when group not loaded")
    void returnsNull_whenGroupNotLoaded() {
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isNull();
    }

    @Test
    @DisplayName("returns value after reload")
    void returnsValue_afterReload() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);

      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
          .isEqualTo("360:2");
    }

    @Test
    @DisplayName("returns null for unknown propertyId within loaded group")
    void returnsNull_forUnknownPropertyId() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);

      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "unknown_key")).isNull();
    }
  }

  @Nested
  @DisplayName("onStart")
  class OnStart {

    @Test
    @DisplayName("loads all properties into cache on construction")
    void loadsAllProperties_onConstruction() {
      when(systemPropertiesRepository.findAll())
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")));

      SystemPropertiesServiceImpl freshService =
          new SystemPropertiesServiceImpl(systemPropertiesRepository);

      assertThat(freshService.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
          .isEqualTo("360:2");
    }

    @Test
    @DisplayName("skips properties with unrecognized groupId")
    void skipsUnrecognizedGroupId() {
      when(systemPropertiesRepository.findAll())
          .thenReturn(Flux.just(prop("unknown_group", "key", "value")));

      SystemPropertiesServiceImpl freshService =
          new SystemPropertiesServiceImpl(systemPropertiesRepository);

      assertThat(freshService.getProperty(ConfigGroup.CURRENCY_FRACTIONS)).isEmpty();
    }
  }

  @Nested
  @DisplayName("getRawProperty")
  class GetRawProperty {

    @Test
    @DisplayName("returns property value from repository when found")
    void returnsValue_whenFound() {
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Mono.just(prop("client_spec_request", "10.97-E001", "{\"result\": .value}")));

      StepVerifier.create(service.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001"))
          .expectNext("{\"result\": .value}")
          .verifyComplete();
    }

    @Test
    @DisplayName("returns empty Mono when property not found in repository")
    void returnsEmpty_whenNotFound() {
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "unknown"))
          .thenReturn(Mono.empty());

      StepVerifier.create(service.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, "unknown"))
          .verifyComplete();
    }

    @Test
    @DisplayName("delegates to repository with correct groupId and selector")
    void delegatesToRepository_withCorrectArguments() {
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_response", "10.97-E001"))
          .thenReturn(Mono.just(prop("client_spec_response", "10.97-E001", "{\"mapped\": .field}")));

      StepVerifier.create(service.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, "10.97-E001"))
          .expectNext("{\"mapped\": .field}")
          .verifyComplete();
    }
  }

  @Nested
  @DisplayName("getByGroupId")
  class GetByGroupId {

    @Test
    @DisplayName("returns all properties for the group from repository")
    void returnsAll_forGroup() {
      SystemProperties sp1 = prop("client_spec_request", "10.97-E001", "template-a");
      SystemProperties sp2 = prop("client_spec_request", "20.50-A001", "template-b");
      when(systemPropertiesRepository.findByGroupId("client_spec_request"))
          .thenReturn(Flux.just(sp1, sp2));

      StepVerifier.create(service.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST))
          .expectNext(sp1, sp2)
          .verifyComplete();
    }

    @Test
    @DisplayName("returns empty Flux when group has no properties")
    void returnsEmpty_whenGroupHasNoProperties() {
      when(systemPropertiesRepository.findByGroupId("client_spec_response"))
          .thenReturn(Flux.empty());

      StepVerifier.create(service.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE))
          .verifyComplete();
    }
  }

  @Nested
  @DisplayName("upsert")
  class Upsert {

    @Test
    @DisplayName("inserts new record when selector not found in repository")
    void upsert_insertsNewRecord_whenNotFound() {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();

      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Mono.empty());
      when(systemPropertiesRepository.save(any(SystemProperties.class))).thenReturn(Mono.just(saved));

      StepVerifier.create(service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .assertNext(sp -> {
            assertThat(sp.getId()).isEqualTo(1L);
            assertThat(sp.getPropertyValue()).isEqualTo("{\"result\": .value}");
          })
          .verifyComplete();
    }

    @Test
    @DisplayName("updates existing record when selector found in repository")
    void upsert_updatesExistingRecord_whenFound() {
      SystemProperties existing = SystemProperties.builder()
          .id(5L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("old_template").build();
      SystemProperties updated = existing.toBuilder().propertyValue("{\"new\": .value}").build();

      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Mono.just(existing));
      when(systemPropertiesRepository.save(updated)).thenReturn(Mono.just(updated));

      StepVerifier.create(service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"new\": .value}"))
          .assertNext(sp -> {
            assertThat(sp.getId()).isEqualTo(5L);
            assertThat(sp.getPropertyValue()).isEqualTo("{\"new\": .value}");
          })
          .verifyComplete();
    }

    @Test
    @DisplayName("saves to repository and does not contaminate the flat-config cache")
    void upsert_savesToRepository_doesNotContaminateConfigCache() {
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();

      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Mono.empty());
      when(systemPropertiesRepository.save(any(SystemProperties.class))).thenReturn(Mono.just(saved));

      StepVerifier.create(service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}"))
          .assertNext(sp -> {
            assertThat(sp.getId()).isEqualTo(1L);
            assertThat(sp.getGroupId()).isEqualTo("client_spec_request");
          })
          .verifyComplete();

      // Template group rows must not bleed into the flat-config cache
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS)).isEmpty();
    }
  }

  @Nested
  @DisplayName("reload")
  class Reload {

    @Test
    @DisplayName("refreshes only the specified group")
    void refreshesOnlySpecifiedGroup() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")));
      when(systemPropertiesRepository.findByGroupId("acquirers"))
          .thenReturn(Flux.just(prop("acquirers", "acquirers", "BANK_A")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      service.reload(ConfigGroup.ACQUIRERS);

      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
          .isEqualTo("360:2");
      assertThat(service.getProperty(ConfigGroup.ACQUIRERS, "acquirers"))
          .isEqualTo("BANK_A");
    }

    @Test
    @DisplayName("overwrites existing value on reload")
    void overwritesExistingValue_onReload() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(Flux.just(prop("currency", "fractions", "360:2")))
          .thenReturn(Flux.just(prop("currency", "fractions", "840:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo(
          "360:2");

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo(
          "840:2");
    }
  }
}
