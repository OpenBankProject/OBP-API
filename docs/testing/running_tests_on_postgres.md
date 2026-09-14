# Running tests on PostgreSQL

How to run the OBP-API test suites against a local PostgreSQL test database, plus
the recurring schema-drift gotcha and how to recover from it.

## Test database

The test props (`obp-api/src/main/resources/props/test.default.props`) point at a
dedicated database:

```
db.url=jdbc:postgresql://localhost:5432/obp_test_only?user=obp_test_only&password=changeme
```

- This DB is **disposable** — `ServerSetupWithTestData` re-seeds the test fixtures
  on every run, so it is always safe to wipe its contents.
- On boot, Lift's **Schemifier** syncs the schema (CREATE TABLE / ALTER TABLE),
  then the **migration** scripts run (`code.api.util.migration.*`), which also
  create the SQL views (`v_consent`, `v_metric`, `v_fast_firehose_accounts`, …)
  and the materialized view `mv_fast_firehose_accounts`.

Connect manually with:

```sh
PGPASSWORD=changeme psql -h localhost -U obp_test_only -d obp_test_only
```

## Running tests

Always run from the repo root (Maven `-pl obp-api` fails with
"Could not find the selected project in the reactor" if the shell's working
directory has drifted into a subfolder).

```sh
# Compile main + test sources first (fast feedback on compile errors)
mvn test-compile -pl obp-api -am -q

# Run a single suite by fully-qualified class name
mvn test -pl obp-api -q -DwildcardSuites="code.api.v7_0_0.Http4s700RoutesTest"

# Run several suites (comma-separated FQCNs)
mvn test -pl obp-api -q \
  -DwildcardSuites="code.api.v7_0_0.Http4s700RoutesTest,code.api.v7_0_0.Http4s700TransactionTest"
```

### `-DwildcardSuites` gotchas

- From the shell, pass an **explicit comma-separated list of fully-qualified
  suite class names**. The bare package-prefix form
  (`-DwildcardSuites="code.api.v3_1_0"`) discovers **zero** tests locally — it
  only works inside the CI workflow's piped invocation.
- Generate a list by grepping each file for its declared class (filename ≠ class
  name in some files, e.g. `RefreshObpDateTest.scala` declares `RefreshUserTest`):

  ```sh
  grep -l '^class.*extends.*ServerSetup' obp-api/src/test/scala/code/api/v3_1_0/*.scala \
    | xargs -I{} grep -hoP '^class \K[A-Z][A-Za-z0-9_]+' {} \
    | sed 's/^/code.api.v3_1_0./' | tr '\n' ',' | sed 's/,$//'
  ```
- Add `-DfailIfNoTests=false` so an empty match doesn't fail the build.

### Reading results when output is large

`mvn -q` still prints ScalaTest's green `Feature:`/`Scenario:` lines and a final
summary. Useful greps against a captured log:

```sh
mvn test -pl obp-api -q -DwildcardSuites="..." > /tmp/run.log 2>&1
grep -E "Tests: succeeded|Run completed|All tests passed|ABORTED|did not equal" /tmp/run.log | tail
```

When a run has hundreds of failures, mine the per-suite XML instead of re-running:
`obp-api/target/surefire-reports/TEST-*.xml` (suites with `failures=`/`errors=` > 0;
per-case `<failure message="...">`; the element *text* holds the full stack trace
and the lift-json `MappingException` body dump).

## Prerequisites

- **PostgreSQL** running on `localhost:5432` with the `obp_test_only` role/database.
- **Redis** running on `127.0.0.1:6379` (boot does a startup health check).
- `hikari.maximumPoolSize` must be **≥ 20** in the test props for concurrent
  tests. `withRequestTransaction` holds one connection per request and rate-limit
  queries need a second — a pool of 10 exhausts at ~5 concurrent requests.

## Schema-drift gotcha (the big one)

### Symptom

A suite **aborts during boot**, before any test runs, with:

```
*** RUN ABORTED ***
  java.lang.ExceptionInInitializerError:
  at code.setup.ServerSetup.$init$(ServerSetup.scala:...)
  Cause: org.postgresql.util.PSQLException: ERROR: cannot alter type of a column used by a view or rule
  Detail: rule _RETURN on view v_metric depends on column "correlationid"
```

or a variant like:

```
  Cause: org.postgresql.util.PSQLException: ERROR: relation "mv_fast_firehose_accounts" already exists
```

### Why

Schemifier wants to `ALTER` a column (e.g. widen `metric.correlationid`,
`mappedconsent.mjsonwebtoken`), but a **view or materialized view created by a
migration depends on that column**, so Postgres refuses the alter. Because this
happens in `ServerSetup.$init$`, it aborts **every** suite in that DB — it is not
specific to whatever test you're trying to run, and it is **not** caused by your
code change.

Dropping views one at a time is whack-a-mole (drop `v_consent` → it next blocks on
`v_metric` → then `mv_fast_firehose_accounts` "already exists", etc.).

### Fix — reset the test DB schema

The clean, reliable fix is to drop **all** objects in the test DB's `public`
schema and let Schemifier + migrations rebuild from scratch. The `obp_test_only`
user is **not** the owner of schema `public` (so `DROP SCHEMA public` fails with
"must be owner of schema public"), but it does own the tables/views/sequences it
created — so drop those individually:

```sh
# 1. Generate DROP statements for every table, view, matview, and sequence
PGPASSWORD=changeme psql -h localhost -U obp_test_only -d obp_test_only -t -A <<'SQL' > /tmp/drops.sql
SELECT 'DROP MATERIALIZED VIEW IF EXISTS '||quote_ident(matviewname)||' CASCADE;' FROM pg_matviews WHERE schemaname='public'
UNION ALL
SELECT 'DROP VIEW IF EXISTS '||quote_ident(table_name)||' CASCADE;' FROM information_schema.views WHERE table_schema='public'
UNION ALL
SELECT 'DROP TABLE IF EXISTS '||quote_ident(tablename)||' CASCADE;' FROM pg_tables WHERE schemaname='public'
UNION ALL
SELECT 'DROP SEQUENCE IF EXISTS '||quote_ident(sequencename)||' CASCADE;' FROM pg_sequences WHERE schemaname='public';
SQL

# 2. Execute them
PGPASSWORD=changeme psql -h localhost -U obp_test_only -d obp_test_only -f /tmp/drops.sql

# 3. Confirm the schema is empty
PGPASSWORD=changeme psql -h localhost -U obp_test_only -d obp_test_only -t \
  -c "SELECT count(*) FROM pg_tables WHERE schemaname='public';"   # -> 0
```

(Most sequences are owned by their tables and get dropped by the `CASCADE` on the
table; the standalone `DROP SEQUENCE` lines then just print harmless
"does not exist, skipping" notices.)

Re-run the suite — Schemifier now CREATEs every table fresh (no ALTER conflicts)
and the migrations recreate the views, so boot succeeds.

> If you have superuser/owner access you can do the equivalent in one shot:
> `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` then re-grant. The
> per-object drops above are the fallback when running as `obp_test_only`.

### When does drift happen?

When the *expected* schema (model field widths/types, or migration-created views)
has changed since the test DB was last built, and the old objects are still
present. A fresh teammate's DB won't hit it; a long-lived local DB across many
branches will. The reset above is the cure regardless of which column/view trips
it.

> **Preventing it in new migrations:** see the "schema-drift / views" doc comment
> at the top of `code/api/util/migration/Migration.scala` — the rule lives next to
> the migration code, not here.
