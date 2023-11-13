SET REFERENTIAL_INTEGRITY FALSE;

truncate table document_metadata_history restart identity;
truncate table document restart identity;

SET REFERENTIAL_INTEGRITY TRUE;
