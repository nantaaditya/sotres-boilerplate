create table if not exists dead_letter_process (
    id bigserial primary key,
    created_by varchar(25),
    updated_by varchar(25),
    created_date timestamp,
    updated_date timestamp,
    version int,
    process_type varchar(50),
    process_name varchar(100),
    idempotency_key varchar(100),
    client_name varchar(50),
    method varchar(10),
    path text,
    headers text,
    last_error text,
    payload bytea,
    retry_count int default 0,
    max_retry int,
    status varchar(10),
    retry_histories bytea
    );

CREATE INDEX idx_processtype_processname_status
    ON dead_letter_process (process_type, process_name, status)
    WHERE status in ('NEW', 'FAILED');

CREATE TABLE IF NOT EXISTS system_properties (
    id bigserial PRIMARY KEY,
    group_id varchar(50),
    property_id varchar(50),
    property_value text
);

CREATE INDEX IF NOT EXISTS idx_groupid
    ON system_properties(group_id);

CREATE TABLE IF NOT EXISTS public.event_logs (
    id varchar(20) NOT NULL,
    client_id varchar(50),
    request_id varchar(50),
    method varchar(10),
    path varchar(255),
    response_code varchar(10),
    response_description varchar(50),
    payload bytea,
    created_date timestamp,
    additional_data bytea,
    CONSTRAINT event_logs_pkey PRIMARY KEY (id)
);
-- ===== seed =====
-- E2E seed for the reactive-baseline regression harness (Phase 0).
-- Self-contained: does NOT reuse src/main/resources/dml.sql so the oracle
-- is insulated from production seed drift.

DELETE FROM system_properties;

INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (1, 'acquirers',     'acquirers',                  'ACQUIRERS:360001:ARTAJASA,360002:RINTIS'),
    -- decimal MTIs matching IsoMessage.getType(): 0x100=256, 0x200=512, 0x420=1056,
    -- 0x800=2048, 0x110=272, 0x210=528, 0x430=1072, 0x810=2064.
    -- (The shipped dml.sql uses "0x100" form, which IsoMessageLoggerHelper.getMTI
    --  cannot parse -- Integer.parseInt, base 10.)
    (2, 'mti',           'incoming',                   '256,512,1056,2048'),
    (3, 'mti',           'outgoing',                   '272,528,1072,2064'),
    (4, 'mask_fields',   'iso8583',                    '2'),
    (5, 'currency',      'fractions',                  '360:2,840:2'),
    (6, 'endpoint_path', 'mapping',                    '20.97-E001:/api/transaction,20.98-E002:/api/passthrough'),
    (7, 'response',      'incoming_outgoing_mapping',  '00:00,05:05,51:51'),
    (8, 'registry',      'callback_selector',          ''),
    (9, 'registry',      'response_selector',          '');

-- JSLT: request shaping for selector 20.97-E001 (RequestContext -> downstream body)
INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (100, 'client_spec_request', '20.97-E001',
     '{"pan": .cardNo, "rrn": .rrn, "stan": .stan, "processingCode": .processingCode}');

-- JSLT: response normalisation for selector 20.97-E001 (downstream body -> ResponseContext)
INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (101, 'client_spec_response', '20.97-E001',
     '{"response": {"code": .rc}, "data": {"transaction": {"approvalCode": .auth}}}');

-- selector 20.98-E002 has a path mapping but NO JSLT templates -> exercises pass-through.
