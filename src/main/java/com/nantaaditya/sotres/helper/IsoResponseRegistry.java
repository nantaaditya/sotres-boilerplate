package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Log4j2
@Component
public class IsoResponseRegistry extends BaseRegistry {

  private final Cache<String, Sinks.One<IsoMessage>> pendingRequests;
  private final ParticipantPoolConfiguration participantPoolConfiguration;

  public IsoResponseRegistry(IsoMessageLoggerHelper isoMessageLoggerHelper,
      ParticipantConfigurationProperties participantConfigurationProperties) {

    super(isoMessageLoggerHelper);
    this.participantPoolConfiguration = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION);

    this.pendingRequests = createCache(
        this.participantPoolConfiguration.flightPool(),
        this.participantPoolConfiguration.flightQueueTimeOut()
    );
  }

  public Mono<IsoMessage> register(IsoMessage request) {
    Sinks.One<IsoMessage> sink = Sinks.one();

    String correlationId = IsoFieldHelper.getCorrelationId(request);
    pendingRequests.put(correlationId, sink);

    log.info(AppLogMessage.message("#ISO - registering request key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(request)));

    return sink.asMono()
        .timeout(Duration.ofMillis(this.participantPoolConfiguration.flightQueueTimeOut()))
        .doFinally(signalType -> {
          pendingRequests.invalidate(correlationId);
        });
  }

  public void onResponse(IsoMessage response) {
    String correlationId = IsoFieldHelper.getCorrelationId(response);
    log.info(AppLogMessage.message("#ISO - receive response key {}", correlationId)
        .isoMessage(isoMessageLoggerHelper.toLogMessage(response)));

    Sinks.One<IsoMessage> sink = pendingRequests.getIfPresent(correlationId);

    if (sink != null) {
      Sinks.EmitResult result = sink.tryEmitValue(response);
      if (result.isFailure()) {
        log.warn(AppLogMessage.message("#ISO - response arrived but could not be delivered, result: [{}] key {}", result, correlationId));
        pendingRequests.invalidate(correlationId);
      }
    } else {
      log.error("#ISO - unsolicited response or already timed out for key {}", correlationId);
    }
  }
}
