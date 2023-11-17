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
    '443b5592-a87d-4b3d-8691-61daa7ec882f',
    'HMCTS_WARRANT',
    'warrant_for_remand_1',
    'pdf',
    48243,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC", "court": "Birmingham Magistrates", "warrantDate": "2023-11-14" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    2,
    '91211779-fccc-4e40-a7f5-27decf107df4',
    'HMCTS_WARRANT',
    'warrant_for_sentencing_2',
    'pdf',
    5252,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "MDI", "prisonNumber": "B2345CD", "court": "Croydon Crown", "warrantDate": "2023-01-02" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    3,
    '4ee0c367-16ac-4bf8-a259-27ebf0b9e7c2',
    'HMCTS_WARRANT',
    'warrant_for_remand_3',
    'pdf',
    57777,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "RSI", "previousPrisonCodes": ["KMI", "MDI"], "prisonNumber": "C3456DE", "previousPrisonNumbers": ["A1234BC", "B2345CD"], "court": "Dudley Magistrates", "warrantDate": "2022-10-23" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    4,
    '25205a12-e329-4842-ba00-429a71653b64',
    'SUBJECT_ACCESS_REQUEST_REPORT',
    'subject_access_request_report_4',
    'pdf',
    47645,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "PVI", "prisonNumber": "A1234BC", "sarCaseReference": "SAR-1234", "sarMetadata": "SAR specific information" }',
    now()::timestamp,
    'Subject Access Request',
    'CREATED_BY_USER'
),
(
    5,
    '311abd72-d802-4105-817c-3bd3b2a7f4ab',
    'HMCTS_WARRANT',
    'warrant_for_remand_5',
    'pdf',
    73755,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "SFI", "prisonNumber": "D4567EF", "court": "Stafford Crown", "warrantDate": "2021-09-27" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    6,
    '9ed71a53-b9b0-4ee0-b501-5f9d7db576e4',
    'HMCTS_WARRANT',
    'warrant_for_sentencing_6',
    'pdf',
    2355,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "SFI", "prisonNumber": "D4567EF", "court": "Stoke on Trent Crown", "warrantDate": "2022-02-06" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
),
(
    7,
    '1fb2ed32-2736-4193-8f97-64566d437fc0',
    'HMCTS_WARRANT',
    'warrant_for_remand_7',
    'pdf',
    45777,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "SFI", "prisonNumber": "E5678FG", "court": "Stafford Crown", "warrantDate": "2022-09-08" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER'
);

-- Deleted HMCTS_WARRANT document
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
    10,
    'f73a0f91-2957-4224-b477-714370c04d37',
    'HMCTS_WARRANT',
    'warrant_for_remand_10',
    'pdf',
    48243,
    'd58e3582afa99040e27b92b13c8f2280',
    'application/pdf',
    JSON '{ "prisonCode": "KMI", "prisonNumber": "A1234BC", "court": "Birmingham Magistrates", "warrantDate": "2023-11-14" }',
    now()::timestamp,
    'Remand and Sentencing',
    'CREATED_BY_USER',
    now()::timestamp,
    'Remand and Sentencing',
    'DELETED_BY_USER'
);
