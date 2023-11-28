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
    deleted_time,
    deleted_by_service_name,
    deleted_by_username
)
VALUES
(
    3,
    'c671814d-7c32-4394-b46d-a70957148925',
    'HMCTS_WARRANT',
    'warrant_for_remand',
    'pdf',
    48243,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonNumbers": ["A1234BC"], "prisonCodes": ["KMI"] }',
    NOW(),
    'Remand and Sentencing',
    'CREATED_BY_USER',
    NOW(),
    'Remand and Sentencing',
    'DELETED_BY_USER'
);
