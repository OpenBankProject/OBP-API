-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE VIEW v_oidc_clients
-- =============================================================================
-- This script creates a read-only view exposing necessary consumer fields for OIDC
-- grant_types come from the consumer's granttypes column, set by an administrator (OBP-API's
-- PUT /obp/v7.0.0/management/consumers/CONSUMER_ID/grant-types). A consumer that sets none gets the defaults
-- authorization_code, refresh_token and client_credentials. direct_login is OBP's own, not an OAuth2 grant: left out.
-- Note: scopes are still hardcoded defaults.

-- Drop the view if it already exists
DROP VIEW IF EXISTS v_oidc_clients CASCADE;

-- Create a read-only view exposing necessary consumer fields for OIDC
CREATE VIEW v_oidc_clients AS
SELECT
    consumerid as consumer_id, -- This is really an identifier for management purposes. Its also used to link trusted consumers together.
    key_c as key, -- The key is the OAuth1 identifier for the app.
    key_c as client_id, -- The client_id is the OAuth2 identifier for the app.
    secret, -- The OAuth1 secret
    secret as client_secret, -- The OAuth2 secret
    redirecturl as redirect_uris,
    CASE
      WHEN COALESCE(TRIM(granttypes), '') = '' THEN 'authorization_code,refresh_token,client_credentials'
      ELSE TRIM(BOTH ',' FROM REGEXP_REPLACE(REPLACE(granttypes, ' ', ''), '(^|,)direct_login(?=,|$)', '', 'g'))
    END as grant_types,                                  -- The consumer's grant types, or the defaults
    'openid,profile,email' as scopes,                   -- Default OIDC scopes
    name as client_name,
    'code' as response_types,
    'client_secret_post' as token_endpoint_auth_method,
    createdat as created_at,
    jwksuri as jwks_uri,
    clientcertificate as client_certificate
FROM consumer
WHERE isactive = true  -- Only expose active consumers to OIDC service
ORDER BY client_name;

-- Add comment to the view for documentation
COMMENT ON VIEW v_oidc_clients IS 'Read-only view of consumer table for OIDC service access. Only includes active consumers. grant_types are the consumer''s own (granttypes), or authorization_code, refresh_token and client_credentials when it sets none. scopes are hardcoded defaults.';

-- Grant SELECT permission on the OIDC view (oidc_user - read-only access)
-- not sure OIDC_USER needs this.
GRANT SELECT ON v_oidc_clients TO :OIDC_USER;

\echo 'OIDC clients view created successfully.'
