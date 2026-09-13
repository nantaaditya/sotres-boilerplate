package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Tracks the in-flight {@link ContextDTO} (and any additional error detail) for each HTTP
 * request, keyed by request id. Entries are removed explicitly by {@link #cleanUp} at the end of
 * every request; the two Caffeine caches (built via {@link CaffeineCacheHelper}, named
 * {@value #CONTEXT_CACHE}/{@value #ADDITIONAL_CONTEXT_CACHE} under
 * {@code apps.cache.configurations}) add a write-expiry TTL as a backstop against a leaked entry
 * on any path that skips that cleanup — a plain unbounded map had no such safety net. A removal
 * listener on both caches ({@link #logIfLeaked}) warns whenever an entry is actually reclaimed by
 * that TTL rather than by {@link #cleanUp}, so a leaking request path shows up in logs instead of
 * silently growing the cache until eviction.
 */
@Log4j2
@Component
public class ContextHelper {

  static final String CONTEXT_CACHE = "context";
  static final String ADDITIONAL_CONTEXT_CACHE = "additionalContext";

  private final Cache<String, ContextDTO> contexts;
  private final Cache<String, String> additionalContexts;

  public ContextHelper(CaffeineCacheHelper caffeineCacheHelper) {
    this.contexts = caffeineCacheHelper.createCache(
        CONTEXT_CACHE,
        caffeine -> caffeine
            .removalListener((String requestId, ContextDTO value, RemovalCause cause) -> logIfLeaked(CONTEXT_CACHE, requestId, cause))
    );
    this.additionalContexts = caffeineCacheHelper.createCache(
        ADDITIONAL_CONTEXT_CACHE,
        caffeine -> caffeine
            .removalListener((String requestId, String value, RemovalCause cause) -> logIfLeaked(ADDITIONAL_CONTEXT_CACHE, requestId, cause))
    );
  }

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
    return contexts.asMap().values().stream().toList();
  }

  public ContextDTO get(String requestId) {
    try {
      return contexts.getIfPresent(requestId);
    } catch (Exception ex) {
      log.error(AppLogMessage.message("#MDC - failed to get {}", requestId).error(ex));
      return null;
    }
  }

  public byte[] getAdditionalData(String requestId) {
    String json = additionalContexts.getIfPresent(requestId);
    return Optional.ofNullable(json)
        .map(result -> result.getBytes(StandardCharsets.UTF_8))
        .orElse(null);
  }

  /**
   * Removes this request's tracked context/additional-data entries. Deliberately does not touch
   * MDC: {@code HeaderFilter} is the sole owner of the request thread's MDC lifecycle (it
   * snapshots on entry and restores/clears in its own {@code finally}, after the whole filter
   * chain — including Logbook's post-chain response logging — has run). This is called from
   * {@code EventLogInterceptor.afterCompletion}, which fires before that chain unwinds; clearing
   * MDC here used to wipe {@code traceId}/{@code spanId}/the request-id baggage key before
   * Logbook's response-side log line could pick them up (see
   * docs/POST_MIGRATION_REMEDIATION_PLAN.md Phase 7).
   */
  public void cleanUp(String requestId) {
    contexts.invalidate(requestId);
    additionalContexts.invalidate(requestId);
  }

  public int size() {
    return contexts.asMap().size();
  }

  /**
   * {@code RemovalCause.EXPIRED} on either cache means an entry outlived its TTL without
   * {@link #cleanUp} ever running for it — i.e. some request path never reached
   * {@code EventLogInterceptor.afterCompletion}, so this is a leak, not routine turnover.
   * {@code EXPLICIT} (a normal {@code cleanUp}/{@code invalidate} call) and every other cause are
   * silent.
   */
  void logIfLeaked(String cacheName, String requestId, RemovalCause cause) {
    if (cause == RemovalCause.EXPIRED) {
      log.warn(AppLogMessage.message(
          "#Context - {} cache entry for requestId {} expired without cleanUp — likely leak "
              + "(a request path that never reached EventLogInterceptor.afterCompletion)",
          cacheName, requestId));
    }
  }
}
