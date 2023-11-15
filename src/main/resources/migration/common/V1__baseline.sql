-- Provides text based document type description when querying the database as well as referential integrity
CREATE TABLE document_type
(
    code                            varchar(40)     NOT NULL CONSTRAINT document_type_pk PRIMARY KEY,
    description                     varchar(100)    NOT NULL
);

INSERT INTO document_type VALUES ('HMCTS_WARRANT', 'Warrants for Remand and Sentencing');

CREATE TABLE document
(
    document_id                     bigserial       NOT NULL CONSTRAINT document_pk PRIMARY KEY,
    document_uuid                   uuid            NOT NULL,
    document_type                   varchar(40)     NOT NULL REFERENCES document_type (code),
    filename                        varchar(255)    NOT NULL,
    file_extension                  varchar(25)     NOT NULL,
    file_size                       bigint          NOT NULL,
    file_hash                       varchar(64)     NOT NULL,
    mime_type                       varchar(100)    NOT NULL,
    metadata                        jsonb           NOT NULL,
    created_time                    timestamp       NOT NULL,
    created_by_service_name         varchar(100)    NOT NULL,
    created_by_username             varchar(100),
    deleted_time                    timestamp,
    deleted_by_service_name         varchar(100),
    deleted_by_username             varchar(100)
);

CREATE UNIQUE INDEX idx_document_document_uuid ON document (document_uuid);
CREATE INDEX idx_document_type ON document (document_type);
CREATE INDEX idx_document_file_hash ON document (file_hash);
CREATE INDEX idx_document_metadata ON document (metadata);
CREATE INDEX idx_document_deleted_time ON document (deleted_time);

CREATE TABLE document_metadata_history
(
    document_metadata_history_id    bigserial       NOT NULL CONSTRAINT document_metadata_history_pk PRIMARY KEY,
    document_id                     bigint          NOT NULL REFERENCES document (document_id),
    metadata                        jsonb           NOT NULL,
    superseded_time                 timestamp       NOT NULL,
    superseded_by_service_name      varchar(100)    NOT NULL,
    superseded_by_username          varchar(100)
);

CREATE INDEX idx_document_metadata_document_id ON document_metadata_history (document_id);
CREATE INDEX idx_document_metadata_history_metadata ON document_metadata_history (metadata);
CREATE INDEX idx_document_metadata_history_superseded_time ON document_metadata_history (superseded_time);
