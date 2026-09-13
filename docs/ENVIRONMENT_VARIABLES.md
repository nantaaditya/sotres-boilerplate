# Environment Variables Reference

> Auto-generated from `src/main/resources/application.yml`  
> Last updated: 2026-09-03

**Runtime:** Spring Boot 3.5.16 · Java 25 · servlet (Tomcat) + virtual threads

---

## Legend

| Symbol | Meaning |
|--------|---------|
| 🔒 | Secret — never commit; rotate if exposed |
| ✅ | Required — no default; application won't start without it |

---

## Go-Live Checklist

Before deploying to production, verify:

- [ ] `DB_URL`, `DB_USER`, `DB_PASS` point to the production database
- [ ] `DB_PASS` is rotated from the default value (`changeme`)
- [ ] `ISO8583_HOST` and `ISO8583_PORT` point to the production acquirer/switch
- [ ] `TRANSACTION_CLIENT_HOSTNAME` points to the production downstream REST endpoint
- [ ] `VIRTUAL_THREAD_ENABLED` is `true` (stays on for virtual thread support)
- [ ] `JPA_SHOW_SQL` is `false` in production
- [ ] `PROMETHEUS_ENABLED` is `true` for observability
- [ ] `ACTUATOR_EXPOSED` is restricted (e.g., `health,info,prometheus`) — not `*`
- [ ] `ACTUATOR_SHUTDOWN` remains `none` (disable unsafe shutdown)
- [ ] `APPS_API_ENABLED`, `APPS_METRIC_ENABLED`, `APPS_TRACE_ENABLED` are set appropriately
- [ ] All timeout values are tuned for production latency expectations
- [ ] `TRANSACTION_RETRY_MAX_ATTEMPT` matches downstream deduplication behavior
- [ ] `TRACING_SAMPLING_PROBABILITY` is set to `1.0` or a representative sample rate

---

## 1. Server Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `SERVER_PORT` | HTTP server listen port | Integer | `8080` | No | | | | Use 8080 for standard Spring Boot port |
| `CONTEXT_PATH` | Servlet context path prefix for all endpoints | String | `/sotres` | No | | | | Must start with `/` and have no trailing `/` |

---

## 2. Application Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `APPLICATION_NAME` | Spring application name | String | `sotres-api` | No | | | | Used in logging and metrics |
| `APPS_VERSION` | Application version string | String | `1.0.0-SNAPSHOT` | No | | | | Displayed in actuator endpoints |
| `VIRTUAL_THREAD_ENABLED` | Enable Java virtual threads (Project Loom) | Boolean | `true` | No | | | | Always `true` for ISO8583 blocking on virtual threads |

---

## 3. Database Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `DB_URL` | JDBC connection URL for PostgreSQL | URL | `jdbc:postgresql://localhost:5432/boilerplate` | No | | | | Production URL must point to your PG instance |
| `DB_USER` | Database username | String | `postgres` | No | | | | Keep non-sensitive |
| `DB_PASS` | Database password | String | `changeme` | No | 🔒 | | | **Rotate immediately in production** |
| `DB_POOL_MAX_SIZE` | HikariCP maximum connection pool size | Integer | `10` | No | | | | Tune based on concurrent transaction load |
| `DB_POOL_MIN_IDLE` | HikariCP minimum idle connections | Integer | `5` | No | | | | Typically half of `MAX_SIZE` |
| `DB_POOL_CONNECTION_TIMEOUT` | Connection acquire timeout (ms) | Long (ms) | `5000` | No | | | | Increase if database startup is slow |
| `DB_POOL_IDLE_TIMEOUT` | Connection idle timeout before eviction (ms) | Long (ms) | `60000` | No | | | | Default 1 minute; raise for persistent connections |
| `DB_POOL_MAX_LIFETIME` | Maximum connection lifetime (ms) | Long (ms) | `300000` | No | | | | Default 5 minutes; prevents stale connections |

---

## 4. JPA / Hibernate Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `JPA_SHOW_SQL` | Log all SQL statements to stdout | Boolean | `true` | No | | | `false` | Set to `false` in production to reduce noise |
| `HIBERNATE_FORMAT_SQL` | Pretty-print SQL in logs | Boolean | `true` | No | | | `false` | Disable in production; Logbook captures formatted payloads |

---

