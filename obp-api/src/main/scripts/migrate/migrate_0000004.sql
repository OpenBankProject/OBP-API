-- This change relates to
-- Simplify User model #187 - Refactored APIUser -> ResourceUser and OBPUser -> AuthUser. added sql migration script for apiuser table
-- in git commit: 3d0e1dd293906932b6a6969741dc6b8f57adb749

-- In which apiuser is changed to resourceuser and OBPUser is renamed to AuthUser

-- There are at least two ways to handle the change



-- 1) (before running the API after 3d0e1dd293906932b6a6969741dc6b8f57adb749)
-- Rename apiuser to resourceuser
ALTER TABLE apiuser RENAME TO resourceuser;
DROP INDEX apiuser_provider__providerid;
CREATE UNIQUE INDEX resourceuser_provider__providerid ON resourceuser (PROVIDER_,PROVIDERID);

ALTER TABLE users RENAME TO authuser;
DROP INDEX USERS_USER_C;
CREATE INDEX AUTHUSER_USER_C ON authuser (USER_C);
DROP INDEX USERS_UNIQUEID;
CREATE INDEX AUTHUSER_UNIQUEID ON authuser (UNIQUEID);
DROP INDEX USERS_USERNAME;
CREATE INDEX AUTHUSER_USERNAME ON authuser (USERNAME);

-- OR --

-- 2) (after running the API after 3d0e1dd293906932b6a6969741dc6b8f57adb749)
-- Copy the records after running the API (and lift-web schemify has created the table)
insert into resourceuser  (id, email, provider_, providerid, name_, userid_) select id, email, provider_, providerid, name_, userid_ from apiuser;

insert into authuser (id ,firstname ,lastname ,email ,username ,password_pw ,password_slt ,provider ,timezone ,user_c ,validated ,superuser ,uniqueid ,locale ) select  id ,firstname ,lastname ,email ,username ,password_pw ,password_slt ,provider ,timezone ,user_c ,validated ,superuser ,uniqueid ,locale  from users;
-- At least for PostgreSQL, you need to update the sequence for the index afterwards
select setval('resourceuser_id_seq', max(id)) from resourceuser;
select setval('authuser_id_seq', max(id)) from authuser;
