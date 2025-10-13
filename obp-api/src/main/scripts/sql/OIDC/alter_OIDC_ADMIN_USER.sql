-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- ALTER OIDC_ADMIN_USER
-- =============================================================================
-- This script alters the OIDC admin user role to update password and connection settings

-- Change the password for the OIDC admin user
ALTER ROLE :OIDC_ADMIN_USER WITH PASSWORD :OIDC_ADMIN_PASSWORD;

-- Set connection limit for the OIDC admin user
ALTER USER :OIDC_ADMIN_USER CONNECTION LIMIT 5;
