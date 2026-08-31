# Environment Variables Reference

> Auto-generated from `src/main/resources/application.yml`.
> Last updated: 2026-08-29

## Legend

| Symbol | Meaning |
|--------|---------|
| 🔒 | Secret — never commit; rotate if exposed |
| 🔑 | PCI-DSS relevant — restrict access |
| ✅ | Required — no default; app won't start without it |

Every variable below has a default, so the app starts with no configuration. The defaults target a
local PostgreSQL at `localhost:5432/boilerplate` and a loopback ISO8583 host — override for any real
environment.

---

## Go-Live Checklist

Before deploying to production, verify:

- [ ] `DB_URL`, `DB_USER`, `DB_PASS` point to the production database
- [ ] `DB_PASS` is rotated — the default `changeme` must never be used in production
- [ ] All `🔒 Secret` variables are set via a secrets manager, not `.env` files
- [ ] `JPA_SHOW_SQL` and `HIBERNATE_FORMAT_SQL` are `false`
- [ ] `JDBC_LOG_LEVEL` is `WARN`+ and `ZALANDO_LOG_LEVEL` is `WARN` or `OFF`
- [ ] `ROOT_LOG_LEVEL` is `WARN` or `ERROR`
- [ ] `ACTUATOR_EXPOSED` is restricted from `*` to e.g. `health,info,prometheus`
- [ ] `ACTUATOR_SHUTDOWN` stays `none`; `HEALTH_DETAIL` is `when-authorized` or `never`
- [ ] `TRACING_SAMPLING_PROBABILITY` is reduced from `1.0` (e.g. `0.1`)
- [ ] `ISO8583_HOST` / `ISO8583_PORT` point to the production ISO8583 host
- [ ] `TRANSACTION_CLIENT_HOSTNAME` points to the production REST downstream
- [ ] `TRANSACTION_RETRY_MAX_ATTEMPT` stays `1` unless the downstream is idempotent on `x-request-id` (RRN); if raised, `TRANSACTION_RETRYABLE_EXCEPTIONS` is scoped and `dead_letter_process` rows have a drainer (no concrete `RetryProcessorService` ships)
- [ ] `ISO8583_MASKING_ENABLED` is `true` and `SENSITIVE_FIELD` covers every PAN/PII field name

---

## 1. Server

| Variable       | Description                                             | Type    | Default    | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|----------------|--------------------------------------------------------|---------|------------|----------|-------------|---------------|------------|-------|
| `SERVER_PORT`  | HTTP (servlet/Tomcat) listen port                       | Integer | `8080`     | No       |             |               |            |       |
| `CONTEXT_PATH` | Servlet context path (`server.servlet.context-path`)    | String  | `/sotres`  | No       |             |               |            | Prefixes every REST path |

---

## 2. Application

| Variable                 | Description                                                       | Type    | Default          | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|--------------------------|-----------------------------------------------------------------|---------|------------------|----------|-------------|---------------|------------|-------|
| `APPLICATION_NAME`       | Spring application name — used in tracing and logs               | String  | `sotres-api`     | No       |             |               |            |       |
| `VIRTUAL_THREAD_ENABLED` | Run Tomcat request threads on virtual threads (`spring.threads.virtual.enabled`) | Boolean | `true`           | No       |             |               |            | Requires JDK 21+ |
| `APPS_VERSION`           | Version string reported on `/actuator/info` and in metrics       | String  | `1.0.0-SNAPSHOT` | No       |             |               |            | Set to the release version on deploy |

---

## 3. Database (JDBC / HikariCP / Spring Data JPA)