## 5. Logging Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `ROOT_LOG_LEVEL` | Root logger level | String | `INFO` | No | | | | Set to `WARN` or `ERROR` in production |
| `ZALANDO_LOG_LEVEL` | Logbook library log level | String | `TRACE` | No | | | | Verbose HTTP logging; set to `INFO` in production |
| `JDBC_LOG_LEVEL` | Spring JDBC core logging level | String | `INFO` | No | | | | Set to `WARN` in production |
| `LOG_PATH` | Directory for application log files | String | `logs/` | No | | | | Ensure directory exists and is writable |
| `APPS_LOG_LEVEL` | Log format: `json` or `text` | String | `json` | No | | | | Use `json` for structured logging in production |
| `APPS_METRIC_ENABLED` | Enable metric logging (via Disruptor) | Boolean | `true` | No | | | | Disable only for very high-volume testing |
| `APPS_TRACE_ENABLED` | Enable Micrometer tracing (Brave/B3) | Boolean | `true` | No | | | | Keep enabled for observability |
| `APPS_API_ENABLED` | Enable Logbook HTTP request/response logging | Boolean | `true` | No | | | | Keep enabled; use sampling in production |
| `SENSITIVE_FIELD` | CSV list of fields to mask in logs | String | `cardNo` | No | | | | Add comma-separated fields (e.g., `cardNo,pan,cvv`) |
| `APPS_IGNORED_PATH` | Paths to exclude from event log (comma-separated) | String | `/actuator/**,/internal-api/**` | No | | | | Prevents actuator spam in audit logs |

---

## 6. ISO8583 Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `ISO8583_HOST` | Upstream ISO8583 acquirer/switch hostname or IP | String | `127.0.0.1` | No | | `127.0.0.1` | Production hostname | Port number is configured separately |
| `ISO8583_PORT` | Upstream ISO8583 port | Integer | `13001` | No | | | | Change per your acquirer's endpoint |
| `ISO8583_WORKER_THREAD_COUNT` | jReactive-8583 worker thread count (Netty) | Integer | `100` | No | | | | Typically 2–4× expected concurrent connections |
| `OUTGOING_PROTOCOL` | Response protocol type | String | `REST` | No | | | | Only `REST` is currently supported |
| `DEFAULT_LOG_HANDLER_ENABLED` | Log ISO messages to file/database | Boolean | `true` | No | | | | Disable only for non-production testing |
| `ISO8583_MASKING_ENABLED` | Mask sensitive ISO fields in logs | Boolean | `true` | No | | | | Keep enabled for PCI compliance |
| `ISO8583_FIELD_DESCRIPTION_ENABLED` | Include ISO field names in logs | Boolean | `true` | No | | | | Disable only for performance-critical environments |
| `FORWARDING_INSTITUTION_ID` | Acquiring institution ID (DE32) | Integer | `625` | No | | | | Set per your ISO8583 agreement |
| `RECONNECT_INTERVAL` | Retry interval after connection failure (ms) | Long (ms) | `60000` | No | | | | Default 1 minute; lower for faster recovery |
| `TIME_OUT_SECOND` | ISO8583 request/response timeout (ms) | Long (ms) | `15000` | No | | | | Tune per upstream response latency |
| `SCHEDULED_ECHO_ENABLED` | Send periodic echo/heartbeat to upstream | Boolean | `true` | No | | | | Keeps connection alive and detects failures |
| `ECHO_INTERVAL_SECOND` | Heartbeat interval (ms) | Long (ms) | `30000` | No | | | | Default 30 seconds; increase if upstream complains |

---

## 7. Participant Pool Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `TRANSACTION_FLIGHT_QUEUE_TIMEOUT` | In-flight request timeout (ms) | Long (ms) | `15000` | No | | | | How long to wait for a response before timing out |
| `TRANSACTION_MESSAGE_QUEUE_TIME_OUT` | Message cache retention after response (ms) | Long (ms) | `60000` | No | | | | Grace period for late/duplicate responses |

---

