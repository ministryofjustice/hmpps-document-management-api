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
    4,
    '1f4e2c96-de62-4585-a79a-9a37c5506b1c',
    'SUBJECT_ACCESS_REQUEST_REPORT',
    'subject-access-request-report',
    'pdf',
    21384,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "sarCaseReference": "SAR-1234", "prisonNumber": "A1234BC" }',
    NOW(),
    'Manage Subject Access Requests',
    'SAR_USER'
);
