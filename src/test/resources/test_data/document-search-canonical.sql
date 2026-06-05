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
    duplicate_of
)
VALUES
-- Canonical warrant.
(
    1,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'HMCTS_WARRANT',
    'warrant_canonical',
    'pdf',
    48243,
    'a0000000000000000000000000000000000000000000000000000000000000a',
    'application/pdf',
    JSON '{ "prisonNumber": "A1234BC" }',
    NOW(),
    'court-data-ingestion-api',
    'CREATED_BY_USER',
    NULL
),
-- Duplicate of the canonical warrant.
(
    2,
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'HMCTS_WARRANT',
    'warrant_duplicate',
    'pdf',
    50111,
    'b0000000000000000000000000000000000000000000000000000000000000b',
    'application/pdf',
    JSON '{ "prisonNumber": "A1234BC" }',
    NOW(),
    'court-data-ingestion-api',
    'CREATED_BY_USER',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
);
