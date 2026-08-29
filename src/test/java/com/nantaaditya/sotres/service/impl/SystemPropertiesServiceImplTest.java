package com.nantaaditya.sotres.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    when(systemPropertiesRepository.findAll()).thenReturn(List.of());
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
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));

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
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);

      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
          .isEqualTo("360:2");
    }

    @Test
    @DisplayName("returns null for unknown propertyId within loaded group")
    void returnsNull_forUnknownPropertyId() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));

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
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));

      SystemPropertiesServiceImpl freshService =
          new SystemPropertiesServiceImpl(systemPropertiesRepository);

      assertThat(freshService.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
          .isEqualTo("360:2");
    }

    @Test
    @DisplayName("skips properties with unrecognized groupId")
    void skipsUnrecognizedGroupId() {
      when(systemPropertiesRepository.findAll())
          .thenReturn(List.of(prop("unknown_group", "key", "value")));

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
          .thenReturn(Optional.of(prop("client_spec_request", "10.97-E001", "{\"result\": .value}")));

      assertThat(service.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001"))
          .isEqualTo("{\"result\": .value}");
    }

    @Test
    @DisplayName("returns null when property not found in repository")
    void returnsNull_whenNotFound() {
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "unknown"))
          .thenReturn(Optional.empty());

      assertThat(service.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, "unknown")).isNull();
    }

    @Test
    @DisplayName("delegates to repository with correct groupId and selector")
    void delegatesToRepository_withCorrectArguments() {
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_response", "10.97-E001"))
          .thenReturn(Optional.of(prop("client_spec_response", "10.97-E001", "{\"mapped\": .field}")));

      assertThat(service.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, "10.97-E001"))
          .isEqualTo("{\"mapped\": .field}");
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
          .thenReturn(List.of(sp1, sp2));

      assertThat(service.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST))
          .containsExactly(sp1, sp2);
    }

    @Test
    @DisplayName("returns empty list when group has no properties")
    void returnsEmpty_whenGroupHasNoProperties() {
      when(systemPropertiesRepository.findByGroupId("client_spec_response"))
          .thenReturn(List.of());

      assertThat(service.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE)).isEmpty();
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
          .thenReturn(Optional.empty());
      when(systemPropertiesRepository.save(any(SystemProperties.class))).thenReturn(saved);

      SystemProperties sp = service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}");

      assertThat(sp.getId()).isEqualTo(1L);
      assertThat(sp.getPropertyValue()).isEqualTo("{\"result\": .value}");
    }

    @Test
    @DisplayName("updates existing record when selector found in repository")
    void upsert_updatesExistingRecord_whenFound() {
      SystemProperties existing = SystemProperties.builder()
          .id(5L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("old_template").build();
      SystemProperties updated = existing.toBuilder().propertyValue("{\"new\": .value}").build();

      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Optional.of(existing));
      when(systemPropertiesRepository.save(updated)).thenReturn(updated);

      SystemProperties sp = service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"new\": .value}");

      assertThat(sp.getId()).isEqualTo(5L);
      assertThat(sp.getPropertyValue()).isEqualTo("{\"new\": .value}");
    }

    @Test
    @DisplayName("saves to repository and does not contaminate the flat-config cache")
    void upsert_savesToRepository_doesNotContaminateConfigCache() {
      // Pre-populate a flat-config entry so we can detect contamination
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));
      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo("360:2");

      // Upsert a template-group record
      SystemProperties saved = SystemProperties.builder()
          .id(1L).groupId("client_spec_request").propertyId("10.97-E001")
          .propertyValue("{\"result\": .value}").build();
      when(systemPropertiesRepository.findByGroupIdAndPropertyId("client_spec_request", "10.97-E001"))
          .thenReturn(Optional.empty());
      when(systemPropertiesRepository.save(any(SystemProperties.class))).thenReturn(saved);

      SystemProperties sp = service.upsert(TemplateGroup.CLIENT_SPEC_REQUEST, "10.97-E001", "{\"result\": .value}");
      assertThat(sp.getId()).isEqualTo(1L);
      assertThat(sp.getGroupId()).isEqualTo("client_spec_request");

      // Flat-config cache must be unchanged after template upsert
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo("360:2");
    }
  }

  @Nested
  @DisplayName("reload")
  class Reload {

    @Test
    @DisplayName("refreshes only the specified group")
    void refreshesOnlySpecifiedGroup() {
      when(systemPropertiesRepository.findByGroupId("currency"))
          .thenReturn(List.of(prop("currency", "fractions", "360:2")));
      when(systemPropertiesRepository.findByGroupId("acquirers"))
          .thenReturn(List.of(prop("acquirers", "acquirers", "BANK_A")));

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
          .thenReturn(List.of(prop("currency", "fractions", "360:2")))
          .thenReturn(List.of(prop("currency", "fractions", "840:2")));

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo(
          "360:2");

      service.reload(ConfigGroup.CURRENCY_FRACTIONS);
      assertThat(service.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions")).isEqualTo(
          "840:2");
    }
  }
}
