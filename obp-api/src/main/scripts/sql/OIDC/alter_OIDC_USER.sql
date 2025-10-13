-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- ALTER OIDC_USER
-- =============================================================================
-- This script alters the OIDC user role to update password and connection settings

-- Change the password for the OIDC user
ALTER ROLE :OIDC_USER WITH PASSWORD :OIDC_PASSWORD;

-- Set connection limit for the OIDC user
ALTER USER :OIDC_USER CONNECTION LIMIT 10;
