package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SystemPropertiesService {
  Map<String, String> getProperty(PropertiesGroup key);
  String getProperty(PropertiesGroup key, String propertyId);
  void reload(PropertiesGroup key);
  Mono<String> getRawProperty(String groupId, String propertyId);
  Flux<SystemProperties> getByGroupId(String groupId);
}
