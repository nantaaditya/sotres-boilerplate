package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SystemPropertiesService {
  Map<String, String> getProperty(ConfigGroup key);
  String getProperty(ConfigGroup key, String propertyId);
  void reload(ConfigGroup key);
  Mono<String> getRawProperty(TemplateGroup group, String selector);
  Flux<SystemProperties> getByGroupId(TemplateGroup group);
  Mono<SystemProperties> upsert(TemplateGroup group, String selector, String value);
}
