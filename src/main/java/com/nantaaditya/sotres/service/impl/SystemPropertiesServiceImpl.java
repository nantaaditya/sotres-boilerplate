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
import reactor.core.scheduler.Schedulers;

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
    // TODO(refactor): direct blocking load; the Reactor bridges below go away when
    // the web + ISO layers are blocking (Phases 2-3) and this interface can too.
    try {
      systemPropertiesRepository.findAll().forEach(this::loadSystemProperties);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#CONFIGURATION - error while loading properties").error(e));
    }
  }

  @Override
  public Map<String, String> getProperty(ConfigGroup key) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, Map.of());
  }

  @Override
  public String getProperty(ConfigGroup key, String propertyId) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, Map.of()).get(propertyId);
  }

  @Override
  public void reload(ConfigGroup key) {
    try {
      systemPropertiesRepository.findByGroupId(key.getGroup()).forEach(this::loadSystemProperties);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#CONFIGURATION - error while loading properties key {}", key.getGroup()).error(e));
    }
  }

  @Override
  public Mono<String> getRawProperty(TemplateGroup group, String selector) {
    return Mono.fromCallable(() -> systemPropertiesRepository
            .findByGroupIdAndPropertyId(group.getGroup(), selector)
            .map(SystemProperties::getPropertyValue)
            .orElse(null))
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public Flux<SystemProperties> getByGroupId(TemplateGroup group) {
    return Mono.fromCallable(() -> systemPropertiesRepository.findByGroupId(group.getGroup()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
  }

  @Override
  public Mono<SystemProperties> upsert(TemplateGroup group, String selector, String value) {
    return Mono.fromCallable(() -> {
      SystemProperties entity = systemPropertiesRepository
          .findByGroupIdAndPropertyId(group.getGroup(), selector)
          .map(existing -> existing.toBuilder().propertyValue(value).build())
          .orElseGet(() -> SystemProperties.builder()
              .groupId(group.getGroup())
              .propertyId(selector)
              .propertyValue(value)
              .build());
      return systemPropertiesRepository.save(entity);
    }).subscribeOn(Schedulers.boundedElastic());
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