| Variable                     | Description                                          | Type      | Default                                        | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|------------------------------|----------------------------------------------------|-----------|-----------------------------------------------|----------|-------------|---------------|------------|-------|
| `DB_URL`                     | JDBC connection URL for PostgreSQL                  | String    | `jdbc:postgresql://localhost:5432/boilerplate` | No       |             |               |            | Format `jdbc:postgresql://<host>:<port>/<db>` |
| `DB_USER`                    | PostgreSQL username                                 | String    | `postgres`                                     | No       | 🔒 Secret   |               |            |       |
| `DB_PASS`                    | PostgreSQL password                                 | String    | `changeme`                                     | No       | 🔒 Secret   |               |            | ⚠️ Rotate before prod — default is a placeholder |
| `DB_POOL_MAX_SIZE`           | HikariCP maximum pool size                          | Integer   | `10`                                           | No       |             |               |            | Size to expected concurrency |
| `DB_POOL_MIN_IDLE`           | HikariCP minimum idle connections                   | Integer   | `5`                                            | No       |             |               |            |       |
| `DB_POOL_CONNECTION_TIMEOUT` | Max wait for a pooled connection                    | Long (ms) | `5000`                                         | No       |             |               |            |       |
| `DB_POOL_IDLE_TIMEOUT`       | Max idle time before a connection is retired        | Long (ms) | `60000`                                        | No       |             |               |            |       |
| `DB_POOL_MAX_LIFETIME`       | Max total lifetime of a pooled connection           | Long (ms) | `300000`                                       | No       |             |               |            |       |
| `JPA_SHOW_SQL`               | Log generated SQL statements                        | Boolean   | `true`                                         | No       |             |               | `false`    | Set `false` in production |
| `HIBERNATE_FORMAT_SQL`       | Pretty-print logged SQL                             | Boolean   | `true`                                         | No       |             |               | `false`    | Set `false` in production |

`spring.jpa.hibernate.ddl-auto` is pinned to `none` (schema is managed by `src/main/resources/ddl.sql`);
`generate-ddl` is `false`; dialect is `org.hibernate.dialect.PostgreSQLDialect`. None of these are env-configurable.

---

## 4. Logging

| Variable            | Description                                                    | Type   | Default  | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------------|-------------------------------------------------------------- -|--------|----------|----------|-------------|---------------|------------|-------|
| `ROOT_LOG_LEVEL`    | Root logger level                                              | String | `INFO`   | No       |             |               | `WARN`     | Set `WARN`/`ERROR` in production |
| `ZALANDO_LOG_LEVEL` | `org.zalando.logbook` level — gates HTTP request/response logs | String | `TRACE`  | No       |             |               | `WARN`     | Set `WARN` or `OFF` in production |
| `JDBC_LOG_LEVEL`    | `org.springframework.jdbc.core` level                          | String | `INFO`   | No       |             |               | `WARN`     |       |
| `LOG_PATH`          | Directory for log-file output                                  | String | `logs/`  | No       |             |               |            | Must be writable by the app process |

Log **format** is chosen by `APPS_LOG_LEVEL` (see §9), which selects `log4j2-spring-<value>.xml`.

---

## 5. ISO8583 Connection & Network

| Variable                          | Description                                                         | Type      | Default     | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|-----------------------------------|-------------------------------------------------------------------|-----------|-------------|----------|-------------|---------------|------------|-------|
| `OUTGOING_PROTOCOL`               | Forwarding protocol for outbound transactions                     | String    | `REST`      | No       |             |               |            | Only `REST` is implemented |
| `DEFAULT_LOG_HANDLER_ENABLED`     | Enable jReactive-8583's built-in message log handler              | Boolean   | `true`      | No       |             |               |            |       |
| `ISO8583_MASKING_ENABLED`         | Mask configured DE fields (see `mask_fields` seed row) in ISO logs | Boolean   | `true`      | No       | 🔑 PCI-DSS  |               | `true`     | Keep `true` — masks PAN in message logs |
| `ISO8583_FIELD_DESCRIPTION_ENABLED`| Include human-readable DE descriptions in ISO message logs        | Boolean   | `true`      | No       |             |               |            |       |
| `ISO8583_HOST`                    | Upstream ISO8583 host to connect to                               | String    | `127.0.0.1` | No       |             |               |            |       |
| `ISO8583_PORT`                    | Upstream ISO8583 TCP port                                         | Integer   | `13001`     | No       |             |               |            |       |
| `ISO8583_WORKER_THREAD_COUNT`     | Netty worker-thread count for the ISO8583 transport               | Integer   | `100`       | No       |             |               |            |       |
| `FORWARDING_INSTITUTION_ID`       | Institution ID written to DE32                                    | String    | `625`       | No       |             |               |            |       |
| `RECONNECT_INTERVAL`              | Delay between reconnect attempts                                  | Long (ms) | `60000`     | No       |             |               |            |       |
| `TIME_OUT_SECOND`                 | ISO8583 response wait timeout                                     | Long (ms) | `15000`     | No       |             |               |            | Value is **ms** despite the name |
| `SCHEDULED_ECHO_ENABLED`          | Send periodic 0800 echo heartbeats                                | Boolean   | `true`      | No       |             |               |            |       |
| `ECHO_INTERVAL_SECOND`            | Interval between scheduled echoes                                 | Long (ms) | `30000`     | No       |             |               |            | Value is **ms** despite the name |

