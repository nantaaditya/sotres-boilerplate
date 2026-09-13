-- Seed data for system_properties configuration table.
--
-- Runtime: Spring Boot 3.5.16 with Spring Data JPA / Hibernate on PostgreSQL.
-- (Post-refactor note: Phases 1–4 migrated from reactive R2DBC to blocking JDBC.
--  Schema is unchanged; this data access pattern remains the same.)
--
-- See docs/ENVIRONMENT_VARIABLES.md for configuration tuning guidance.
-- See docs/JSLT_GUIDE.md for CLIENT_SPEC_REQUEST / CLIENT_SPEC_RESPONSE template setup.

INSERT INTO system_properties
    (id, group_id, property_id, property_value)
VALUES
    (1, 'acquirers', 'acquirers', 'ACQUIRERS:360001:ARTAJASA,360002:RINTIS,360003:ALTO,360004:JALIN'),
    (2, 'mti', 'incoming', '0x100,0x200,0x420,0x421,0x422,0x423,0x800'),
    (3, 'mti', 'outgoing', '0x110,0x210,0x430,0x431,0x432,0x433,0x810'),
    (4, 'mask_fields', 'iso8583', '2'),
    (5, 'currency', 'fractions', '360:2,840:2'),
    (6, 'endpoint_path', 'mapping', '10.97-E001:/api/transaction'),
    (7, 'response', 'incoming_outgoing_mapping', '00:00'),
    (8, 'registry', 'callback_selector', '21.26-E001,21.26-E002,21.26-E003,21.36-E001,21.36-E002,21.36-E003,11.98-A001,11.98-A003'), -- CLIENT_REGISTRY_TYPE = CALLBACK (fire-and-forget mode)
    (9, 'registry', 'response_selector', '21.26-E001,21.26-E002,21.26-E003,21.36-E001,21.36-E002,21.36-E003,11.98-A001,11.98-A003'); -- CLIENT_REGISTRY_TYPE = RESPONSE (blocking send mode)