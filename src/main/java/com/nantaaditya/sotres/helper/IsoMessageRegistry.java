package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.dto.RegistryContext;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class IsoMessageRegistry {

  private final Cache<String, Boolean> inFlights;
  private final Cache<String, Boolean> registeredMessages;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final ParticipantPoolConfiguration participantPoolConfiguration;

  public IsoMessageRegistry(IsoMessageLoggerHelper isoMessageLoggerHelper,
      ParticipantConfigurationProperties participantConfigurationProperties) {

    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
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

  public void put(IsoMessage request) {
    String correlationId = IsoFieldHelper.getCorrelationId(request);

    log.debug(AppLogMessage.message("#ISO - registering in-flight key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(request)));

    this.inFlights.put(correlationId, Boolean.TRUE);
    this.registeredMessages.put(correlationId, Boolean.TRUE);
  }

  public RegistryContext onResponse(IsoMessage response) {
    String correlationId = IsoFieldHelper.getCorrelationId(response);
    log.debug(AppLogMessage.message("#ISO - receive registry key {}", correlationId));

    Boolean success = inFlights.getIfPresent(correlationId);
    if (success) {
      inFlights.invalidate(correlationId);
      registeredMessages.invalidate(correlationId);
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

  private <T> Cache<String, T> createCache(int poolSize, int timeOut) {
    return Caffeine.newBuilder()
      .expireAfterWrite(timeOut, TimeUnit.MILLISECONDS)
      .maximumSize(poolSize)
      .removalListener((String key, T value, RemovalCause cause) -> {
        if (RemovalCause.EXPIRED == cause && value != null) {
          log.warn(AppLogMessage.message("#ISO - registry key {} expired", key));
        }
      })
      .build();
  }
}
