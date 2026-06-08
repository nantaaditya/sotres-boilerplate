package com.nantaaditya.sotres.model.constant;

import com.nantaaditya.sotres.helper.StringHelper;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public enum PropertiesGroup {
  ISO8583_MASK_FIELDS("mask_fields", "iso8583"),
  ACQUIRERS("acquirers", "acquirers"),
  INCOMING_MTI("mti", "incoming"),
  OUTGOING_MTI("mti", "outgoing"),
  CURRENCY_FRACTIONS("currency", "fractions"),
  PATH_MAPPING("endpoint_path", "mapping"),
  RESPONSE_MAPPING("response", "incoming_outgoing_mapping"),
  REGISTRY_RESPONSE_SELECTOR("registry", "response_selector"), // selector when use sendWithResponse
  REGISTRY_CALLBACK_SELECTOR("registry", "callback_selector"), // selector when use sendWithCallback
  CLIENT_SPEC_REQUEST("client_specification_mapper", "request"),
  CLIENT_SPEC_RESPONSE("client_specification_mapper", "response");

  private String group;
  private String propertyId;

  PropertiesGroup(String group, String propertyId) {
    this.group = group;
    this.propertyId = propertyId;
  }

  public static List<String> getList(SystemPropertiesService systemPropertiesService, PropertiesGroup group) {
    return (List<String>) StringHelper.toCollection(
        systemPropertiesService.getProperty(group, group.getPropertyId()),
        ",",
        ArrayList.class
    );
  }

  public static Map<String, String> getMap(SystemPropertiesService systemPropertiesService, PropertiesGroup group) {
    return (Map<String, String>) StringHelper.toCollection(
        systemPropertiesService.getProperty(group, group.getPropertyId()),
        ",",
        ":",
        HashMap.class
    );
  }
}
