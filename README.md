# So-t-Res
<img src=".diagram/img.png">

## Introduction
This document provides technical specifications and operational details for the Spring Boot Boilerplate Project with main capabilities to convert ISO8583 message to JSON REST API.
This project serves as a foundational, reactive, single-module application built on the Spring Boot framework and [jreactive8583](https://github.com/kpavlov/jreactive-8583), 
designed to incorporate common enterprise capabilities like robust logging, retry mechanisms, and operational endpoints.

## Prerequisites

Before running this project, ensure the following are installed and available:

| Requirement | Version | Notes |
|---|---|---|
| Java (JDK) | 25 | Virtual threads enabled by default |
| Maven | 3.9+ | Or use the included `./mvnw` wrapper |
| PostgreSQL | 14+ | Database named `boilerplate` must exist |
| ISO8583 Server | — | A live ISO8583 TCP server or simulator on `ISO8583_HOST:ISO8583_PORT` |

> The ISO8583 connection is required at startup. If the server is unreachable, the application will retry on a configurable interval (`RECONNECT_INTERVAL`, default 60 s).

---

## Database Setup

Run the DDL and DML scripts against your PostgreSQL instance to create the required tables and seed the initial `system_properties` data.

```bash
psql -U postgres -d boilerplate -f src/main/resources/ddl.sql
psql -U postgres -d boilerplate -f src/main/resources/dml.sql
```

### Tables Created

| Table | Purpose |
|---|---|
| `dead_letter_process` | Stores failed transactions for scheduled retry processing |
| `system_properties` | Runtime configuration loaded into a Caffeine in-memory cache on startup |
| `event_logs` | Audit log of every HTTP request processed by the application |

### Seed Data (`dml.sql`)

The DML file seeds the minimum required `system_properties` rows:

| `group_id` | `property_id` | Purpose |
|---|---|---|
| `acquirers` | `acquirers` | Acquirer network routing map |
| `mti` | `incoming` | Allowed incoming MTI whitelist |
| `mti` | `outgoing` | Allowed outgoing MTI whitelist |
| `mask_fields` | `iso8583` | ISO8583 DE field numbers to mask in logs |
| `currency` | `fractions` | Currency fraction digits (e.g. `360:2` = IDR with 2 decimal places) |
| `endpoint_path` | `mapping` | Selector → REST path mapping for outgoing HTTP calls |
| `response` | `incoming_outgoing_mapping` | Response code translation map |
| `registry` | `callback_selector` | Selectors routed via callback-style response correlation |
| `registry` | `response_selector` | Selectors routed via Mono-based response correlation |

---

## Environment Variables

All variables have defaults defined in `application.yml`. Override them via system environment, a `.env` file, or Docker `--env-file`.

### Server

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP API port |
| `ACTUATOR_PORT` | `1000` | Metrics and actuator port |
| `CONTEXT_PATH` | `/sotres` | WebFlux base path |
| `APPLICATION_NAME` | `sotres-api` | Spring application name |

### Database

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `r2dbc:postgresql://localhost:5432/boilerplate` | R2DBC connection URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | `changeme` | Database password |
| `R2DBC_POOL_ENABLED` | `true` | Enable R2DBC connection pool |
| `R2DBC_MAX_SIZE` | `10` | Maximum pool size |

### ISO8583 Connection

| Variable | Default | Description |
|---|---|---|
| `ISO8583_HOST` | `127.0.0.1` | Remote ISO8583 server host |
| `ISO8583_PORT` | `13001` | Remote ISO8583 server port |
| `FORWARDING_INSTITUTION_ID` | `625` | Institution ID used in DE11 prefix |
| `RECONNECT_INTERVAL` | `60000` | Reconnect interval in milliseconds |
| `TIME_OUT_SECOND` | `15000` | ISO8583 message timeout in milliseconds |
| `SCHEDULED_ECHO_ENABLED` | `true` | Enable periodic echo (heartbeat) messages |
| `ECHO_INTERVAL_SECOND` | `30000` | Echo interval in milliseconds |

### Outgoing Strategy

| Variable | Default | Description |
|---|---|---|
| `OUTGOING_PROTOCOL` | `REST` | How ISO8583 transactions are forwarded (`REST` is currently supported) |
| `CLIENT_REGISTRY_TYPE` | `CALLBACK` | Response correlation mode (`CALLBACK` or `RESPONSE`) |
| `TRANSACTION_CLIENT_HOSTNAME` | `http://localhost:8080` | Base URL of the downstream REST service |
| `TRANSACTION_CLIENT_READ_TIMEOUT` | `10000` | HTTP client read timeout in milliseconds |
| `TRANSACTION_RETRY_MAX_ATTEMPT` | `1` | Max HTTP retry attempts on `PrematureCloseException` |

### Logging

| Variable | Default | Description |
|---|---|---|
| `LOG_PATH` | `logs/` | Directory for log files |
| `APPS_LOG_LEVEL` | `json` | Log format: `json` or `text` |
| `APPS_API_ENABLED` | `true` | Enable HTTP request/response logging via Logbook |
| `APPS_TRACE_ENABLED` | `true` | Enable response time trace log |
| `SENSITIVE_FIELD` | `cardNo` | Space-separated JSON fields to mask in logs |

---

## Running Locally

```bash
# 1. Initialize the database (first time only)
psql -U postgres -d boilerplate -f src/main/resources/ddl.sql
psql -U postgres -d boilerplate -f src/main/resources/dml.sql

# 2. Build
./mvnw install -DskipTests

# 3. Run
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080/sotres` by default.
Actuator endpoints are available on `http://localhost:1000/actuator`.

---

## Running with Docker

```bash
# 1. Build the JAR
bash .script/build_jar.sh

# 2. Build the Docker image
bash .script/build_docker.sh

# 3. Run the container
docker run -d \
  --cpus="0.5" --memory="768m" \
  -p 8080:8080 \
  --env-file .env/dev.env \
  --name sotres \
  sotres:1.0.0-SNAPSHOT
```

Create `.env/dev.env` with the environment variables listed above, overriding any defaults for your environment.

---

## Running Tests

```bash
# Run all tests
./mvnw test

# Run tests and generate JaCoCo coverage report
./mvnw verify

# Open coverage report (macOS)
open target/site/jacoco/index.html
```

---

## How It Works

The application bridges an ISO8583 TCP channel to a downstream REST API. There are two independent processing lanes: ISO8583 message handling and HTTP API handling.

### ISO8583 Transaction Flow

```
ISO8583 Server (TCP :13001)
        │
        ▼
TransactionProcessorParticipant
  ├─ Decodes ISO8583 message
  ├─ Builds RequestContext (MTI, amount, currency, selector)
  └─ Resolves AbstractTransactionHandler by selector
        │
        ▼
AbstractTransactionHandler.process()
  ├─ Applies business logic / enrichment
  └─ Delegates to SenderProtocolStrategy
        │
        ▼
RestProtocolStrategy.send()
  ├─ Calls TransactionClient (reactive WebClient)
  └─ POST to endpoint resolved from system_properties[endpoint_path]
        │
        ▼
handleResponse()
  ├─ Maps response code via system_properties[response]
  └─ Writes ISO8583 0210 response back to TCP channel
```

**Network messages** (MTI 0800) are handled separately by `NetworkProcessorParticipant`:

| Subtype | Action |
|---|---|
| LOGON | Marks channel as signed-on (`HealthCheckHelper.setSignedOn(true)`) |
| LOGOFF | Marks channel as signed-off |
| ECHO | Replies with healthy 0810 response |

### Response Correlation Modes

Two modes are supported, selected by `CLIENT_REGISTRY_TYPE`:

| Mode | Class | Behaviour |
|---|---|---|
| `CALLBACK` | `IsoCallbackRegistry` | Stores a `Consumer<ResponseContext>` keyed on RRN. The callback is invoked when the matching response arrives. |
| `RESPONSE` | `IsoResponseRegistry` | Stores a `Sinks.One<ResponseContext>` keyed on RRN. The transaction thread subscribes and waits reactively. |

Unsolicited responses (0210 arriving without a prior 0200) are routed through `TransactionResponseParticipant` → `IsoResponseRegistry.onResponse()`.

### HTTP API Flow

```
HTTP Request (:8080)
      │
      ▼
AppFilter  (order = HIGHEST_PRECEDENCE + 2)
  ├─ Caches request body for downstream re-reading
  ├─ Copies request headers to response
  ├─ Starts Micrometer Observation
  └─ Puts ContextDTO in Reactor context
      │
      ▼
Controller → Service
      │
      ▼
doFinally (on complete or error):
  ├─ Saves audit record to event_logs table
  └─ Stops Observation
```

### Dead Letter & Retry

Failed transactions that cannot be delivered are persisted to `dead_letter_process` with status `NEW`. A scheduled processor:

1. Queries records where `status IN ('NEW', 'FAILED')` and `retry_count < max_retry`
2. Re-delivers the payload
3. Marks as `SUCCESS` on delivery, or increments `retry_count` / sets `FAILED` on error
4. Records that reach `max_retry` are marked `EXHAUSTED` and cleaned up on a configurable schedule

### System Properties Cache

Runtime configuration (response mappings, path mappings, acquirer lists, etc.) is stored in the `system_properties` table and loaded into a Caffeine in-memory cache at startup via `SystemPropertiesService`. Individual groups can be reloaded at runtime without restarting the application.

---

## Project Structure

### Module Structure
`src/main/java` The project employs a standard single-module structure, organized by functional concern to promote separation of duties and maintainability.

| Package       | Description                               | Key Responsibilities                                                                                      |
|---------------|-------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| API           | Houses all REST API endpoint controllers. | Defines application-facing services and handles HTTP request mapping.                                     |
| Client        | External Client Integration.              | Contains components for making outbound calls to external services.                                       |
| Configuration | Application Configuration.                | Stores Spring bean definitions, external library setups, and general application helpers.                 |
| Entity        | Data Persistence Layer.                   | Stores POJO classes that map directly to database tables (JPA Entities).                                  |
| Factory       | Component Creation.                       | Used for creating or managing implementations of specific beans or components.                            |
| Helper        | General Utility Classes.                  | Stores reusable utility and helper methods.                                                               |
| Interceptor   | Request/Response Processing Hooks.        | Contains logic executed before or after request/response processing (e.g., logging, header manipulation). |
| Listener      | Event Handling.                           | Stores classes that listen for and react to application or external events.                               |
| Model         | Data Transfer Objects (DTOs).             | Stores request/response DTOs, constants, and enums for data structuring.                                  |
| Participant   | Base Handler for Incoming ISO8583.        | ISO8583 Handler for incoming message seggregated based on transaction / network message                   |
| Properties    | Configuration Definitions.                | Stores custom application configuration properties, designed to be environment-overridable.               |
| Repository    | Data Access Layer.                        | Defines interfaces for database query operations (e.g., Spring Data JPA repositories).                    |
| Service       | Contains the core business logic.         | Orchestrates business processes, independent of persistence or transport layers.                          |
| Strategy      | Strategy Pattern Classes.                 | Strategy Pattern for ISO8583 transaction message                                                          |

### Supporting Files
- `.docker` Contains the Dockerfile for building a standardized Docker container image of the application.
- `.deployment` Stores environment variables required for running the application via Docker containers.
- `.script` Holds shell scripts for common operational tasks, such as building the JAR, building the Docker image, and running the application.

## Core Capabilities and Features
The boilerplate includes several advanced features to enhance observability, reliability, and operational control.

### Logging
| Feature                             | Description                                                                                               | Configuration                                                                                                                                                                                                |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Structured & Segregated Logging     | Logs are output in a custom JSON format for machine readability and segregated into distinct files.       | App Log: `${LOG_PATH}/${APPLICATION_NAME}.log` Metric Log: `${LOG_PATH}/metrics.log` Trace Log: `${LOG_PATH}/trace.log`                                                                                      |
| Response Time Tracing               | Automatically logs the duration/response time of each endpoint call.                                      | Enabled by default. Disable with: `apps.log.enable-trace-log=false`. Paths can be ignored using `apps.log.ignored-path`.                                                                                     |
| ISO & HTTP Request/Response Logging | Logs the full content of incoming requests and outgoing responses.                                        | Enabled by default. Disable with: `apps.log.enable-api-log=false`.                                                                                                                                           |
| Masking Sensitive PII               | Automatically masks PII (Personally Identifiable Information) in logs generated by external client calls. | Define sensitive fields in configuration: `apps.masking.sensitive-field` and in `system_properties` table with group_id `mask_fields`. Space separated value. Supports both headers and JSON payload fields. |

> Note on Logging Usage: Developers must use the custom wrapper (`AppLogMessage`) with `Log4j2` annotation to ensure structured JSON logging is correctly populated with context (HTTP details, errors, etc.).

Example:
```java
@Log4j2
public class ExampleController {
  public void method() {
    log.info(AppLogMessage
        .message("log message {} {}", "p1", "p2")
        .httpRequest(request)
        .httpResponse(response)
        .isoMessage(isoMessage)
        .error(throwable)
        .additionalData(additionalData)
    );
  }
}
```

### Observability
The application is configured to expose standard and custom feature-level metrics compatible with Prometheus.
- Endpoint: Metrics are exposed on a dedicated port: `http://localhost:1000/actuator/prometheus`.
- Default Port: Port `1000` is used exclusively for the metrics endpoint, separating it from the main application traffic port.

#### Feature Name Matching Mechanism
To utilize feature-level metrics, the developer must explicitly define the feature name mapping:

- Configuration Requirement: Developers must add an enum entry to the `ApiFeatureConstant` class for REST API and `IsoFeatureConstant` for ISO8583 transaction messages.
- Matching Logic: 
  - When an incoming HTTP request is processed, the application attempts to match the request's HTTP Method (e.g., GET, POST) and the API Path (e.g., /api/user/{id}) against the defined entries in `ApiFeatureConstant`.
  - When an ISO8583 message is processed, the application attempts to match the message's MTI (Message Type Indicator) & selector against the defined entries in `IsoFeatureConstant`.
- Metrics Scraping: If a match is found, the corresponding enum name from `ApiFeatureConstant` & `IsoFeatureConstant` is used as the feature name tag when the metrics are scraped by Prometheus, allowing for targeted monitoring of specific business transactions.

Example:
<img src=".diagram/img_1.png">

## ISO8583 Handler
ISO8583 using TCP protocol, so it can both as a client and server. which means it can be used to send and receive a message simulatenously.

### ISO8583 as a sender
to use as a client, you need to inject bean `Iso8583Client<IsoMessage>` into your class.

Example:
```java
iso8583Client.send(request, isoMessageProperties.network().timeOut(), TimeUnit.MILLISECONDS);
```

### ISO8583 as a receiver
to use as a server, you need to extends `IsoMessageListener<IsoMessage>` class.
or in this boilerplate, it's already handled on `TransactionProcessorParticipant` class.
and you need to create bean that extends `AbstractTransactionHandler` class.

<img src=".diagram/img_2.png">