ALTER TABLE document
    ADD COLUMN duplicate_of uuid;

CREATE INDEX idx_document_duplicate_of ON document (duplicate_of);
