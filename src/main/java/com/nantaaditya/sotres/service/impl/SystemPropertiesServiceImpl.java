package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.JsltPropertyGroup;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Collections;
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

  // [PropertiesGroup: [propertyId: propertyValue]] — enum-keyed parsed config
  private final Map<PropertiesGroup, Map<String, String>> PROPERTY_COLLECTION_MAP = new ConcurrentHashMap<>();

  // [groupId: [propertyId: propertyValue]] — string-keyed raw config (JSLT templates etc.)
  private final Map<String, Map<String, String>> RAW_PROPERTY_MAP = new ConcurrentHashMap<>();

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

    Flux.concat(
        systemPropertiesRepository.findByGroupId(JsltPropertyGroup.REQUEST_GROUP),
        systemPropertiesRepository.findByGroupId(JsltPropertyGroup.RESPONSE_GROUP)
    ).subscribe(
        this::loadRawProperty,
        error -> log.error(AppLogMessage.message("#CONFIGURATION - error while loading JSLT templates").error(error))
    );
  }

  @Override
  public Map<String, String> getProperty(PropertiesGroup key) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, new ConcurrentHashMap<>());
  }

  @Override
  public String getProperty(PropertiesGroup key, String propertyId) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key, new ConcurrentHashMap<>()).get(propertyId);
  }

  @Override
  public void reload(PropertiesGroup key) {
    systemPropertiesRepository.findByGroupId(key.getGroup())
      .subscribe(
        this::loadSystemProperties,
        error -> log.error(AppLogMessage.message("#CONFIGURATION - error while loading properties key {}", key.getGroup()).error(error))
      );
  }

  @Override
  public Mono<String> getRawProperty(String groupId, String propertyId) {
    String cached = RAW_PROPERTY_MAP
        .getOrDefault(groupId, Collections.emptyMap())
        .get(propertyId);
    if (cached != null) {
      return Mono.just(cached);
    }
    return systemPropertiesRepository.findByGroupIdAndPropertyId(groupId, propertyId)
        .map(sp -> {
          loadRawProperty(sp);
          return sp.getPropertyValue();
        });
  }

  @Override
  public Flux<SystemProperties> getByGroupId(String groupId) {
    return systemPropertiesRepository.findByGroupId(groupId);
  }

  private void loadSystemProperties(SystemProperties sp) {
    for (PropertiesGroup group : PropertiesGroup.values()) {
      if (group.getGroup().equals(sp.getGroupId())) {
        PROPERTY_COLLECTION_MAP
          .computeIfAbsent(group, k -> new ConcurrentHashMap<>())
          .put(sp.getPropertyId(), sp.getPropertyValue());
      }
    }
  }

  private void loadRawProperty(SystemProperties sp) {
    RAW_PROPERTY_MAP
        .computeIfAbsent(sp.getGroupId(), k -> new ConcurrentHashMap<>())
        .put(sp.getPropertyId(), sp.getPropertyValue());
  }
}
