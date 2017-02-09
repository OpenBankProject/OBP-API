-- Rename apiuser to resourceuser 
alter table apiuser RENAME TO resourceuser;
alter table apiuser RENAME COLUMN apiuser_pk TO resourceuser_pk;
alter table apiuser RENAME COLUMN apiuser_provider__providerid TO resourceuser_provider__providerid;
