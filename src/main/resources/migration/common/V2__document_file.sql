-- Temporary table for storing file binaries. Service will eventually store files in S3 at which point this table
-- will be deleted
CREATE TABLE document_file
(
    document_file_id                bigserial       NOT NULL CONSTRAINT document_file_pk PRIMARY KEY,
    document_uuid                   uuid            NOT NULL,
    file_data                       bytea           NOT NULL
);

CREATE UNIQUE INDEX idx_document_file_document_uuid ON document_file (document_uuid);
