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