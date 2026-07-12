# So-t-Res — ISO8583 to REST Gateway

<img src=".diagram/img.png">

A reactive Spring Boot boilerplate that bridges an ISO8583 TCP channel to downstream REST APIs.
Incoming financial messages (0200 authorisations, 0420 reversals, 0800 network) are decoded,
enriched, shape-transformed via JSLT templates, and forwarded to any REST backend — then the
ISO8583 response is written back to the originating TCP connection.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Features](#features)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Local Development](#local-development)
- [Database](#database)

---

## Overview

sotres owns the translation layer between an ISO8583 acquirer or switch and one or more downstream
REST services. It accepts ISO8583 messages over a persistent TCP connection, converts them to JSON
via configurable JSLT templates, posts to the mapped REST endpoint, then maps the JSON response
back into an ISO8583 0210/0430/0810 reply.

Secondary responsibilities:

- **Dead-letter retry** — failed outgoing calls are persisted and retried on a scheduler until success or exhaustion
- **Audit logging** — every HTTP request processed by the service is written to `event_logs`
- **Runtime configuration** — all routing tables, acquirer maps, response mappings, and JSLT templates are stored in `system_properties` and can be reloaded at runtime without restart
- **Network management** — sign-on, sign-off, and echo messages are handled and exposed as operational endpoints

**Runtime**: Spring Boot 3.5.8 · Java 25 · PostgreSQL 14+ (R2DBC reactive)

---

## Architecture

```
┌─────────────────────────────────┐   ┌──────────────────────────────────┐
│  ISO8583 TCP (port 13001)       │   │  HTTP Clients (port 8080)        │
└──────────────┬──────────────────┘   └───────────────┬──────────────────┘
               │                                      │
               ▼                                      ▼
┌─────────────────────────────────┐   ┌──────────────────────────────────┐
│  Participant Layer              │   │  REST Layer                      │
│  TransactionProcessorParticipant│   │  ExampleController               │
│  NetworkProcessorParticipant    │   │  (internal) DeadLetterProcess-   │
│  TransactionResponseParticipant │   │  Controller, EventLogController, │
└──────────────┬──────────────────┘   │  NetworkController,              │
               │                      │  SystemPropertiesController,     │
               ▼                      │  JsltAdminController             │
┌─────────────────────────────────┐   └───────────────┬──────────────────┘
│  Strategy Layer                 │                   │
│  RestProtocolStrategy           │                   ▼
│  AbstractTransactionHandler     │   ┌──────────────────────────────────┐
└──────────────┬──────────────────┘   │  Service Layer                   │
               │                      │  SystemPropertiesService          │
               ▼                      │  DeadLetterProcessService         │
┌─────────────────────────────────┐   │  EventLogService                 │
│  Client Layer                   │   │  NetworkService                  │
│  TransactionClient (WebClient)  │   └───────────────┬──────────────────┘
│  JsltTransformationHelper       │                   │
└──────────────┬──────────────────┘                   │
               │                                      │
               ▼                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  Repository Layer  (Spring Data R2DBC)                                   │
│  SystemPropertiesRepository · DeadLetterProcessRepository                │
│  EventLogRepository                                                      │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
                         PostgreSQL 14+
```

### Key design decisions

**Response correlation modes**

Two modes handle the asymmetric nature of ISO8583 — where a 0200 request and its 0210 reply may
arrive on different threads or even sockets:

| Mode | Class | Behaviour |
|---|---|---|
| `CALLBACK` | `IsoCallbackRegistry` | Stores a `Consumer<ResponseContext>` keyed on RRN. Invoked when the matching 0210 arrives. |
| `RESPONSE` | `IsoResponseRegistry` | Stores a `Sinks.One<ResponseContext>` keyed on RRN. The originating reactive chain subscribes and waits. |

The active mode is selected per-selector via `system_properties[registry]` and globally via
`CLIENT_REGISTRY_TYPE`.

**JSLT transformation**

Each selector (derived from MTI + processing code + product indicator) maps to two JSLT templates
stored in `system_properties`: one for request shaping (`client_spec_request`) and one for
response normalisation (`client_spec_response`). Templates are compiled once and cached in a
`ConcurrentHashMap<String, Expression>`; the next call after a cache eviction re-fetches from the
database and recompiles. If no template exists for a selector, the input passes through unchanged.

**System properties cache**

All routing and configuration data is held in a Caffeine-backed in-memory map keyed by
`PropertiesGroup`. Individual groups can be reloaded at runtime via the admin API without
restarting the process.

---

## Project Structure

```
src/main/java/com/nantaaditya/sotres/
├── api/
│   ├── ExampleController.java          # Boilerplate greeting / error demo
│   ├── BaseController.java             # Shared response building and observation
│   └── internal/
│       ├── DeadLetterProcessController # Purge and manual retry of failed calls
│       ├── EventLogController          # Purge aged HTTP audit records
│       ├── JsltAdminController         # JSLT template CRUD and cache management
│       ├── NetworkController           # ISO8583 sign-on / sign-off / echo
│       └── SystemPropertiesController  # Runtime config reload and inspection
├── client/
│   └── TransactionClient.java          # Reactive WebClient for downstream REST
├── configuration/                      # Spring bean and library configuration
├── entity/
│   ├── DeadLetterProcess.java          # Failed outgoing calls pending retry
│   ├── EventLog.java                   # HTTP request audit trail
│   └── SystemProperties.java          # Runtime key-value configuration store
├── factory/                            # Component creation and strategy resolution
├── helper/
│   ├── JsltTransformationHelper.java   # JSLT compile-once cache and transform
│   ├── AppLogMessage.java              # Structured log builder
│   └── ...                            # Date, string, field, masking utilities
├── interceptor/
│   └── AppFilter.java                  # Request caching, observation, audit write
├── listener/                           # Application lifecycle event handlers
├── model/
│   ├── constant/                       # ApiResponseCode, PropertiesGroup enums
│   ├── dto/                            # RequestContext, ResponseContext
│   ├── error/                          # Domain exception types
│   ├── logger/                         # AppLogMessage builder model
│   ├── request/                        # Validated API request records
│   └── response/                       # Response envelope
├── participant/
│   ├── TransactionProcessorParticipant # Decodes inbound ISO8583 transactions
│   ├── NetworkProcessorParticipant     # Handles 0800 network messages
│   └── TransactionResponseParticipant  # Routes unsolicited 0210 responses
├── properties/                         # @ConfigurationProperties bindings
├── repository/                         # Spring Data R2DBC repositories
├── service/
│   ├── internal/                       # Service interfaces
│   └── impl/                           # Implementations
└── strategy/
    ├── internal/                       # ISO8583 network message strategies
    ├── outgoing/                       # RestProtocolStrategy — sends to REST
    └── transaction/                    # AbstractTransactionHandler and extensions
```

---

## Features

### ISO8583 transaction forwarding

<img src=".diagram/img_2.png"/>

Incoming ISO8583 financial messages (0200, 0420, 0421–0423) are decoded by
`TransactionProcessorParticipant`, enriched by the matching `AbstractTransactionHandler`, then
forwarded to a downstream REST endpoint by `RestProtocolStrategy` via a reactive `WebClient`. The
response is mapped back to an ISO8583 0210/0430 reply and written to the originating TCP channel.

### JSLT request and response transformation

Each transaction selector maps to two JSLT templates in `system_properties`. The request template
reshapes the internal `RequestContext` into the exact JSON body expected by the downstream REST API.
The response template normalises the downstream reply into a `ResponseContext` that the ISO8583
layer can translate to a wire response. Templates are compiled once on first use and cached; if no
template is configured for a selector the raw object passes through as-is.

### Dead-letter retry

Outgoing calls that fail are written to `dead_letter_process` with status `NEW`. A bounded-elastic
scheduler periodically re-attempts delivery, incrementing `retry_count` on each failure. Records
that reach `max_retry` are marked `EXHAUSTED`. The admin API allows manual purge and targeted retry
by `processType` + `processName`.

### HTTP request audit log

Every HTTP request handled by the service is recorded to `event_logs` by `AppFilter` after the
response is sent. Audit records include client ID, request ID, method, path, response code, payload,
and timestamp. Old records can be purged in bulk by age.

### ISO8583 network management

Sign-on (0800/logon), sign-off (0800/logoff), and echo (0800/echo) messages are handled by
`NetworkProcessorParticipant`. The channel health state is tracked by `HealthCheckHelper`. Operators
can trigger these messages on demand via the internal API.

### Runtime configuration without restart

All routing tables (path mappings, acquirer lists, MTI whitelists, response code translations) and
JSLT templates are stored in `system_properties` and loaded into an in-memory cache at startup.
Individual property groups can be reloaded at any time via the admin API, and JSLT expression caches
can be evicted and rewarmed per-selector or globally.

### Observability

- **Structured logging** — JSON log output via Log4j2 + LMAX Disruptor async appender. Use
  `AppLogMessage.message(...)` to emit structured entries enriched with ISO8583 message, HTTP
  context, and errors.
- **Prometheus metrics** — exposed on the actuator port (`/actuator/prometheus`). Feature-level
  tagging via `ApiFeatureConstant` (REST) and `IsoFeatureConstant` (ISO8583) enums.
- **Distributed tracing** — Brave/B3 + W3C propagation via Micrometer, with configurable baggage
  fields (default: `x-request-id`).

<img src=".diagram/img_1.png"/>
---

## API Reference

All responses use a common envelope:

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-07-12T10:00:00.000+07:00"
  },
  "data": {},
  "error": null
}
```

**Response codes**

| Code | Constant | Meaning |
|------|----------|---------|
| `000` | `SUCCESS` | Request processed successfully |
| `900` | `INVALID_PARAMS` | Bean validation failure — check `error.violations` |
| `998` | `BAD_REQUEST` | Business rule rejection |
| `999` | `INTERNAL_ERROR` | Unexpected server error |

---

### Example

#### `GET /sotres/api/example?name=Alice`

Health-check / smoke-test endpoint.

**Response `200`**
```json
{
  "response": { "code": "000", "description": "success", "time": "..." },
  "data": "Hi Alice!"
}
```

---

### Internal API

> These endpoints are intended for platform operations only. They are served under
> `/sotres/internal-api/**` and excluded from API audit logs by default.

#### `GET /internal-api/network/sign-on`

Sends an ISO8583 0800 logon message to the upstream host and marks the channel as signed on.

**Response `200`**
```json
{ "response": { "code": "000", "description": "success", "time": "..." }, "data": true }
```

#### `GET /internal-api/network/sign-off`

Sends an ISO8583 0800 logoff message and marks the channel as signed off.

#### `GET /internal-api/network/echo`

Sends an ISO8583 0800 echo and returns the health check result.

---

#### `DELETE /internal-api/dead_letter_process?days=30`

Purges dead-letter records older than `days` (default 30). Returns immediately; deletion runs asynchronously.

#### `POST /internal-api/dead_letter_process/_retry`

Triggers immediate retry of dead-letter records matching the given process type and name.

**Request**
```json
{
  "processType": "TRANSACTION",
  "processName": "outgoing-rest-call",
  "size": 10
}
```

**Response `200`**
```json
{ "response": { "code": "000", "description": "success", "time": "..." }, "data": true }
```

**Validation error `400`**
```json
{
  "response": { "code": "900", "description": "invalid parameters", "time": "..." },
  "error": { "violations": { "processType": ["NotBlank"], "size": ["MustPositive"] } }
}
```

---

#### `DELETE /internal-api/event_log?days=30`

Purges audit log records older than `days` (default 30). Runs asynchronously.

---

#### `GET /internal-api/configurations?key=PATH_MAPPING`

Returns the in-memory cache contents for a `PropertiesGroup`.

Valid `key` values: `PATH_MAPPING`, `ACQUIRERS`, `INCOMING_MTI`, `OUTGOING_MTI`,
`CURRENCY_FRACTIONS`, `RESPONSE_MAPPING`, `REGISTRY_CALLBACK_SELECTOR`,
`REGISTRY_RESPONSE_SELECTOR`, `CLIENT_SPEC_REQUEST`, `CLIENT_SPEC_RESPONSE`, `ISO8583_MASK_FIELDS`.

**Response `200`**
```json
{
  "response": { "code": "000", "description": "success", "time": "..." },
  "data": { "10.97-E001": "/api/transaction" }
}
```

#### `PUT /internal-api/configurations/_reload?group=PATH_MAPPING`

Reloads a single property group from the database into the in-memory cache.

---

#### `PUT /internal-api/jslt/template?selector=10.97-E001&group=CLIENT_SPEC_REQUEST`

Creates or updates a JSLT template for the given selector and direction. Body is plain text
(`Content-Type: text/plain`). Evicts the compiled expression after save so the next transform
picks up the new template.

**Request body** (raw JSLT)
```
{"amount": .amount, "currency": .currency, "pan": .cardNo}
```

**Response `200`**
```json
{
  "response": { "code": "000", "description": "success", "time": "..." },
  "data": {
    "id": 42,
    "groupId": "client_spec_request",
    "propertyId": "10.97-E001",
    "propertyValue": "{\"amount\": .amount, \"currency\": .currency, \"pan\": .cardNo}"
  }
}
```

#### `GET /internal-api/jslt/templates?selector=10.97-E001`

Returns the current raw template text for both directions of a selector (does not recompile).

**Response `200`**
```json
{
  "response": { "code": "000", "description": "success", "time": "..." },
  "data": {
    "client_spec_request": "{\"amount\": .amount, \"currency\": .currency}",
    "client_spec_response": "{\"response\": {\"code\": .responseCode}}"
  }
}
```

#### `POST /internal-api/jslt/_reload?selector=10.97-E001`

Evicts the compiled expression cache for both directions of a selector, re-fetches from the
database, and recompiles. Returns the reloaded template text.

#### `POST /internal-api/jslt/_reload-all`

Clears the entire JSLT expression cache and rewarms it from the database for all selectors.

---

## Configuration

All values are injectable via environment variable. Full reference: [`docs/ENVIRONMENT_VARIABLES.md`](docs/ENVIRONMENT_VARIABLES.md).

### Server

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP API port |
| `ACTUATOR_PORT` | `1000` | Actuator / metrics port |
| `CONTEXT_PATH` | `/sotres` | WebFlux base path |
| `APPLICATION_NAME` | `sotres-api` | Spring application name |

### Database

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `r2dbc:postgresql://localhost:5432/boilerplate` | R2DBC connection URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | `changeme` | Database password — **rotate before production** |
| `R2DBC_POOL_ENABLED` | `true` | Enable connection pooling |
| `R2DBC_MAX_SIZE` | `10` | Maximum pool connections |

### ISO8583 Connection

| Variable | Default | Description |
|---|---|---|
| `ISO8583_HOST` | `127.0.0.1` | ISO8583 upstream host |
| `ISO8583_PORT` | `13001` | ISO8583 upstream TCP port |
| `FORWARDING_INSTITUTION_ID` | `625` | Institution ID written to DE33 |
| `RECONNECT_INTERVAL` | `60000` | Reconnect interval (ms) |
| `TIME_OUT_SECOND` | `15000` | ISO8583 response timeout (ms) |
| `SCHEDULED_ECHO_ENABLED` | `true` | Send periodic echo heartbeats |
| `ECHO_INTERVAL_SECOND` | `30000` | Echo interval (ms) |

### Outgoing REST Client

| Variable | Default | Description |
|---|---|---|
| `OUTGOING_PROTOCOL` | `REST` | Forwarding protocol (currently `REST` only) |
| `CLIENT_REGISTRY_TYPE` | `CALLBACK` | Response correlation mode (`CALLBACK` or `RESPONSE`) |
| `TRANSACTION_CLIENT_HOSTNAME` | `http://localhost:8080` | Downstream REST base URL |
| `TRANSACTION_CLIENT_READ_TIMEOUT` | `10000` | HTTP read timeout (ms) |
| `TRANSACTION_RETRY_MAX_ATTEMPT` | `1` | Max retry attempts on `PrematureCloseException` |

### Logging

| Variable | Default | Description |
|---|---|---|
| `LOG_PATH` | `logs/` | Log file directory |
| `APPS_LOG_LEVEL` | `json` | Log format: `json` or `text` |
| `APPS_API_ENABLED` | `true` | Enable HTTP request/response logging |
| `SENSITIVE_FIELD` | `cardNo` | Comma-separated JSON fields to mask in logs |

### Observability

| Variable | Default | Description |
|---|---|---|
| `ACTUATOR_EXPOSED` | `*` | Exposed actuator endpoints — restrict in production |
| `PROMETHEUS_ENABLED` | `true` | Enable Prometheus metrics export |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Trace sampling rate (reduce in high-traffic prod) |
| `ROOT_LOG_LEVEL` | `INFO` | Root log level — set `WARN` in production |

---

## Local Development

**Prerequisites**: Java 25, Maven 3.9+, PostgreSQL 14+

**1. Initialise the database** (first time only)
```bash
psql -U postgres -d boilerplate -f src/main/resources/ddl.sql
psql -U postgres -d boilerplate -f src/main/resources/dml.sql
```

**2. Run**
```bash
mvn spring-boot:run
```

All environment variables have defaults; no overrides are required for local runs against
`localhost:5432/boilerplate` with user `postgres` / password `changeme`.

- API base: `http://localhost:8080/sotres`
- Swagger UI: `http://localhost:8080/sotres/swagger-ui.html`
- Actuator: `http://localhost:1000/actuator/health`

**3. Run tests**
```bash
mvn test

# Tests + JaCoCo coverage report
mvn verify
open target/site/jacoco/index.html
```

**Docker**
```bash
# Build JAR
bash .script/build_jar.sh

# Build image
bash .script/build_docker.sh

# Run container
docker run -d \
  --cpus="0.5" --memory="768m" \
  -p 8080:8080 \
  --env-file .env/dev.env \
  --name sotres \
  sotres:1.0.0-SNAPSHOT
```

Create `.env/dev.env` with the variables listed in the [Configuration](#configuration) section,
overriding any defaults for your environment.

**Structured logging**

Use `AppLogMessage` with `@Log4j2` to emit properly structured JSON log entries:

```java
@Log4j2
public class MyHandler {
  public void handle() {
    log.info(AppLogMessage
        .message("processing transaction {} for amount {}", rrn, amount)
        .isoMessage(isoMessage)
        .error(throwable)        // optional — include on exceptions
    );
  }
}
```

**Feature metrics**

Add an entry to `ApiFeatureConstant` (for REST requests) or `IsoFeatureConstant` (for ISO8583
messages) to tag Prometheus metrics with a business feature name. The matching logic compares
HTTP method + path or MTI + selector against the enum entries at scrape time.

---

## Database

Schema is managed via SQL scripts in `src/main/resources/`.

```bash
# Create tables
psql -U postgres -d boilerplate -f src/main/resources/ddl.sql

# Seed required system_properties rows
psql -U postgres -d boilerplate -f src/main/resources/dml.sql
```

**Tables**

| Table | Purpose |
|---|---|
| `dead_letter_process` | Failed outgoing REST calls pending scheduled or manual retry |
| `system_properties` | Runtime key-value configuration: routing tables, JSLT templates, acquirer maps |
| `event_logs` | Immutable HTTP request audit trail written after each response |

**Seed data (`dml.sql`)**

| `group_id` | `property_id` | Content |
|---|---|---|
| `acquirers` | `acquirers` | Acquirer network routing map (e.g. `360001:ARTAJASA`) |
| `mti` | `incoming` | Allowed inbound MTI whitelist |
| `mti` | `outgoing` | Allowed outbound MTI whitelist |
| `mask_fields` | `iso8583` | ISO8583 DE field numbers to mask in logs (e.g. `2` for PAN) |
| `currency` | `fractions` | Currency decimal digits (e.g. `360:2` = IDR, 2 dp) |
| `endpoint_path` | `mapping` | Selector → REST path map (e.g. `10.97-E001:/api/transaction`) |
| `response` | `incoming_outgoing_mapping` | Response code translation (e.g. `00:00`) |
| `registry` | `callback_selector` | Selectors using `CALLBACK` correlation mode |
| `registry` | `response_selector` | Selectors using `RESPONSE` correlation mode |

**Primary key strategy**

`event_logs` uses a TSID (Time-Sorted ID) string primary key generated by
[tsid-creator](https://github.com/f4b6a3/tsid-creator), giving k-sortable, compact, collision-free
IDs without a sequence. `dead_letter_process` uses PostgreSQL `bigserial`.

**JSLT templates**

After seeding, add JSLT transformation templates via the admin API or directly:

```sql
-- Request template: shape RequestContext into downstream REST body
INSERT INTO system_properties (group_id, property_id, property_value)
VALUES ('client_spec_request', '10.97-E001', '{"amount": .amount, "currency": .currency}');

-- Response template: normalise downstream response into ResponseContext fields
INSERT INTO system_properties (group_id, property_id, property_value)
VALUES ('client_spec_response', '10.97-E001', '{"response": {"code": .responseCode}}');
```
