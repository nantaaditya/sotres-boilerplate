package com.nantaaditya.sotres.configuration;

import com.github.kpavlov.jreactive8583.iso.ISO8583Version;
import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.github.kpavlov.jreactive8583.iso.MessageOrigin;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class PackagerConfiguration {

  private static final int STAN = 1_000_000;

  public MessageFactory<IsoMessage> createMessageFactory(PackagerConstant packagerKey) {
    try {
      com.solab.iso8583.MessageFactory<IsoMessage> factory = getMessageFactory(packagerKey);
      factory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
      factory.setUseBinaryMessages(false);
      factory.setForceSecondaryBitmap(true);
      factory.setAssignDate(true);
      factory.setTraceNumberGenerator(
          new SimpleTraceGenerator((int) (System.currentTimeMillis() % STAN))
      );
      return new J8583MessageFactory<>(factory, ISO8583Version.V1987, MessageOrigin.OTHER);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid packager config file: " + packagerKey, e);
    }
  }

  private com.solab.iso8583.MessageFactory<IsoMessage> getMessageFactory(
      PackagerConstant packagerKey) throws IOException {

    return packagerKey == null ?
        ConfigParser.createDefault()
        : ConfigParser.createFromClasspathConfig(packagerKey.getPath());
  }
}
