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
    2,
    '8cdadcf3-b003-4116-9956-c99bd8df6a00',
    'HMCTS_WARRANT',
    'warrant_for_remand',
    'pdf',
    48243,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonNumbers": ["C3456DE"], "prisonCodes": ["KMI"] }',
    now()::timestamp - 3,
    'Remand and Sentencing',
    'CREATED_BY_USER'
);

INSERT INTO document_metadata_history
(
    document_metadata_history_id,
    document_id,
    metadata,
    superseded_time,
    superseded_by_service_name,
    superseded_by_username
)
VALUES
(
    1,
    2,
    JSON '{ "prisonNumbers": ["A1234BC"], "prisonCodes": ["KMI"] }',
    now()::timestamp - 2,
    'Remand and Sentencing',
    'SUPERSEDED_BY_USER'
),
(
    2,
    2,
    JSON '{ "prisonNumbers": ["B2345CD"], "prisonCodes": ["KMI"] }',
    now()::timestamp - 1,
    'Remand and Sentencing',
    'SUPERSEDED_BY_USER'
);
