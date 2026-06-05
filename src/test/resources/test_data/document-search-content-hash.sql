INSERT INTO document
(
    document_id,
    document_uuid,
    document_type,
    filename,
    file_extension,
    file_size,
    file_hash,
    mime_type,
    metadata,
    created_time,
    created_by_service_name,
    created_by_username,
    file_content_hash
)
VALUES
-- Two warrants sharing the same extracted-content hash (the dual-delivery case).
(
    1,
    '11111111-1111-1111-1111-111111111111',
    'HMCTS_WARRANT',
    'warrant_a',
    'pdf',
    48243,
    'a0000000000000000000000000000000000000000000000000000000000000a',
    'application/pdf',
    JSON '{ "prisonNumber": "A1234BC" }',
    NOW(),
    'court-data-ingestion-api',
    'CREATED_BY_USER',
    '58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae'
),
(
    2,
    '22222222-2222-2222-2222-222222222222',
    'HMCTS_WARRANT',
    'warrant_b',
    'pdf',
    50111,
    'b0000000000000000000000000000000000000000000000000000000000000b',
    'application/pdf',
    JSON '{ "prisonNumber": "A1234BC" }',
    NOW(),
    'court-data-ingestion-api',
    'CREATED_BY_USER',
    '58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae'
),
-- A warrant with a different content hash, must not match.
(
    3,
    '33333333-3333-3333-3333-333333333333',
    'HMCTS_WARRANT',
    'warrant_other',
    'pdf',
    51222,
    'c0000000000000000000000000000000000000000000000000000000000000c',
    'application/pdf',
    JSON '{ "prisonNumber": "B2345CD" }',
    NOW(),
    'court-data-ingestion-api',
    'CREATED_BY_USER',
    '0000000000000000000000000000000000000000000000000000000000000000'
),
-- A SAR document sharing the hash, must be filtered out for a caller without the SAR role.
(
    4,
    '44444444-4444-4444-4444-444444444444',
    'SUBJECT_ACCESS_REQUEST_REPORT',
    'sar_c',
    'pdf',
    47645,
    'd0000000000000000000000000000000000000000000000000000000000000d',
    'application/pdf',
    JSON '{ "sarCaseReference": "SAR-1234" }',
    NOW(),
    'Subject Access Request',
    'CREATED_BY_USER',
    '58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae'
);
