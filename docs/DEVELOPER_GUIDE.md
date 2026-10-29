# Developer Guide — Building on the sotres Boilerplate

> Audience: software engineers who will extend this gateway for a new acquirer / switch / downstream.
> Runtime: Java 25 · Spring Boot 4.1.1 · servlet MVC + virtual threads · jReactive-8583 (Netty) · PostgreSQL.
> Everything below was derived from the source in `src/main` and `src/test`. Where behaviour is *inferred from
> reading code rather than exercised by a test*, the text says so.

Related docs (this guide links to them rather than repeating them):
[`JSLT_GUIDE.md`](JSLT_GUIDE.md) · [`ENVIRONMENT_VARIABLES.md`](ENVIRONMENT_VARIABLES.md) · [`API_REFERENCE.md`](API_REFERENCE.md)

---

## Contents

1. [Mental model](#1-mental-model)
2. [First-hour checklist: wiring one new transaction end to end](#2-first-hour-checklist)
3. [`application.yml` — every block, its code consumer, its impact](#3-applicationyml-reference)
4. [Adding another ISO8583 packager](#4-adding-another-iso8583-packager)
5. [System properties (`ConfigGroup`)](#5-system-properties-configgroup)
6. [Customizing the selector](#6-customizing-the-selector)
7. [JSLT mapping](#7-jslt-mapping)
8. [Response mode vs. callback mode](#8-response-mode-vs-callback-mode)
9. [Extending `AbstractTransactionHandler`](#9-extending-abstracttransactionhandler)
10. [Extending `SenderProtocolStrategy`](#10-extending-senderprotocolstrategy)
11. [Dead-letter process handler](#11-dead-letter-process-handler)
12. [Observability mapping](#12-observability-mapping)
13. [Awareness notes and known gotchas](#13-awareness-notes-and-known-gotchas)
14. [Appendix](#14-appendix)

---

## 1. Mental model

The service is an **ISO8583 TCP client** (it dials *out* to a switch) that also behaves like a server for requests the
switch pushes down the same socket. Each inbound business message is converted to a `RequestContext`, optionally
transformed by JSLT, POSTed to a REST backend, and the REST reply is converted back to an ISO8583 response.

```
 Switch / Acquirer                                 sotres                                      REST backend
 ─────────────────                  ─────────────────────────────────────────────           ────────────
                                    Netty event loop (NEVER block here)
   0x0200 request ───────────────►  IsoCallbackResponseHandler   classify: reply to us? or new request?
                                    └► TransactionProcessorParticipant.onMessage
                                         • build RequestContext, compute selector
                                         • start Observation `iso.message` + span
                                         • hand off ─────────────┐
                                    ─────────────────────────────┼────────────────────
                                    virtual thread (isoTransactionAsyncTaskExecutor)
                                         • find AbstractTransactionHandler by selector
                                         • bulkhead.tryAcquire (shed → DE39 96)
                                         • handler.validate → handler.process
                                         • SenderProtocolStrategy.send ──► TransactionClient
                                                                            JSLT request tpl ─────►  POST {path}
                                                                            JSLT response tpl ◄─────  JSON reply
                                         • strategy.handleResponse
                                           (handler.populateResponse → map DE39 → write 0x0210)
   0x0210 response ◄──────────────  IsoResponseSender.sendResponseWithObservation
```

Key classes and where they sit:

| Concern | Class | Package |
|---|---|---|
| Socket + pipeline wiring | `CoreConfiguration`, `EnhancedIsoClient` | `configuration`, `helper` |
| Inbound classification (reply vs. new request) | `IsoCallbackResponseHandler` | `helper` |
| Business message entry | `TransactionProcessorParticipant` | `participant` |
| Echo / sign-on / sign-off | `NetworkProcessorParticipant`, `NetworkService` | `participant`, `service` |
| Reply completion (response mode) | `TransactionResponseParticipant`, `CorrelationRegistry` | `participant`, `helper` |
| **Your business logic** | subclasses of `AbstractTransactionHandler` | `strategy.transaction` |
| **Your outbound protocol** | implementations of `SenderProtocolStrategy` | `strategy.outgoing` |
| REST call + JSLT + retry | `TransactionClient`, `JsltTransformationHelper`, `RestSender` | `client`, `helper` |
| Runtime config in DB | `SystemPropertiesService`, `ConfigGroup`, `TemplateGroup` | `service`, `model.constant` |
| Dead letters | `RestSenderRetryListener`, `AbstractRetryProcessorService`, `DeadLetterProcessService` | `listener`, `service` |

**What ships vs. what you must add.** The boilerplate ships *no* `AbstractTransactionHandler` and *no*
`AbstractRetryProcessorService` implementation. Until you add a handler, every business message is answered with
DE39 `92` (unable to route) — see `TransactionProcessorParticipant.handleTransaction`, and the note in
`src/test/.../e2e/support/E2eTestConfig.java`.

---

## 2. First-hour checklist

To get a brand-new transaction type flowing, you touch these places, in this order. Details for each step are in the
numbered sections.

| # | Step | Where | Section |
|---|---|---|---|
| 1 | Decide the **selector** the message will produce (e.g. `20.97-E001` for a `0200`, DE3 `97xxxx`, DE48 `PI=E001`) | `IsoFieldHelper.createSelector` | [6](#6-customizing-the-selector) |
| 2 | Make sure the fields you need exist in the packager XML | `src/main/resources/default-packager.xml` | [4](#4-adding-another-iso8583-packager) |
| 3 | Write a handler whose `getSelectors()` contains that selector | new `@Component` extending `AbstractTransactionHandler` | [9](#9-extending-abstracttransactionhandler) |
| 4 | Insert DB rows: `endpoint_path/mapping`, `client_spec_request`, `client_spec_response` | `system_properties` | [5](#5-system-properties-configgroup), [7](#7-jslt-mapping) |
| 5 | Add the **response-code mapping** for every downstream code you expect | `response/incoming_outgoing_mapping` | [5](#5-system-properties-configgroup) |
| 6 | Reload config: `PUT /sotres/internal-api/configurations/_reload?group=PATH_MAPPING` (and `RESPONSE_MAPPING`) | internal API | [5](#5-system-properties-configgroup) |
| 7 | Map the observability feature names | `IsoFeatureConstant`, `ExternalFeatureConstant` | [12](#12-observability-mapping) |
| 8 | Add tests (E2E harness exists; coverage gate is 85%) | `src/test` | [13](#13-awareness-notes-and-known-gotchas) |
| 9 | Document new env vars / endpoints | `docs/ENVIRONMENT_VARIABLES.md`, `docs/API_REFERENCE.md` | — |

Base URL for all `curl` examples below: `http://localhost:8080/sotres` (`server.port` + `server.servlet.context-path`).
Internal endpoints live under `/sotres/internal-api/**`; the management port is separate (`1000`).

---

## 3. `application.yml` reference

Every value is `${ENV_VAR:default}`; the full env-var table (types, secrets, prod values) is in
[`ENVIRONMENT_VARIABLES.md`](ENVIRONMENT_VARIABLES.md). This section answers a different question: **what code reads it,
and what changes if you change it.**

### 3.1 Server and Spring

| Key | Default | Consumed by | Impact |
|---|---|---|---|
| `server.port` | `8080` | Tomcat | Public + internal API port. Actuator is on a different port (see 3.7). |
| `server.servlet.context-path` | `/sotres` | Tomcat; `HeaderFilter` (`@Value`) | Every URL is prefixed. `HeaderFilter` strips it before building `ContextDTO.path`, so `ApiFeatureConstant` paths are written **without** the prefix. |
| `spring.threads.virtual.enabled` | `true` | Boot → Tomcat request threads | Request threads become virtual. The architecture (blocking JDBC/REST inside handlers) assumes this is `true`. |
| `spring.datasource.*` / `hikari.*` | pool 10 | HikariCP | JDBC is used for config loads, JSLT template cache-misses, `event_logs`, `dead_letter_process`. Pool size (10) is far below the bulkhead ceiling (110 — see 3.5); if you add a handler that hits the DB per transaction, size the pool accordingly. |
| `spring.jpa.hibernate.ddl-auto` | `none` | Hibernate | **Schema is external.** Apply `src/main/resources/ddl.sql` yourself; entity changes need a manual DDL change. |
| `logging.config` | `classpath:log4j2-spring-${apps.log.log-format}.xml` | Log4j2 | Picks `log4j2-spring-json.xml` or `-text.xml`. |

### 3.2 `iso8583.configuration` → `IsoMessageProperties`

| Key | Default | Consumed by | Impact |
|---|---|---|---|
| `outgoing-protocol` | `REST` | `@ConditionalOnProperty` on strategy beans; `TransactionProcessorParticipant` constructor | Selects *which* `SenderProtocolStrategy` bean is active. Only `RestProtocolStrategy` exists; setting `SOAP` or `ISO8583` without writing a strategy fails startup with `IllegalStateException("no sender protocol strategy found: …")`. See [§10](#10-extending-senderprotocolstrategy). |
| `log.default-log-handler-enabled` | `true` | `CoreConfiguration` → jReactive `addLoggingHandler` | Netty's built-in message logging handler, in addition to the app's own `#ISO` log lines. |
| `log.masking-enabled` | `true` | `CoreConfiguration` → `logSensitiveData(!masking)` | Masking inside the **library's** log handler. Masking for the app's own logs is a different mechanism: `mask_fields/iso8583` ([§5](#5-system-properties-configgroup)). |
| `log.field-description-enabled` | `true` | `describeFieldsInLog` | Adds field names to the library's log output (uses `iso8583fields.properties`). |
| `connection.host` / `port` | `127.0.0.1` / `13001` | `CoreConfiguration.client()` | The switch to dial. `client()` connects **during bean creation**; if it cannot connect the context fails to start. |
| `connection.worker-thread-count` | `100` | `ClientConfiguration.workerThreadsCount` | Netty worker threads. These only read/decode and hand off — do not size by business concurrency (that is the async executor + bulkhead). |
| `connection.forwarding-institution-id` | `625` | **nothing** | Bound into the record but not referenced anywhere in `src/main` today. Changing it has no effect until you use it (e.g. when building outbound DE33). |
| `network.reconnect-interval` | `60000` ms | `ClientConfiguration.reconnectInterval` | How often Netty retries a dropped connection. **Each reconnect rebuilds the pipeline and a new `IsoCallbackResponseHandler`**. |
| `network.time-out` | `15000` ms | `idleTimeout`; `NetworkService` echo/sign-on timeout | Connection idle timeout *and* the wait for network-message replies. |
| `network.scheduled-echo-enabled` / `echo-interval` | `true` / `30000` | `NetworkService.onStart` | Keep-alive 0800/echo (DE70). Echo only fires when connected **and** signed on (`HealthCheckHelper.isSignedOn`). |

### 3.3 `participant.configuration.pool.transaction` → `ParticipantPoolConfiguration`

| Key | Default | Consumed by | Impact |
|---|---|---|---|
| `flight-queue-time-out` | `15000` ms | `CorrelationRegistry.inFlights` TTL; `TransactionProcessorParticipant` bulkhead wait | (a) the real-timeout window for a correlation; (b) how long a message waits for a bulkhead permit before it is shed with DE39 `96`. It is "the transaction's whole life budget". |
| `message-queue-time-out` | `60000` ms | `CorrelationRegistry.registered` TTL | Grace window: a reply that arrives after `flight-queue-time-out` but before this is classified `LATE_RESPONSE` instead of `ORPHAN`. **Must be ≥ `flight-queue-time-out`** or startup fails (`ParticipantConfigurationProperties.getPool`). |

Rule of thumb: downstream `client-connect-time-out + client-read-time-out` (and any retries) should fit inside
`flight-queue-time-out`; otherwise the correlation window closes while the REST call is still running.

### 3.4 `client` → `ClientProperties` (outbound REST + registry mode)

| Key | Default | Consumed by | Impact |
|---|---|---|---|
| `client.registry-type` | `CALLBACK` | `EnhancedIsoClient`, both participants | `CALLBACK` or `RESPONSE`. Full explanation in [§8](#8-response-mode-vs-callback-mode). Read at construction: **restart to change.** |
| `client.configurations.<name>.hostname` | `http://localhost:8080` | `BaseClient.createRestClient` → `RestClient.baseUrl` | Downstream base URL. The per-selector path comes from `endpoint_path/mapping`. |
| `max-connections` | `50` | Apache HttpClient pool (total *and* per-route) | Concurrency ceiling to the backend. |
| `max-idle-time` / `evict-in-background` | `30000` / `120000` | `evictIdleConnections` | Connection hygiene. `evict-in-background` is bound but the code only calls `evictExpiredConnections()` — do not expect it to drive a timer. |
| `max-life-time` | `60000` | `setConnectionTimeToLive` | Max lifetime of a pooled connection. |
| `pending-acquire-time-out` | `3000` | `setConnectionRequestTimeout` | Wait for a free pooled connection. |
| `client-connect-time-out` | `5000` | `HttpClientSettings.withConnectTimeout` | TCP connect timeout. |
| `client-read-time-out` | `10000` | `withReadTimeout` + `setSoTimeout` | **A read timeout means "no ISO reply is sent"** — see [§10](#10-extending-senderprotocolstrategy). |
| `client-write-time-out`, `time-unit` | — | **nothing** | Bound, not used by `BaseClient`. |

**Adding a second downstream client:** add `client.configurations.<name>`, an `apps.retry.configurations.<name>` block if
you want retry, and create a class extending `BaseClient` that calls `createRestSender("<name>", …)` — copy
`TransactionClient`. The `<name>` must match across the three places.

### 3.5 `apps.async`, `apps.bulkhead`, `apps.cache`

**`apps.async.configurations.<key>`** — `AsyncTaskConfiguration` registers one `ThreadPoolTaskExecutor` bean named
`<key>AsyncTaskExecutor` per entry (MDC-propagating via `AsyncMDCTaskDecorator`).

| Entry | Bean | Used by | Notes |
|---|---|---|---|
| `default` | `defaultAsyncTaskExecutor` | `@Async("defaultAsyncTaskExecutor")` (dead-letter retry/remove, event-log save) | `CALLER_RUNS` is acceptable here — losing an audit write is worse than a slow caller. |
| `isoTransaction` | `isoTransactionAsyncTaskExecutor` | `TransactionProcessorParticipant` | Receives work **directly from the Netty event loop.** `rejection-policy` **must stay `ABORT`**. `CALLER_RUNS` would run a full transaction (JSLT + JDBC + REST) *on the event loop* once the pool saturates and stall every ISO connection. A rejection is handled as a normal shed (DE39 `96`). |
| `isoTransactionResponse` | `isoTransactionResponseAsyncTaskExecutor` | `TransactionResponseParticipant` | Deliberately separate so a fast "complete this future" never queues behind a slow transaction. Also `ABORT`. |

`virtual-thread-enabled: true` makes the executor spawn virtual threads; in that case `core-pool-size`/`max-pool-size`
still define the *admission* envelope together with `queue-capacity`.

**`apps.bulkhead.configurations.isoTransaction`** — `BulkheadConfiguration` registers a `Semaphore` bean named
`isoTransactionBulkhead`. Permits default to `async.isoTransaction.max-pool-size + queue-capacity + headroom`
(= 90 + 10 + 10 = **110** by default; set `permits` explicitly to override). The bulkhead exists so the number of
*in-flight transactions* is bounded independently of the thread pool. A bulkhead key with no matching async entry and no
explicit `permits` fails startup.

**`apps.cache.configurations.<name>`** — `CaffeineCacheHelper.createCache(name)` throws if a cache name has no entry.

| Cache | Used by | Impact |
|---|---|---|
| `context` / `additionalContext` | `ContextHelper` | Per-request `ContextDTO` + extra data kept until `EventLogInterceptor.afterCompletion`. TTL is a safety net. |
| `jslt` | `JsltTransformationHelper` | Compiled-expression cache. `maximum-size: 100` is **per template-group + selector**; two rows per selector, so ~50 selectors fit before size-eviction (logged as a warning). Raise it as you add selectors. |

### 3.6 `apps.retry.configurations.<name>` → `RetryConfiguration`

One Spring-Retry `RetryTemplate` per entry (`RetryTemplateConfiguration`), keyed `<name>RetryTemplate`.

| Key | Default | Impact |
|---|---|---|
| `type` | `EXPONENTIAL_RANDOM` | `FIXED`, `EXPONENTIAL`, `EXPONENTIAL_RANDOM`, `UNIFORM_RANDOM` (`BackoffPolicyConstant`). |
| `initial-interval` / `multiplier` / `max-interval` | `500` / `2.0` / `10000` | Backoff shape. For `UNIFORM_RANDOM`, `initial-interval`/`max-interval` are the min/max. |
| `max-attempt` | **`1`** | *Total executions*, not retries. `1` = one call, **no retry, and no dead letter** (the listener refuses to dead-letter when `maxAttempts <= 1`). Raise it **only if the backend de-duplicates on `x-request-id` (the RRN)** — otherwise a retried POST can double-post. |
| `dead-letter-enabled` | `true` | Persist an exhausted call to `dead_letter_process`. Needs `max-attempt > 1` to have any effect. See [§11](#11-dead-letter-process-handler). |
| `retryable-exceptions` | `…ResourceAccessException:true` | Comma list of `<fqcn>:<bool>`. `true` = whitelist, `false` = blacklist. If the whitelist is empty, everything not blacklisted is retried. **`ResourceAccessException` wraps connect/read timeouts**, so with `max-attempt > 1` timeouts *are* retried — combine with the idempotency point above. |

### 3.7 `management`, tracing, `apps.log`

| Key | Default | Impact |
|---|---|---|
| `management.server.port` | `1000` | Actuator on its own port. |
| `management.endpoints.web.exposure.include` | `*` | **Restrict in production** (`health,info,prometheus`). |
| `management.prometheus.metrics.export.enabled` | `true` | Prometheus scrape endpoint. |
| `management.tracing.export.enabled` | `true` | Boot 4 property name (was `management.tracing.enabled`). |
| `management.tracing.sampling.probability` | `1.0` | Sample rate. |
| `management.tracing.baggage.*` | `x-request-id` | Baggage correlated into MDC; `TracerHelper.setBaggage` writes it. |
| `management.tracing.propagation.type` | `w3c, b3, b3_multi` | Header formats accepted/emitted. |
| `apps.log.enable-metric-log` | `true` | Gates `ObservationListener` (the `#Observation - start/event/stop` log lines). |
| `apps.log.enable-trace-log` | `true` | Gates `TraceLogConfiguration` (HTTP exchange logging). |
| `apps.log.enable-api-log` | `true` | Gates API request/response logging (Logbook). |
| `apps.log.sensitive-field` | `cardNo` | Comma list of JSON keys masked in API/event logs (`LogProperties.isSensitiveFields`). Add every field that can carry a PAN or secret. |
| `apps.log.log-format` | `json` | `json` or `text` — **the env var is named `APPS_LOG_LEVEL` but it selects the log *format*.** |
| `apps.log.ignored-path` | `/actuator/**,/internal-api/**` | Ant patterns excluded from trace log **and from the `event_logs` audit table** (`EventLogInterceptor`). Anything under `/internal-api/**` is therefore not audited. |

---

## 4. Adding another ISO8583 packager

A *packager* is the j8583 XML that defines, per MTI, which data elements exist and how they are encoded.

**How it is wired today**

```
PackagerConstant.DEFAULT("default-packager.xml")            ← enum of known packager files
        │
PackagerConfiguration.messageFactoryHelper()                ← @Bean, hard-coded PackagerConstant.DEFAULT
        │
MessageFactoryHelper(PackagerConstant)                      ← parses the XML from the classpath, applies encoding settings
        │  getDefaultMessageFactory()
        ├── CoreConfiguration.client()                      ← decodes inbound / builds outbound on the socket
        ├── IsoResponseSender.createResponse(...)           ← builds 0x0210 etc. from the request
        └── NetworkService                                  ← builds 0800 sign-on/echo
```

`MessageFactoryHelper.configureFactory` fixes: **ASCII encoding, text (non-binary) bitmap, forced secondary bitmap, ISO8583:1987,
auto-assigned date, STAN generator seeded from the clock.** A packager with different wire encoding (binary bitmap, EBCDIC,
no secondary bitmap) needs a change there too — the XML alone is not enough.

### Step 1 — add the XML

Put it next to the default one, e.g. `src/main/resources/acquirer-b-packager.xml`. Minimal shape:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE j8583-config PUBLIC "-//J8583//DTD CONFIG 1.0//EN" "http://j8583.sourceforge.net/j8583.dtd">
<j8583-config>
  <parse type="0200">
    <field num="2"  type="LLVAR"   length="19">Primary Account Number</field>
    <field num="3"  type="NUMERIC" length="6">Processing Code</field>
    <field num="4"  type="NUMERIC" length="12">Amount, Transaction</field>
    <field num="7"  type="NUMERIC" length="10">Transmission Date Time</field>
    <field num="11" type="NUMERIC" length="6">STAN</field>
    <field num="37" type="ALPHA"   length="12">Retrieval Reference Number</field>
    <field num="48" type="LLLVAR"  length="999">Additional Data - Private</field>
    <field num="49" type="ALPHA"   length="3">Currency Code</field>
  </parse>
  <parse type="0210" extends="0200">
    <field num="38" type="ALPHA" length="6">Authorization Identification Response</field>
    <field num="39" type="ALPHA" length="2">Response Code</field>
  </parse>

  <!-- REQUIRED: network messages. NetworkProcessorParticipant / NetworkService depend on DE70 and DE39. -->
  <parse type="0800">
    <field num="7"  type="NUMERIC" length="10"/>
    <field num="11" type="NUMERIC" length="6"/>
    <field num="70" type="NUMERIC" length="3"/>
  </parse>
  <parse type="0810" extends="0800">
    <field num="39" type="NUMERIC" length="2"/>
  </parse>
</j8583-config>
```

Notes learned from `default-packager.xml`:
- Response MTIs `extends` their request MTI so `createResponse` can echo fields. `0210` extends `0110`, which extends `0100`, and so on.
- Every MTI your handlers receive **and** every response you send must have a `<parse>` entry, including reversals (`0420/0430/…`) if you use them.
- The codebase reads fixed DEs by number in `RequestContextHelper` (DE2, 3, 4, 7, 11–15, 17, 18, 22, 28, 32, 33, 37, 41–43, 48, 49, 90, 100, 102, 123).
  If your packager moves business data elsewhere, change `RequestContextHelper` and `RequestContext` — they are the single translation point.
- DE48 is parsed as **TLV with 2-char tags and 2-digit lengths** (`IsoFieldHelper.unpackTLV(raw, 2, 2)`), and the `PI` tag feeds the selector. A different DE48 layout needs a different parser — see [§6](#6-customizing-the-selector).

### Step 2 — register it

```java
// PackagerConstant.java
public enum PackagerConstant {
  DEFAULT("default-packager.xml"),
  ACQUIRER_B("acquirer-b-packager.xml");
  ...
}
```

### Step 3 — choose it

Today the bean is hard-wired. The smallest change that makes it configurable (this is a suggested edit, not existing code):

```java
@Bean
public MessageFactoryHelper messageFactoryHelper(
    @Value("${iso8583.configuration.packager:DEFAULT}") PackagerConstant packager) {
  return new MessageFactoryHelper(packager);
}
```

```yaml
iso8583.configuration:
  packager: ${ISO8583_PACKAGER:DEFAULT}     # new — remember to add it to docs/ENVIRONMENT_VARIABLES.md
```

`MessageFactoryHelper.createMessageFactory(PackagerConstant)` also builds an *additional* factory for a different packager without
replacing the default — useful if one process must talk to two switches, but `CoreConfiguration` currently builds **one**
`EnhancedIsoClient`, so a second switch also needs a second client bean.

### Step 4 — verify

`MessageFactoryHelperTest` shows how to load a packager in a unit test. A missing/unreadable XML does **not** fail fast:
`CustomConfigParser.createFromClasspathConfig` logs `ISO8583 File not found in classpath` and returns an *empty* factory, so the failure
shows up later as undecodable messages. Check the startup log for that line.

---

## 5. System properties (`ConfigGroup`)

Runtime routing configuration lives in the `system_properties` table (`group_id`, `property_id`, `property_value`) and is cached
in memory by `SystemPropertiesServiceImpl`. `ConfigGroup` maps an enum to a `(group_id, property_id)` pair and gives you two
parsers: `getList` (comma-separated) and `getMap` (`key:value,key:value`).

| `ConfigGroup` | `group_id` / `property_id` | Format | Read by | Read when |
|---|---|---|---|---|
| `ISO8583_MASK_FIELDS` | `mask_fields` / `iso8583` | `2,35` (DE numbers) | `IsoMessageLoggerHelper` | every log call — **hot-reloadable** |
| `CURRENCY_FRACTIONS` | `currency` / `fractions` | `360:2,840:2` (ISO 4217 numeric : decimals) | `RequestContextHelper.createTransaction` | every request — hot-reloadable |
| `PATH_MAPPING` | `endpoint_path` / `mapping` | `<selector>:<path>,…` | `TransactionClient.getPath` | every request — hot-reloadable |
| `RESPONSE_MAPPING` | `response` / `incoming_outgoing_mapping` | `<downstreamCode>:<isoDE39>,…` | `RestProtocolStrategy.mappingResponseCode` | every response — hot-reloadable |
| `REGISTRY_CALLBACK_SELECTOR` | `registry` / `callback_selector` | `sel1,sel2,…` | `IsoCallbackResponseHandler` | every inbound message — hot-reloadable |
| `REGISTRY_RESPONSE_SELECTOR` | `registry` / `response_selector` | `sel1,sel2,…` | both participants | every inbound message — hot-reloadable |
| `ACQUIRERS` | `acquirers` / `acquirers` | free-form | **nothing in `src/main`** | extension point only |

The seed data is `src/main/resources/dml.sql`. Its `mti/incoming` and `mti/outgoing` rows have **no `ConfigGroup`** and are not read
by any code — they are informational.

### 5.1 Adding or changing a value

1. Change the row (SQL, or `UPDATE system_properties …`).
2. Reload the cache for that group:

```bash
curl -X PUT "http://localhost:8080/sotres/internal-api/configurations/_reload?group=PATH_MAPPING"
curl      "http://localhost:8080/sotres/internal-api/configurations?key=PATH_MAPPING"      # verify
```

`group`/`key` are the **enum names** (`PATH_MAPPING`, `RESPONSE_MAPPING`, …), not the `group_id` strings.

Worked example — route selector `20.97-E001` to `/api/payment/authorize` and map downstream codes:

```sql
UPDATE system_properties
   SET property_value = property_value || ',20.97-E001:/api/payment/authorize'
 WHERE group_id = 'endpoint_path' AND property_id = 'mapping';

UPDATE system_properties
   SET property_value = '00:00,01:05,02:51,99:96'           -- downstream code : ISO DE39
 WHERE group_id = 'response' AND property_id = 'incoming_outgoing_mapping';
```

### 5.2 Adding a **new** `ConfigGroup`

```java
// ConfigGroup.java
MERCHANT_BLOCKLIST("merchant", "blocklist");
```
```sql
INSERT INTO system_properties (group_id, property_id, property_value) VALUES ('merchant', 'blocklist', 'M001,M002');
```
```java
// in a handler (inject SystemPropertiesService)
List<String> blocked = ConfigGroup.getList(systemPropertiesService, ConfigGroup.MERCHANT_BLOCKLIST);
```
`loadSystemProperties` only caches rows whose `group_id` matches an enum constant, so a new group is invisible until the enum exists.

### 5.3 Pitfalls specific to this store

- **Reload only adds and overwrites; it never removes.** `reload()` re-puts every row it finds; a deleted row stays in memory until restart.
- **The parser is `StringTokenizer` + `split(":")`.** Values cannot contain `,` and map values cannot contain `:`. A path such as
  `http://host:8443/x` or `/api/v1/x:y` will be silently truncated.
- **Unmapped downstream codes become DE39 `96`.** `RESPONSE_MAPPING` seeds only `00:00`. Any other downstream code — and an empty map —
  is answered `96` (`RestProtocolStrategy.mappingResponseCode`). Add every code you expect.
- **An empty `PATH_MAPPING` entry gives a `null` path** → the REST call fails. Add the row before routing traffic.
- `onStart()` runs on `ApplicationReadyEvent` and swallows DB errors (logs `#CONFIGURATION - error while loading properties`). A DB outage at boot means an *empty* config cache, not a failed start.

---

## 6. Customizing the selector

The **selector** is the string key that ties everything together: handler lookup, path mapping, JSLT templates, registry-mode lists,
observability feature names. The default shape is:

```
{MTI chars 1–2}.{DE3 chars 0–2}-{DE48 PI tag}          e.g. 0200 / 970000 / PI=E001   →   20.97-E001
```

Code path: `RequestContext.getSelector()` and `IsoFieldHelper.createSelector(IsoMessage)` both delegate to
`IsoFieldHelper.createSelector(String mti, String processingCode, Map<String,String> tlv)` — **that one method is the single place to change.**
(`RequestContext.getSelector()` even carries a `// TODO: change this mapping` marker.)

### Example — include DE18 (merchant category) so two merchant types route differently

```java
// IsoFieldHelper.java  — extend the 3-arg method, or add an overload used by both callers
public static String createSelector(String mti, String processingCode, Map<String, String> tlv) {
  StringBuilder sb = new StringBuilder();
  sb.append(substring(mti, 1, 3)).append('.');
  sb.append(StringUtils.isNotBlank(processingCode) ? substring(processingCode, 0, 2) : "NA");
  sb.append('-').append(tlv.getOrDefault("PI", "NA"));
  return sb.toString();
}
```

If the new component is not derivable from `(mti, processingCode, tlv)`, change the signature **and both callers**
(`RequestContext.getSelector` has the full context; `createSelector(IsoMessage)` has the raw message).

### Checklist when the selector format changes

Everything that stores a selector string must move together:

| Place | What to update |
|---|---|
| `AbstractTransactionHandler.getSelectors()` of every handler | new strings |
| `system_properties`: `endpoint_path/mapping`, `client_spec_request`, `client_spec_response` | selector keys |
| `system_properties`: `registry/callback_selector`, `registry/response_selector` | selector lists |
| `IsoFeatureConstant` request/response sets | selector strings (they are matched with `contains`) |
| Unit tests | `IsoFieldHelperTest`, `RequestContextHelperTest`, and any E2E that hardcodes `20.97-E001`-style selectors (`E2eTestConfig`) |

### Things to know before you change it

- **Request and response get different selectors by design.** MTI `0200` → `20`, its reply `0210` → `21`. That is why the callback/response lists
  contain `21.xx-…` and `IsoFeatureConstant` has separate `requests`/`responses` sets.
- **The correlation ID is a different thing.** `IsoFieldHelper.getCorrelationId` is `PI.DE3[0..2]|STAN-RRN-DE7`. Changing the selector does not change it,
  and a reply must echo DE3/7/11/37/48-PI of its request or it will be an `ORPHAN` ([§8](#8-response-mode-vs-callback-mode)).
- **DE48 is assumed present.** `RequestContext.getAdditionalDataMap()` calls `unpackTLV(additionalData, 2, 2)`, which does not null-check; reading
  the code, a non-network message without DE48 would throw while the `RequestContext` is built on the event loop (inferred from code, not
  covered by a test). `getCorrelationId` *does* handle a missing DE48. If your packager can omit DE48, null-guard `getAdditionalDataMap`.
- **Malformed TLV also throws** (`Integer.parseInt` / `substring` in `unpackTLV`). Validate at the boundary if the switch is not trusted.

---

## 7. JSLT mapping

[`JSLT_GUIDE.md`](JSLT_GUIDE.md) has the language cheat-sheet and cache behaviour. This section is the end-to-end recipe, plus two
corrections you need when following it.

**Two templates per selector**, both rows of `system_properties`:

| `group_id` (`TemplateGroup`) | Input | Output |
|---|---|---|
| `client_spec_request` (`CLIENT_SPEC_REQUEST`) | the `RequestContext` serialized to JSON | the JSON body POSTed to the backend |
| `client_spec_response` (`CLIENT_SPEC_RESPONSE`) | the backend's raw JSON reply | JSON that must deserialize into **`ResponseContext`** |

No template for a selector → that side is a **pass-through** (the input is forwarded unchanged). Pass-through on the response side means
the backend must already reply in `ResponseContext` shape.

### 7.1 The request side

Available paths are the `RequestContext` fields (`src/main/java/.../model/dto/RequestContext.java`): `.mti .cardNo .processingCode .stan .rrn
.additionalData .cardAcceptorTerminalId .cardAcceptorId …`, plus the nested objects `.transaction.{originalAmount, transactionFeeAmount,
transactionAmount, originalCurrencyCode}`, `.merchant.{merchantCategoryCode, merchantName, merchantCity, merchantCountryCode}`,
`.reversal.{originalMti, originalStan, …}`. Amounts are already converted to decimals using `currency/fractions`.

```sql
INSERT INTO system_properties (group_id, property_id, property_value) VALUES (
  'client_spec_request', '20.97-E001',
  '{
     "partnerReferenceNo": .rrn,
     "amount": { "value": .transaction.transactionAmount, "currency": .transaction.originalCurrencyCode },
     "merchant": { "id": .cardAcceptorId, "terminalId": .cardAcceptorTerminalId },
     "cardNo": .cardNo
   }');
```

Outbound headers are fixed in `TransactionClient.buildHeaders`: `Content-Type`/`Accept: application/json`, `x-client-id` (= `spring.application.name`),
`x-request-id` (= RRN), `x-request-time`. The RRN becomes the **idempotency key** downstream and in dead letters.

### 7.2 The response side — **match the `ResponseContext` shape**

`TransactionClient` does `objectMapper.treeToValue(normalized, ResponseContext.class)`. `ResponseContext` is:

```json
{
  "response": { "code": "…", "description": "…", "time": "…" },
  "data": {
    "cpan": "…",
    "transaction": { "paymentId": "…", "referenceId": "…", "rrn": "…", "approvalCode": "…" }
  }
}
```

and has `@JsonIgnoreProperties(ignoreUnknown = true)`. Two consequences:

- `response.code` is what gets looked up in `response/incoming_outgoing_mapping` → DE39.
- **A template that emits other field names deserializes to an empty `ResponseContext` without error**, and `getResponseCode()` then falls back to
  `96`. If every reply comes back as `96`, check this first.

```sql
INSERT INTO system_properties (group_id, property_id, property_value) VALUES (
  'client_spec_response', '20.97-E001',
  '{
     "response": { "code": .resultCode, "description": .resultMessage },
     "data": { "transaction": { "rrn": .partnerReferenceNo, "approvalCode": .authCode } }
   }');
```

`approvalCode` is written to DE38 by `RestProtocolStrategy.handleResponse`.

> **Doc drift to be aware of:** the response-template example and the reload command in `JSLT_GUIDE.md` predate this behaviour
> (the example emits flat `responseCode`/`responseMessage`; the reload example uses `POST /internal-api/system-properties/_reload`, but the real
> endpoint is `PUT /sotres/internal-api/configurations/_reload`). Prefer the shapes and URLs in this guide.

### 7.3 Manage templates at runtime (no deploy)

All under `/sotres/internal-api/jslt`:

```bash
# save/update (validated by compiling it first; invalid JSLT → rejected, nothing persisted)
curl -X PUT "$BASE/internal-api/jslt/template?selector=20.97-E001&group=CLIENT_SPEC_REQUEST" \
     -H 'Content-Type: text/plain' --data-binary @request.jslt
# inspect what is stored
curl "$BASE/internal-api/jslt/templates?selector=20.97-E001"
# force this selector to recompile from the DB (both directions)
curl -X POST "$BASE/internal-api/jslt/_reload?selector=20.97-E001"
# recompile everything; the response reports per-template success
curl -X POST "$BASE/internal-api/jslt/_reload-all"
```

`PUT /template` already evicts the cached expression (`SystemPropertiesServiceImpl.upsert`), so `_reload` is only needed after direct SQL edits.
A template that fails to compile on `_reload-all` is cached as pass-through (traffic keeps flowing) but is reported `false` in the response —
**read the response body**, a 200 does not mean every template is healthy.

### 7.4 Jackson 2 vs. Jackson 3

The app uses Jackson 3 (`tools.jackson.*`) everywhere **except** inside `JsltTransformationHelper`, because JSLT has no Jackson 3 release yet.
The helper converts 3 → 2 before `Expression.apply` and back after. Do not import `com.fasterxml.jackson.databind` anywhere else.
Details: `docs/SPRING_BOOT_4_MIGRATION_PLAN.md`; cost: `docs/BENCHMARK_JSLT.md`.

---

## 8. Response mode vs. callback mode

Both modes answer the question: *"I sent an ISO request **to** the switch — how do I match the reply?"* One primitive, `CorrelationRegistry`,
backs both; `client.registry-type` (`CLIENT_REGISTRY_TYPE`) chooses the behaviour and is read once at startup.

**Correlation id** (`IsoFieldHelper.getCorrelationId`): `<PI>.<DE3[0..2]>|<STAN>-<RRN>-<DE7>`. The switch must echo these fields in the reply.
**Both windows are TTL-only caches** (no size cap — an early eviction would drop a live correlation):

| Window | TTL | Meaning |
|---|---|---|
| in-flight | `flight-queue-time-out` | the real wait; on expiry the future completes with `TimeoutException` |
| registered (grace) | `message-queue-time-out` | reply inside it but after in-flight expired → `LATE_RESPONSE`; after it → `ORPHAN` |

Gauges `correlation.inflight` and `correlation.registered` are exported; alert on `inflight` approaching the bulkhead ceiling (110 by default).

| | `CALLBACK` (default) | `RESPONSE` |
|---|---|---|
| Caller API | `enhancedIsoClient.sendWithCallback(IsoMessage)` — returns when the write completes | `enhancedIsoClient.send(IsoMessage, Duration)` — **blocks the virtual thread** until reply/timeout |
| Who completes the correlation | `IsoCallbackResponseHandler` (Netty pipeline, right after the decoder) for selectors in `registry/callback_selector` | `TransactionResponseParticipant` for selectors in `registry/response_selector` |
| What happens to the reply | classified (`SUCCESS`/`LATE_RESPONSE`/`ORPHAN`) then **flows through the normal pipeline**: your `AbstractTransactionHandler` for the *reply's* selector runs and forwards it downstream | the participant only resolves the future; `TransactionProcessorParticipant` deliberately skips it (`isResponseRegistryEnabled`) |
| ISO reply back to the switch | **suppressed** (`RequestContext.callbackResponse`) — nobody is waiting for a reply-to-a-reply | n/a |
| Timeout result | no caller to tell; the entry lapses → `LATE_RESPONSE`/`ORPHAN` | `send` returns a synthetic response with DE39 `68` (`SUSPEND_TRANSACTION`) on timeout, `96` on other errors |

### 8.1 Configure

```yaml
client.registry-type: ${CLIENT_REGISTRY_TYPE:CALLBACK}      # or RESPONSE
```
```sql
-- selectors are the *reply* selectors (response MTI → 21, 11, …). Keep the two lists DISJOINT.
UPDATE system_properties SET property_value = '21.26-E001,21.26-E002'  WHERE group_id='registry' AND property_id='callback_selector';
UPDATE system_properties SET property_value = '21.36-E001'             WHERE group_id='registry' AND property_id='response_selector';
```
> The shipped `dml.sql` seeds the **same** list into both rows. Because only one `registry-type` is active at a time that is workable,
> but it contradicts the "keep disjoint" rule in the code comments (a selector in both lists is completed twice, and the second pass logs a
> misleading `ORPHAN`). Trim whichever list you are not using.

The selector lists are read per inbound message, so `PUT /configurations/_reload?group=REGISTRY_CALLBACK_SELECTOR` (or `…RESPONSE_SELECTOR`) takes effect immediately. `client.registry-type` itself is still read once at startup.

### 8.2 Use — CALLBACK

```java
@Service @RequiredArgsConstructor
class OutboundInquiryService {
  private final EnhancedIsoClient isoClient;
  private final MessageFactoryHelper messageFactoryHelper;

  void sendInquiry(String rrn, String stan) {                 // run on a virtual thread, never the Netty loop
    IsoMessage req = messageFactoryHelper.getDefaultMessageFactory().newMessage(0x200);
    req.setField(3,  IsoType.NUMERIC.value("260000", 6));
    req.setField(11, IsoType.NUMERIC.value(stan, 6));
    req.setField(37, IsoType.ALPHA.value(rrn, 12));
    req.setField(48, IsoType.LLLVAR.value("PI" + "04" + "E001", 999));   // TLV: tag(2) + len(2) + value
    isoClient.sendWithCallback(req);                           // returns after write; reply arrives via the pipeline
  }
}
```
Then add a handler for the *reply* selector (`21.26-E001`) — see [§9](#9-extending-abstracttransactionhandler). (Field construction is a sketch;
check the `IsoType` helpers against your packager.)

### 8.3 Use — RESPONSE

```java
IsoMessage reply = isoClient.send(req, Duration.ofSeconds(10));      // blocks this virtual thread
String de39 = IsoFieldHelper.getField(reply, 39);                    // "68" on timeout, "96" on error
```
Keep `Duration` below `flight-queue-time-out` (15 s) — beyond it the registry has already expired the future.

### 8.4 Behaviours worth knowing

- A caller timeout in `RESPONSE` mode **cancels** the correlation, so a reply that arrives later is `ORPHAN`, not `LATE_RESPONSE`.
- `sendWithCallback` throws `IllegalStateException` if the channel is not writable or the mode is not `CALLBACK` (and `send` likewise for `RESPONSE`).
- Reconnects (`network.reconnect-interval`) create a new pipeline; in-flight correlations in the registry survive, but the socket they were sent on does not.

---

## 9. Extending `AbstractTransactionHandler`

`strategy/transaction/AbstractTransactionHandler.java` is the extension point for **business logic on an inbound ISO message**.

```java
public abstract class AbstractTransactionHandler {
  public abstract Set<String> getSelectors();                                      // which selectors this handler owns
  protected abstract ParticipantContext validate(ParticipantContext ctx);         // step 1
  protected abstract ParticipantContext process(ParticipantContext ctx);          // step 2
  public void populateResponse(ParticipantContext ctx) { }                         // optional hook, after the backend replied
  public ParticipantContext execute(ParticipantContext ctx) { return process(validate(ctx)); }
}
```

**Discovery and routing.** `TransactionProcessorParticipant` is constructed with `List<AbstractTransactionHandler>`, so *any Spring bean* extending
the class is picked up — no registration. It picks the **first** handler whose `getSelectors()` contains `requestContext.getSelector()`; do not give two
handlers the same selector (order is unspecified). No match → DE39 `92`.

**Lifecycle** (all on a virtual thread, inside the span/observation scope and holding a bulkhead permit):

```
validate(ctx)  →  process(ctx)  →  senderProtocolStrategy.send(...)  →  [handler.populateResponse(ctx)]  →  map DE39  →  write ISO reply
```

`ParticipantContext` gives you: `getIsoMessage()` (raw), `getRequestContext()` (parsed + `getSelector()`, `getTransaction()`, `getRrn()` …),
`getResponseContext()` (only after the backend replied), `getObservation()`, `getChannelHandlerContext()`.

### 9.1 Example — a payment handler

```java
@Component
@RequiredArgsConstructor
public class PaymentAuthorizationHandler extends AbstractTransactionHandler {

  private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000.00");   // move to config in real code
  private final IsoResponseSender isoResponseSender;

  @Override
  public Set<String> getSelectors() {
    return Set.of("20.97-E001", "20.97-E002");          // request selectors (see §6)
  }

  @Override
  protected ParticipantContext validate(ParticipantContext ctx) {
    RequestContext req = ctx.getRequestContext();
    if (req.getTransaction().getTransactionAmount().compareTo(MAX_AMOUNT) > 0) {
      reject(ctx, IsoResponseCode.EXCEEDS_TRANSACTION_AMOUNT_LIMIT);       // DE39 "61"
    }
    return ctx;
  }

  @Override
  protected ParticipantContext process(ParticipantContext ctx) {
    // enrich the request before it is sent downstream, or call other services. The RequestContext is mutable (@Data).
    return ctx;
  }

  @Override
  public void populateResponse(ParticipantContext ctx) {
    // e.g. stamp an approval code if the backend did not provide one
    ResponseContext response = ctx.getResponseContext();
    if (response.getApprovalCode() == null) {
      response.setData(ResponseContext.ResponseData.builder()
          .transaction(ResponseContext.Transaction.builder().approvalCode("000000").build()).build());
    }
  }

  private void reject(ParticipantContext ctx, IsoResponseCode code) {
    isoResponseSender.sendResponseWithObservation(ctx, code.getCode(), null);   // writes the 0x0210 now
    throw new IllegalStateException("rejected " + code);                         // stop the pipeline; see note below
  }
}
```

**Error semantics — important, and derived from `TransactionProcessorParticipant` + `RestProtocolStrategy.handleError` (not from a handler example in the repo):**

| What your handler does | Result for the switch |
|---|---|
| returns normally | request is forwarded; reply built from the backend response |
| throws `TransactionException` | DE39 `96`, **unless** its cause is a timeout, in which case *no* reply |
| throws any other exception | logged as "skipping unknown exception" — **no ISO reply at all** (the acquirer will time out) |
| writes its own decline via `isoResponseSender` **then** throws | the decline reaches the switch; the subsequent exception is swallowed by `handleError`. This is the pattern in `reject(...)` above. |

If you only `throw new IllegalArgumentException(...)` from `validate`, the switch receives nothing. Decide per failure whether silence (let the acquirer
reverse) or an explicit decline is the right contract, and keep it consistent with the strategy's `handleError`.

### 9.2 Rules

- **Blocking is fine here** (JDBC, REST, JSLT) — you are on a virtual thread. **Never** add work to `onMessage`-level code; that is the Netty event loop.
- Handlers are **singletons shared across threads** — no per-request state in fields.
- In `CALLBACK` mode your handler is also invoked for the *reply* selector; check `ctx.getRequestContext().isCallbackResponse()` /
  `isLateResponse()` / `isOrphanResponse()` to treat those differently.
- The handler runs **after** the bulkhead permit is acquired and **before** the downstream call, so slow `validate/process` work reduces throughput for everyone.

---

## 10. Extending `SenderProtocolStrategy`

```java
public interface SenderProtocolStrategy {
  OutgoingProtocol getProtocol();                                                       // REST | SOAP | ISO8583
  ResponseContext send(ChannelHandlerContext ctx, IsoMessage in, RequestContext rc);   // blocking downstream call
  void handleResponse(ParticipantContext ctx);                                          // write the ISO reply
  void handleError(ParticipantContext ctx, Throwable t);                                // map failures
}
```

**Selection.** `TransactionProcessorParticipant` takes `List<SenderProtocolStrategy>` and picks the one whose `getProtocol()` equals
`iso8583.configuration.outgoing-protocol`. Exactly one runs per process. Guard each implementation with
`@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "<ENUM>")` as `RestProtocolStrategy` does,
so inactive strategies are not instantiated.

**Reference implementation: `RestProtocolStrategy`.**
`send` → `TransactionClient.send` (JSLT + `RestSender` + retry). `handleResponse` → `handler.populateResponse` → map code via `RESPONSE_MAPPING` →
`isoResponseSender.sendResponseWithObservation(ctx, code, null, msg -> { setApprovalCode; set DE39 })`. `handleError` → see table:

| Failure | `RestProtocolStrategy` behaviour | Why |
|---|---|---|
| connect/read timeout (cause chain contains `ReadTimeoutException`, `TimeoutException`, `HttpTimeoutException`, `SocketTimeoutException`) | **no ISO reply** | the backend may have processed it; guessing a decline would risk a double settlement — the acquirer drives the reversal |
| other `TransactionException` | DE39 `96` | |
| anything else | logged, no reply | |

### 10.1 Example — a SOAP strategy

`OutgoingProtocol.SOAP` already exists in the enum; there is no implementation.

```java
@Log4j2
@Component
@ConditionalOnProperty(prefix = "iso8583.configuration", name = "outgoing-protocol", havingValue = "SOAP")
@RequiredArgsConstructor
public class SoapProtocolStrategy implements SenderProtocolStrategy {

  private final SoapTransactionClient soapClient;           // your BaseClient-style client; keep it blocking
  private final IsoResponseSender isoResponseSender;
  private final SystemPropertiesService systemPropertiesService;

  @Override public OutgoingProtocol getProtocol() { return OutgoingProtocol.SOAP; }

  @Override
  public ResponseContext send(ChannelHandlerContext ctx, IsoMessage in, RequestContext rc) {
    try {
      return soapClient.call(rc);                           // must return the SAME ResponseContext shape
    } catch (Exception e) {
      throw (e instanceof TransactionException te) ? te : new TransactionException(e, rc);   // wrap so handleError can classify
    }
  }

  @Override
  public void handleResponse(ParticipantContext ctx) {
    ResponseContext response = ctx.getResponseContext();
    if (response == null) {
      isoResponseSender.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), null);
      return;
    }
    ctx.getTransactionHandler().populateResponse(ctx);       // keep the handler hook contract
    String iso39 = ConfigGroup.getMap(systemPropertiesService, ConfigGroup.RESPONSE_MAPPING)
        .getOrDefault(response.getResponseCode(), IsoResponseCode.SYSTEM_MALFUNCTION.getCode());
    isoResponseSender.sendResponseWithObservation(ctx, iso39, null,
        msg -> msg.setField(39, IsoType.ALPHA.value(iso39, 2)));
  }

  @Override
  public void handleError(ParticipantContext ctx, Throwable t) {
    if (t instanceof TransactionException e && !isTimeout(e.getOriginalError())) {
      isoResponseSender.sendResponseWithObservation(ctx, IsoResponseCode.SYSTEM_MALFUNCTION.getCode(), e);
      return;
    }
    log.error(AppLogMessage.message("#Transaction - no ISO reply for RRN {}", ctx.getRequestContext().getRrn()).error(t));
  }
}
```
```yaml
iso8583.configuration.outgoing-protocol: SOAP
```

### 10.2 Checklist for a new strategy

- **Always route replies through `IsoResponseSender.sendResponseWithObservation`.** It handles: skipping the reply for callback-mode replies,
  logging/masking, the `iso_response` observation event, and recording DE39 on the observation. Writing to the `ChannelHandlerContext` directly
  bypasses all of it.
- **Decide your timeout contract explicitly** (silence vs. decline). Copy `RestProtocolStrategy`'s unless you have a reason not to.
- **Reuse the observation/retry plumbing:** open `Observation.start(ObservationConstant.API_EXTERNAL.getName(), registry)`, call
  `ObservationHelper.createTransactionContext/observeResponse` (see `TransactionClient`), and use `RestSender`/`RetryTemplateHelper` if the transport is HTTP.
- **Return a `ResponseContext`.** If the transport is not JSON, build the `ResponseContext` in Java; the JSLT response template is a REST convenience, not a requirement.
- `send` and `handleResponse` run on a virtual thread holding a bulkhead permit — blocking is expected; leaking the permit is not (the participant releases it in
  `finally`, so just don't spawn untracked work that outlives the call).
- Strategy beans are chosen once at startup (constructor), so switching protocol needs a restart.

---

## 11. Dead-letter process handler

A **dead letter** is an outbound REST call that failed all of its retry attempts, persisted so it can be replayed later.

```
RestSender.executeWithRetry(...)                     ── seeds RetryContext (method, path, headers, body, requestId, maxAttempts, deadLetterEnabled)
   └─ RetryTemplate (apps.retry.configurations.<name>)
        └─ RestSenderRetryListener.close(...)        ── on exhaustion AND max-attempt > 1 AND dead-letter-enabled
             └─ INSERT dead_letter_process (status=NEW, retry_count=0, max_retry=max-attempt)
                                                      process_type="client", process_name=<processName arg>
POST /internal-api/dead_letter_process/_retry        ── YOU (or a scheduler) trigger a replay batch
   └─ DeadLetterProcessServiceImpl.retry(...)
        └─ RetryProcessorHelper.getProcessor(type, name)   ← looks up YOUR AbstractRetryProcessorService bean
             └─ isEligibleToBeRetried → execute → update(onSuccess/onError)
```

**There is no producer other than `RestSenderRetryListener` and no processor shipped.** For the shipped `TransactionClient` the key is
`process_type = "client"`, `process_name = "transaction"` (the last argument of `restSender.executeWithRetry(…, "transaction")`). A processor is registered by
the key `getProcessType() + "|" + getProcessName()`.

### 11.1 Turn it on

```yaml
apps.retry.configurations.transaction:
  max-attempt: ${TRANSACTION_RETRY_MAX_ATTEMPT:3}        # > 1 — the default of 1 disables retry AND dead-lettering
  dead-letter-enabled: ${TRANSACTION_RETRY_DEAD_LETTER:true}
```
Do this only when the backend de-duplicates on `x-request-id` (see §3.6).

### 11.2 Write the processor

Extend `AbstractRetryProcessorService` and make it a Spring bean; `RetryProcessorConfiguration` collects all such beans at startup.

```java
@Service
public class TransactionRetryProcessorService extends AbstractRetryProcessorService {

  private final RestClient restClient;

  public TransactionRetryProcessorService(DeadLetterProcessRepository repository, ObjectMapper objectMapper,
      ClientProperties clientProperties) {
    super(repository, objectMapper);
    this.restClient = RestClient.create(clientProperties.getConfiguration("transaction").hostname());
  }

  @Override public String getProcessType() { return "client"; }
  @Override public String getProcessName() { return "transaction"; }

  @Override
  public boolean isEligibleToBeRetried(DeadLetterProcess dl) {
    // e.g. refuse to replay when the original switch transaction is long expired
    return dl.getPayload() != null && dl.getCreatedDate().isAfter(LocalDateTime.now().minusHours(24));
  }

  @Override
  public DeadLetterContext execute(DeadLetterProcess dl) {
    try {
      Map<String, List<String>> headers = objectMapper.readValue(dl.getHeaders(),
          new TypeReference<Map<String, List<String>>>() {});
      ResponseEntity<String> response = restClient.method(HttpMethod.valueOf(dl.getMethod()))
          .uri(dl.getPath())
          .headers(h -> headers.forEach(h::addAll))             // includes x-request-id → idempotency
          .body(dl.getPayload())
          .retrieve()
          .toEntity(String.class);                              // 4xx/5xx throw → caught below
      return new DeadLetterContext(true, response.getBody(), null);
    } catch (Exception e) {
      return new DeadLetterContext(false, null, e);
    }
  }

  @Override public void onSuccess(DeadLetterProcess dl, String response) { /* notify, reconcile, … */ }
  @Override public void onError(DeadLetterProcess dl, Throwable t)       { /* alert */ }
}
```

> For production, build the `RestClient` through `BaseClient.createRestClient(...)` (extend it from a small `@Component` and inject that here) so pooling,
> timeouts and Logbook match the live client. The bare `RestClient.create` above is only to keep the example short.

### 11.3 Operate it

```bash
# replay up to 50 NEW/FAILED rows for this processor, oldest first (runs @Async — the HTTP call returns immediately)
curl -X POST "$BASE/internal-api/dead_letter_process/_retry" -H 'Content-Type: application/json' \
     -d '{"processType":"client","processName":"transaction","size":50}'

# purge EXHAUSTED rows older than N days (default 30). Only EXHAUSTED rows are ever deleted.
curl -X DELETE "$BASE/internal-api/dead_letter_process?days=30"
```
There is **no built-in scheduler.** Call `_retry` from a cron/Kubernetes `CronJob`/your orchestrator. Results are only visible in logs
(`#Retry - [type] [name] total N retry processed`) and in the processor's `getSuccessCounter()/getFailedCounter()/getNotEligibleCounter()`.

### 11.4 Status machine (`RetryStatus`)

```
NEW ──► RETRYING ──► SUCCESS                       (execute ok, or "not eligible")
              └────► FAILED ──► (next _retry) ──► …
              └────► EXHAUSTED                     (retry_count + 1 >= max_retry)
```
`max_retry` is copied from `max-attempt` at insert time. Only `NEW`/`FAILED` rows with `retry_count < max_retry` are picked up. The partial index
`idx_processtype_processname_status` covers exactly `status IN ('NEW','FAILED')`.

### 11.5 Gotchas

- **"Not eligible" marks the row `SUCCESS`** (`DeadLetterProcessServiceImpl.handleNotEligibleToBeRetried`), not a distinct status. Dashboards counting `SUCCESS` will include skipped rows.
- Replay is **sequential** within a batch; parallelise inside your processor if you need throughput.
- If the process dies after rows are set to `RETRYING`, nothing resets them — they will not be picked up again (only `NEW`/`FAILED` are). Add a sweeper if that matters.
- **The stored `payload` is the transformed request body, unmasked.** If your JSLT request template includes `.cardNo`, the PAN is persisted in `dead_letter_process.payload` (bytea).
  `apps.log.sensitive-field` masks logs, not this table. Either keep the PAN out of the request template, encrypt at rest, or restrict table access.
- `retry_histories` is a JSON array of `RetryHistoryContext`; `update()` appends to it and, if it cannot parse it (e.g. it was stored `null`), logs `failed to update retry histories` and carries on.
- The `/internal-api/**` endpoints have no authentication in this codebase (no Spring Security dependency). Keep them off the public network.

---

## 12. Observability mapping

Three Micrometer **observation names** (`ObservationConstant`), each with a matching **feature enum** that supplies the low-cardinality `feature` tag:

| Observation | Started by | `feature` tag comes from | Fallback when not mapped |
|---|---|---|---|
| `api.public` | `HeaderFilter` (every inbound HTTP request) | `ApiFeatureConstant.get(method, path)` — `AntPathMatcher` against the path *without* the context-path | `<METHOD>_<path>` (`ContextDTO.getUnknownFeature`) |
| `iso.message` | `TracerHelper.startIsoObservation` (participants, `EnhancedIsoClient.send`) | `IsoFeatureConstant.getBySelector(selector)` | the **raw selector** string |
| `api.external` | `TransactionClient.send` | `ExternalFeatureConstant.getFeature("POST", apiPath)` where `apiPath` is the `PATH_MAPPING` value | `"POST" + path` (no separator, e.g. `POST/api/transaction`) |

Other tags set by `ObservationHelper`: `responseCode` (low-cardinality; DE39 for ISO, API code for HTTP), `error` (low; exception class), `requestId` (**high**-cardinality; the RRN / `x-request-id`).
Events attached: `iso_request`, `iso_response`, `request`, `response`, `error`. `ObservationListener` writes `#Observation - start/event/stop` log lines for these three names only, gated by `apps.log.enable-metric-log`.

Why mapping matters: the `feature` tag is a **metrics dimension**. Mapping groups related traffic under one stable, human-readable name; leaving it unmapped means either a
raw selector (fine, bounded) or a raw URL path (**unbounded if paths contain IDs** → cardinality explosion in Prometheus).

### 12.1 `ApiFeatureConstant` — your public REST endpoints

```java
public enum ApiFeatureConstant implements FeatureConstant {
  GET_EXAMPLE("GET",  "/api/example"),
  POST_EXAMPLE("POST", "/api/example"),
  // new:
  GET_ORDER("GET",  "/api/orders/{id}"),        // AntPathMatcher: {id}, *, ** all work
  CREATE_ORDER("POST", "/api/orders");
  ...
}
```
First match wins (`Stream.findFirst`) — list specific patterns before wildcards. Use the path **without** `/sotres`.
The `/internal-api/**` endpoints are not in this enum; they fall back to `METHOD_path` (they have no variable segments, so it stays bounded).

### 12.2 `IsoFeatureConstant` — ISO selectors

```java
AUTHENTICATE_FOR_PAYMENT(
    Set.of("10.97-E001", "10.97-E002", "10.97-E003"),      // request selectors
    Set.of("11.97-E001", "11.97-E002", "11.97-E003")),     // response selectors (same feature → request+reply share one name)
PAYMENT_AUTHORIZATION(
    Set.of("20.97-E001", "20.97-E002"),
    Set.of("21.97-E001", "21.97-E002"));
```
Grouping request **and** response selectors under one enum is what lets an incoming 0200 and the reply you write (and a callback-mode 0210) share a feature.
Lookup is a linear `Set.contains` over all constants per message — fine at this scale. The javadoc in the file documents the selector anatomy
(MTI → digits 2–3, DE3 → digits 1–2, DE48 → PI tag).

### 12.3 `ExternalFeatureConstant` — outbound REST calls

```java
PAYMENT_AUTHORIZE("POST", "/api/payment/authorize"),
```
The value matched is the **resolved path from `endpoint_path/mapping`**, so add an entry for every path you route to. Note the shipped entries are `/api/example` while the shipped
mapping is `/api/transaction`, so out of the box outbound calls show up under the fallback name. `TransactionClient` always sends `POST`; a `GET_*` entry can never match there.

### 12.4 Adding a brand-new observation

Add a constant to `ObservationConstant` (`ObservationListener` only logs names listed there), then follow the `TransactionClient` pattern:

```java
Observation obs = Observation.start(ObservationConstant.MY_THING.getName(), observationRegistry);
ObservationHelper.createTransactionContext(obs, requestId, featureName);
try { …; ObservationHelper.observeResponse(obs, "00", null); }
catch (Exception e) { ObservationHelper.observeResponse(obs, "99", e); throw e; }
finally { obs.stop(); }
```
(An un-stopped observation never fires `onStop` and its span is never reported — the participants go to some length to avoid that leak.)

### 12.5 Correlating one transaction across signals

`x-request-id` (the RRN for ISO flows) is written to baggage + MDC, so it appears on: the JSON logs, the `iso.message` and `api.external` observations (`requestId`), the outbound
`x-request-id` header, `event_logs.request_id`, and `dead_letter_process.idempotency_key`. Search any of them by RRN to reconstruct a transaction.
`ObservabilityE2eTest` asserts this correlation end to end.

---

## 13. Awareness notes and known gotchas

Ordered roughly by how likely they are to cost you a day.

1. **Never snapshot `ConfigGroup` values in a constructor.** `SystemPropertiesServiceImpl.onStart()` fills the cache on `ApplicationReadyEvent`, but `client()` connects (building the
   Netty pipeline) and the participants are constructed *during* context refresh — earlier. The registry selector lists used to be captured at those points, so on a cold start they were
   empty and registry-mode routing silently didn't match until a reconnect (`IsoCallbackResponseHandlerTest.LateConfigPath` reproduces it). They are now read per message. Do the same in
   any new component: call `ConfigGroup.getList/getMap` at use time.
   `CallbackModeE2eTest` still forces a reconnect in its `@BeforeEach`; that workaround is no longer required.
2. **No handler → DE39 `92` for everything.** And no processor → `_retry` logs "no retry processor handler found" and does nothing.
3. **Silence is a valid response.** Timeouts and unexpected exceptions send *no* ISO reply by design ([§10](#10-extending-senderprotocolstrategy)). Make sure your acquirer's reversal flow exists before relying on it.
4. **`max-attempt: 1` is deliberate.** Raising it turns on retries of timeouts too (`ResourceAccessException`), and creates dead letters. Don't, unless the backend is idempotent on `x-request-id`.
5. **The Netty event loop must never block.** `onMessage` bodies in participants only classify, start observations, and hand off. `isoTransaction` / `isoTransactionResponse` executors must stay `ABORT`.
6. **Shedding order under load:** executor rejection → DE39 `96` (immediate); bulkhead wait up to `flight-queue-time-out` → DE39 `96`. If you see `96` bursts, check `correlation.inflight` and the executor/bulkhead sizing before blaming the backend.
7. **Timeout budget:** `client-connect-time-out + client-read-time-out` (×attempts + backoff) < `flight-queue-time-out` < `message-queue-time-out`, and all < the acquirer's own timeout.
8. **`config reload` ≠ restart.** Hot-reloadable: path, response mapping, currency fractions, mask fields, JSLT templates. Also hot-reloadable: the registry selector lists. Not hot-reloadable: `client.registry-type`, `outgoing-protocol`, the packager, and every `application.yml` value.
9. **Unused / partial knobs** (so you don't tune them in vain): `connection.forwarding-institution-id`, `client…time-unit`, `client…client-write-time-out`, `ConfigGroup.ACQUIRERS`, `dml.sql`'s `mti/*` rows, `APPS_LOG_LEVEL` (is actually the log *format*).
10. **PII hygiene.** Mask list for ISO logs = `mask_fields/iso8583` (DE numbers); for API/event logs = `apps.log.sensitive-field` (JSON keys). Dead-letter payloads and any `additionalData` you add to the event log are **not** masked by either.
11. **Jackson 3 everywhere except `JsltTransformationHelper`.** A stray `com.fasterxml.jackson.databind.ObjectMapper` import is almost certainly a mistake (annotations `com.fasterxml.jackson.annotation.*` are still correct — they are shared).
12. **Database is external.** `ddl.sql` is hand-applied; `ddl-auto=none`. New entity/column → new DDL, and update `ddl.sql`.
13. **`/internal-api/**` and `/actuator/**` are unauthenticated and un-audited** (excluded by `apps.log.ignored-path`). Restrict by network policy; narrow `management.endpoints.web.exposure.include`.
14. **Only REST ships.** `SOAP`/`ISO8583` are enum values, not implementations.

### Testing

- Build/verify: `JAVA_HOME=~/.sdkman/candidates/java/25-tem mvn -o verify` (~2–3 min). E2E tests use Testcontainers Postgres → **Docker must be running**.
- JaCoCo gate: INSTRUCTION / BRANCH / LINE ≥ 85% in `verify`. A new handler/strategy/processor without tests will likely trip it.
- Fast loop: `mvn -o -q test-compile`.
- E2E harness: `src/test/.../e2e/support/` (`FakeIsoHost` simulates the switch, WireMock simulates the backend, `E2eTestConfig` registers a pass-through handler, `init.sql` /
  `callback-mode-init.sql` seed `system_properties`). Copy `IsoToRestE2eTest` for a happy-path test and `RestSenderRetryDeadLetterE2eTest` for dead letters.

### Where to change what (cheat sheet)

| I want to… | Edit |
|---|---|
| support a new transaction type | handler bean + DB rows + `IsoFeatureConstant` + `ExternalFeatureConstant` |
| change how selectors are built | `IsoFieldHelper.createSelector` (+ everything in the §6 checklist) |
| use a different wire format | new XML + `PackagerConstant` + `PackagerConfiguration` (+ `MessageFactoryHelper.configureFactory`) |
| change DE → field parsing | `RequestContextHelper` / `RequestContext` |
| call a non-REST backend | new `SenderProtocolStrategy` + `outgoing-protocol` |
| call a second REST backend | `client.configurations.<n>` + `apps.retry.configurations.<n>` + a `BaseClient` subclass |
| tune throughput / back-pressure | `apps.async.configurations.isoTransaction`, `apps.bulkhead`, `participant.configuration.pool` |
| replay failed calls | `AbstractRetryProcessorService` bean + external trigger of `_retry` |

---

## 14. Appendix

### 14.1 Internal endpoints (all under `/sotres/internal-api`)

| Method + path | Purpose |
|---|---|
| `GET /configurations?key=<ConfigGroup>` | dump cached values for a group |
| `PUT /configurations/_reload?group=<ConfigGroup>` | reload a group from the DB |
| `GET /jslt/templates?selector=` | stored request/response templates |
| `PUT /jslt/template?selector=&group=CLIENT_SPEC_REQUEST\|CLIENT_SPEC_RESPONSE` (`text/plain`) | validate + save + evict |
| `POST /jslt/_reload?selector=` / `POST /jslt/_reload-all` | recompile from DB |
| `POST /dead_letter_process/_retry` | replay a batch |
| `DELETE /dead_letter_process?days=` | purge old `EXHAUSTED` rows |
| `GET /network/…` (three `GET` endpoints) | send sign-on / sign-off / echo (see `API_REFERENCE.md` for exact paths) |
| `DELETE /event_log` | purge old audit rows (see `API_REFERENCE.md`) |

Full request/response bodies: [`API_REFERENCE.md`](API_REFERENCE.md).

### 14.2 DE39 codes the framework emits itself

| Code | Constant | When |
|---|---|---|
| `92` | `UNABLE_TO_ROUTE_TRANSACTION` | no handler for the selector |
| `96` | `SYSTEM_MALFUNCTION` | executor/bulkhead shed; unmapped or missing downstream code; `TransactionException` (non-timeout); response-write failure |
| `68` | `SUSPEND_TRANSACTION` | `EnhancedIsoClient.send` (RESPONSE mode) timed out waiting for a reply |

### 14.3 Table cheat-sheet (`ddl.sql`)

| Table | Written by | Notes |
|---|---|---|
| `system_properties` | you / admin API | routing config + JSLT templates; index on `group_id` |
| `event_logs` | `EventLogInterceptor` → `EventLogService` | audit of non-ignored HTTP requests |
| `dead_letter_process` | `RestSenderRetryListener`, processors | partial index on `(process_type, process_name, status)` for `NEW`/`FAILED` |

### 14.4 Source map

```
configuration/   Spring wiring (CoreConfiguration, AsyncTask, Bulkhead, RetryTemplate, Observation, Packager…)
participant/     Netty-facing listeners (Transaction, TransactionResponse, Network)
strategy/        YOUR extension points: transaction/ (handlers), outgoing/ (protocols)
client/          outbound REST (BaseClient, TransactionClient)
helper/          RequestContextHelper, IsoFieldHelper, CorrelationRegistry, EnhancedIsoClient, JsltTransformationHelper, RestSender, …
model/constant/  enums that drive behaviour: ConfigGroup, TemplateGroup, *FeatureConstant, ObservationConstant, PackagerConstant, RegistryType …
service/         SystemPropertiesService, DeadLetterProcessService, AbstractRetryProcessorService
listener/        RestSenderRetryListener (dead-letter producer), ObservationListener
properties/      @ConfigurationProperties records mirroring application.yml
```