---

## 6. Participant Transaction Pool

Sizes the virtual-thread executor + `CorrelationRegistry` windows for inbound ISO8583 processing.

| Variable                            | Description                                                        | Type      | Default              | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|-------------------------------------|----------------------------------------------------------------- -|-----------|----------------------|----------|-------------|---------------|------------|-------|
| `TRANSACTION_CORE_POOL_SIZE`        | Core threads for the transaction executor                          | Integer   | `25`                 | No       |             |               |            | Also `apps.async.configurations.isoTransaction.core-pool-size` |
| `TRANSACTION_QUEUE_SIZE`            | Queue capacity for the transaction executor                        | Integer   | `50`                 | No       |             |               |            | Also the isoTransaction async `queue-capacity` |
| `TRANSACTION_FLIGHT_POOL_SIZE`      | Max in-flight entries in `CorrelationRegistry.pending`             | Integer   | `100`                | No       |             |               |            |       |
| `TRANSACTION_MESSAGE_POOL`          | Max entries in `CorrelationRegistry.registered` (grace window)     | Integer   | `200`                | No       |             |               |            | Also the isoTransaction async `max-pool-size` |
| `TRANSACTION_FLIGHT_QUEUE_TIMEOUT`  | TTL of a `pending` correlation entry (real-timeout window)         | Long (ms) | `15000`              | No       |             |               |            |       |
| `TRANSACTION_MESSAGE_QUEUE_TIME_OUT`| TTL of a `registered` correlation entry (grace window)             | Long (ms) | `60000`              | No       |             |               |            |       |
| `TRANSACTION_PREFIX`                | Thread-name / manager prefix                                       | String    | `TransactionManager` | No       |             |               |            | Defaults to `iso-transaction-` for the isoTransaction async pool |

---

## 7. Outbound REST Client

`client.configurations.transaction` — the blocking `RestClient` that forwards transactions downstream.

| Variable                              | Description                                                             | Type      | Default                                                              | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------------------------------|---------------------------------------------------------------------- -|-----------|--------------------------------------------------------------------|----------|-------------|---------------|------------|-------|
| `CLIENT_REGISTRY_TYPE`                | Response-correlation mode                                              | String    | `CALLBACK`                                                          | No       |             |               |            | `CALLBACK` (send-and-callback) or `RESPONSE` (send-and-wait); per-selector override via the `registry` seed rows |
| `TRANSACTION_CLIENT_HOSTNAME`         | Downstream REST base URL                                               | URL       | `http://localhost:8080`                                             | No       |             |               |            |       |
| `TRANSACTION_MAX_CONNECTION`          | Max pooled HTTP connections                                           | Integer   | `50`                                                               | No       |             |               |            |       |
| `TRANSACTION_MAX_IDLE_TIME`           | Max idle time for a pooled connection                                 | Long (ms) | `30000`                                                            | No       |             |               |            |       |
| `TRANSACTION_MAX_LIFE_TIME`           | Max total lifetime of a pooled connection                             | Long (ms) | `60000`                                                            | No       |             |               |            |       |
| `TRANSACTION_EVICT_BACKGROUND`        | Idle-connection eviction sweep interval                               | Long (ms) | `120000`                                                           | No       |             |               |            |       |
| `TRANSACTION_PENDING_ACQUIRE_TIMEOUT` | Max wait to acquire a connection from the pool                        | Long (ms) | `3000`                                                             | No       |             |               |            |       |
| `TRANSACTION_CLIENT_CONNECT_TIMEOUT`  | TCP connect timeout                                                   | Long (ms) | `5000`                                                             | No       |             |               |            |       |
| `TRANSACTION_CLIENT_READ_TIMEOUT`     | Socket read timeout                                                   | Long (ms) | `10000`                                                            | No       |             |               |            |       |
| `TRANSACTION_CLIENT_WRITE_TIMEOUT`    | Socket write timeout                                                  | Long (ms) | `10000`                                                            | No       |             |               |            |       |
| `TRANSACTION_CLIENT_TIMEUNIT`         | Unit for the timeout values above                                     | String    | `MILLISECONDS`                                                     | No       |             |               |            | `java.util.concurrent.TimeUnit` name |

