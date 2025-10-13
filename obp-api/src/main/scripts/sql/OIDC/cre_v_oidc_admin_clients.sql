-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE VIEW v_oidc_admin_clients
-- =============================================================================
-- This script creates an admin view for OIDC client management with full CRUD access

-- Drop the view if it already exists
DROP VIEW IF EXISTS v_oidc_admin_clients CASCADE;

-- Create a view that exposes all consumer fields for full CRUD operations
CREATE VIEW v_oidc_admin_clients AS
SELECT
name
,apptype
,description
,developeremail
,sub
,consumerid
,createdat
,updatedat
,secret
,azp
,aud
,iss
,redirecturl
,logourl
,userauthenticationurl
,clientcertificate
,company
,key_c
,isactive
FROM consumer
ORDER BY name;

-- Add comment to the view for documentation
COMMENT ON VIEW v_oidc_admin_clients IS 'Full admin view of consumer table for OIDC service administration. Provides complete CRUD access to all consumer fields for client management operations.';

-- Grant full CRUD permissions on the admin view and underlying consumer table (oidc_admin_user only)
GRANT SELECT, INSERT, UPDATE, DELETE ON consumer TO :OIDC_ADMIN_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON v_oidc_admin_clients TO :OIDC_ADMIN_USER;

GRANT USAGE, SELECT ON SEQUENCE consumer_id_seq TO :OIDC_ADMIN_USER;

\echo 'OIDC admin clients view created successfully.'
