package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ContextHelper {

  private final Map<String, ContextDTO> contexts = new ConcurrentHashMap<>();
  private final Map<String, String> additionalContexts = new ConcurrentHashMap<>();

  public void put(ContextDTO contextDTO) {
    try {
      Optional.ofNullable(contextDTO)
        .ifPresent(ctx -> contexts.put(ctx.getRequestId(), ctx));
    } catch (Exception ex) {
      log.error(AppLogMessage.message("#Context - failed to save {}", contextDTO.getRequestId()).error(ex));
    }
  }

  public void update(String requestId, UnaryOperator<ContextDTO> contextFunction) {
    ContextDTO contextDTO = get(requestId);
    if (contextDTO != null) {
      contextDTO = contextFunction.apply(contextDTO);
      put(contextDTO);
    }
  }

  public void put(String requestId, String additionalData) {
    try {
      additionalContexts.put(requestId, additionalData);
    } catch (Exception ex) {
      log.error(AppLogMessage.message("#Context - failed to update {}", requestId).error(ex));
    }
  }

  public List<ContextDTO> list() {
    return contexts.values().stream().toList();
  }

  public ContextDTO get(String requestId) {
    try {
      return contexts.getOrDefault(requestId, null);
    } catch (Exception ex) {
      log.error(AppLogMessage.message("#MDC - failed to get {}", requestId).error(ex));
      return null;
    }
  }

  public byte[] getAdditionalData(String requestId) {
    String json = additionalContexts.getOrDefault(requestId, null);
    return Optional.ofNullable(json)
        .map(result -> result.getBytes(StandardCharsets.UTF_8))
        .orElse(null);
  }

  public void cleanUp(String requestId) {
    contexts.remove(requestId);
    additionalContexts.remove(requestId);
    MDC.clear();
  }

  public int size() {
    return contexts.size();
  }
}
