package com.nantaaditya.sotres.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.helper.EnhancedIsoClient;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.MessageFactoryHelper;
import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.embedded.IsoMessageNetworkConfiguration;
import com.solab.iso8583.IsoMessage;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("NetworkService")
@ExtendWith(MockitoExtension.class)
class NetworkServiceTest {

  private static final long TIME_OUT_MS = 15000;

  @Mock
  private EnhancedIsoClient enhancedIsoClient;

  @Mock
  private IsoMessageLoggerHelper isoMessageLoggerHelper;

  private final HealthCheckHelper healthCheckHelper = new HealthCheckHelper();
  private final MessageFactoryHelper messageFactoryHelper = new MessageFactoryHelper(PackagerConstant.DEFAULT);

  private NetworkService service;

  @BeforeEach
  void setUp() {
    service = buildService(false, 30000);
  }

  private NetworkService buildService(boolean scheduledEchoEnabled, int echoInterval) {
    IsoMessageNetworkConfiguration network =
        new IsoMessageNetworkConfiguration(60000, (int) TIME_OUT_MS, scheduledEchoEnabled, echoInterval);
    IsoMessageProperties properties = new IsoMessageProperties(OutgoingProtocol.REST, null, null, network);
    return new NetworkService(enhancedIsoClient, messageFactoryHelper, healthCheckHelper, properties,
        isoMessageLoggerHelper);
  }

  @Nested
  @DisplayName("sendEcho()")
  class SendEcho {

    @Test
    @DisplayName("connected and signed on: sends a 0800/DE70=301 echo and returns true")
    void connectedAndSignedOn_sendsEchoAndReturnsTrue() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);
      healthCheckHelper.setIsSignedOn(true);

      boolean result = service.sendEcho();

      assertThat(result).isTrue();
      ArgumentCaptor<IsoMessage> captor = ArgumentCaptor.forClass(IsoMessage.class);
      verify(isoMessageLoggerHelper).logIsoMessage(captor.capture());
      assertThat(IsoFieldHelper.getField(captor.getValue(), 70)).isEqualTo("301");
      verify(enhancedIsoClient).send(any(IsoMessage.class), eq(TIME_OUT_MS), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("not connected: returns false without sending")
    void notConnected_returnsFalseWithoutSending() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(false);

      boolean result = service.sendEcho();

      assertThat(result).isFalse();
      verify(enhancedIsoClient, never()).send(any(), anyLong(), any());
    }

    @Test
    @DisplayName("connected but not signed on: returns false without sending")
    void connectedButNotSignedOn_returnsFalse() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);

      boolean result = service.sendEcho();

      assertThat(result).isFalse();
      verify(enhancedIsoClient, never()).send(any(), anyLong(), any());
    }

    @Test
    @DisplayName("send() interrupted: returns false and restores the thread's interrupt flag")
    void sendInterrupted_returnsFalseAndSetsInterruptFlag() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);
      healthCheckHelper.setIsSignedOn(true);
      doThrow(new InterruptedException("boom")).when(enhancedIsoClient)
          .send(any(IsoMessage.class), anyLong(), any());

      boolean result = service.sendEcho();

      assertThat(result).isFalse();
      assertThat(Thread.interrupted()).as("interrupt flag restored").isTrue();
    }
  }

  @Nested
  @DisplayName("sendSignOn() / sendSignOff()")
  class SignOnOff {

    @Test
    @DisplayName("sendSignOn(): connected -> sends a 0800/DE70=001 logon message")
    void signOn_whenConnected_sendsLogonMessage() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);

      service.sendSignOn();

      ArgumentCaptor<IsoMessage> captor = ArgumentCaptor.forClass(IsoMessage.class);
      verify(isoMessageLoggerHelper).logIsoMessage(captor.capture());
      assertThat(IsoFieldHelper.getField(captor.getValue(), 70)).isEqualTo("001");
      assertThat(captor.getValue().hasField(48)).as("DE48 network management data present").isTrue();
      verify(enhancedIsoClient).send(any(IsoMessage.class), eq(TIME_OUT_MS), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("sendSignOn(): not connected -> does nothing")
    void signOn_whenNotConnected_doesNothing() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(false);

      service.sendSignOn();

      verifyNoInteractions(isoMessageLoggerHelper);
      verify(enhancedIsoClient, never()).send(any(), anyLong(), any());
    }

    @Test
    @DisplayName("sendSignOff(): connected -> sends a 0800/DE70=002 logoff message")
    void signOff_whenConnected_sendsLogoffMessage() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);

      service.sendSignOff();

      ArgumentCaptor<IsoMessage> captor = ArgumentCaptor.forClass(IsoMessage.class);
      verify(isoMessageLoggerHelper).logIsoMessage(captor.capture());
      assertThat(IsoFieldHelper.getField(captor.getValue(), 70)).isEqualTo("002");
      verify(enhancedIsoClient).send(any(IsoMessage.class), eq(TIME_OUT_MS), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("send() interrupted: logs the failure and restores the thread's interrupt flag")
    void sendMessage_interrupted_setsInterruptFlag() throws InterruptedException {
      when(enhancedIsoClient.isConnected()).thenReturn(true);
      doThrow(new InterruptedException("boom")).when(enhancedIsoClient)
          .send(any(IsoMessage.class), anyLong(), any());

      service.sendSignOn();

      assertThat(Thread.interrupted()).as("interrupt flag restored").isTrue();
    }
  }

  @Nested
  @DisplayName("onStart()")
  class OnStart {

    @Test
    @DisplayName("scheduled echo enabled: the scheduled task eventually sends an echo")
    void scheduledEchoEnabled_eventuallyInvokesSendEcho() {
      NetworkService scheduledService = buildService(true, 20);
      when(enhancedIsoClient.isConnected()).thenReturn(true);
      healthCheckHelper.setIsSignedOn(true);

      scheduledService.onStart();

      await().atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> verify(enhancedIsoClient, atLeastOnce())
              .send(any(IsoMessage.class), anyLong(), any()));
    }

    @Test
    @DisplayName("scheduled echo disabled: the scheduled task never sends an echo")
    void scheduledEchoDisabled_neverInvokesSendEcho() throws InterruptedException {
      NetworkService disabledService = buildService(false, 20);

      disabledService.onStart();
      Thread.sleep(100);

      verifyNoInteractions(enhancedIsoClient, isoMessageLoggerHelper);
    }
  }
}
