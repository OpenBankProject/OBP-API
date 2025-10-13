THIS IS OBSOLETED BY THE SCIPTS IN sql/OIDC

--you might want to login as the oidc_user and try the two views you have access to.

-- =============================================================================
-- OBP-API OIDC User Setup Script
-- =============================================================================
-- This script creates a dedicated OIDC database user and provides read-only
-- access to the authuser table via a view.
--
-- ⚠️  SECURITY WARNING: This view exposes password hashes and salts!
-- Only run this script if you understand the security implications.
--
-- Prerequisites:
-- 1. Run this script as a PostgreSQL superuser or database owner
-- 2. Ensure the OBP database exists and authuser table is created
-- 3. Update the database connection parameters below as needed
-- 4. IMPORTANT: Review and implement additional security measures below
--
-- Required Security Measures:
-- 1. Use SSL/TLS encrypted connections to the database
-- 2. Restrict database access by IP address in pg_hba.conf
-- 3. Use a very strong password for the OIDC user
-- 4. Monitor and audit access to this view
-- 5. Consider regular password rotation for the OIDC user
--
-- Usage:
-- psql -h localhost -p 5432 -d your_obp_database -U your_admin_user -f create_oidc_user_and_views.sql

-- e.g.

-- psql -h localhost -p 5432 -d sandbox -U obp -f ~/Documents/workspace_2024/OBP-API-C/OBP-API/obp-api/src/main/scripts/sql/create_oidc_user_and_views.sql


--psql -d sandbox -f ~/Documents/workspace_2024/OBP-API-C/OBP-API/obp-api/src/main/scripts/sql/create_oidc_user_and_views.sql

-- If any difficulties see the TOP OF THIS FILE for step by step instructions.
-- =============================================================================

-- NOTE: Variable definitions and database connection have been moved to:
-- - OIDC/set_and_connect.sql
-- You can include them with: \i OIDC/set_and_connect.sql

-- =============================================================================
-- 1. Create OIDC user role
-- =============================================================================
-- NOTE: Database connection command has been moved to:
-- - OIDC/set_and_connect.sql
\echo 'Creating OIDC user role...'

-- NOTE: User creation commands have been moved to:
-- - OIDC/cre_OIDC_USER.sql
-- - OIDC/cre_OIDC_ADMIN_USER.sql
-- - OIDC/alter_OIDC_USER.sql
-- - OIDC/alter_OIDC_ADMIN_USER.sql

\echo 'OIDC users created successfully.'

-- =============================================================================
-- 3. Create read-only view for authuser table
-- =============================================================================
\echo 'Creating read-only view for OIDC access to authuser...'

-- NOTE: View creation commands have been moved to:
-- - OIDC/cre_v_oidc_users.sql

\echo 'OIDC users view created successfully.'

-- =============================================================================
-- 3b. Create read-only view for consumer table (OIDC clients)
-- =============================================================================
\echo 'Creating read-only view for OIDC access to consumers...'

-- NOTE: View creation commands have been moved to:
-- - OIDC/cre_v_oidc_clients.sql

\echo 'OIDC clients view created successfully.'

-- =============================================================================
-- 3c. Create read-write view for consumer table administration (OIDC clients admin)
-- =============================================================================
\echo 'Creating admin view for OIDC client management...'

-- NOTE: View creation commands have been moved to:
-- - OIDC/cre_v_oidc_admin_clients.sql

\echo 'OIDC admin clients view created successfully.'

-- =============================================================================
-- 4. Grant appropriate permissions to OIDC user
-- =============================================================================
\echo 'Granting permissions to OIDC user...'

-- NOTE: GRANT CONNECT and GRANT USAGE commands have been moved to:
-- - OIDC/cre_OIDC_USER.sql
-- - OIDC/cre_OIDC_ADMIN_USER.sql

-- NOTE: View-specific GRANT permissions have been moved to:
-- - OIDC/cre_v_oidc_users.sql
-- - OIDC/cre_v_oidc_clients.sql
-- - OIDC/cre_v_oidc_admin_clients.sql

-- Explicitly revoke any other permissions to ensure proper access control



-- NOTE: Final GRANT permissions have been moved to the view creation files:
-- - OIDC/cre_v_oidc_users.sql
-- - OIDC/cre_v_oidc_clients.sql
-- - OIDC/cre_v_oidc_admin_clients.sql

\echo 'Permissions granted successfully.'

-- =============================================================================
-- 5. Create additional security measures
-- =============================================================================
\echo 'Implementing additional security measures...'





\echo 'Security measures implemented successfully.'

-- =============================================================================
-- 6. Verify the setup
-- =============================================================================
\echo 'Verifying OIDC setup...'

-- Check if users exist
SELECT 'OIDC User exists: ' || CASE WHEN EXISTS (
    SELECT 1 FROM pg_user WHERE usename = :'OIDC_USER'
) THEN 'YES' ELSE 'NO' END AS oidc_user_check;

SELECT 'OIDC Admin User exists: ' || CASE WHEN EXISTS (
    SELECT 1 FROM pg_user WHERE usename = :'OIDC_ADMIN_USER'
) THEN 'YES' ELSE 'NO' END AS oidc_admin_user_check;

-- Check if views exist and have data
SELECT 'Users view exists: ' || CASE WHEN EXISTS (
    SELECT 1 FROM information_schema.views
    WHERE table_name = 'v_oidc_users' AND table_schema = 'public'
) THEN 'YES' ELSE 'NO' END AS users_view_check;

