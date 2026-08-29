# Environment Variables Reference

> Auto-generated from `src/main/resources/application.yml`.
> Last updated: 2026-08-29 — **partially hand-patched** for the reactive→blocking + JDBC→JPA
> migration; regenerate with `/env-var-documentation` for a clean sweep.

## Legend

| Symbol | Meaning                                           |
|--------|---------------------------------------------------|
| 🔒     | Secret — never commit; rotate if exposed          |
| 🔑     | PCI-DSS relevant — restrict access                |
| ✅      | Required — no default; app won't start without it |

---

## Go-Live Checklist

Before deploying to production, verify:

- [ ] `DB_URL`, `DB_USER`, `DB_PASS` point to the production database
- [ ] `DB_PASS` is rotated — default value `changeme` must never be used in production
- [ ] All `🔒 Secret` variables are set via secrets manager, not `.env` files
- [ ] `ACTUATOR_EXPOSED` is restricted from `*` to specific endpoints (e.g. `health,info,prometheus`)
- [ ] `ACTUATOR_SHUTDOWN` remains `none` in production
- [ ] `HEALTH_DETAIL` is changed from `always` to `when-authorized` or `never` in production
- [ ] `ISO8583_HOST` and `ISO8583_PORT` point to the production ISO8583 host
- [ ] `TRANSACTION_CLIENT_HOSTNAME` points to the production REST downstream
- [ ] `ROOT_LOG_LEVEL` is `WARN` or `ERROR` — not `INFO` or `DEBUG` — in production
- [ ] `ZALANDO_LOG_LEVEL`, `JDBC_LOG_LEVEL`, `POSTGRESQL_LOG_LEVEL` are set to `WARN` or `OFF` in production
- [ ] `JPA_DDL_AUTO` is `none` (schema is managed by `ddl.sql`) and `JPA_SHOW_SQL` is `false` in production
- [ ] `TRACING_SAMPLING_PROBABILITY` is reduced from `1.0` (100%) to an appropriate rate (e.g. `0.1`) in production

---

## 1. Server

| Variable      | Description                     | Type    | Default | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------|---------------------------------|---------|---------|----------|-------------|---------------|------------|-------|
| `SERVER_PORT` | HTTP port the server listens on | Integer | `8080`  | No       |             |               |            |       |

---

## 2. Application

| Variable                 | Description                                         | Type    | Default          | Required | Sensitivity | Nonprod Value | Prod Value | Notes                            |
|--------------------------|-----------------------------------------------------|---------|------------------|----------|-------------|---------------|------------|----------------------------------|
| `APPLICATION_NAME`       | Spring application name, used in tracing and logs   | String  | `sotres-api`     | No       |             |               |            |                                  |
| `CONTEXT_PATH`           | Servlet context path (`server.servlet.context-path`) for all endpoints | String  | `/sotres`        | No       |             |               |            |                                  |
| `VIRTUAL_THREAD_ENABLED` | Run Tomcat request threads and the async executors on virtual threads  | Boolean | `true`           | No       |             |               |            | Requires JDK 21+                 |
| `APPS_VERSION`           | Application version string reported in metrics/info | String  | `1.0.0-SNAPSHOT` | No       |             |               |            | Set to release version on deploy |

---

## 3. Database (JDBC / HikariCP / Spring Data JPA)

| Variable                    | Description                                                | Type    | Default                                        | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                            |
|-----------------------------|-----------------------------------------------------------|---------|-----------------------------------------------|----------|-------------|---------------|------------|--------------------------------------------------|
| `DB_URL`                    | JDBC connection URL for PostgreSQL                         | String  | `jdbc:postgresql://localhost:5432/boilerplate` | No       |             |               |            | Format: `jdbc:postgresql://<host>:<port>/<db>`   |
| `DB_USER`                   | PostgreSQL username                                        | String  | `postgres`                                     | No       | 🔒 Secret   |               |            |                                                  |
| `DB_PASS`                   | PostgreSQL password                                        | String  | `changeme`                                     | No       | 🔒 Secret   |               |            | ⚠️ Rotate before prod — default is a placeholder |
| `DB_POOL_MAX_SIZE`          | HikariCP maximum pool size                                 | Integer | `10`                                           | No       |             |               |            | Size to expected concurrency                     |
| `DB_POOL_MIN_IDLE`          | HikariCP minimum idle connections                          | Integer | `5`                                            | No       |             |               |            |                                                  |
| `DB_POOL_CONNECTION_TIMEOUT`| Max wait for a connection from the pool (ms)               | Integer | `5000`                                         | No       |             |               |            |                                                  |
| `DB_POOL_IDLE_TIMEOUT`      | Max idle time before a connection is retired (ms)          | Integer | `60000`                                        | No       |             |               |            |                                                  |
| `DB_POOL_MAX_LIFETIME`      | Max total lifetime of a connection (ms)                    | Integer | `300000`                                       | No       |             |               |            |                                                  |
| `JPA_DDL_AUTO`              | Hibernate `ddl-auto`                                       | String  | `none`                                         | No       |             |               |            | Schema is managed externally via `ddl.sql`       |
| `JPA_SHOW_SQL`              | Log generated SQL statements                               | Boolean | `true`                                         | No       |             |               | `false`    | Set `false` in production                        |
| `HIBERNATE_FORMAT_SQL`      | Pretty-print logged SQL                                    | Boolean | `true`                                         | No       |             |               | `false`    |                                                  |

