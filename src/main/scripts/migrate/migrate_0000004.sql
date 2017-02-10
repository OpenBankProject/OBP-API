-- Rename apiuser to resourceuser 
ALTER TABLE apiuser RENAME TO resourceuser;
DROP INDEX apiuser_provider__providerid;
CREATE UNIQUE INDEX resourceuser_provider__providerid ON resourceuser (PROVIDER_,PROVIDERID);