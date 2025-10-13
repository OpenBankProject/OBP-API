-- Include variable definitions and database connection
\i set_and_connect.sql

-- =============================================================================
-- CREATE VIEW v_oidc_users
-- =============================================================================
-- This script creates a read-only view exposing only necessary authuser fields for OIDC
-- TODO: Consider excluding locked users by joining with mappedbadloginattempt table
--       and checking mbadattemptssinceresetorsuccess against max.bad.login.attempts prop

-- Drop the view if it already exists
DROP VIEW IF EXISTS v_oidc_users CASCADE;

-- Create a read-only view exposing only necessary authuser fields for OIDC
CREATE VIEW v_oidc_users AS
SELECT
    ru.userid_ AS user_id,
    au.username,
    au.firstname,
    au.lastname,
    au.email,
    au.validated,
    au.provider,
    au.password_pw,
    au.password_slt,
    au.createdat,
    au.updatedat
FROM authuser au
INNER JOIN resourceuser ru ON au.user_c = ru.id
WHERE au.validated = true  -- Only expose validated users to OIDC service
ORDER BY au.username;

-- Add comment to the view for documentation
COMMENT ON VIEW v_oidc_users IS 'Read-only view of authuser and resourceuser tables for OIDC service access. Only includes validated users and returns user_id from resourceuser.userid_. WARNING: Includes password hash and salt for OIDC credential verification - ensure secure access.';

-- Grant SELECT permission on the OIDC view (oidc_user - read-only access)
GRANT SELECT ON v_oidc_users TO :OIDC_USER;

\echo 'OIDC users view created successfully.'
