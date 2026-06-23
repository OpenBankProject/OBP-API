# Database migrations — and a warning about SQL views

This package holds the OBP schema migrations (`MigrationOf*.scala`), run on boot by
`Migration.scala`. Each migration is `runOnce` (tracked by name in `MigrationScriptLog`, so it
executes exactly once per database).

A handful of these migrations create **SQL views**. Views carry a specific, recurring hazard that
everyone touching migrations needs to know about.

## The SQL views created here

| View (SQL object) | Created by | Read by app code? |
|---|---|---|
| `v_account_access_with_views` | `MigrationOfAccountAccessWithViewsView` | **Yes** — `DoobieAccountAccessViewQueries` ← `MapperViews` (account access control) |
| `v_consent` | `MigrationOfConsentView` | **Yes** — `DoobieConsentQueries` (consent lookups, v3.1/v4.0/v5.1) |
| `v_metric` | `MigrationOfMetricView` | No — inspection/reporting only |

The first two are load-bearing: dropping them breaks live request paths. `v_metric` is a convenience
object for looking at the database and is not referenced by any code.

### Retired views (dropped)

| View (SQL object) | Created by | Dropped by |
|---|---|---|
| `v_fast_firehose_accounts` | `MigrationOfFastFireHoseView` | `MigrationOfDropFastFireHoseViews` |
| `mv_fast_firehose_accounts` | `MigrationOfFastFireHoseMaterializedView` | `MigrationOfDropFastFireHoseViews` |

The fast-firehose views have been retired (firehose → account directory + ABAC). The create
migrations still run for historical/`runOnce`-ordering reasons, but `MigrationOfDropFastFireHoseViews`
drops both objects afterwards (`DROP ... IF EXISTS ... CASCADE`, Postgres-only) — so they are not
present on a migrated database. Neither was ever read by application code.

## ⚠️ The hazard: a view pins the columns it references

Postgres refuses to change the type of a column a view depends on:

```
ERROR: cannot alter type of a column used by a view or rule
DETAIL: rule _RETURN on view <view> depends on column "<col>"
```

This fires whenever an `ALTER COLUMN ... TYPE` — from a migration here, or from Lift **Schemifier**
auto-matching a changed model field width on boot — targets a column that a view selects. It is the
recurring **"schema drift"** that aborts boot on long-lived databases. Note it triggers **even when
the type is unchanged** (e.g. re-running `ALTER ... TYPE varchar(100)` on a column already
`varchar(100)`), which is how it surfaces on log-wiping test resets.

## The rules (authoritative copy lives in `Migration.scala`)

The full rule is the scaladoc at the top of `object Migration` in `Migration.scala` — read it there.
In short:

1. **Never drop all views on boot/migrate.** In a multi-node deployment another node is live and
   querying these views; a global drop (even transient) errors the serving node.
2. **Prefer an idempotent `ALTER`.** If the column is already the target width, *skip* the alter — it
   then never conflicts with a view and no view needs touching. Guard with
   `DbFunction.columnMaxLength(table, column).contains(targetWidth)`. Worked example:
   `MigrationOfResourceUser.alterColumnEmail`.
3. **Only when the width/type genuinely changes** on a column a view pins: do `DROP VIEW <only the
   dependent view> → ALTER COLUMN → CREATE [OR REPLACE] VIEW` inside **one** `runOnce` migration, as a
   single statement/transaction (Postgres DDL is transactional, so other nodes never see the view
   missing). Don't edit/duplicate the original migration to repair an existing DB — `runOnce` skips a
   name already logged; add a **new-named** migration.

## When adding a column-altering migration

Check whether a view references the column first:

```sql
SELECT DISTINCT cl.relname AS dependent_view
FROM pg_attribute a
JOIN pg_class t  ON t.oid = a.attrelid AND t.relname = '<lower_table>'
JOIN pg_depend d ON d.refobjid = a.attrelid AND d.refobjsubid = a.attnum
JOIN pg_rewrite rw ON rw.oid = d.objid
JOIN pg_class cl ON cl.oid = rw.ev_class
WHERE a.attname = '<lower_column>';
```

If it returns a view, follow rule 2 (preferred) or rule 3.

## Recovering a database that has already drifted

Drop all objects in the schema and let Schemifier + migrations rebuild from scratch (Schemifier
`CREATE`s fresh, so no `ALTER` conflicts). Full procedure: `running_tests_on_postgres.md`.
