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
    '45833102-617f-4035-bcd8-cfa6ea309eb0',
    'HMCTS_WARRANT',
    'warrant_for_remand_1',
    'png',
    48243,
    '',
    'image/png',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW() - INTERVAL '3 DAYS',
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    2,
    '09440dbb-5a27-48ce-a646-ff1601bc46f2',
    'HMCTS_WARRANT',
    'warrant_for_sentencing_2',
    'pdf',
    5252,
    '',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW() - INTERVAL '1 DAYS',
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    3,
    '92abedda-52ef-43ba-86e4-e7397921d35e',
    'HMCTS_WARRANT',
    'warrant_for_remand_3',
    'pdf',
    57777,
    '',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW() - INTERVAL '5 DAYS',
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    4,
    '8a87d33c-7d43-4ad9-b0f8-fc5faa858fc3',
    'HMCTS_WARRANT',
    'warrant_for_remand_5',
    'pdf',
    47645,
    '',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW() - INTERVAL '2 DAYS',
    'Subject Access Request',
    'CREATED_BY_USER'
),
(
    5,
    '76f27d16-acd0-4885-93fa-6373e9677021',
    'HMCTS_WARRANT',
    'warrant_for_remand_4',
    'tiff',
    73755,
    '',
    'image/tiff',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC" }',
    NOW(),
    'Remand and Sentencing',
    'CREATED_BY_USER'
);
