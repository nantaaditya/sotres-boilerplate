package com.nantaaditya.sotres.participant;

import com.github.kpavlov.jreactive8583.IsoMessageListener;
import com.nantaaditya.sotres.helper.HealthCheckHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.model.constant.IsoResponseCode;
import com.nantaaditya.sotres.model.constant.NetworkInformationCode;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import io.netty.channel.ChannelHandlerContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class NetworkProcessorParticipant implements IsoMessageListener<IsoMessage> {

  private final HealthCheckHelper healthCheckHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final IsoFieldHelper isoFieldHelper;

  private static final Set<Integer> NETWORK_MTI = Set.of(0x800, 0x810);

  @Override
  public boolean applies(@NotNull IsoMessage isoMessage) {
    return NETWORK_MTI.contains(isoMessage.getType());
  }

  @Override
  public boolean onMessage(@NotNull ChannelHandlerContext channelHandlerContext,
      @NotNull IsoMessage isoMessage) {

    isoMessageLoggerHelper.logIsoMessage(isoMessage);

    IsoValue<Integer> nic = isoMessage.getField(70);
    if (StringUtils.equals(nic.toString(), NetworkInformationCode.LOGON.getCode())) {
      handleSignOn(channelHandlerContext, isoMessage);
    } else if (StringUtils.equals(nic.toString(), NetworkInformationCode.LOGOFF.getCode())) {
      handleSignOff(channelHandlerContext, isoMessage);
    } else if (StringUtils.equals(nic.toString(), NetworkInformationCode.ECHO.getCode())) {
      handleEcho(channelHandlerContext, isoMessage);
    } else if (StringUtils.equals(nic.toString(), NetworkInformationCode.CUTOVER.getCode())) {
      handleCutOver(channelHandlerContext, isoMessage);
    }
    return false;
  }

  private void handleSignOn(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
    switch (isoMessage.getType()) {
      case 0x800: {
        log.info(AppLogMessage.message("#Network - got message sign on request"));
        healthCheckHelper.setIsSignedOn(true);
        healthCheckHelper.setIsHealthy(true);

        isoFieldHelper.sendResponse(channelHandlerContext, isoMessage, IsoResponseCode.APPROVED.getCode());
        break;
      }
      case 0x810: {
        log.info(AppLogMessage.message("#Network - got message sign on response"));
        healthCheckHelper.setIsSignedOn(isSuccess(isoMessage));
        healthCheckHelper.setIsHealthy(isSuccess(isoMessage));
        break;
      }
    }
  }

  private void handleSignOff(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
    healthCheckHelper.setIsSignedOn(false);

    switch (isoMessage.getType()) {
      case 0x800: {
        log.info(AppLogMessage.message("#Network - got message sign off request"));
        isoFieldHelper.sendResponse(channelHandlerContext, isoMessage, IsoResponseCode.APPROVED.getCode());
        break;
      }
      case 0x810: {
        log.info(AppLogMessage.message("#Network - got message sign off response"));
        break;
      }
    }
  }

  private void handleEcho(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
    switch (isoMessage.getType()) {
      case 0x800: {
        log.info(AppLogMessage.message("#Network - got message echo request"));
        isoFieldHelper.sendResponse(channelHandlerContext, isoMessage, IsoResponseCode.APPROVED.getCode());
        break;
      }
      case 0x810: {
        log.info(AppLogMessage.message("#Network - got message echo response"));
        break;
      }
    }
  }

  private void handleCutOver(ChannelHandlerContext channelHandlerContext, IsoMessage isoMessage) {
    switch (isoMessage.getType()) {
      case 0x800: {
        log.info(AppLogMessage.message("#Network - got message cut over request"));
        isoFieldHelper.sendResponse(channelHandlerContext, isoMessage, IsoResponseCode.APPROVED.getCode());
        break;
      }
      case 0x810: {
        log.info(AppLogMessage.message("#Network - got message cut over response"));
        break;
      }
    }
  }

  private boolean isSuccess(IsoMessage isoMessage) {
    return isoMessage.hasField(39) ?
        StringUtils.equals(isoMessage.getField(39).toString(), IsoResponseCode.APPROVED.getCode()) : false;
  }
}