---

## 4. Logging

| Variable               | Description                                                  | Type    | Default                         | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                                                  |
|------------------------|--------------------------------------------------------------|---------|---------------------------------|----------|-------------|---------------|------------|------------------------------------------------------------------------|
| `ROOT_LOG_LEVEL`       | Root logging level for the application                       | String  | `INFO`                          | No       |             |               |            | Set to `WARN` in production                                            |
| `ZALANDO_LOG_LEVEL`    | Log level for Logbook HTTP request/response logging          | String  | `TRACE`                         | No       |             |               |            | Set to `WARN` or `OFF` in production                                   |
| `JDBC_LOG_LEVEL`       | Log level for `org.springframework.jdbc.core`                | String  | `INFO`                          | No       |             |               |            | Set to `WARN` in production                                            |
| `POSTGRESQL_LOG_LEVEL` | Log level for PostgreSQL query and parameter logging         | String  | `DEBUG`                         | No       |             |               |            | Controls both `QUERY` and `PARAM` loggers; set to `WARN` in production |
| `LOG_PATH`             | Directory path for log file output                           | String  | `logs/`                         | No       |             |               |            | Ensure path is writable by the app process                             |
| `APPS_LOG_LEVEL`       | Log format selector: `json` for structured, `text` for plain | String  | `json`                          | No       |             |               |            | Selects the `log4j2-spring-<value>.xml` config                         |
| `APPS_METRIC_ENABLED`  | Enable metric logging in the application layer               | Boolean | `true`                          | No       |             |               |            |                                                                        |
| `APPS_TRACE_ENABLED`   | Enable trace logging in the application layer                | Boolean | `true`                          | No       |             |               |            |                                                                        |
| `APPS_API_ENABLED`     | Enable API request/response logging                          | Boolean | `true`                          | No       |             |               |            |                                                                        |
| `SENSITIVE_FIELD`      | Comma-separated field names to mask in API logs              | String  | `cardNo`                        | No       |             |               |            | Add PAN, CVV, and PIN field names as needed                            |
| `APPS_IGNORED_PATH`    | Comma-separated URL patterns excluded from API logging       | String  | `/actuator/**,/internal-api/**` | No       |             |               |            |                                                                        |

---

## 5. Actuator & Observability

| Variable                       | Description                                                    | Type    | Default        | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                                                   |
|--------------------------------|----------------------------------------------------------------|---------|----------------|----------|-------------|---------------|------------|-------------------------------------------------------------------------|
| `ACTUATOR_PORT`                | Management server port for actuator endpoints                  | Integer | `1000`         | No       |             |               |            | Keep on a separate port from `SERVER_PORT`                              |
| `ACTUATOR_EXPOSED`             | Comma-separated actuator endpoints to expose via HTTP          | String  | `*`            | No       |             |               |            | ⚠️ `*` exposes all — restrict to `health,info,prometheus` in production |
| `ACTUATOR_SHUTDOWN`            | Access policy for the shutdown endpoint                        | String  | `none`         | No       |             |               |            | Keep `none` in production                                               |
| `HEALTH_DETAIL`                | Detail level of the `/health` endpoint response                | String  | `always`       | No       |             |               |            | Set to `when-authorized` or `never` in production                       |
| `PROMETHEUS_ENABLED`           | Enable Prometheus metrics export                               | Boolean | `true`         | No       |             |               |            |                                                                         |
| `TRACING_SAMPLING_PROBABILITY` | Fraction of requests sampled for distributed tracing (0.0–1.0) | String  | `1.0`          | No       |             |               |            | `1.0` = 100% sampling; reduce to `0.1` in high-traffic production       |
| `TRACING_CORRELATION_FIELDS`   | Baggage fields propagated as trace correlation headers         | String  | `x-request-id` | No       |             |               |            | Comma-separated                                                         |
| `TRACING_LOCAL_FIELDS`         | Baggage fields propagated locally within the service           | String  | `x-request-id` | No       |             |               |            |                                                                         |
| `TRACING_REMOTE_FIELDS`        | Baggage fields propagated to downstream services               | String  | `x-request-id` | No       |             |               |            |                                                                         |

