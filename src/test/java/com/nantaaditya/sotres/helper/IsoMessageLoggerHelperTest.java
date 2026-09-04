package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IsoMessageLoggerHelper")
class IsoMessageLoggerHelperTest {

  @Mock
  private SystemPropertiesService systemPropertiesService;

  private IsoMessageLoggerHelper helper;
  private MessageFactory<IsoMessage> factory;

  @BeforeEach
  void setUp() {
    helper = new IsoMessageLoggerHelper(systemPropertiesService);
    factory = buildMessageFactory();
    setupDefaultMocks();
  }

  private void setupDefaultMocks() {
    lenient().when(systemPropertiesService.getProperty(ConfigGroup.INCOMING_MTI, ConfigGroup.INCOMING_MTI.getPropertyId()))
        .thenReturn("256,512,1056,1057,1058,1059,2048");
    lenient().when(systemPropertiesService.getProperty(ConfigGroup.OUTGOING_MTI, ConfigGroup.OUTGOING_MTI.getPropertyId()))
        .thenReturn("272,528,1072,1073,1074,1075,2064");
    lenient().when(systemPropertiesService.getProperty(ConfigGroup.ISO8583_MASK_FIELDS, ConfigGroup.ISO8583_MASK_FIELDS.getPropertyId()))
        .thenReturn("2");
  }

  private MessageFactory<IsoMessage> buildMessageFactory() {
    try {
      MessageFactory<IsoMessage> mf =
          ConfigParser.createFromClasspathConfig("default-packager.xml");
      mf.setCharacterEncoding(StandardCharsets.US_ASCII.name());
      mf.setUseBinaryMessages(false);
      mf.setForceSecondaryBitmap(true);
      return mf;
    } catch (IOException e) {
      throw new UncheckedIOException("cannot load default-packager.xml", e);
    }
  }

  @Test
  @DisplayName("toLogMessage resolves direction correctly for incoming message")
  void testDirectionResolution_incoming() {
    IsoMessage message = factory.newMessage(0x200);
    message.setValue(2, "4111111111111111", IsoType.LLVAR, 19);

    JsonLogIsoMessage result = helper.toLogMessage(message);

    assertThat(result)
        .isNotNull()
        .satisfies(msg -> {
          assertThat(msg.direction()).isEqualTo("incoming");
          assertThat(msg.mti()).isEqualTo("0200");
        });
  }

  @Test
  @DisplayName("toLogMessage resolves direction correctly for outgoing message")
  void testDirectionResolution_outgoing() {
    IsoMessage message = factory.newMessage(0x210);
    message.setValue(2, "4111111111111111", IsoType.LLVAR, 19);

    JsonLogIsoMessage result = helper.toLogMessage(message);

    assertThat(result)
        .isNotNull()
        .satisfies(msg -> {
          assertThat(msg.direction()).isEqualTo("outgoing");
          assertThat(msg.mti()).isEqualTo("0210");
        });
  }

  @Test
  @DisplayName("toLogMessage masks DE2 (PAN) with 6,4 format")
  void testPanMasking() {
    IsoMessage message = factory.newMessage(0x200);
    message.setValue(2, "4111111111111111", IsoType.LLVAR, 19);

    JsonLogIsoMessage result = helper.toLogMessage(message);

    assertThat(result)
        .isNotNull()
        .satisfies(msg -> {
          String de2 = msg.dataElements().get("2");
          // 6,4 format: show first 6 (411111) + mask 6 middle (******) + show last 4 (1111)
          assertThat(de2).isEqualTo("411111******1111");
        });
  }

  @Test
  @DisplayName("toLogMessage handles unknown MTI as unknown direction")
  void testDirectionResolution_unknown() {
    IsoMessage message = factory.newMessage(0x999);
    message.setValue(2, "4111111111111111", IsoType.LLVAR, 19);

    JsonLogIsoMessage result = helper.toLogMessage(message);

    assertThat(result)
        .isNotNull()
        .satisfies(msg -> {
          assertThat(msg.direction()).isEqualTo("unknown");
          assertThat(msg.mti()).isEqualTo("0999");
        });
  }

}