## 8. Downstream REST Client Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `CLIENT_REGISTRY_TYPE` | Correlation mode: `CALLBACK` (fire-and-forget) or `RESPONSE` (blocking) | String | `CALLBACK` | No | | | | See docs/api-reference.md for behavior |
| `TRANSACTION_CLIENT_HOSTNAME` | Base URL of downstream transaction API | URL | `http://localhost:8080` | No | | `http://localhost:8080` | Production endpoint URL | Must include `http://` or `https://` |
| `TRANSACTION_MAX_CONNECTION` | Maximum concurrent connections to downstream | Integer | `50` | No | | | | Tune per downstream capacity |
| `TRANSACTION_MAX_IDLE_TIME` | Connection idle timeout (ms) | Long (ms) | `30000` | No | | | | Time before idle connection is evicted |
| `TRANSACTION_MAX_LIFE_TIME` | Maximum connection lifetime (ms) | Long (ms) | `60000` | No | | | | Total time connection can live |
| `TRANSACTION_EVICT_BACKGROUND` | Background eviction interval (ms) | Long (ms) | `120000` | No | | | | **Not currently enforced** — Apache HttpClient 5's `evictIdleConnections`/`evictExpiredConnections` run on a fixed internal interval, not a configurable one. `TRANSACTION_MAX_IDLE_TIME` is still honored. |
| `TRANSACTION_PENDING_ACQUIRE_TIMEOUT` | Time to acquire a connection from pool (ms) | Long (ms) | `3000` | No | | | | If pool is saturated, fail quickly |
| `TRANSACTION_CLIENT_CONNECT_TIMEOUT` | TCP connect timeout (ms) | Long (ms) | `5000` | No | | | | Time to establish TCP connection |
| `TRANSACTION_CLIENT_READ_TIMEOUT` | Read timeout for response (ms) | Long (ms) | `10000` | No | | | | Time to receive response body |
| `TRANSACTION_CLIENT_WRITE_TIMEOUT` | Write timeout for request (ms) | Long (ms) | `10000` | No | | | | **Not currently enforced** — classic (blocking) Apache HttpClient 5 has one socket timeout covering both read and write, mapped from `TRANSACTION_CLIENT_READ_TIMEOUT` only (deliberately not the max of the two, which would silently widen an intentionally short read timeout). |
| `TRANSACTION_CLIENT_TIMEUNIT` | Time unit for timeouts | String | `MILLISECONDS` | No | | | | Always `MILLISECONDS` |

---

## 9. Retry and Dead-Letter Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `TRANSACTION_RETRY_TYPE` | Retry backoff strategy | String | `EXPONENTIAL_RANDOM` | No | | | | `EXPONENTIAL_RANDOM` for jittered exponential backoff |
| `TRANSACTION_RETRY_INITIAL_INTERVAL` | First retry delay (ms) | Long (ms) | `500` | No | | | | Start with 500ms, exponentially increase |
| `TRANSACTION_RETRY_MULTIPLIER` | Exponential backoff multiplier | Decimal | `2.0` | No | | | | Each retry multiplies previous interval by this |
| `TRANSACTION_RETRY_MAX_INTERVAL` | Maximum retry delay (ms) | Long (ms) | `10000` | No | | | | Cap at 10 seconds to avoid indefinite backoff |
| `TRANSACTION_RETRY_MAX_ATTEMPT` | Maximum retry attempts | Integer | `1` | No | | | | **Set to 1 if downstream dedupes on `x-request-id`**; raise only if safe |
| `TRANSACTION_RETRY_DEAD_LETTER` | Store failed requests in dead-letter table | Boolean | `true` | No | | | | Enable for audit trail and manual retry |
| `TRANSACTION_RETRYABLE_EXCEPTIONS` | Exceptions triggering retry (comma-separated) | String | `org.springframework.web.client.ResourceAccessException:true` | No | | | | List exception class names and retry flag |

---

