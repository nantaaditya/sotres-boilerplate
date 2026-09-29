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

  public static final String INCOMING_ISO = "incoming";
  public static final String OUTGOING_ISO = "outgoing";

  private final SystemPropertiesService systemPropertiesService;

  public IsoMessageLoggerHelper(SystemPropertiesService systemPropertiesService) {
    this.systemPropertiesService = systemPropertiesService;
  }

  public void logIsoMessage(IsoMessage isoMessage, String direction) {
    JsonLogIsoMessage logIsoMessage = toLogMessage(isoMessage, direction);
    log.info(AppLogMessage.message("#ISO").isoMessage(logIsoMessage));
  }

  public JsonLogIsoMessage toLogMessage(IsoMessage message, String direction) {
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

      return new JsonLogIsoMessage(direction, mti, dataElements);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Log - failed to serialize ISO8583 message. with message : {}", e.getMessage()).error(e));
      return null;
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
    return getConfiguredIntSet(ConfigGroup.ISO8583_MASK_FIELDS);
  }

  private Set<Integer> getConfiguredIntSet(ConfigGroup group) {
    return ConfigGroup.getList(systemPropertiesService, group)
      .stream()
      .map(String::trim)
      .map(Integer::parseInt)
      .collect(Collectors.toSet());
  }

}