### Retry (`apps.retry.configurations.transaction`)

Backs a `org.springframework.retry.support.RetryTemplate` (classic `spring-retry`) wired into
`RestSender.executeWithRetry`.

> ⚠️ **Retry is off by default (`max-attempt = 1`).** The default retryable set is
> `ResourceAccessException`, which includes **read timeouts** — where the downstream may already
> have processed the request. Only raise `TRANSACTION_RETRY_MAX_ATTEMPT` above `1` if the
> downstream service is **idempotent on `x-request-id` (the RRN)**; otherwise a retried
> authorisation/capture can double-charge.

| Variable                              | Description                                                             | Type      | Default                                                             | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------------------------------|-----------------------------------------------------------------------|-----------|-------------------------------------------------------------------|----------|-------------|---------------|------------|-------|
| `TRANSACTION_RETRY_TYPE`              | Backoff strategy                                                      | Enum      | `EXPONENTIAL_RANDOM`                                              | No       |             |               |            | `FIXED` \| `EXPONENTIAL` \| `EXPONENTIAL_RANDOM` \| `UNIFORM_RANDOM` |
| `TRANSACTION_RETRY_MAX_ATTEMPT`       | Total executions (incl. the first)                                   | Integer   | `1`                                                              | No       |             |               | `1`        | `1` = one call, no retry. Raise only for an idempotent downstream |
| `TRANSACTION_RETRY_DEAD_LETTER`       | Persist an exhausted call to `dead_letter_process` (else log only)   | Boolean   | `true`                                                            | No       |             |               |            | No row is ever written when `max-attempt = 1` |
| `TRANSACTION_RETRY_INITIAL_INTERVAL`  | First backoff interval / fixed period / uniform min                  | Long (ms) | `500`                                                            | No       |             |               |            |       |
| `TRANSACTION_RETRY_MULTIPLIER`        | Exponential growth factor                                            | Double    | `2.0`                                                            | No       |             |               |            | `EXPONENTIAL` / `EXPONENTIAL_RANDOM` only |
| `TRANSACTION_RETRY_MAX_INTERVAL`      | Backoff cap / uniform max                                            | Long (ms) | `10000`                                                          | No       |             |               |            |       |
| `TRANSACTION_RETRYABLE_EXCEPTIONS`    | Exception→retryable map                                              | String    | `org.springframework.web.client.ResourceAccessException:true`     | No       |             |               |            | Comma-separated `FQCN:boolean` pairs; `true` = whitelist, `false` = blacklist; subclass-aware. Empty whitelist ⇒ retry on any exception |

---

## 8. Actuator & Observability

| Variable                       | Description                                              | Type    | Default        | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|--------------------------------|--------------------------------------------------------|---------|----------------|----------|-------------|---------------|------------|-------|
| `ACTUATOR_PORT`                | Management server port (separate from the API port)     | Integer | `1000`         | No       |             |               |            |       |
| `ACTUATOR_SHUTDOWN`            | Access level for the `/actuator/shutdown` endpoint      | String  | `none`         | No       |             |               | `none`     | Keep `none` in production |
| `HEALTH_DETAIL`               | `management.endpoint.health.show-details`               | String  | `always`       | No       |             |               | `when-authorized` | Do not expose full health detail unauthenticated in prod |
| `ACTUATOR_EXPOSED`            | `management.endpoints.web.exposure.include`             | String  | `*`            | No       |             |               | `health,info,prometheus` | ⚠️ `*` exposes `env`/`beans`/`configprops`/`threaddump`/`heapdump` — restrict in prod |
| `PROMETHEUS_ENABLED`         | Enable the Prometheus metrics endpoint                  | Boolean | `true`         | No       |             |               |            |       |
| `TRACING_SAMPLING_PROBABILITY`| Trace sampling rate (`0.0`–`1.0`)                       | String  | `1.0`          | No       |             |               | `0.1`      | Reduce in high-traffic production |
| `TRACING_CORRELATION_FIELDS`  | Baggage fields written to the MDC / log correlation     | String  | `x-request-id` | No       |             |               |            |       |
| `TRACING_LOCAL_FIELDS`        | Baggage fields kept process-local                       | String  | `x-request-id` | No       |             |               |            |       |
| `TRACING_REMOTE_FIELDS`       | Baggage fields propagated to downstream calls           | String  | `x-request-id` | No       |             |               |            |       |

