package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import java.util.Map;

public interface SystemPropertiesService {
  Map<String, String> getProperty(PropertiesGroup key);
  String getProperty(PropertiesGroup key, String propertyId);
  void reload(PropertiesGroup key);
}
