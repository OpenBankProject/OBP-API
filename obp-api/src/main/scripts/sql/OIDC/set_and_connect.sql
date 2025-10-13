-- =============================================================================
-- SET VARIABLES
-- =============================================================================
-- This script defines all variables used in the OIDC setup scripts
-- Update these values to match your environment and security requirements

-- Database connection parameters (update these to match your OBP configuration)
-- These should match the values in your OBP-API props file (db.url)
\set DB_HOST 'localhost'
\set DB_PORT '5432'
\set DB_NAME 'sandbox'

-- OIDC user credentials
-- To be used by a production grade OIDC server such as Keycloak
\set OIDC_USER "oidc_user"
\set OIDC_PASSWORD '''lakij8777fagg'''

-- OIDC admin user credentials
-- To be used by a development OIDC server such as OBP-OIDC which will create Clients / Consumers in the OBP Database via the Consumer table.
\set OIDC_ADMIN_USER "oidc_admin"
\set OIDC_ADMIN_PASSWORD '''fhka77uefassEE'''


\c :DB_NAME