---

## 9. Application Behaviour & Logging Toggles

`apps.log.*` — the custom structured-logging layer (separate from the framework logger levels in §4).

| Variable              | Description                                                            | Type    | Default                        | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|-----------------------|-------------------------------------------------------------------- -|---------|--------------------------------|----------|-------------|---------------|------------|-------|
| `APPS_METRIC_ENABLED` | Emit application-layer metric log entries                              | Boolean | `true`                         | No       |             |               |            |       |
| `APPS_TRACE_ENABLED`  | Emit application-layer trace log entries                              | Boolean | `true`                         | No       |             |               |            |       |
| `APPS_API_ENABLED`    | Emit HTTP request/response log entries (via logbook + `ApiLogbookWriter`) | Boolean | `true`                     | No       |             |               |            |       |
| `SENSITIVE_FIELD`     | Comma-separated JSON field names to mask in API logs                  | String  | `cardNo`                       | No       | 🔑 PCI-DSS  |               |            | Add every PAN/PII field name here |
| `APPS_LOG_LEVEL`      | Log **format** selector — picks `log4j2-spring-<value>.xml`           | String  | `json`                         | No       |             |               | `json`     | `json` or `text` (name is misleading — it is a format, not a level) |
| `APPS_IGNORED_PATH`   | Ant path patterns excluded from the `event_logs` audit trail          | String  | `/actuator/**,/internal-api/**` | No       |             |               |            | Comma-separated |

---

## 10. Async Thread Pools

`apps.async.configurations` — two `ThreadPoolTaskExecutor`s registered by `AsyncTaskConfiguration`.
`default` backs `@Async("defaultAsyncTaskExecutor")` (event-log / dead-letter purge); `isoTransaction`
backs the inbound ISO8583 hand-off.

| Variable                              | Description                                                | Type    | Default          | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------------------------------|-------------------------------------------------------- -|---------|------------------|----------|-------------|---------------|------------|-------|
| `ASYNC_CORE_POOL_SIZE`                | `default` pool — core threads                             | Integer | `5`              | No       |             |               |            |       |
| `ASYNC_MAX_POOL_SIZE`                 | `default` pool — max threads                              | Integer | `25`             | No       |             |               |            |       |
| `ASYNC_QUEUE_CAPACITY`               | `default` pool — task queue capacity                      | Integer | `50`             | No       |             |               |            |       |
| `ASYNC_KEEP_ALIVE_SECONDS`           | `default` pool — idle-thread keep-alive                   | Integer | `30`             | No       |             |               |            | Unit is **seconds** |
| `ASYNC_THREAD_NAME_PREFIX`           | `default` pool — thread-name prefix                       | String  | `default-async-` | No       |             |               |            |       |
| `ASYNC_VIRTUAL_THREAD_ENABLED`       | `default` pool — run tasks on virtual threads             | Boolean | `true`           | No       |             |               |            |       |
| `TRANSACTION_KEEP_ALIVE_SECONDS`     | `isoTransaction` pool — idle-thread keep-alive            | Integer | `60`             | No       |             |               |            | Unit is **seconds** |
| `ISO_TRANSACTION_VIRTUAL_THREAD_ENABLED` | `isoTransaction` pool — run tasks on virtual threads   | Boolean | `true`           | No       |             |               |            |       |

The `isoTransaction` pool's core / max / queue sizes reuse `TRANSACTION_CORE_POOL_SIZE`,
`TRANSACTION_MESSAGE_POOL`, `TRANSACTION_QUEUE_SIZE` from §6.

---

## 11. ISO8583 Bulkhead

`apps.bulkhead.configurations.isoTransaction` — a `Semaphore` capping concurrent in-flight transactions.

| Variable                          | Description                                          | Type    | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|-----------------------------------|--------------------------------------------------- -|---------|---------|----------|-------------|---------------|------------|-------|
| `ISO_TRANSACTION_INFLIGHT_PERMITS`| Max concurrent in-flight ISO8583 transactions       | Integer | `100`   | No       |             |               |            | Excess is shed with a DE39 busy response |
| `ISO_TRANSACTION_INFLIGHT_FAIR`   | Use a fair (FIFO) semaphore                          | Boolean | `false` | No       |             |               |            |       |
