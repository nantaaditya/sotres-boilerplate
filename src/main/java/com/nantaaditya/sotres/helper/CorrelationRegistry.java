package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ManagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.properties.embedded.ParticipantPoolConfiguration;
import com.solab.iso8583.IsoMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Matches an outbound ISO8583 request to its inbound reply, and classifies how late that reply was.
 * Backs both correlation modes with one primitive:
 *
 * <ul>
 *   <li><b>RESPONSE</b> mode — {@link EnhancedIsoClient#send} blocks the calling (virtual) thread
 *       on the {@link CompletableFuture} returned by {@link #register(String)} until the reply
 *       arrives or its own bounded {@code get(timeout)} lapses.</li>
 *   <li><b>CALLBACK</b> mode — the caller ignores the future; the reply is delivered
 *       asynchronously by {@code TransactionResponseParticipant} calling {@link #complete}.</li>
 * </ul>
 *
 * <p>Two time windows, each a Caffeine cache keyed by correlation id:
 *
 * <ul>
 *   <li>{@code inFlights} — TTL {@code flightQueueTimeOut}, the real-timeout window. Holds the
 *       future. On expiry the future is completed exceptionally with
 *       {@link java.util.concurrent.TimeoutException} (best-effort — Caffeine expiry is amortized,
 *       so a RESPONSE-mode caller relies on its own {@code get(timeout)}, not on this).</li>
 *   <li>{@code registered} — TTL {@code messageQueueTimeOut}, the longer grace window. A reply that
 *       arrives after {@code inFlights} expired but while this entry is still alive is
 *       {@link IsoCategory#LATE_RESPONSE} rather than {@link IsoCategory#ORPHAN}.</li>
 * </ul>
 *
 * <p><b>Both caches are TTL-only, never size-capped.</b> A size eviction would drop a legitimate
 * live correlation early (a spurious timeout, or a LATE_RESPONSE misclassified as ORPHAN).
 * Concurrency is already bounded upstream by the isoTransaction bulkhead {@code Semaphore}, and each
 * entry self-clears on its TTL, so the footprint is {@code arrivalRate x window}.
 *
 * <p>{@link #register} populates both windows; {@link #complete} on SUCCESS and {@link #cancel}
 * clear both. So a RESPONSE-mode caller that timed out and cancelled sees a later reply as ORPHAN,
 * while a CALLBACK-mode entry (never cancelled) lapses by TTL and a late reply within the grace
 * window is LATE_RESPONSE.
 */
@Log4j2
@Component
public class CorrelationRegistry {

  private final Cache<String, CompletableFuture<IsoMessage>> inFlights;
  private final Cache<String, Boolean> registered;

  /**
   * @param participantConfigurationProperties supplies the {@code transaction} pool timings;
   *     construction fails fast if {@code messageQueueTimeOut < flightQueueTimeOut}
   *     (see {@link ParticipantConfigurationProperties#getPool}).
   */
  public CorrelationRegistry(ParticipantConfigurationProperties participantConfigurationProperties) {
    ParticipantPoolConfiguration pool = participantConfigurationProperties.getPool(ManagerConstant.TRANSACTION);
    this.inFlights = createCache(pool.flightQueueTimeOut(), TimeUnit.MILLISECONDS,
        caffeine -> caffeine.removalListener((key, future, cause) -> {
          if (cause == RemovalCause.EXPIRED && future != null && !future.isDone()) {
            future.completeExceptionally(new TimeoutException(
                "message [" + key + "] timed out before a response arrived"));
          }
        }));

    this.registered = createCache(pool.messageQueueTimeOut(), TimeUnit.MILLISECONDS,
        UnaryOperator.identity());
  }

  /**
   * Register a pending correlation in both windows. RESPONSE-mode callers block on the
   * returned future; CALLBACK-mode callers ignore it and let the pipeline complete it.
   */
  public CompletableFuture<IsoMessage> register(String correlationId) {
    CompletableFuture<IsoMessage> future = new CompletableFuture<>();
    inFlights.put(correlationId, future);
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
    CompletableFuture<IsoMessage> future = inFlights.asMap().remove(correlationId);

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
    CompletableFuture<IsoMessage> future = inFlights.asMap().remove(correlationId);
    registered.invalidate(correlationId);
    if (future != null) {
      future.cancel(false);
    }
    log.warn(AppLogMessage.message("#ISO - cancelled correlation key {}", correlationId));
  }

  /**
   * Build a write-expiring {@link Cache} and let {@code operator} add per-cache tuning
   * (a removal listener, a size cap). The unchecked cast is unavoidable: {@link Caffeine#newBuilder()}
   * is {@code Caffeine<Object, Object>} and cannot be typed until a terminal builder call, but the
   * {@code operator} lambda needs the concrete {@code Caffeine<String, T>} to bind its parameter
   * types — Caffeine's builder is safe to reinterpret this way (it carries no state keyed by K/V).
   *
   * @param timeOut  write-expiry duration
   * @param timeUnit unit for {@code timeOut}
   * @param operator per-cache tuning applied before {@code build()}
   */
  @SuppressWarnings("unchecked")
  private <T> Cache<String, T> createCache(int timeOut, TimeUnit timeUnit,
      UnaryOperator<Caffeine<String, T>> operator) {
    Caffeine<String, T> caffeine =
        (Caffeine<String, T>) (Caffeine<?, ?>) Caffeine.newBuilder().expireAfterWrite(timeOut, timeUnit);
    return operator.apply(caffeine).build();
  }
}
