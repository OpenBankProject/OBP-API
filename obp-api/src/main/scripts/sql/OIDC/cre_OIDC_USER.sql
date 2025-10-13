-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE OIDC_USER
-- =============================================================================
-- This script creates the OIDC user with limited privileges for read-only access


REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM :OIDC_USER;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM :OIDC_USER;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM :OIDC_USER;


-- Drop the user if they already exist (for re-running the script)
-- First revoke all privileges to avoid dependency errors
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM :OIDC_USER;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM :OIDC_USER;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public FROM :OIDC_USER;
REVOKE USAGE ON SCHEMA public FROM :OIDC_USER;
REVOKE CONNECT ON DATABASE :DB_NAME FROM :OIDC_USER;

DROP USER IF EXISTS :OIDC_USER;

-- Create the OIDC user with limited privileges
CREATE USER :OIDC_USER WITH
    PASSWORD :OIDC_PASSWORD
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    LOGIN
    NOREPLICATION
    NOBYPASSRLS;



-- Grant USAGE on the public schema (or specific schema where authuser exists)
GRANT USAGE ON SCHEMA public TO :OIDC_USER;

-- Grant CONNECT privilege on the database
GRANT CONNECT ON DATABASE :DB_NAME TO :OIDC_USER;

-- Set default privileges to prevent future access to new objects that this user might create
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM :OIDC_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM :OIDC_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM :OIDC_USER;

\echo 'OIDC user created successfully.'
