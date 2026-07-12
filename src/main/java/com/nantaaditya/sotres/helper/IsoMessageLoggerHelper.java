package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IsoMessageLoggerHelper {

  private final SystemPropertiesService systemPropertiesService;

  public IsoMessageLoggerHelper(SystemPropertiesService systemPropertiesService) {
    this.systemPropertiesService = systemPropertiesService;
  }

  public void logIsoMessage(IsoMessage isoMessage) {
    JsonLogIsoMessage logIsoMessage = toLogMessage(isoMessage);
    log.info(AppLogMessage.message("#ISO").isoMessage(logIsoMessage));
  }

  public JsonLogIsoMessage toLogMessage(IsoMessage message) {
    String direction = getDirection(message);
    String mti = String.format("%04x", message.getType());

    try {
      Map<String, String> dataElements = new LinkedHashMap<>();
      for (int i = 2; i <= 127; i++) {
        if (message.hasField(i)) {
          IsoValue<?> field = message.getField(i);
          String value = field.toString();
          dataElements.put(String.valueOf(i), getMaskedFields().contains(i) ? maskingValue(value, i) : value);
        }
      }

      return new JsonLogIsoMessage(mti, direction, dataElements);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Log - failed to serialize ISO8583 message. with message : {}", e.getMessage()).error(e));
      return null;
    }
  }

  private String getDirection(IsoMessage message) {
    String direction;
    if (getMTI(ConfigGroup.INCOMING_MTI).contains(message.getType())) {
      return "incoming";
    } else if (getMTI(ConfigGroup.OUTGOING_MTI).contains(message.getType())) {
      return "outgoing";
    } else {
      return "unknown";
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
    return ConfigGroup.getList(systemPropertiesService, ConfigGroup.ISO8583_MASK_FIELDS)
      .stream()
      .map(String::trim)
      .map(Integer::parseInt)
      .collect(Collectors.toSet());
  }

  private Set<Integer> getMTI(ConfigGroup group) {
    return ConfigGroup.getList(systemPropertiesService, group)
      .stream()
      .map(String::trim)
      .map(Integer::parseInt)
      .collect(Collectors.toSet());
  }

}
