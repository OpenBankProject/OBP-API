-- =============================================================================
-- CREATE VIEW v_oidc_users (MS SQL Server Version)
-- =============================================================================
-- This script creates a read-only view exposing only necessary authuser fields for OIDC
--
-- PREREQUISITES:
-- - Database must exist and you must be connected to it
-- - Tables 'authuser' and 'resourceuser' must exist
-- - User/Login for OIDC service must be created beforehand
--
-- TODO: Consider excluding locked users by joining with mappedbadloginattempt table
--       and checking mbadattemptssinceresetorsuccess against max.bad.login.attempts prop
--
-- USAGE:
-- 1. Connect to your target database
-- 2. Run this script to create the view
-- 3. Manually grant permissions: GRANT SELECT ON v_oidc_users TO [your_oidc_user];

-- Drop the view if it already exists
IF OBJECT_ID('dbo.v_oidc_users', 'V') IS NOT NULL
    DROP VIEW dbo.v_oidc_users;
GO

-- Create a read-only view exposing only necessary authuser fields for OIDC
CREATE VIEW dbo.v_oidc_users AS
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
FROM dbo.authuser au
INNER JOIN dbo.resourceuser ru ON au.user_c = ru.id
WHERE au.validated = 1;  -- Only expose validated users to OIDC service (1 = true in MS SQL Server)
GO

-- Add extended property to the view for documentation
EXEC sp_addextendedproperty
    @name = N'MS_Description',
    @value = N'Read-only view of authuser and resourceuser tables for OIDC service access. Only includes validated users and returns user_id from resourceuser.userid_. WARNING: Includes password hash and salt for OIDC credential verification - ensure secure access.',
    @level0type = N'SCHEMA', @level0name = 'dbo',
    @level1type = N'VIEW', @level1name = 'v_oidc_users';
GO

-- Grant SELECT permission on the OIDC view
-- IMPORTANT: Replace 'oidc_user' with your actual OIDC database user/login name
-- Uncomment and modify the following line:
-- GRANT SELECT ON dbo.v_oidc_users TO [oidc_user];
-- GO

PRINT 'OIDC users view created successfully.';
GO