## 10. Async Thread Pool Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `ASYNC_CORE_POOL_SIZE` | Default async executor core threads | Integer | `5` | No | | | | For general async tasks (logs, dead-letter cleanup) |
| `ASYNC_MAX_POOL_SIZE` | Default async executor max threads | Integer | `25` | No | | | | Upper limit for thread scaling |
| `ASYNC_QUEUE_CAPACITY` | Default async queue size before rejection | Integer | `50` | No | | | | Work queue capacity |
| `ASYNC_KEEP_ALIVE_SECONDS` | Time to keep excess threads alive (s) | Integer | `30` | No | | | | Threads above core size die after this idle time |
| `ASYNC_THREAD_NAME_PREFIX` | Thread name prefix for logging | String | `default-async-` | No | | | | Helps identify thread source in stack traces |
| `ASYNC_VIRTUAL_THREAD_ENABLED` | Use virtual threads for default async executor | Boolean | `true` | No | | | | Virtual threads are cheap; keep enabled |
| `ASYNC_REJECTION_POLICY` | Default executor saturation policy (`CALLER_RUNS`\|`ABORT`) | String | `CALLER_RUNS` | No | | | | `CALLER_RUNS`: audit/event-log writes are guaranteed even under saturation (caller may stall) — deliberate, see decision D2 in `docs/POST_MIGRATION_REMEDIATION_PLAN.md` |
| `TRANSACTION_CORE_POOL_SIZE` | ISO transaction executor core threads | Integer | `25` | No | | | | Handles incoming ISO8583 messages from Netty |
| `TRANSACTION_MESSAGE_POOL` | ISO transaction executor max threads | Integer | `90` | No | | | | Real admission ceiling for concurrent transactions. Also drives the bulkhead's derived permit count below — resize here, not via a standalone bulkhead permits var |
| `TRANSACTION_QUEUE_SIZE` | ISO transaction queue capacity | Integer | `10` | No | | | | Work queue for pending transactions; part of the same derived-permits ceiling as `TRANSACTION_MESSAGE_POOL` |
| `TRANSACTION_KEEP_ALIVE_SECONDS` | ISO transaction thread idle timeout (s) | Integer | `60` | No | | | | Default 1 minute |
| `TRANSACTION_PREFIX` | ISO transaction thread name prefix | String | `iso-transaction-` | No | | | | Appears in logs and thread names |
| `ISO_TRANSACTION_VIRTUAL_THREAD_ENABLED` | Use virtual threads for ISO transaction executor | Boolean | `true` | No | | | | Must be `true` for efficient message processing |
| `ISO_TRANSACTION_REJECTION_POLICY` | ISO transaction executor saturation policy (`ABORT`\|`CALLER_RUNS`) | String | `ABORT` | No | | | | **Do not change to `CALLER_RUNS`.** This executor is fed directly from the Netty event loop; `CALLER_RUNS` would run a full transaction (JSLT + JDBC + outbound REST) on the event loop once saturated, blocking every ISO8583 connection sharing it. See decision D1. |
| `TRANSACTION_RESPONSE_CORE_POOL_SIZE` | ISO response-completion executor core threads | Integer | `5` | No | | | | Separate pool from `isoTransaction` (see Phase 3C) so a fast completion never queues behind a slow transaction |
| `TRANSACTION_RESPONSE_MESSAGE_POOL` | ISO response-completion executor max threads | Integer | `20` | No | | | | Response completions are near-instant (resolve a `CompletableFuture`); small pool is intentional |
| `TRANSACTION_RESPONSE_QUEUE_SIZE` | ISO response-completion executor queue capacity | Integer | `5` | No | | | | |
| `TRANSACTION_RESPONSE_KEEP_ALIVE_SECONDS` | ISO response-completion thread idle timeout (s) | Integer | `60` | No | | | | |
| `TRANSACTION_RESPONSE_PREFIX` | ISO response-completion thread name prefix | String | `iso-transaction-response-` | No | | | | |
| `ISO_TRANSACTION_RESPONSE_VIRTUAL_THREAD_ENABLED` | Use virtual threads for the response-completion executor | Boolean | `true` | No | | | | |
| `ISO_TRANSACTION_RESPONSE_REJECTION_POLICY` | Response-completion executor saturation policy (`ABORT`\|`CALLER_RUNS`) | String | `ABORT` | No | | | | **Do not change to `CALLER_RUNS`.** Also fed directly from the Netty event loop (`TransactionResponseParticipant.onMessage`). |

---

## 11. Bulkhead (In-Flight Limiter) Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `ISO_TRANSACTION_INFLIGHT_HEADROOM` | Extra permits added on top of the derived pool capacity | Integer | `10` | No | | | | **There is no `ISO_TRANSACTION_INFLIGHT_PERMITS` var.** Permits are derived in code (`BulkheadConfiguration.resolvePermits`) as `TRANSACTION_MESSAGE_POOL + TRANSACTION_QUEUE_SIZE + this headroom`, so the bulkhead can never itself be the bottleneck ahead of the executor and the two numbers cannot drift out of sync. To change real concurrency, resize `TRANSACTION_MESSAGE_POOL`/`TRANSACTION_QUEUE_SIZE` above, not this. |
| `ISO_TRANSACTION_INFLIGHT_FAIR` | Fair queuing for bulkhead permits | Boolean | `false` | No | | | | `false` = FIFO, `true` = fair distribution |

