package com.nantaaditya.sotres.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IsoMessageLoggerHelper {

  private final IsoMessageProperties isoMessageProperties;
  private final ObjectMapper objectMapper;
  private final SystemPropertiesService systemPropertiesService;
  @Value("${app.log-style}")
  private String logStyle;

  public IsoMessageLoggerHelper(SystemPropertiesService systemPropertiesService,
      IsoMessageProperties isoMessageProperties, ObjectMapper objectMapper) {
    this.systemPropertiesService = systemPropertiesService;
    this.isoMessageProperties = isoMessageProperties;
    this.objectMapper = objectMapper;
  }

  public void logIsoMessage(IsoMessage isoMessage) {
    Map<String, Object> jsonMessage = toJson(isoMessage);

    switch (logStyle) {
      case "json": {
        log.info("{}", jsonMessage);
        break;
      }
      case "text": {
        try {
          String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMessage);
          log.info("{}", json);
        } catch (Exception e) {
          ErrorHelper.loggingError("#Log - failed to serialize ISO8583 message. with message : {} , and root cause : {}", e);
        }
        break;
      }
    }
  }

  private Map<String, Object> toJson(IsoMessage message) {
    Map<String, Object> jsonMap = new LinkedHashMap<>();

    try {
      if (getMTI(PropertiesGroup.INCOMING_MTI).contains(message.getType())) {
        jsonMap.put("direction", "incoming");
      } else if (getMTI(PropertiesGroup.OUTGOING_MTI).contains(message.getType())) {
        jsonMap.put("direction", "outgoing");
      }

      jsonMap.put("MTI", String.format("%04x", message.getType()));
      for (int i = 2; i <= 127; i++) {
        if (message.hasField(i)) {
          IsoValue<?> field = message.getField(i);
          String value = field.toString();
          jsonMap.put(String.valueOf(i), getMaskedFields().contains(i) ? maskingValue(value, i) : value);
        }
      }

      return jsonMap;
    } catch (Exception e) {
      ErrorHelper.loggingError("#Log - failed to serialize ISO8583 message. with message : {} , and root cause : {}", e);
      return Map.of("error", "failed to serialize ISO8583 message");
    }
  }

  private static String maskingValue(String value, int field) {
    if (field == 2) {
      return MaskingHelper.masking(value, 6, 4);
    }
    return MaskingHelper.masking(value);
  }

  @NotNull
  private Set<Integer> getMaskedFields() {
    return PropertiesGroup.getList(systemPropertiesService, PropertiesGroup.ISO8583_MASK_FIELDS)
      .stream()
      .map(String::trim)
      .map(Integer::parseInt)
      .collect(Collectors.toSet());
  }

  private Set<Integer> getMTI(PropertiesGroup group) {
    return PropertiesGroup.getList(systemPropertiesService, group)
      .stream()
      .map(String::trim)
      .map(Integer::parseInt)
      .collect(Collectors.toSet());
  }

}
