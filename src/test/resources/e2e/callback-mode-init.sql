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
-- E2E seed for CallbackModeE2eTest (Case 2: EnhancedIsoClient.sendWithCallback flow).
-- Same shape as e2e/init.sql, except registry.callback_selector is pre-populated so
-- IsoCallbackResponseHandler (built once, at client-connect time during context startup)
-- captures it before the test runs -- a later systemPropertiesService.reload() would not
-- reach that already-constructed handler instance.

DELETE FROM system_properties;

INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (1, 'acquirers',     'acquirers',                  'ACQUIRERS:360001:ARTAJASA,360002:RINTIS'),
    (2, 'mti',           'incoming',                   '256,512,1056,2048'),
    (3, 'mti',           'outgoing',                   '272,528,1072,2064'),
    (4, 'mask_fields',   'iso8583',                    '2'),
    (5, 'currency',      'fractions',                  '360:2,840:2'),
    (6, 'endpoint_path', 'mapping',                    '20.97-E001:/api/transaction'),
    (7, 'response',      'incoming_outgoing_mapping',  '00:00,05:05'),
    -- "21.97-E001": selector of the 0210 callback reply built by
    -- IsoMessages.callbackReply(request, "E001", ...) -- MTI 0210 -> substring(1,3)="21",
    -- DE3 "970000" -> "97", DE48 PI "E001". No AbstractTransactionHandler registers this
    -- selector, so TransactionProcessorParticipant hits its "no handler" branch and calls
    -- IsoResponseSender.sendResponseWithObservation -- exactly where the callbackResponse guard
    -- must suppress writing a second ISO reply back to the switch.
    (8, 'registry',      'callback_selector',          '21.97-E001'),
    (9, 'registry',      'response_selector',          '');

-- JSLT: request shaping for selector 20.97-E001, used only by the initial 0200 auth
-- request's own selector match (not exercised directly by the callback-mode test, kept
-- for parity with e2e/init.sql in case a handler-match variant is added later).
INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (100, 'client_spec_request', '20.97-E001',
     '{"pan": .cardNo, "rrn": .rrn, "stan": .stan, "processingCode": .processingCode}');

INSERT INTO system_properties (id, group_id, property_id, property_value) VALUES
    (101, 'client_spec_response', '20.97-E001',
     '{"response": {"code": .rc}, "data": {"transaction": {"approvalCode": .auth}}}');