---

## 12. Request-Context Cache Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `CONTEXT_CACHE_TTL_SECONDS` | Write-expiry TTL for `ContextHelper`'s per-request `ContextDTO` cache | Integer | `60` | No | | | | Entries are removed explicitly by `ContextHelper.cleanUp` on every request; this TTL is only a backstop against a leaked entry on a path that skips cleanup. Uncapped (no `maximumSize`) — see `CacheConfiguration` Javadoc |
| `ADDITIONAL_CONTEXT_CACHE_TTL_SECONDS` | Write-expiry TTL for `ContextHelper`'s per-request additional-error-detail cache | Integer | `60` | No | | | | Same backstop rationale as `CONTEXT_CACHE_TTL_SECONDS`, separate named cache |

---

## 13. Observability & Actuator Configuration

| Variable | Description | Type | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------|-------------|------|---------|----------|-------------|---------------|------------|-------|
| `ACTUATOR_PORT` | Actuator endpoints listen port | Integer | `1000` | No | | | | Separate from main HTTP port for security |
| `ACTUATOR_EXPOSED` | Actuator endpoints to expose (comma-separated) | String | `*` | No | | `*` | `health,info,prometheus` | **Restrict in production** for security |
| `ACTUATOR_SHUTDOWN` | Allow `/actuator/shutdown` endpoint | String | `none` | No | | | `none` | Keep as `none` to disable unsafe shutdown |
| `HEALTH_DETAIL` | Health check detail level | String | `always` | No | | | `when_authorized` | Set to `when_authorized` in production |
| `PROMETHEUS_ENABLED` | Export Prometheus metrics | Boolean | `true` | No | | | | Keep enabled for observability |
| `TRACING_SAMPLING_PROBABILITY` | Trace sampling rate (0.0–1.0) | Decimal | `1.0` | No | | | | Set to `0.1` or lower in high-volume production |
| `TRACING_CORRELATION_FIELDS` | Fields to correlate in traces (comma-separated) | String | `x-request-id` | No | | | | Typically just the request ID |
| `TRACING_LOCAL_FIELDS` | Local trace baggage fields (comma-separated) | String | `x-request-id` | No | | | | Baggage added to each span |
| `TRACING_REMOTE_FIELDS` | Fields to propagate downstream (comma-separated) | String | `x-request-id` | No | | | | Sent to called services |

---

## Notes for Operations

### Timeout Strategy

The application has several timeout layers, each with distinct purposes:

1. **ISO8583 Timeout** (`TIME_OUT_SECOND`): How long to wait for upstream to respond after sending a transaction
2. **REST Client Timeouts** (`TRANSACTION_CLIENT_*_TIMEOUT`): Connection, read, and write timeouts for calls to the downstream API
3. **Transaction Flight Timeout** (`TRANSACTION_FLIGHT_QUEUE_TIMEOUT`): How long the in-flight registry holds a pending response
4. **Message Queue Timeout** (`TRANSACTION_MESSAGE_QUEUE_TIME_OUT`): Grace period to accept late or duplicate responses

Typically: `REST read timeout` < `ISO8583 timeout` < `Flight timeout` < `Message queue timeout`.

### Thread Pools

- **Default Async** (`ASYNC_*`): Fire-and-forget operations (audit log cleanup, dead-letter removal)
- **ISO Transaction** (`TRANSACTION_*`): Handles ISO8583 inbound messages on virtual threads; must be large enough to handle peak message rate
- **Bulkhead** (`ISO_TRANSACTION_INFLIGHT_*`): Limits concurrent in-flight transactions to prevent resource exhaustion; shed excess with DE39 response code

### Secret Management

- **Production secrets** (e.g., `DB_PASS`) must be injected via a secrets manager, not hardcoded in `.env` files
- **Never log secret variables**; Logbook masks them automatically
- **Rotate `DB_PASS` immediately** after each new environment deployment

### Virtual Threads

Java virtual threads (Project Loom) are **always enabled** in this application:

- Allows hundreds of thousands of threads without exhausting OS resources
- ISO transaction executor runs on virtual threads; blocking calls are cheap
- Makes concurrency model simple and debuggable: write blocking code that reads like synchronous code

---

**Generated:** 2026-09-03 | **Stack:** Spring Boot 3.5.16 · Java 25 · servlet (Tomcat) + virtual threads
