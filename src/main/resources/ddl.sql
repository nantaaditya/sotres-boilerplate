CREATE TABLE IF NOT EXISTS dead_letter_process (
    id bigserial PRIMARY KEY,
    process_type varchar(50),
    process_name varchar(50),
    last_error text,
    payload bytea,
    processed boolean,
    created_by varchar(50),
    updated_by varchar(50),
    created_date timestamp,
    updated_date timestamp,
    version int
);

CREATE INDEX IF NOT EXISTS idx_processtype_processname_processed
    ON dead_letter_process(process_type, process_name, processed)
    WHERE processed IS false;

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