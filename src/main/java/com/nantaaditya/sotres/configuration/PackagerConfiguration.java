package com.nantaaditya.sotres.configuration;

import com.github.kpavlov.jreactive8583.iso.ISO8583Version;
import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.github.kpavlov.jreactive8583.iso.MessageOrigin;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PackagerConfiguration {

  private final SystemPropertiesService systemPropertiesService;

  public MessageFactory<IsoMessage> createMessageFactory(PackagerConstant packagerKey) {
    try {
      com.solab.iso8583.MessageFactory<IsoMessage> factory = getMessageFactory(packagerKey);
      factory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
      factory.setUseBinaryMessages(false);
      factory.setForceSecondaryBitmap(true);
      factory.setAssignDate(true);
      factory.setTraceNumberGenerator(
          new SimpleTraceGenerator((int) (System.currentTimeMillis() % 1_000_000))
      );
      return new J8583MessageFactory<>(factory, ISO8583Version.V1987, MessageOrigin.OTHER);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid packager config file: " + packagerKey, e);
    }
  }

  private com.solab.iso8583.MessageFactory<IsoMessage> getMessageFactory(
      PackagerConstant packagerKey) throws IOException {

    Map<String, String> packagers = PropertiesGroup.getMap(systemPropertiesService, PropertiesGroup.PACKAGERS);
    return packagerKey == null ?
        ConfigParser.createDefault()
        : ConfigParser.createFromClasspathConfig(packagers.get(packagerKey.name()));
  }
}
