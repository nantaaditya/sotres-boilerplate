package com.nantaaditya.sotres.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.nantaaditya.sotres.helper.MessageFactoryHelper;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.solab.iso8583.IsoMessage;
import org.junit.jupiter.api.Test;

/**
 * Isolates the FakeIsoHost -> application decode: encode a test 0200 the way the
 * embedded host would, then parse it with the application's own message factory.
 */
class IsoMessagesRoundTripTest {

  private final MessageFactory<IsoMessage> appFactory =
      new MessageFactoryHelper(PackagerConstant.DEFAULT).createMessageFactory(PackagerConstant.DEFAULT);

  @Test
  void authRequestParsesWithApplicationFactory() throws Exception {
    IsoMessage req =
        IsoMessages.authRequest("4111111111111111", "970000", 150000, "100001", "RRN000000001", "E001");

    byte[] wire = req.writeData();

    IsoMessage parsed = appFactory.parseMessage(wire, 0);

    assertThat(parsed.getType()).isEqualTo(0x200);
    assertThat(field(parsed, 2)).isEqualTo("4111111111111111");
    assertThat(field(parsed, 3)).isEqualTo("970000");
    assertThat(field(parsed, 37).trim()).isEqualTo("RRN000000001");
    assertThat(field(parsed, 48)).isEqualTo("PI04E001");
  }

  @Test
  void echoRequestParsesWithApplicationFactory() throws Exception {
    IsoMessage echo = IsoMessages.echoRequest("100002");

    IsoMessage parsed = appFactory.parseMessage(echo.writeData(), 0);

    assertThat(parsed.getType()).isEqualTo(0x800);
    assertThat(field(parsed, 70)).isEqualTo("301");
  }

  private static String field(IsoMessage message, int num) {
    return message.getField(num) == null ? null : message.getField(num).toString();
  }
}
