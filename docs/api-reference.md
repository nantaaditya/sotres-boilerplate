# API Reference

**Runtime:** Spring Boot 3.5.16 · Java 25 · servlet (Tomcat) + virtual threads  
**Last Updated:** 2026-09-03

---

## Shared Conventions

### Global Headers

All endpoints use these optional headers for request tracking and correlation:

| Header | Mandatory | Description |
|--------|-----------|-------------|
| `x-client-id` | O | Client identifier; echoed in response |
| `x-request-id` | O | Request correlation ID; echoed in response |
| `x-request-time` | O | Client-side timestamp (ISO 8601); echoed in response |

**Response headers** include:
- `x-received-time` — server receive timestamp (ISO 8601)
- `x-response-time` — round-trip duration (milliseconds)

### Response Envelope

All responses follow this structure:

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {},
  "error": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `response.code` | String | Three-digit code: `000` (success), `900` (validation error), `998` (bad request), `999` (internal error) |
| `response.description` | String | Human-readable description |
| `response.time` | String | ISO 8601 timestamp |
| `data` | Any | Payload on success; null on error |
| `error` | Object or null | Validation violations (if present) |

### Response Codes

| Code | Description | HTTP Status |
|------|-------------|-------------|
| `000` | success | 200 |
| `900` | invalid parameters | 400 |
| `998` | bad request | 400 |
| `999` | internal error | 400 |

---

## Endpoints

### 1. Greeting

**Purpose:** Simple greeting endpoint to verify the API is responding.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/api/example` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Default | Description |
|-----------|------|-----------|---------|-------------|
| `name` | String | O | `"you"` | Name to greet |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": "Hi Alice!",
  "error": null
}
```

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success |
| `999` | internal error |

---

### 2. Error Response Demo

**Purpose:** Demonstrate error response structure.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/api/example/error` | Standard headers (all optional) |

**Response**

```json
{
  "response": {
    "code": "998",
    "description": "bad request",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": null,
  "error": {
    "violations": {
      "key": ["value"]
    }
  }
}
```

---

### 3. System Properties: Get Configuration

**Purpose:** Retrieve all system properties for a given configuration group.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/internal-api/configurations` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Description |
|-----------|------|-----------|-------------|
| `key` | String | M | Configuration group (enum: `REGISTRY_TYPE`, etc.) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {
    "key1": "value1",
    "key2": "value2"
  },
  "error": null
}
```

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success |
| `998` | bad request (invalid `key`) |
| `999` | internal error |

---

### 4. System Properties: Reload Configuration

**Purpose:** Reload a configuration group from the database and refresh the in-memory cache.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| PUT | `/internal-api/configurations/_reload` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Description |
|-----------|------|-----------|-------------|
| `group` | String | M | Configuration group to reload |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

---

### 5. Network: Send Sign-On

**Purpose:** Initiate ISO8583 sign-on handshake with the upstream host.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/internal-api/network/sign-on` | Standard headers (all optional) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

---

### 6. Network: Send Sign-Off

**Purpose:** Initiate ISO8583 sign-off handshake with the upstream host.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/internal-api/network/sign-off` | Standard headers (all optional) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

---

### 7. Network: Send Echo

**Purpose:** Send an ISO8583 echo to verify connectivity.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/internal-api/network/echo` | Standard headers (all optional) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success (echo received) |
| `998` | bad request (echo failed) |
| `999` | internal error |

---

### 8. Dead Letter Process: Remove Old Records

**Purpose:** Delete dead-letter records older than a specified number of days.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| DELETE | `/internal-api/dead_letter_process` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Default | Description |
|-----------|------|-----------|---------|-------------|
| `days` | Integer | O | `30` | Age threshold in days |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

**Note:** Deletion runs asynchronously on `defaultAsyncTaskExecutor`.

---

### 9. Dead Letter Process: Retry Failed Processes

**Purpose:** Retry failed processes by type and name.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| POST | `/internal-api/dead_letter_process/_retry` | Standard headers (all optional) |

**Request Body**

```json
{
  "processType": "iso_transaction",
  "processName": "outbound_payment",
  "size": 10
}
```

| Field | Type | Mandatory | Description |
|-------|------|-----------|-------------|
| `processType` | String | M | Process type (must be non-blank) |
| `processName` | String | M | Process name (must be non-blank) |
| `size` | Integer | M | Batch size (must be ≥ 1) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success (retry scheduled) |
| `900` | invalid parameters (validation failed) |
| `998` | bad request (malformed JSON) |
| `999` | internal error |

**Note:** Retry runs asynchronously on `defaultAsyncTaskExecutor`.

---

### 10. JSLT Admin: Get Templates

