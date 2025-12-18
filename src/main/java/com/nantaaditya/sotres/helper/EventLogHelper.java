package com.nantaaditya.sotres.helper;

import com.google.gson.Gson;
import com.nantaaditya.sotres.entity.EventLog;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.repository.EventLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Log4j2
@Component
@RequiredArgsConstructor
public class EventLogHelper {

  private final EventLogRepository eventLogRepository;
  private final LogProperties logProperties;
  private final Gson gson;
  private final ContextHelper contextHelper;

  public void save(ServerWebExchange exchange, ContextDTO context) {
    try {
      if (context == null) {
        log.warn(AppLogMessage.message("#EventLog - context is null"));
        return;
      }

      byte[] additionalData = contextHelper.getAdditionalData(context.getRequestId());

      if (logProperties.isIgnoredPath(context.getPath())) {
        log.debug(AppLogMessage.message("#EventLog - ignored trace log path"));
        contextHelper.cleanUp(context.getRequestId());
        return;
      }

      byte[] cachedBody = (byte[]) exchange.getAttribute("cachedRequestBody");
      String payload = new String(cachedBody, StandardCharsets.UTF_8);
      String cleanedPayload = GsonHelper.cleanJson(payload, gson);

      Mono.fromSupplier(() -> createEventLog(context, additionalData, cleanedPayload))
          .flatMap(eventLogRepository::save)
          .subscribe(
              success -> log.debug(AppLogMessage.message("#EventLog - success save event log")),
              error -> log.error(AppLogMessage.message("#EventLog - error save event log").error(error)),
              () -> contextHelper.cleanUp(context.getRequestId())
          );
    } catch (Exception e) {
      log.error(AppLogMessage.message("#EventLog - failed save event log").error(e));
    }
  }

  private EventLog createEventLog(ContextDTO context, byte[] additionalData, String payload) {
    return EventLog.builder()
        .id(TsidHelper.generateStringId())
        .clientId(context.getClientId())
        .requestId(context.getRequestId())
        .method(context.getMethod())
        .path(context.getPath())
        .responseCode(context.getResponseCode())
        .responseDescription(context.getResponseDescription())
        .payload(payload.getBytes(StandardCharsets.UTF_8))
        .additionalData(additionalData)
        .createdDate(LocalDateTime.now())
        .build();
  }
}
