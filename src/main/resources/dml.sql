INSERT INTO system_properties
    (id, group_id, property_id, value)
VALUES
    (1, 'packagers', 'packagers', 'DEFAULT:default-packager.xml'),
    (2, 'acquirers', 'acquirers', 'ACQUIRERS:360001:ARTAJASA,360002:RINTIS,360003:ALTO,360004:JALIN'),
    (3, 'mti', 'incoming', '0x100,0x200,0x420,0x421,0x422,0x423,0x800'),
    (4, 'mti', 'outgoing', '0x110,0x210,0x430,0x431,0x432,0x433,0x810'),
    (5, 'mask_fields', 'iso8583', '2'),
    (6, 'currency', 'fractions', '360:2,840:2'),
    (7, 'endpoint_path', 'mapping', '10.97-E001:/api/transaction'),
    (8, 'response', 'incoming_outgoing_mapping', '00:00');