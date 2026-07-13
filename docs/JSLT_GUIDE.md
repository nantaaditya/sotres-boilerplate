# JSLT Transformation Guide

JSLT templates let you map an inbound ISO 8583 request context into the JSON body that the downstream REST host expects, and map the REST response back into the normalized `ResponseContext` that the ISO layer understands — all without touching Java code.

---

## Table of Contents

- [How It Fits the Request Lifecycle](#how-it-fits-the-request-lifecycle)
- [How the Selector Works](#how-the-selector-works)
- [Database Setup](#database-setup)
- [Writing JSLT Templates](#writing-jslt-templates)
- [Cache Behavior](#cache-behavior)
- [Runtime Reload (Admin API)](#runtime-reload-admin-api)
- [Adding a New Selector](#adding-a-new-selector)

---

## How It Fits the Request Lifecycle

```
Inbound ISO 8583 message
  │
  ▼
RequestContext          ← assembled from DE fields by RequestContextHelper
  │
  │  [CLIENT_SPEC_REQUEST template]
  │  transform(requestContext) → JsonNode
  ▼
POST /downstream/path   ← TransactionClient sends this as the JSON body
  │
  ▼
raw JsonNode response
  │
  │  [CLIENT_SPEC_RESPONSE template]
  │  transform(rawResponse) → normalized JsonNode
  ▼
ResponseContext         ← mapped back to ISO 8583 response fields
```

`TransactionClient.send()` drives the two transforms. If no template exists for a selector, both sides fall through as a pass-through (the input is returned as-is as a `JsonNode`).

---

## How the Selector Works

Every transaction is identified by a **selector** string computed from three ISO 8583 fields:

```
{MTI[1..2]}.{processingCode[0..2]}-{DE48_PI}
```

| Part | Source | Example |
|------|--------|---------|
| `MTI[1..2]` | Characters 1–2 of the 4-digit hex MTI string | MTI `0x0100` → `"0100"` → `"10"` |
| `processingCode[0..2]` | First 2 digits of DE3 | `"970000"` → `"97"` |
| `DE48_PI` | TLV tag `"PI"` from DE48 | `"E001"` |

**Example:** an authorization request (`0x0100`) for a product indicator `E001` transaction with processing code `97xxxx` produces:

```
10.97-E001
```

The same selector key identifies both the request template and the response template, and the downstream path mapping in `endpoint_path / mapping`.

If DE3 is blank, the processing-code segment becomes `NA`. If DE48 has no `PI` tag, the product indicator segment becomes `NA`.

### Code path

```
IsoFieldHelper.createSelector(mti, processingCode, unpackTLV(DE48))
  → RequestContext.getSelector()
    → TransactionClient.send(requestContext)
```

---

## Database Setup

Templates are rows in the `system_properties` table:

```sql
CREATE TABLE system_properties (
    id             bigserial PRIMARY KEY,
    group_id       varchar(50),   -- "client_spec_request" or "client_spec_response"
    property_id    varchar(50),   -- the selector string, e.g. "10.97-E001"
    property_value text           -- the JSLT template text
);

CREATE INDEX idx_groupid ON system_properties(group_id);
```

A selector needs two rows — one per direction:

```sql
-- Request template: transforms RequestContext → downstream JSON body
INSERT INTO system_properties (group_id, property_id, property_value)
VALUES (
  'client_spec_request',
  '10.97-E001',
  '{
    "partnerReferenceNo": .rrn,
    "amount": {
      "value": .transaction.transactionAmount,
      "currency": .transaction.originalCurrencyCode
    },
    "cardNo": .cardNo,
    "merchantId": .merchant.merchantCategoryCode,
    "terminalId": .cardAcceptorTerminalId
  }'
);

-- Response template: transforms raw downstream JSON → ResponseContext fields
INSERT INTO system_properties (group_id, property_id, property_value)
VALUES (
  'client_spec_response',
  '10.97-E001',
  '{
    "responseCode": .resultCode,
    "responseMessage": .resultMessage,
    "rrn": .partnerReferenceNo,
    "stan": .transactionId
  }'
);
```

You also need a path mapping for the same selector (in the flat-config group):

```sql
-- Append selector:path to the existing endpoint_path/mapping value
UPDATE system_properties
SET property_value = property_value || ',10.97-E001:/api/payment/authorization'
WHERE group_id = 'endpoint_path' AND property_id = 'mapping';
```

---

## Writing JSLT Templates

JSLT is a JSON-to-JSON transformation language. The `RequestContext` or raw response `JsonNode` is the input; the template produces a new `JsonNode`.

### Field access

```jslt
// Top-level field
.fieldName

// Nested field
.parent.child

// Array element
.items[0]
```

### Constructing output

```jslt
{
  "outputKey": .inputField,
  "nested": {
    "value": .transaction.transactionAmount
  }
}
```

### Conditionals

```jslt
{
  "status": if (.responseCode == "00") "SUCCESS" else "FAILED"
}
```

### String operations

```jslt
{
  "ref":   .rrn + "-" + .stan,
  "upper": uppercase(.cardNo)
}
```

### Handling missing or null fields

```jslt
{
  // ?? provides a default when a field is null or absent
  "currency": .transaction.originalCurrencyCode ?? "360"
}
```

### Common pitfalls

- **Amount type mismatch** — `transactionAmount` is a `BigDecimal` serialized as a JSON number. If the downstream expects a string, wrap it: `string(.transaction.transactionAmount)`.
- **Missing PI in DE48** — `getSelector()` uses `"NA"` when the `PI` TLV tag is absent, producing selectors like `10.97-NA`. These resolve to templates normally; just insert the rows with `property_id = '10.97-NA'`.
- **Null nested objects** — if `merchant` is null on the `RequestContext`, accessing `.merchant.merchantCategoryCode` produces null rather than throwing. Guard with `if (.merchant != null) .merchant.merchantCategoryCode else ""`.

Full JSLT language reference: https://github.com/schibsted/jslt

---

## Cache Behavior

`JsltTransformationHelper` keeps a `ConcurrentHashMap<String, Mono<Expression>>` keyed by `groupId:selector` (e.g., `client_spec_request:10.97-E001`).

| Event | What happens |
|-------|-------------|
| First `transform` call for a key | DB fetch → compile → result cached as a hot `Mono` |
| Subsequent calls for the same key | Cached `Mono` replays the compiled `Expression` — no DB round-trip |
| Template not found in DB | Pass-through result returned; **cache entry evicted** so the next call retries the DB |
| Compilation error (`JsltException`) | Error propagated to caller; **cache entry evicted** so the next call retries the DB |
| `evictExpression(group, selector)` | Specific entry removed; next call recompiles from DB |
| `evictAndReload(selector)` | Both directions evicted, then immediately re-fetched and recompiled |
| `evictAll()` | Entire cache cleared, then rewarm by fetching all rows for both groups |

The self-evicting behavior on empty and error means a transient DB unavailability or a missing template at startup does not permanently poison the cache. Once the template is inserted and `_reload` is called, all subsequent callers pick it up.

---

## Runtime Reload (Admin API)

All endpoints live under `/internal-api/jslt`. They are internal-only and should not be exposed through the public gateway.

### Read current templates

```
GET /internal-api/jslt/templates?selector={selector}
```

Returns the raw template text for both directions without touching the expression cache.

```json
{
  "response": { "code": "000", "description": "success", "time": "..." },
  "data": {
    "client_spec_request":  "{ \"partnerReferenceNo\": .rrn }",
    "client_spec_response": "{ \"responseCode\": .resultCode }"
  }
}
```

---

### Save or update one template

```
PUT /internal-api/jslt/template?selector={selector}&group={CLIENT_SPEC_REQUEST|CLIENT_SPEC_RESPONSE}
Content-Type: text/plain

{ "partnerReferenceNo": .rrn }
```

Writes to the DB (insert if new, update if existing) and evicts the compiled expression for that direction so the next `transform` call picks up the new template.

---

### Reload one selector (both directions)

```
POST /internal-api/jslt/_reload?selector={selector}
```

Evicts the compiled expressions for both `client_spec_request:{selector}` and `client_spec_response:{selector}`, re-fetches from DB, and recompiles both. Returns the current template texts.

Use this after a direct DB update to a template row.

---

### Reload everything

```
POST /internal-api/jslt/_reload-all
```

Clears the entire expression cache and rewarms it by loading all rows for both groups from the DB. Use this after a bulk DB import or to pre-warm the cache on a fresh pod.

---

## Adding a New Selector

Checklist for each new downstream client spec:

**1. Insert the two template rows**

```sql
INSERT INTO system_properties (group_id, property_id, property_value) VALUES
  ('client_spec_request',  '10.26-A002', '{ "partnerRef": .rrn }'),
  ('client_spec_response', '10.26-A002', '{ "responseCode": .status }');
```

**2. Add the path mapping**

Append to the existing `endpoint_path / mapping` value (comma-separated `selector:path` pairs):

```sql
UPDATE system_properties
SET property_value = property_value || ',10.26-A002:/api/payment/inquiry'
WHERE group_id = 'endpoint_path' AND property_id = 'mapping';
```

**3. Reload the JSLT cache**

```bash
curl -X POST "http://localhost:8080/internal-api/jslt/_reload?selector=10.26-A002"
```

**4. Verify both templates are live**

```bash
curl "http://localhost:8080/internal-api/jslt/templates?selector=10.26-A002"
```

**5. Reload the path mapping**

The `endpoint_path` group is a flat-config reloaded separately. Call the system-properties reload endpoint or restart the pod:

```bash
curl -X POST "http://localhost:8080/internal-api/system-properties/_reload?group=PATH_MAPPING"
```
