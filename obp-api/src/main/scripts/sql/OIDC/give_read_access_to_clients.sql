-- Include variable definitions and database connection
\i set_and_connect.sql

-- Note we don't create the OIDC_USER here

-- Create the v_oidc_users view (which includes GRANT SELECT to OIDC_USER)
\i cre_v_oidc_clients.sql

\echo 'Bye from give_read_access_to_obp_clients.sql'
