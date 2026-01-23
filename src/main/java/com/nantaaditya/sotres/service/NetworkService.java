package com.nantaaditya.sotres.service;

import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.EnhancedIsoClient;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.MessageFactoryHelper;
import com.nantaaditya.sotres.model.constant.NetworkInformationCode;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class NetworkService {

  private final EnhancedIsoClient enhancedIsoClient;
  private final HealthCheckHelper healthCheckHelper;
  private final MessageFactoryHelper messageFactoryHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoMessageProperties isoMessageProperties;
  private final ThreadPoolTaskScheduler scheduler;

  public NetworkService(EnhancedIsoClient enhancedIsoClient, MessageFactoryHelper messageFactoryHelper,
      HealthCheckHelper healthCheckHelper, IsoMessageProperties isoMessageProperties,
      IsoMessageLoggerHelper isoMessageLoggerHelper) {

    this.enhancedIsoClient = enhancedIsoClient;
    this.healthCheckHelper = healthCheckHelper;
    this.isoMessageProperties = isoMessageProperties;
    this.messageFactoryHelper = messageFactoryHelper;
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;

    this.scheduler = new ThreadPoolTaskScheduler();
    this.scheduler.setThreadNamePrefix("Network-");
    this.scheduler.setPoolSize(3);
    this.scheduler.initialize();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStart() {
    Runnable task = () -> {
      if (isoMessageProperties.network().scheduledEchoEnabled()) {
        sendEcho();
      }
    };
    this.scheduler.scheduleAtFixedRate(task, isoMessageProperties.network().echoInterval());

  }

  @Async
  public void sendSignOn() {
    try {
      if (enhancedIsoClient.isConnected()) {
        IsoMessage request = constructMessage(NetworkInformationCode.LOGON, "Log on");
        isoMessageLoggerHelper.logIsoMessage(request);
        enhancedIsoClient.send(request, isoMessageProperties.network().timeOut(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      log.error(AppLogMessage.message("#Network - send sign on failed. with message : {}", e.getMessage()).error(e));
    }
  }

  @Async
  public void sendSignOff() {
    try {
      if (enhancedIsoClient.isConnected()) {
        IsoMessage request = constructMessage(NetworkInformationCode.LOGOFF, "Log off");
        isoMessageLoggerHelper.logIsoMessage(request);
        enhancedIsoClient.send(request, isoMessageProperties.network().timeOut(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      log.error(AppLogMessage.message("#Network - send sign off failed. with message : {}", e.getMessage()).error(e));
    }
  }

  public boolean sendEcho() {
    try {
      if (enhancedIsoClient.isConnected() && healthCheckHelper.isSignedOn()) {
        IsoMessage request = constructMessage(NetworkInformationCode.ECHO, "Echo");
        isoMessageLoggerHelper.logIsoMessage(request);
        enhancedIsoClient.send(request, isoMessageProperties.network().timeOut(), TimeUnit.MILLISECONDS);
        return true;
      }
      return false;
    } catch (InterruptedException e) {
     log.error(AppLogMessage.message("#Network - send echo failed. with message : {} , and root cause : {}", e.getMessage()).error(e));
      return false;
    }
  }

  private IsoMessage constructMessage(NetworkInformationCode nic, String message) {
    IsoMessage isoMessage = this.messageFactoryHelper.getDefaultMessageFactory().newMessage(0x800);

    isoMessage.setValue(7, DateTimeHelper.getDateInFormat(
            ZonedDateTime.now(DateTimeHelper.GMT_ZONE), DateTimeHelper.TRANSMISSION_DATE_TIME_FORMAT),
        IsoType.NUMERIC, 10);
    isoMessage.setValue(11, IsoFieldHelper.generateNumeric(6), IsoType.NUMERIC, 6);

    if (NetworkInformationCode.LOGON.equals(nic)) {
      constructNetworkManagementData(isoMessage);
    } else if (NetworkInformationCode.CUTOVER.equals(nic)) {
      isoMessage.setValue(15, DateTimeHelper.getDateInFormat(ZonedDateTime.now(), "MMdd"),
          IsoType.NUMERIC, 4);
    }

    isoMessage.setValue(70, nic.getCode(), IsoType.NUMERIC, 3);

    return isoMessage;
  }

  private void constructNetworkManagementData(IsoMessage isoMessage) {
    StringBuilder builder = new StringBuilder();
    builder.append("60");
    builder.append("1");
    builder.append("1");
    builder.append("0");
    builder.append("0");
    builder.append("0");
    builder.append("1");
    builder.append("1");
    builder.append("2");
    builder.append("N");
    builder.append("0");
    builder.append("0");
    builder.append("360");
    builder.append("2");
    isoMessage.setValue(48, builder.toString(), IsoType.LLLVAR, 20);
  }
}
