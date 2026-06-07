#!/usr/bin/env bash
#
# create_test_db.sh — create a dedicated, wipe-safe Postgres role + database for the OBP-API test suite.
#
# WHY: the test suite defaults to in-memory H2, which can't run the Postgres-specific DDL used by
# the DE indexing projection (CREATE INDEX CONCURRENTLY, pg_extension) or PostGIS (spatial). To test
# those, point the suite at a real Postgres DB — using a DEDICATED role + database so it can never
# interfere with your dev/shared 'obp' role or 'sandbox' database.
#
# WARNING: the OBP test suite RESETS this database on every test class. Use a throwaway DB ONLY —
# never your dev/prod data.
#
# Idempotent: safe to re-run. Configure via env vars (defaults shown):
#   DB_NAME=obp_test_only  DB_USER=obp_test_only  DB_PASS=changeme  DB_HOST=localhost  DB_PORT=5432
#   WITH_POSTGIS=true       # set false to skip the PostGIS extension (Phase 4 only)
#   RESET_PASSWORD=false    # if the role already exists, set true to reset its password to DB_PASS
#                           # (safe ONLY because DB_USER is a dedicated test role — never point this
#                           #  at a shared role like 'obp', or you'll break other apps)
#   DROP_EXISTING=false     # set true to DROP and recreate the database from scratch (throwaway!)
#   PSQL_SUPER='sudo -u postgres psql'  # how to run psql as a superuser; override for non-peer auth,
#                                       # e.g. PSQL_SUPER='psql -h localhost -U postgres'
#
# Usage:  ./scripts/create_test_db.sh
#         DB_PASS=secret ./scripts/create_test_db.sh
#         DROP_EXISTING=true ./scripts/create_test_db.sh    # clean slate
#
set -euo pipefail

DB_NAME="${DB_NAME:-obp_test_only}"
DB_USER="${DB_USER:-obp_test_only}"   # DEDICATED test role — separate from the shared dev 'obp' role
DB_PASS="${DB_PASS:-changeme}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
WITH_POSTGIS="${WITH_POSTGIS:-true}"
RESET_PASSWORD="${RESET_PASSWORD:-false}"
DROP_EXISTING="${DROP_EXISTING:-false}"
PSQL_SUPER="${PSQL_SUPER:-sudo -u postgres psql}"

# Run from a world-readable dir so `sudo -u postgres psql` doesn't warn
# "could not change directory to ..." when the cwd isn't readable by the postgres OS user.
cd /tmp

echo "==> Ensuring dedicated test role '$DB_USER'"
# Create the role if missing. If it already exists, only reset its password when RESET_PASSWORD=true.
# (The shared-role-clobber safety: never silently change an existing role's password.)
$PSQL_SUPER -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN
    CREATE ROLE "$DB_USER" LOGIN PASSWORD '$DB_PASS';
    RAISE NOTICE 'Created role %.', '$DB_USER';
  ELSIF '$RESET_PASSWORD' = 'true' THEN
    ALTER ROLE "$DB_USER" LOGIN PASSWORD '$DB_PASS';
    RAISE NOTICE 'Role % exists — password RESET to DB_PASS (RESET_PASSWORD=true).', '$DB_USER';
  ELSE
    RAISE NOTICE 'Role % exists — password left unchanged (set RESET_PASSWORD=true to rotate).', '$DB_USER';
  END IF;
END
\$\$;
SQL

if [ "$DROP_EXISTING" = "true" ]; then
  echo "==> DROP_EXISTING=true — dropping database '$DB_NAME' if it exists"
  $PSQL_SUPER -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$DB_NAME\";"
fi

echo "==> Ensuring database '$DB_NAME' (owner '$DB_USER')"
if $PSQL_SUPER -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1; then
  echo "    exists — reassigning owner to '$DB_USER'"
  $PSQL_SUPER -v ON_ERROR_STOP=1 -c "ALTER DATABASE \"$DB_NAME\" OWNER TO \"$DB_USER\";"
else
  $PSQL_SUPER -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";"
fi

echo "==> Granting privileges (DB, public schema, and any pre-existing objects)"
$PSQL_SUPER -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE \"$DB_NAME\" TO \"$DB_USER\";"
$PSQL_SUPER -v ON_ERROR_STOP=1 -d "$DB_NAME" -c "GRANT ALL ON SCHEMA public TO \"$DB_USER\";"
$PSQL_SUPER -v ON_ERROR_STOP=1 -d "$DB_NAME" -c "GRANT ALL ON ALL TABLES IN SCHEMA public TO \"$DB_USER\";"
$PSQL_SUPER -v ON_ERROR_STOP=1 -d "$DB_NAME" -c "GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO \"$DB_USER\";"

if [ "$WITH_POSTGIS" = "true" ]; then
  echo "==> Enabling PostGIS extension (for spatial / Phase 4)"
  if ! $PSQL_SUPER -v ON_ERROR_STOP=1 -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;"; then
    echo "    WARNING: could not enable PostGIS — the OS package is probably missing."
    echo "             Install it, e.g.:  sudo apt-get install postgresql-<version>-postgis-3"
    echo "             then re-run, or set WITH_POSTGIS=false to skip (Phase 3 doesn't need it)."
  fi
fi

cat <<MSG

==> Done. Dedicated test role '$DB_USER' owns database '$DB_NAME' (your shared 'obp'/'sandbox' setup is untouched).

Point the TEST suite at it — set in obp-api/src/main/resources/props/test.default.props (do NOT commit):

    db.driver=org.postgresql.Driver
    db.url=jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?user=$DB_USER&password=$DB_PASS

Verify the connection:

    psql "postgresql://$DB_USER:$DB_PASS@$DB_HOST:$DB_PORT/$DB_NAME" -c "SELECT version();"
MSG
