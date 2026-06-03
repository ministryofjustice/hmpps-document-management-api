ALTER TABLE document
    ADD COLUMN file_content_hash varchar(64);

CREATE INDEX idx_document_file_content_hash ON document (file_content_hash);
