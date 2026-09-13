package com.nantaaditya.sotres.interceptor;

import com.google.gson.Gson;
import com.nantaaditya.sotres.entity.EventLog;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.GsonHelper;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.service.internal.EventLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Log4j2
@Component
@RequiredArgsConstructor
public class EventLogInterceptor implements HandlerInterceptor {

  private final EventLogService eventLogService;
  private final LogProperties logProperties;
  private final Gson gson;
  private final ContextHelper contextHelper;

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) {
    ContextDTO context = (ContextDTO) request.getAttribute(HeaderFilter.CONTEXT_ATTRIBUTE);
    if (context == null) {
      log.warn(AppLogMessage.message("#EventLog - context is null"));
      return;
    }

    try {
      if (logProperties.isIgnoredPath(context.getPath())) {
        log.debug(AppLogMessage.message("#EventLog - ignored trace log path"));
        return;
      }

      byte[] additionalData = contextHelper.getAdditionalData(context.getRequestId());
      String cleanedPayload = GsonHelper.cleanJson(readBody(request), gson);

      EventLog eventLog = createEventLog(context, additionalData, cleanedPayload);
      log.debug(AppLogMessage.message("#EventLog - queue event log save"));
      eventLogService.save(eventLog);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#EventLog - failed to build event log").error(e));
    } finally {
      contextHelper.cleanUp(context.getRequestId());
    }
  }

  private String readBody(HttpServletRequest request) throws IOException {
    InputStream inputStream = request.getInputStream();
    return new String(StreamUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
  }

  private EventLog createEventLog(ContextDTO context, byte[] additionalData, String payload) {
    return EventLog.builder()
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
