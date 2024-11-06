-- Replace the PHYSICAL_MARKS_PICTURE document type.
UPDATE document_type
SET code        = 'DISTINGUISHING_MARK_IMAGE',
    description = 'Image of distinguishing mark for prisoners'
WHERE code = 'PHYSICAL_IDENTIFIER_PICTURE';

-- Update all existing documents of type PHYSICAL_IDENTIFIER_PICTURE to DISTINGUISHING_MARK_IMAGE.
UPDATE document
SET document_type = 'DISTINGUISHING_MARK_IMAGE'
WHERE code = 'PHYSICAL_IDENTIFIER_PICTURE';