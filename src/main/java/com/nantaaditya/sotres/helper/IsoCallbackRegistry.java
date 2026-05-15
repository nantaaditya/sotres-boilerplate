package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.dto.RegistryContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IsoCallbackRegistry extends BaseRegistry {

  // ISO8583 in transaction flight cache (real timeout transaction)
  private final Cache<String, Boolean> inFlights;
  // ISO8583 in registered message cache (grace timeout transaction)
  private final Cache<String, Boolean> registeredMessages;
  private final ParticipantPoolConfiguration participantPoolConfiguration;

  public IsoCallbackRegistry(IsoMessageLoggerHelper isoMessageLoggerHelper,
      ParticipantConfigurationProperties participantConfigurationProperties) {

    super(isoMessageLoggerHelper);
    this.participantPoolConfiguration = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION);
    this.inFlights = createCache(
      this.participantPoolConfiguration.flightPool(),
      this.participantPoolConfiguration.flightQueueTimeOut()
    );
    this.registeredMessages = createCache(
      this.participantPoolConfiguration.messagePool(),
      this.participantPoolConfiguration.messageQueueTimeOut()
    );
  }

  public void register(String correlationId, IsoMessage request) {
    log.info(AppLogMessage.message("#ISO - registering in-flight key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(request)));

    this.inFlights.put(correlationId, Boolean.TRUE);
    this.registeredMessages.put(correlationId, Boolean.TRUE);
  }

  public RegistryContext onResponse(IsoMessage response) {
    String correlationId = IsoFieldHelper.getCorrelationId(response);
    log.info(AppLogMessage.message("#ISO - receive in-flight key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(response)));

    Boolean success = inFlights.getIfPresent(correlationId);
    if (Boolean.TRUE.equals(success)) {
      remove(correlationId);
      return new RegistryContext(response, false, false);
    }

    // late response
    if (registeredMessages.getIfPresent(correlationId)) {
      log.warn(AppLogMessage.message("#ISO - receive late response registry key {}", correlationId)
          .isoMessage(isoMessageLoggerHelper.toLogMessage(response)));
      registeredMessages.invalidate(correlationId);
      return new RegistryContext(response, true, false);
    }

    // orphan response
    log.warn(AppLogMessage.message("#ISO - receive unknown response registry key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(response)));
    registeredMessages.invalidate(correlationId);
    return new RegistryContext(response, false, true);
  }

  public void remove(String correlationId) {
    inFlights.invalidate(correlationId);
    registeredMessages.invalidate(correlationId);
  }

}