---

## 6. ISO8583 Connection

| Variable                            | Description                                                     | Type      | Default     | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                |
|-------------------------------------|-----------------------------------------------------------------|-----------|-------------|----------|-------------|---------------|------------|--------------------------------------|
| `OUTGOING_PROTOCOL`                 | Protocol used for outgoing transaction forwarding               | String    | `REST`      | No       |             |               |            | Currently only `REST` is implemented |
| `ISO8583_HOST`                      | Hostname or IP of the ISO8583 upstream host                     | String    | `127.0.0.1` | No       |             |               |            |                                      |
| `ISO8583_PORT`                      | TCP port of the ISO8583 upstream host                           | Integer   | `13001`     | No       |             |               |            |                                      |
| `ISO8583_WORKER_THREAD_COUNT`       | Number of worker threads for the ISO8583 channel                | Integer   | `100`       | No       |             |               |            |                                      |
| `FORWARDING_INSTITUTION_ID`         | DE 33 forwarding institution identification code                | String    | `625`       | No       |             |               |            |                                      |
| `RECONNECT_INTERVAL`                | Interval between reconnection attempts to the ISO8583 host (ms) | Long (ms) | `60000`     | No       |             |               |            |                                      |
| `TIME_OUT_SECOND`                   | Timeout waiting for an ISO8583 response (ms)                    | Long (ms) | `15000`     | No       |             |               |            |                                      |
| `SCHEDULED_ECHO_ENABLED`            | Enable periodic echo/heartbeat messages to the ISO8583 host     | Boolean   | `true`      | No       |             |               |            |                                      |
| `ECHO_INTERVAL_SECOND`              | Interval between echo/heartbeat messages (ms)                   | Long (ms) | `30000`     | No       |             |               |            |                                      |
| `DEFAULT_LOG_HANDLER_ENABLED`       | Enable the default ISO8583 message log handler                  | Boolean   | `true`      | No       |             |               |            |                                      |
| `ISO8583_MASKING_ENABLED`           | Enable field masking in ISO8583 message logs                    | Boolean   | `true`      | No       |             |               |            | Keep `true` in all environments      |
| `ISO8583_FIELD_DESCRIPTION_ENABLED` | Include ISO8583 field descriptions in logs                      | Boolean   | `true`      | No       |             |               |            |                                      |

---

## 7. Participant Pool

| Variable                             | Description                                                     | Type      | Default              | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                                                                                                                  |
|--------------------------------------|-----------------------------------------------------------------|-----------|----------------------|----------|-------------|---------------|------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `TRANSACTION_CORE_POOL_SIZE`         | Core thread pool size for transaction and API participant pools | Integer   | `25`                 | No       |             |               |            | Shared by both `transaction` and `api` pool configs                                                                                    |
| `TRANSACTION_QUEUE_SIZE`             | Work queue size for participant pools                           | Integer   | `50`                 | No       |             |               |            | Shared by both pools                                                                                                                   |
| `TRANSACTION_FLIGHT_POOL_SIZE`       | Maximum in-flight transaction slots                             | Integer   | `100`                | No       |             |               |            | Shared by both pools                                                                                                                   |
| `TRANSACTION_MESSAGE_POOL`           | Message queue pool capacity                                     | Integer   | `200`                | No       |             |               |            | Shared by both pools                                                                                                                   |
| `TRANSACTION_FLIGHT_QUEUE_TIMEOUT`   | Timeout waiting for an in-flight slot (ms)                      | Long (ms) | `15000`              | No       |             |               |            | Shared by both pools                                                                                                                   |
| `TRANSACTION_MESSAGE_QUEUE_TIME_OUT` | Timeout waiting in the message queue (ms)                       | Long (ms) | `60000`              | No       |             |               |            | Shared by both pools                                                                                                                   |
| `TRANSACTION_PREFIX`                 | Thread name prefix for participant pool threads                 | String    | `TransactionManager` | No       |             |               |            | Used by both `transaction` (default `TransactionManager`) and `api` (default `ApiManager`) pools — setting this env var overrides both |

---

## 8. External Clients