**Purpose:** Retrieve JSLT transformation templates for a selector.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| GET | `/internal-api/jslt/templates` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Description |
|-----------|------|-----------|-------------|
| `selector` | String | M | Selector identifier (e.g., `0200.000001.01`) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {
    "client_spec_request": ".clientId = .merchant.id | .amount = .txn.amount",
    "client_spec_response": ".de39 = .response.responseCode | .amount = .response.amount"
  },
  "error": null
}
```

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success (returns templates or empty map) |
| `998` | bad request |
| `999` | internal error |

---

### 11. JSLT Admin: Reload Template Cache

**Purpose:** Evict and re-fetch a selector's JSLT templates from the database.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| POST | `/internal-api/jslt/_reload` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Description |
|-----------|------|-----------|-------------|
| `selector` | String | M | Selector to reload |

**Response (success — both directions compiled)**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {
    "client_spec_request": ".clientId = .merchant.id | ...",
    "client_spec_response": ".de39 = .response.responseCode | ..."
  },
  "error": null
}
```

**Response (failure — a direction has an invalid template)**

```json
{
  "response": {
    "code": "900",
    "description": "invalid parameters",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": null,
  "error": {
    "violations": {
      "template": ["NotValid"]
    }
  }
}
```

**Note:** Prior to Phase 5 of `docs/POST_MIGRATION_REMEDIATION_PLAN.md`, an invalid template was silently cached as a pass-through and this endpoint returned `200 OK` with the broken template text, giving no signal that the reload failed. It now returns `400` (`InvalidTemplateException`). The broken template is still cached as a pass-through internally so live traffic for that selector keeps working — only the operator-facing reload call now fails loudly.

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success (both directions compiled) |
| `900` | invalid parameters (a direction's template failed to compile) |
| `998` | bad request |
| `999` | internal error |

---

### 12. JSLT Admin: Reload All Templates

**Purpose:** Clear the entire JSLT template cache and re-fetch every configured selector.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| POST | `/internal-api/jslt/_reload-all` | Standard headers (all optional) |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {
    "client_spec_request:20.97-E001": true,
    "client_spec_response:20.97-E001": true,
    "client_spec_request:20.98-E002": false
  },
  "error": null
}
```

| Field | Type | Description |
|-------|------|--------------|
| `data` | Object | Map of `"<groupId>:<selector>"` → `true` (compiled) / `false` (compile failed, cached as pass-through) |

**Note:** Prior to Phase 5, this endpoint always returned `data: true` regardless of whether any selector's template actually compiled. It now reports per-selector compile status. Always returns `200` — one bad selector's template does not prevent the others from reloading.

---

### 13. JSLT Admin: Save or Update Template

**Purpose:** Create or update a JSLT template for a selector and direction.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| PUT | `/internal-api/jslt/template` | Standard headers + `Content-Type: text/plain` |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Description |
|-----------|------|-----------|-------------|
| `selector` | String | M | Selector identifier |
| `group` | String | M | Direction: `CLIENT_SPEC_REQUEST` or `CLIENT_SPEC_RESPONSE` |

Request body (raw JSLT, `text/plain`):
```
.clientId = .merchant.id | .amount = .txn.amount | .currency = "IDR"
```

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": {
    "id": 12345,
    "group": "CLIENT_SPEC_REQUEST",
    "selector": "0200.000001.01",
    "template": ".clientId = .merchant.id | .amount = .txn.amount | .currency = \"IDR\""
  },
  "error": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `data.id` | Long | Database record ID |
| `data.group` | String | Template direction |
| `data.selector` | String | Selector identifier |
| `data.template` | String | JSLT template text |

**Response Codes**

| Code | Description |
|------|-------------|
| `000` | success (template saved) |
| `998` | bad request (invalid JSLT, missing selector/group) |
| `999` | internal error |

**Note:** After save, the compiled expression cache is evicted for this selector/direction.

---

### 14. Event Log: Remove Old Records

**Purpose:** Delete event log records older than a specified number of days.

| HTTP Method | Path | Headers |
|-------------|------|---------|
| DELETE | `/internal-api/event_log` | Standard headers (all optional) |

**Request**

Query parameters:

| Parameter | Type | Mandatory | Default | Description |
|-----------|------|-----------|---------|-------------|
| `days` | Integer | O | `30` | Age threshold in days |

**Response**

```json
{
  "response": {
    "code": "000",
    "description": "success",
    "time": "2026-09-03T10:30:45.123Z"
  },
  "data": true,
  "error": null
}
```

**Note:** Deletion runs asynchronously on `defaultAsyncTaskExecutor`.

---

## Architecture Notes

### ISO8583 Message Flow

- **Inbound:** Upstream switch sends `0200` → Netty event loop → ISO transaction virtual-thread executor → REST client → downstream API → response → ISO `0210`
- **Outbound:** API endpoints manage network configuration and JSLT templates; they do not construct ISO messages directly
- **Dead-letter:** ISO errors are captured in the `dead_letter_process` table; retry endpoint allows manual replay

### JSLT Transformation

- Templates are cached on first use with a 10-minute TTL
- Pass-through mode: if no template exists for a selector, request and response are passed through unchanged
- Negative cache: selectors with no template are cached as a sentinel to avoid repeated DB queries

### Async Operations

These endpoints execute asynchronously and return immediately:

- `DELETE /internal-api/dead_letter_process`
- `POST /internal-api/dead_letter_process/_retry`
- `DELETE /internal-api/event_log`

Failures during async execution are logged but do not affect the HTTP response.

---

**Generated:** 2026-09-03 | **Runtime:** Spring Boot 3.5.16 · servlet + virtual threads
