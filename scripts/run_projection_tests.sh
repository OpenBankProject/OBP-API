#!/usr/bin/env bash
#
# run_projection_tests.sh — fast iteration on just the DE-indexing / projection test suites.
#
# Runs only:
#   - code.api.dynamic.entity.query.QuerySpec            (pure: parser / planner / executor)
#   - code.api.dynamic.entity.projection.ProjectionNamingSpec  (pure)
#   - code.api.dynamic.entity.projection.ProjectionSqlSpec     (pure: SQL generation)
#   - code.api.v6_0_0.ProjectionDataPlaneIntegrationTest (Postgres-only; cancels on H2)
#
# The integration test needs: test.default.props pointing at Postgres (db.driver/db.url) AND
# test.projection.postgres=true, plus a clean schema (this script does the DROP OWNED clean first).
# On H2 / no Postgres it simply cancels — the pure specs still run.
#
# Config via env (defaults match scripts/create_test_db.sh):
#   DB_NAME=obp_test_only DB_USER=obp_test_only DB_PASS=changeme DB_HOST=localhost DB_PORT=5432
#   SKIP_COMMONS_INSTALL=false   # set true to skip the obp-commons install (faster, only safe if
#                                # obp-commons is unchanged in ~/.m2)
#
set -uo pipefail   # not -e: we want to proceed even if the clean step warns

# Pin the JDK before any Maven work, exactly as the other runners do — the suite must
# run on the project's JDK, because a different one produces different results.
. "$(dirname "${BASH_SOURCE[0]}")/java_env.sh"

DB_NAME="${DB_NAME:-obp_test_only}"
DB_USER="${DB_USER:-obp_test_only}"
DB_PASS="${DB_PASS:-changeme}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
SKIP_COMMONS_INSTALL="${SKIP_COMMONS_INSTALL:-false}"

cd "$(dirname "$0")/.."

SUITES="code.api.dynamic.entity.query.QuerySpec,code.api.dynamic.entity.projection.ProjectionNamingSpec,code.api.dynamic.entity.projection.ProjectionSqlSpec,code.api.v6_0_0.ProjectionDataPlaneIntegrationTest"

# 1) Clean the Postgres test schema so the integration test's boot schemify doesn't abort. Tolerant.
if command -v psql >/dev/null 2>&1; then
  PG_URL="postgresql://${DB_USER}:${DB_PASS}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
  if psql "$PG_URL" -tAc "SELECT 1" >/dev/null 2>&1; then
    if psql "$PG_URL" -c "DROP OWNED BY ${DB_USER} CASCADE;" >/dev/null 2>&1; then
      echo "[OK] Cleaned Postgres test schema: ${DB_NAME}"
    else
      echo "[WARN] Could not clean Postgres test schema (continuing)"
    fi
  else
    echo "[info] Postgres test DB not reachable - the integration test will cancel; pure specs still run."
  fi
else
  echo "[info] psql not found - skipping Postgres clean; integration test cancels unless db.url is Postgres."
fi

# 2) Keep obp-commons fresh in ~/.m2 (tests resolve it from there, not target/classes).
if [ "$SKIP_COMMONS_INSTALL" != "true" ]; then
  echo "==> Installing obp-commons (skipTests)"
  mvn install -pl obp-commons -DskipTests -q || { echo "obp-commons install failed"; exit 1; }
fi

# 3) Run just the projection suites.
echo "==> Running projection test suites"
mvn test -pl obp-api -DwildcardSuites="$SUITES" -DfailIfNoTests=false
