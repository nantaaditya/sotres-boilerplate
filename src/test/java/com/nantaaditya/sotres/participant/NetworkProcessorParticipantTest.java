package com.nantaaditya.sotres.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.HealthCheckHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("NetworkProcessorParticipant")
@ExtendWith(MockitoExtension.class)
class NetworkProcessorParticipantTest {

  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;
  @Mock
  private IsoFieldHelper isoFieldHelper;
  @Mock
  private IsoMessage msg;
  @Mock
  private ChannelHandlerContext ctx;

  private HealthCheckHelper healthCheckHelper;
  private NetworkProcessorParticipant participant;

  @BeforeEach
  void setUp() {
    healthCheckHelper = new HealthCheckHelper();
    participant = new NetworkProcessorParticipant(healthCheckHelper, isoMessageLoggerHelper,
        isoFieldHelper);
  }

  @Test
  @DisplayName("unknown NIC value: no handler invoked, returns false")
  void onMessage_unknownNic_noHandlerInvoked() {
    participant.onMessage(ctx, msg);
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> isoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  @Nested
  @DisplayName("applies(IsoMessage)")
  class Applies {

    @Test
    @DisplayName("returns true for MTI 0x800 (network request)")
    void applies_networkRequest_returnsTrue() {
      when(msg.getType()).thenReturn(0x800);

      assertThat(participant.applies(msg)).isTrue();
    }

    @Test
    @DisplayName("returns true for MTI 0x810 (network response)")
    void applies_networkResponse_returnsTrue() {
      when(msg.getType()).thenReturn(0x810);

      assertThat(participant.applies(msg)).isTrue();
    }

    @Test
    @DisplayName("returns false for non-network MTI 0x200")
    void applies_transactionMessage_returnsFalse() {
      when(msg.getType()).thenReturn(0x200);

      assertThat(participant.applies(msg)).isFalse();
    }
  }

  @Nested
  @DisplayName("onMessage — always returns false")
  class OnMessageReturnValue {

    @Test
    @DisplayName("always returns false regardless of NIC")
    void onMessage_always_returnsFalse() {
      when(msg.getType()).thenReturn(0x800);
      when(msg.getField(70)).thenReturn(isoValue("001"));

      boolean result = participant.onMessage(ctx, msg);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleSignOn — NIC=001")
  class HandleSignOn {

    @Test
    @DisplayName("0x800 request: sets isSignedOn/isHealthy true and sends APPROVED response")
    void signOn_request_setsHealthyAndSendsApprovedResponse() {
      when(msg.getType()).thenReturn(0x800);
      when(msg.getField(70)).thenReturn(isoValue("001"));

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isTrue();
      assertThat(healthCheckHelper.isHealthy()).isTrue();
      verify(isoFieldHelper).sendResponse(ctx, msg, "00");
    }

    @Test
    @DisplayName("0x810 response with DE39=00: sets isSignedOn and isHealthy to true")
    void signOn_responseApproved_setsSignedOnAndHealthy() {
      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("001"));
      when(msg.hasField(39)).thenReturn(true);
      when(msg.getField(39)).thenReturn(isoValue("00"));

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isTrue();
      assertThat(healthCheckHelper.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("0x810 response with DE39 != 00: sets isSignedOn and isHealthy to false")
    void signOn_responseDeclined_clearsSignedOnAndHealthy() {
      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("001"));
      when(msg.hasField(39)).thenReturn(true);
      when(msg.getField(39)).thenReturn(isoValue("99"));

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isFalse();
      assertThat(healthCheckHelper.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("0x810 response with no DE39: treats as not approved, clears isSignedOn and isHealthy")
    void signOn_responseWithoutResponseCode_clearsSignedOnAndHealthy() {
      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("001"));
      when(msg.hasField(39)).thenReturn(false);

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isFalse();
      assertThat(healthCheckHelper.isHealthy()).isFalse();
    }
  }

  @Nested
  @DisplayName("handleSignOff — NIC=002")
  class HandleSignOff {

    @Test
    @DisplayName("0x800 request: clears isSignedOn and sends APPROVED response")
    void signOff_request_clearsSignedOnAndSendsResponse() {
      healthCheckHelper.setIsSignedOn(true);

      when(msg.getType()).thenReturn(0x800);
      when(msg.getField(70)).thenReturn(isoValue("002"));

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isFalse();
      verify(isoFieldHelper).sendResponse(ctx, msg, "00");
    }

    @Test
    @DisplayName("0x810 response: clears isSignedOn without sending a response")
    void signOff_response_clearsSignedOnWithoutResponse() {
      healthCheckHelper.setIsSignedOn(true);

      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("002"));

      participant.onMessage(ctx, msg);

      assertThat(healthCheckHelper.isSignedOn()).isFalse();
      verify(isoFieldHelper, never()).sendResponse(any(ChannelHandlerContext.class),
          any(IsoMessage.class), any(String.class));
    }
  }

  @Nested
  @DisplayName("handleEcho — NIC=301")
  class HandleEcho {

    @Test
    @DisplayName("0x800 request: sends APPROVED response")
    void echo_request_sendsApprovedResponse() {
      when(msg.getType()).thenReturn(0x800);
      when(msg.getField(70)).thenReturn(isoValue("301"));

      participant.onMessage(ctx, msg);

      verify(isoFieldHelper).sendResponse(ctx, msg, "00");
    }

    @Test
    @DisplayName("0x810 response: does not send response")
    void echo_response_doesNotSendResponse() {
      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("301"));

      participant.onMessage(ctx, msg);

      verify(isoFieldHelper, never()).sendResponse(any(ChannelHandlerContext.class),
          any(IsoMessage.class), any(String.class));
    }
  }

  @Nested
  @DisplayName("handleCutOver — NIC=201")
  class HandleCutOver {

    @Test
    @DisplayName("0x800 request: sends APPROVED response")
    void cutOver_request_sendsApprovedResponse() {
      when(msg.getType()).thenReturn(0x800);
      when(msg.getField(70)).thenReturn(isoValue("201"));

      participant.onMessage(ctx, msg);

      verify(isoFieldHelper).sendResponse(ctx, msg, "00");
    }

    @Test
    @DisplayName("0x810 response: does not send response")
    void cutOver_response_doesNotSendResponse() {
      when(msg.getType()).thenReturn(0x810);
      when(msg.getField(70)).thenReturn(isoValue("201"));

      participant.onMessage(ctx, msg);

      verify(isoFieldHelper, never()).sendResponse(any(ChannelHandlerContext.class),
          any(IsoMessage.class), any(String.class));
    }
  }

  @Nested
  @DisplayName("unknown NIC")
  class UnknownNic {

  }
}