| Variable                              | Description                                                  | Type      | Default                 | Required | Sensitivity | Nonprod Value | Prod Value | Notes |
|---------------------------------------|--------------------------------------------------------------|-----------|-------------------------|----------|-------------|---------------|------------|-------|
| `CLIENT_REGISTRY_TYPE`                | Strategy for resolving the outgoing client                   | String    | `CALLBACK`              | No       |             |               |            |       |
| `TRANSACTION_CLIENT_HOSTNAME`         | Base URL of the downstream REST transaction service          | URL       | `http://localhost:8080` | No       |             |               |            |       |
| `TRANSACTION_MAX_CONNECTION`          | Maximum concurrent connections in the HTTP connection pool   | Integer   | `50`                    | No       |             |               |            |       |
| `TRANSACTION_MAX_IDLE_TIME`           | Maximum idle time for pooled HTTP connections (ms)           | Long (ms) | `30000`                 | No       |             |               |            |       |
| `TRANSACTION_MAX_LIFE_TIME`           | Maximum total lifetime of pooled HTTP connections (ms)       | Long (ms) | `60000`                 | No       |             |               |            |       |
| `TRANSACTION_EVICT_BACKGROUND`        | Interval for background eviction of expired connections (ms) | Long (ms) | `120000`                | No       |             |               |            |       |
| `TRANSACTION_PENDING_ACQUIRE_TIMEOUT` | Max wait time for a connection to become available (ms)      | Long (ms) | `3000`                  | No       |             |               |            |       |
| `TRANSACTION_CLIENT_CONNECT_TIMEOUT`  | TCP connection establishment timeout (ms)                    | Long (ms) | `5000`                  | No       |             |               |            |       |
| `TRANSACTION_CLIENT_READ_TIMEOUT`     | Read timeout for HTTP responses (ms)                         | Long (ms) | `10000`                 | No       |             |               |            |       |
| `TRANSACTION_CLIENT_WRITE_TIMEOUT`    | Write timeout for HTTP requests (ms)                         | Long (ms) | `10000`                 | No       |             |               |            |       |
| `TRANSACTION_CLIENT_TIMEUNIT`         | Time unit applied to client timeout values                   | String    | `MILLISECONDS`          | No       |             |               |            |       |

---

## 9. Client Retry

| Variable                           | Description                                                                                | Type      | Default                                                  | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                         |
|------------------------------------|--------------------------------------------------------------------------------------------|-----------|----------------------------------------------------------|----------|-------------|---------------|------------|-----------------------------------------------|
| `TRANSACTION_RETRY_MAX_ATTEMPT`    | Maximum retry attempts for failed downstream calls                                         | Integer   | `1`                                                      | No       |             |               |            | `1` means one retry after the initial failure |
| `TRANSACTION_RETRY_MIN_BACK_OFF`   | Minimum back-off delay between retries (ms)                                                | Long (ms) | `500`                                                    | No       |             |               |            |                                               |
| `TRANSACTION_RETRYABLE_EXCEPTIONS` | Colon-separated `ClassName:shouldRetry` pairs controlling which exceptions trigger a retry | String    | `reactor.netty.http.client.PrematureCloseException:true` | No       |             |               |            | Format: `fully.qualified.ClassName:true`      |

---

## 10. Async Thread Pool

| Variable                   | Description                                        | Type    | Default          | Required | Sensitivity | Nonprod Value | Prod Value | Notes                                 |
|----------------------------|----------------------------------------------------|---------|------------------|----------|-------------|---------------|------------|---------------------------------------|
| `ASYNC_CORE_POOL_SIZE`     | Core threads in the default async executor         | Integer | `5`              | No       |             |               |            |                                       |
| `ASYNC_MAX_POOL_SIZE`      | Maximum threads in the default async executor      | Integer | `25`             | No       |             |               |            |                                       |
| `ASYNC_QUEUE_CAPACITY`     | Task queue capacity for the async executor         | Integer | `50`             | No       |             |               |            |                                       |
| `ASYNC_KEEP_ALIVE_SECONDS` | Idle thread keep-alive time for the async executor | Integer | `30`             | No       |             |               |            | Unit is **seconds**, not milliseconds |
| `ASYNC_THREAD_NAME_PREFIX` | Thread name prefix for the default async executor  | String  | `default-async-` | No       |             |               |            |                                       |

---

## 11. Scheduler

_Removed._ The reactor-scheduler retry-processor infrastructure (`RETRY_PROCESSOR_*`,
`SCHEDULER_*`) was deleted in the reactive→blocking migration. Background work now runs on the
`@Async("defaultAsyncTaskExecutor")` pool (see [Async](#8-async-execution)); the dead-letter
retry path is a synchronous, unwired extension point.
