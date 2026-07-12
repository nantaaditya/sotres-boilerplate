package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class SystemPropertiesServiceImpl implements SystemPropertiesService {

  private final SystemPropertiesRepository systemPropertiesRepository;

  // [ConfigGroup: [propertyId: propertyValue]]
  private final Map<ConfigGroup, Map<String, String>> PROPERTY_COLLECTION_MAP = new ConcurrentHashMap<>();

  public SystemPropertiesServiceImpl(SystemPropertiesRepository systemPropertiesRepository) {
    this.systemPropertiesRepository = systemPropertiesRepository;
    onStart();
  }

  public void onStart() {
    systemPropertiesRepository.findAll()
      .subscribe(
        this::loadSystemProperties,
        error -> log.error(AppLogMessage.message("#CONFIGURATION - error while loading properties").error(error))
      );
  }

  @Override
  public Map<String, String> getProperty(ConfigGroup key) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, new ConcurrentHashMap<>());
  }

  @Override
  public String getProperty(ConfigGroup key, String propertyId) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, new ConcurrentHashMap<>()).get(propertyId);
  }

  @Override
  public void reload(ConfigGroup key) {
    systemPropertiesRepository.findByGroupId(key.getGroup())
      .subscribe(
        this::loadSystemProperties,
        error -> log.error(AppLogMessage.message("#CONFIGURATION - error while loading properties key {}", key.getGroup()).error(error))
      );
  }

  @Override
  public Mono<String> getRawProperty(TemplateGroup group, String selector) {
    return systemPropertiesRepository
        .findByGroupIdAndPropertyId(group.getGroup(), selector)
        .map(SystemProperties::getPropertyValue);
  }

  @Override
  public Flux<SystemProperties> getByGroupId(TemplateGroup group) {
    return systemPropertiesRepository.findByGroupId(group.getGroup());
  }

  @Override
  public Mono<SystemProperties> upsert(TemplateGroup group, String selector, String value) {
    return systemPropertiesRepository
        .findByGroupIdAndPropertyId(group.getGroup(), selector)
        .map(existing -> existing.toBuilder().propertyValue(value).build())
        .switchIfEmpty(Mono.just(SystemProperties.builder()
            .groupId(group.getGroup())
            .propertyId(selector)
            .propertyValue(value)
            .build()))
        .flatMap(entity -> systemPropertiesRepository.save(entity));
  }

  private void loadSystemProperties(SystemProperties sp) {
    for (ConfigGroup group : ConfigGroup.values()) {
      if (group.getGroup().equals(sp.getGroupId())) {
        PROPERTY_COLLECTION_MAP
          .computeIfAbsent(group, k -> new ConcurrentHashMap<>())
          .put(sp.getPropertyId(), sp.getPropertyValue());
      }
    }
  }
}