SELECT 'Clients view exists: ' || CASE WHEN EXISTS (
    SELECT 1 FROM information_schema.views
    WHERE table_name = 'v_oidc_clients' AND table_schema = 'public'
) THEN 'YES' ELSE 'NO' END AS clients_view_check;

SELECT 'Admin clients view exists: ' || CASE WHEN EXISTS (
    SELECT 1 FROM information_schema.views
    WHERE table_name = 'v_oidc_admin_clients' AND table_schema = 'public'
) THEN 'YES' ELSE 'NO' END AS admin_clients_view_check;

-- Show row counts in the views (if accessible)
SELECT 'Validated users count: ' || COUNT(*) AS user_count
FROM v_oidc_users;

SELECT 'Active clients count: ' || COUNT(*) AS client_count
FROM v_oidc_clients;

SELECT 'Total clients count (admin view): ' || COUNT(*) AS total_client_count
FROM v_oidc_admin_clients;

-- Display the permissions granted to OIDC users
SELECT 'OIDC_USER permissions:' AS permission_info;
SELECT
    table_schema,
    table_name,
    privilege_type,
    is_grantable
FROM information_schema.role_table_grants
WHERE grantee = :'OIDC_USER'
ORDER BY table_schema, table_name;

SELECT 'OIDC_ADMIN_USER permissions:' AS permission_info;
SELECT
    table_schema,
    table_name,
    privilege_type,
    is_grantable
FROM information_schema.role_table_grants
WHERE grantee = :'OIDC_ADMIN_USER'
ORDER BY table_schema, table_name;


\echo 'Here are the views:'


\d v_oidc_users;

\d v_oidc_clients;

\d v_oidc_admin_clients;



-- =============================================================================
-- 7. Display connection information
-- =============================================================================
\echo ''
\echo '====================================================================='
\echo 'OIDC User Setup Complete!'
\echo '====================================================================='
\echo ''
\echo 'Connection details for your OIDC service:'
\echo 'Database Host: ' :DB_HOST
\echo 'Database Port: ' :DB_PORT
\echo 'Database Name: ' :DB_NAME
\echo ''
\echo 'OIDC User (read-only):'
\echo 'Username: ' :OIDC_USER
\echo 'Password: [REDACTED - check script variables]'
\echo 'Available views: v_oidc_users, v_oidc_clients'
\echo 'Permissions: SELECT only (read-only access)'
\echo ''
\echo 'OIDC Admin User (full CRUD for client management):'
\echo 'Username: ' :OIDC_ADMIN_USER
\echo 'Password: [REDACTED - check script variables]'
\echo 'Available views: v_oidc_admin_clients'
\echo 'Permissions: SELECT, INSERT, UPDATE, DELETE (full CRUD access)'
\echo ''
\echo 'Test connection commands:'
\echo '# OIDC User (read-only):'
\echo 'psql -h ' :DB_HOST ' -p ' :DB_PORT ' -d ' :DB_NAME ' -U ' :OIDC_USER ' -c "SELECT COUNT(*) FROM v_oidc_users;"'
\echo 'psql -h ' :DB_HOST ' -p ' :DB_PORT ' -d ' :DB_NAME ' -U ' :OIDC_USER ' -c "SELECT COUNT(*) FROM v_oidc_clients;"'
\echo '# OIDC Admin User (full CRUD):'
\echo 'psql -h ' :DB_HOST ' -p ' :DB_PORT ' -d ' :DB_NAME ' -U ' :OIDC_ADMIN_USER ' -c "SELECT COUNT(*) FROM v_oidc_admin_clients;"'
\echo ''
\echo '====================================================================='
\echo '⚠️  CRITICAL SECURITY WARNINGS ⚠️'
\echo '====================================================================='
\echo 'This view exposes PASSWORD HASHES AND SALTS - implement these measures:'
\echo ''
\echo '1. DATABASE CONNECTION SECURITY:'
\echo '   - Configure SSL/TLS encryption in postgresql.conf'
\echo '   - Add "sslmode=require" to OIDC service connection string'
\echo '   - Use certificate-based authentication if possible'
\echo ''
\echo '2. ACCESS CONTROL:'
\echo '   - Restrict access by IP in pg_hba.conf:'
\echo '     "hostssl dbname oidc_user your.oidc.server.ip/32 md5"'
\echo '   - Use firewall rules to limit database port (5432) access'
\echo ''
\echo '3. MONITORING & AUDITING:'
\echo '   - Enable PostgreSQL query logging'
\echo '   - Monitor failed login attempts'
\echo '   - Set up alerts for unusual access patterns'
\echo '   - Regularly review access logs'
\echo ''
\echo '4. PASSWORD SECURITY:'
\echo '   - Use a strong password for oidc_user (min 20 chars, mixed case, symbols)'
\echo '   - Rotate the password regularly (e.g., quarterly)'
\echo '   - Store password securely (vault/secrets manager)'
\echo ''
\echo '5. ADDITIONAL RECOMMENDATIONS:'
\echo '   - Consider using connection pooling with authentication'
\echo '   - Implement rate limiting on the OIDC service side'
\echo '   - Use read-only database replicas if possible'
\echo '   - Regular security audits of database access'
\echo ''
\echo 'BASIC INFO:'
\echo '- OIDC_USER: Read-only access to validated authuser records and active clients'
\echo '- OIDC_ADMIN_USER: Full CRUD access to all client records for administration'
\echo '- OIDC_USER connection limit: 10 concurrent connections'
\echo '- OIDC_ADMIN_USER connection limit: 5 concurrent connections'
\echo '- Client view uses hardcoded grant_types and scopes (consider adding to schema)'

\echo ''
\echo '====================================================================='

-- =============================================================================
-- End of script
-- =============================================================================
