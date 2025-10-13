-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE VIEW v_oidc_clients
-- =============================================================================
-- This script creates a read-only view exposing necessary consumer fields for OIDC
-- Note: Some OIDC-specific fields like grant_types and scopes may not exist in current schema
-- TODO: Add grant_types and scopes fields to consumer table if needed for full OIDC compliance

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
    'authorization_code,refresh_token' as grant_types,  -- Default OIDC grant types
    'openid,profile,email' as scopes,                   -- Default OIDC scopes
    name as client_name,
    'code' as response_types,
    'client_secret_post' as token_endpoint_auth_method,
    createdat as created_at
FROM consumer
WHERE isactive = true  -- Only expose active consumers to OIDC service
ORDER BY client_name;

-- Add comment to the view for documentation
COMMENT ON VIEW v_oidc_clients IS 'Read-only view of consumer table for OIDC service access. Only includes active consumers. Note: grant_types and scopes are hardcoded defaults - consider adding these fields to consumer table for full OIDC compliance.';

-- Grant SELECT permission on the OIDC view (oidc_user - read-only access)
-- not sure OIDC_USER needs this.
GRANT SELECT ON v_oidc_clients TO :OIDC_USER;

\echo 'OIDC clients view created successfully.'
