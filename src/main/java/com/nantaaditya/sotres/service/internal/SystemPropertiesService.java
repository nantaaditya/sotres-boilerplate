package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import java.util.List;
import java.util.Map;

public interface SystemPropertiesService {
  Map<String, String> getProperty(ConfigGroup key);
  String getProperty(ConfigGroup key, String propertyId);
  void reload(ConfigGroup key);
  String getRawProperty(TemplateGroup group, String selector);
  List<SystemProperties> getByGroupId(TemplateGroup group);
  SystemProperties upsert(TemplateGroup group, String selector, String value);
}
