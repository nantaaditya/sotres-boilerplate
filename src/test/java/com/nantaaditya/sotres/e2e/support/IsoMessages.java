package com.nantaaditya.sotres.e2e.support;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds inbound ISO8583 test messages using the application packager
 * ({@code default-packager.xml}), configured identically to
 * {@code MessageFactoryHelper}.
 */
public final class IsoMessages {

  private static final MessageFactory<IsoMessage> FACTORY = build();

  private IsoMessages() {}

  private static MessageFactory<IsoMessage> build() {
    try {
      MessageFactory<IsoMessage> factory =
          ConfigParser.createFromClasspathConfig("default-packager.xml");
      factory.setCharacterEncoding(StandardCharsets.US_ASCII.name());
      factory.setUseBinaryMessages(false);
      factory.setForceSecondaryBitmap(true);
      factory.setAssignDate(true);
      return factory;
    } catch (IOException e) {
      throw new UncheckedIOException("cannot load default-packager.xml", e);
    }
  }

  /**
   * 0200 financial request. {@code processingCode} + {@code productIndicator}
   * drive the selector: {@code "20." + processingCode[0:2] + "-" + productIndicator}.
   */
  public static IsoMessage authRequest(
      String pan,
      String processingCode,
      long amountMinor,
      String stan,
      String rrn,
      String productIndicator) {

    IsoMessage m = FACTORY.newMessage(0x200);
    m.setValue(2, pan, IsoType.LLVAR, 19);
    m.setValue(3, processingCode, IsoType.NUMERIC, 6);
    m.setValue(4, String.valueOf(amountMinor), IsoType.NUMERIC, 12);
    m.setValue(11, stan, IsoType.NUMERIC, 6);
    // DE28: fee type (1) + 8-digit fee. Optional in ISO8583 but the app's
    // createTransaction() NPEs without it (IsoFieldHelper.java:122).
    m.setValue(28, "D00000000", IsoType.LLVAR, 9);
    m.setValue(12, "103045", IsoType.NUMERIC, 6);
    m.setValue(13, "0615", IsoType.NUMERIC, 4);
    m.setValue(37, rrn, IsoType.ALPHA, 12);
    m.setValue(41, "TERM0001", IsoType.ALPHA, 16);
    m.setValue(42, "MERCHANT0000001", IsoType.ALPHA, 15);
    // DE48 TLV: tag(2) + length(2) + value  ->  "PI" + "04" + "E001"
    String de48 = "PI" + String.format("%02d", productIndicator.length()) + productIndicator;
    m.setValue(48, de48, IsoType.LLLVAR, 999);
    m.setValue(49, "360", IsoType.ALPHA, 3);
    return m;
  }

  /** 0800 network management echo (DE70 = 301). */
  public static IsoMessage echoRequest(String stan) {
    IsoMessage m = FACTORY.newMessage(0x800);
    m.setValue(11, stan, IsoType.NUMERIC, 6);
    m.setValue(70, "301", IsoType.NUMERIC, 3);
    return m;
  }
}
