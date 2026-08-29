package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Single correlation primitive for ISO8583 request/response matching: a blocking
 * {@link CompletableFuture} map with a two-window classification —
 *
 * <ul>
 *   <li>{@code pending}   — TTL {@code flightQueueTimeOut}, the real-timeout window; holds the
 *       future a RESPONSE-mode caller blocks on (CALLBACK-mode callers ignore it).</li>
 *   <li>{@code registered} — TTL {@code messageQueueTimeOut}, the longer grace window; a
 *       response that arrives after {@code pending} expired but while this entry is still
 *       alive is {@link IsoCategory#LATE_RESPONSE} rather than {@link IsoCategory#ORPHAN}.</li>
 * </ul>
 *
 * <p>{@link #register(String)} populates both; {@link #complete(IsoMessage)} on SUCCESS and
 * {@link #cancel(String)} clear both, so a RESPONSE-mode caller that timed out and cancelled
 * sees a subsequent response as ORPHAN. A CALLBACK-mode entry is never cancelled, so
 * {@code pending} lapses by TTL and a late reply within the grace window is LATE_RESPONSE.
 */
@Log4j2
@Component
public class CorrelationRegistry {

  private final Cache<String, CompletableFuture<IsoMessage>> pending;
  private final Cache<String, Boolean> registered;

  public CorrelationRegistry(ParticipantConfigurationProperties participantConfigurationProperties) {
    ParticipantPoolConfiguration pool =
        participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION);
    this.pending = Caffeine.newBuilder()
        .expireAfterWrite(pool.flightQueueTimeOut(), TimeUnit.MILLISECONDS)
        .maximumSize(pool.flightPool())
        .<String, CompletableFuture<IsoMessage>>removalListener((key, future, cause) -> {
          if (cause.wasEvicted() && future != null && !future.isDone()) {
            future.completeExceptionally(new TimeoutException(
                "correlation " + key + " evicted before a response arrived"));
          }
        })
        .build();
    this.registered = Caffeine.newBuilder()
        .expireAfterWrite(pool.messageQueueTimeOut(), TimeUnit.MILLISECONDS)
        .maximumSize(pool.messagePool())
        .build();
  }

  /**
   * Register a pending correlation in both windows. RESPONSE-mode callers block on the
   * returned future; CALLBACK-mode callers ignore it and let the pipeline complete it.
   */
  public CompletableFuture<IsoMessage> register(String correlationId) {
    CompletableFuture<IsoMessage> future = new CompletableFuture<>();
    pending.put(correlationId, future);
    registered.put(correlationId, Boolean.TRUE);
    log.info(AppLogMessage.message("#ISO - registering correlation key {}", correlationId));
    return future;
  }

  /**
   * Deliver an inbound response to its waiting future and classify the outcome.
   *
   * @return {@link IsoCategory#SUCCESS} when the response arrived within the real-timeout
   *         window; {@link IsoCategory#LATE_RESPONSE} when that window had lapsed but the
   *         grace window is still open; {@link IsoCategory#ORPHAN} otherwise.
   */
  public IsoCategory complete(IsoMessage response) {
    String correlationId = IsoFieldHelper.getCorrelationId(response);
    CompletableFuture<IsoMessage> future = pending.asMap().remove(correlationId);

    if (future != null && future.complete(response)) {
      registered.invalidate(correlationId);
      log.info(AppLogMessage.message("#ISO - completed correlation key {}", correlationId));
      return IsoCategory.SUCCESS;
    }

    // real-timeout window lapsed (pending expired), grace window still open
    if (registered.asMap().remove(correlationId) != null) {
      log.warn(AppLogMessage.message("#ISO - late response for correlation key {}", correlationId));
      return IsoCategory.LATE_RESPONSE;
    }

    // no record of this request (never registered, cancelled, or grace window also lapsed)
    log.warn(AppLogMessage.message("#ISO - orphan response for correlation key {}", correlationId));
    return IsoCategory.ORPHAN;
  }

  /** Abandon a pending correlation in both windows (e.g. the Netty write failed, or a
   *  RESPONSE-mode caller's own wait timed out). */
  public void cancel(String correlationId) {
    CompletableFuture<IsoMessage> future = pending.asMap().remove(correlationId);
    registered.invalidate(correlationId);
    if (future != null) {
      future.cancel(false);
    }
    log.warn(AppLogMessage.message("#ISO - cancelled correlation key {}", correlationId));
  }
}
