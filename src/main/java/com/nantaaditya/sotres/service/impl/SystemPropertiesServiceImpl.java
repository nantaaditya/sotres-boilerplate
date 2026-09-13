package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class SystemPropertiesServiceImpl implements SystemPropertiesService {

  private final SystemPropertiesRepository systemPropertiesRepository;
  private final JsltTransformationHelper jsltTransformationHelper;

  // [ConfigGroup: [propertyId: propertyValue]]
  private final Map<ConfigGroup, Map<String, String>> PROPERTY_COLLECTION_MAP = new ConcurrentHashMap<>();

  /**
   * Warm the in-memory routing cache once the context is up — never from the constructor, so a
   * slow or unavailable database does not block bean creation / application start.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onStart() {
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
  public String getRawProperty(TemplateGroup group, String selector) {
    return systemPropertiesRepository
        .findByGroupIdAndPropertyId(group.getGroup(), selector)
        .map(SystemProperties::getPropertyValue)
        .orElse(null);
  }

  @Override
  public List<SystemProperties> getByGroupId(TemplateGroup group) {
    return systemPropertiesRepository.findByGroupId(group.getGroup());
  }

  @Override
  public SystemProperties upsert(TemplateGroup group, String selector, String value) {
    jsltTransformationHelper.validateTemplate(value);

    SystemProperties entity = systemPropertiesRepository
        .findByGroupIdAndPropertyId(group.getGroup(), selector)
        .map(existing -> existing.toBuilder().propertyValue(value).build())
        .orElseGet(() -> SystemProperties.builder()
            .groupId(group.getGroup())
            .propertyId(selector)
            .propertyValue(value)
            .build()
        );
    SystemProperties result = systemPropertiesRepository.save(entity);
    jsltTransformationHelper.evictExpression(group, selector);
    return result;
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
