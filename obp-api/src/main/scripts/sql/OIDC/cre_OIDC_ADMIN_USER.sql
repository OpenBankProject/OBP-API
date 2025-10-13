-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE OIDC_ADMIN_USER
-- =============================================================================
-- This script creates the OIDC admin user with limited privileges for client administration


REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM :OIDC_ADMIN_USER;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM :OIDC_ADMIN_USER;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM :OIDC_ADMIN_USER;
REVOKE USAGE ON SCHEMA public FROM :OIDC_ADMIN_USER;
REVOKE CONNECT ON DATABASE :DB_NAME FROM :OIDC_ADMIN_USER;

-- Drop the user if they already exist (for re-running the script)
DROP USER IF EXISTS :OIDC_ADMIN_USER;

-- Create the OIDC admin user with limited privileges
CREATE USER :OIDC_ADMIN_USER WITH
    PASSWORD :OIDC_ADMIN_PASSWORD
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    LOGIN
    NOREPLICATION
    NOBYPASSRLS;

-- Grant CONNECT privilege on the database
GRANT CONNECT ON DATABASE :DB_NAME TO :OIDC_ADMIN_USER;

-- Grant USAGE on the public schema (or specific schema where authuser exists)
GRANT USAGE ON SCHEMA public TO :OIDC_ADMIN_USER;

-- TODO: THIS IS NOT WORKING FOR SOME REASON, WE HAVE TO MANUALLY DO THIS LATER
-- need this so the admin can create rows
GRANT USAGE, SELECT ON SEQUENCE consumer_id_seq TO :OIDC_ADMIN_USER;

-- double check this
-- GRANT USAGE, SELECT ON SEQUENCE consumer_id_seq TO oidc_admin;

-- restrict default access on objects this user might create in the future.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM :OIDC_ADMIN_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM :OIDC_ADMIN_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM :OIDC_ADMIN_USER;


\echo 'Bye from cre_OIDC_ADMIN_USER.sql'
