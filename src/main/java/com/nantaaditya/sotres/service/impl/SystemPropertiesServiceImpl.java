package com.nantaaditya.sotres.service.impl;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SystemPropertiesServiceImpl implements SystemPropertiesService {

  private final SystemPropertiesRepository systemPropertiesRepository;

  // [groupId: [propertyId: propertyValue]]
  private static final Map<PropertiesGroup, Map<String, String>> PROPERTY_COLLECTION_MAP = new ConcurrentHashMap<>();

  public SystemPropertiesServiceImpl(SystemPropertiesRepository systemPropertiesRepository) {
    this.systemPropertiesRepository = systemPropertiesRepository;
    onStart();
  }

  public void onStart() {
    systemPropertiesRepository.findAll()
      .subscribe(
        this::loadSystemProperties,
        error -> {
          log.error("#CONFIGURATION - error while loading properties {}, cause {}", error.getMessage(),
              ErrorHelper.getRootCause(error));
        }
      );
  }

  @Override
  public Map<String, String> getProperty(PropertiesGroup key) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(key.getGroup(), new ConcurrentHashMap<>());
  }

  @Override
  public String getProperty(PropertiesGroup key, String propertyId) {
    return PROPERTY_COLLECTION_MAP.getOrDefault(
        key.getGroup(),
        new ConcurrentHashMap<>()
    ).get(propertyId);
  }

  @Override
  public void reload(PropertiesGroup key) {
    systemPropertiesRepository.findByGroupId(key.getGroup())
      .subscribe(
        this::loadSystemProperties,
        error -> {
          log.error("#CONFIGURATION - error while loading properties key {}, error {} cause {}",
              key.getGroup(), error.getMessage(), ErrorHelper.getRootCause(error));
        }
      );
  }

  private void loadSystemProperties(SystemProperties systemProperties) {
    for (PropertiesGroup group : PropertiesGroup.values()) {
      if (group.getGroup().equals(systemProperties.getGroupId())) {

        if (!PROPERTY_COLLECTION_MAP.containsKey(systemProperties.getGroupId())) {
          PROPERTY_COLLECTION_MAP.put(group, new ConcurrentHashMap<>());
        }

        Map<String, String> propertyMap = PROPERTY_COLLECTION_MAP.get(systemProperties.getGroupId());
        if (!propertyMap.containsKey(systemProperties.getPropertyId())) {
          PROPERTY_COLLECTION_MAP.put(group, propertyMap);
        }

        propertyMap.put(systemProperties.getPropertyId(), systemProperties.getPropertyValue());
        PROPERTY_COLLECTION_MAP.put(group, propertyMap);
      }
    }
  }
}
