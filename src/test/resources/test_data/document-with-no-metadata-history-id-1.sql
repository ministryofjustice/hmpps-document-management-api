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
    created_by_username
)
VALUES
(
    1,
    'f73a0f91-2957-4224-b477-714370c04d37',
    'HMCTS_WARRANT',
    'warrant_for_remand',
    'pdf',
    20688,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW(),
    'Remand and Sentencing',
    'CREATED_BY_USER'
);
