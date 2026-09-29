package com.nantaaditya.sotres.helper;

import com.github.kpavlov.jreactive8583.iso.ISO8583Version;
import com.github.kpavlov.jreactive8583.iso.J8583MessageFactory;
import com.github.kpavlov.jreactive8583.iso.MessageOrigin;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.impl.SimpleTraceGenerator;
import com.solab.iso8583.parse.ConfigParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.log4j.Log4j2;
import org.xml.sax.InputSource;

@Log4j2
public class MessageFactoryHelper {

  private CustomMessageFactory<IsoMessage> customMessageFactory = null;
  private J8583MessageFactory j8583MessageFactory = null;

  private static final int STAN = 1_000_000;

  public MessageFactoryHelper(PackagerConstant packager) {
    try {
      this.customMessageFactory = getMessageFactory(packager);
      configureFactory(this.customMessageFactory);
      this.j8583MessageFactory = new J8583MessageFactory<>(this.customMessageFactory, ISO8583Version.V1987, MessageOrigin.OTHER);
    } catch (IOException e) {
      throw new IllegalStateException("could not initialize packager: " + packager, e);
    }
  }

  public J8583MessageFactory getDefaultMessageFactory() {
    return this.j8583MessageFactory;
  }

  private CustomMessageFactory<IsoMessage> getMessageFactory(
      PackagerConstant packagerKey) throws IOException {

    return packagerKey == null ?
        CustomConfigParser.createDefault()
        : CustomConfigParser.createFromClasspathConfig(packagerKey.getPath());
  }

  private void configureFactory(com.solab.iso8583.MessageFactory<IsoMessage> factory) {
    factory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
    factory.setUseBinaryMessages(false);
    factory.setForceSecondaryBitmap(true);
    factory.setAssignDate(true);
    factory.setTraceNumberGenerator(
        new SimpleTraceGenerator((int) (System.currentTimeMillis() % STAN))
    );
  }

  public com.github.kpavlov.jreactive8583.iso.MessageFactory<IsoMessage> createMessageFactory(PackagerConstant packagerKey) {
    try {
      com.solab.iso8583.MessageFactory<IsoMessage> factory = getMessageFactory(packagerKey);
      configureFactory(factory);
      return new J8583MessageFactory<>(factory, ISO8583Version.V1987, MessageOrigin.OTHER);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid packager config file: " + packagerKey, e);
    }
  }

  static class CustomMessageFactory<T extends IsoMessage> extends MessageFactory<T> { }

  static class CustomConfigParser extends ConfigParser {
    public static CustomMessageFactory createFromClasspathConfig(String path) throws IOException {
      return createFromClasspathConfig(CustomMessageFactory.class.getClassLoader(), path);
    }

    public static CustomMessageFactory createDefault() throws IOException {
      return createDefault(CustomMessageFactory.class.getClassLoader());
    }

    public static CustomMessageFactory createDefault(
        ClassLoader loader) throws IOException {
      if (loader.getResource("j8583.xml") == null) {
        log.warn(AppLogMessage.message("ISO8583 ConfigParser cannot find j8583.xml, returning empty message factory"));
        return new CustomMessageFactory<>();
      } else {
        return createFromClasspathConfig(loader, "j8583.xml");
      }
    }

    public static CustomMessageFactory createFromClasspathConfig(
        ClassLoader loader, String path) throws IOException {
      CustomMessageFactory mfact = new CustomMessageFactory<>();
      try (InputStream ins = loader.getResourceAsStream(path)) {
        if (ins != null) {
          log.debug(AppLogMessage.message("ISO8583 Parsing config from classpath file {}", path));
          parse(mfact, new InputSource(ins));
        } else {
          log.error(AppLogMessage.message("ISO8583 File not found in classpath: {}", path));
        }
      }
      return mfact;
    }
  }
}
