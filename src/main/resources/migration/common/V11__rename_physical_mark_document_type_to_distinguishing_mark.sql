-- Add new document type for distinguishing marks
INSERT INTO document_type VALUES ('DISTINGUISHING_MARK_IMAGE', 'Image of distinguishing mark for prisoners');

-- Update all existing documents of type PHYSICAL_IDENTIFIER_PICTURE to DISTINGUISHING_MARK_IMAGE.
UPDATE document
SET document_type = 'DISTINGUISHING_MARK_IMAGE'
WHERE document_type = 'PHYSICAL_IDENTIFIER_PICTURE';

-- Delete to Physical marks document type
DELETE FROM document_type WHERE code = 'PHYSICAL_IDENTIFIER_PICTURE';