# A write-audit table: who changed what, including deletes

Written 2026-09-16. Not built. Companion to `ON_BEHALF_OF_USER_ID_PLAN.md`, which puts the *owner*
on the row; this is about putting the *history* somewhere.

## The gap

On-row attribution answers "whose is this?" — `MapperAccountHolders.user` says the human holds the
account, so every existing endpoint finds it. What it cannot answer:

- **Who actually did this?** Only for the tables that record both (`MappedTransactionRequest`,
  and now `MappedCounterparty`). Everywhere else the actor is recoverable only by correlating a
  timestamp against `MappedMetric`, which is a fuzzy join against a table with its own retention.
- **What did this consent do?** No user-id column can answer the reverse lookup. This is the
  question you ask when a consent turns out to have been compromised.
- **What was deleted?** Nothing. On-row columns die with the row. For a counterparty — the control
  on where money may be sent — deleting one is as security-relevant as creating it.

## It does not replace the on-row columns

Two different questions, and neither substitutes:

| | answers | how you query it |
|---|---|---|
| on-row `user_id` / `on_behalf_of_user_id` | whose is this, **now** | `WHERE` clause on the table itself |
| write-audit table | what happened, **when**, by whom | scan/replay by table + pk, or by consent |

Ownership reads must not require replaying a log, and audit tables get archived — there is already a
`MetricsArchiveScheduler` doing exactly that to metrics. Keep both.

## Shape

One append-only table:

```
obp_write_audit
  id                       bigserial
  table_name               text        -- from TG_TABLE_NAME
  row_pk                   text        -- the changed row's primary key
  operation                text        -- INSERT | UPDATE | DELETE
  authenticated_user_id    text        -- who made the call
  on_behalf_of_user_id     text        -- who they were acting for
  consent_reference_id     text        -- under which grant (null when no consent)
  changed_at               timestamptz
  row_data                 jsonb       -- optional; see "volume" below
```

`consent_reference_id` is the column that buys the reverse lookup, and it is the same key
`MappedMetric` already carries, so the two join cleanly.

## Approach A — Postgres trigger (Simon's preference; yes, it can be generic)

It genuinely can. Three Postgres features make one function serve every table:

- `TG_TABLE_NAME` / `TG_OP` — the trigger knows which table and which operation fired it
- `to_jsonb(NEW)` / `to_jsonb(OLD)` — captures the whole row **without knowing its columns**
- `current_setting('obp.x', true)` — reads a transaction-local variable; the `true` means "return
  NULL if unset" instead of raising

```sql
CREATE OR REPLACE FUNCTION obp_write_audit_fn() RETURNS trigger AS $$
BEGIN
  INSERT INTO obp_write_audit(
    table_name, row_pk, operation,
    authenticated_user_id, on_behalf_of_user_id, consent_reference_id,
    changed_at, row_data)
  VALUES (
    TG_TABLE_NAME,
    COALESCE(to_jsonb(NEW)->>'id', to_jsonb(OLD)->>'id'),
    TG_OP,
    current_setting('obp.authenticated_user_id', true),
    current_setting('obp.on_behalf_of_user_id',  true),
    current_setting('obp.consent_reference_id',  true),
    now(),
    CASE TG_OP WHEN 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END);
  RETURN NULL;
END; $$ LANGUAGE plpgsql;
```

Attaching it is a loop, not 200 hand-written statements:

```sql
DO $$ DECLARE t text; BEGIN
  FOR t IN SELECT table_name FROM information_schema.tables
           WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
             AND table_name <> 'obp_write_audit'
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS obp_audit_t ON %I', t);
    EXECUTE format('CREATE TRIGGER obp_audit_t AFTER INSERT OR UPDATE OR DELETE ON %I
                    FOR EACH ROW EXECUTE FUNCTION obp_write_audit_fn()', t);
  END LOOP;
END $$;
```

The application sets the variables once per request, at the point `withRequestTransaction` already
opens the transaction:

```sql
SELECT set_config('obp.authenticated_user_id', ?, true);  -- true = TRANSACTION-local
```

`is_local := true` matters: HikariCP hands the same physical connection to the next request, and a
session-scoped variable would leak the previous caller's identity into it. Transaction-local is
discarded at commit or rollback.

### What this buys

Total coverage. It catches writes that bypass the providers entirely — connector code, migrations,
schedulers, and anyone typing SQL into a console. No application path can forget it, which is the
failure mode of every approach that relies on a developer remembering.

### What it costs

1. **Postgres only.** `DBUtil.isSqlServer` exists, so this is not a Postgres-only product. Either
   audit is a Postgres-only feature, or a SQL Server equivalent gets written and maintained, or the
   app-level fallback covers other databases. This is the main argument against, and it needs an
   answer before starting.
2. **Re-attaching after Schemifier.** New tables arrive without the trigger, and if Schemifier ever
   recreates a table the trigger goes with it. The attach loop must run at boot, after Schemifier,
   and be idempotent — the `DROP TRIGGER IF EXISTS` above is what makes re-running safe.
3. **Recursion.** The audit table must be excluded, or every audit insert audits itself.
4. **Writes with no user** — Boot, the sandbox import, schedulers — log NULLs. That is correct and
   arguably useful: it distinguishes "system did this" from "someone did this".

## Approach B — Mapper lifecycle hooks

`beforeSave` / `afterDelete`, already used in three places (`MappedTransaction`, `UserAgreement`,
`ViewDefinition`). Database-agnostic, and the caller is reachable through the request scope that
`withRequestTransaction` establishes.

Misses anything that does not go through Lift Mapper: Doobie queries (`DoobieConsentQueries`,
`DoobieMetricsQueries`), raw `DBUtil.runQuery`, migrations. That is a real and growing set.

## Approach C — explicit calls in providers

Rejected. Same failure mode the `UserReference` ratchet exists to police: it depends on people
remembering, and the gap is invisible until someone looks.

## Synchronous, not async

The instinct is to make this async to avoid contention. I would not, at least not first.

**In-transaction**, the audit row commits or rolls back with the change it describes. The log
cannot claim something that did not happen, or miss something that did. That atomicity is the
property an auditor actually asks about, and a trigger gives it for free.

**Async** (the `MetricBatchWriter` pattern) is right for metrics, because metrics are observability
and losing a few rows is acceptable. An audit trail is not that class of thing: it can lose entries
on crash, and it can record events for transactions that later rolled back.

The contention worry is also mostly overstated for append-only inserts — Postgres handles high-rate
appends well. The real costs are storage growth and vacuum, and the standard answers are monthly
partitioning plus a retention policy, not asynchrony. Measure before trading atomicity away.

### Volume

`to_jsonb(NEW)` stores the entire row, which is the expensive part. Options, in increasing cost:
pk + operation only; pk + operation + changed columns; full row. Starting with the first and
turning on `row_data` for a named set of security-relevant tables is probably the right default.

## Scope

"Every write" is ~200 mappers. If the trigger approach is taken the loop covers them all at once,
which is an argument for it — but the *retention* question then applies to all of them too.

If starting narrower, the tables that authorise money movement or access are the ones where deletes
matter most: counterparties, transaction requests, account access, consents, entitlements, account
holders.

## Sequencing constraint

`MigrationOfConsentReferenceIdUuid` is in flight and is delete-and-replace with no backfill — the
counter becomes a UUID and existing values are not converted. Anything storing a consent reference
before that lands ends up holding dangling references. Wait for it.

## Open questions

- Postgres-only audit, or a SQL Server equivalent, or app-level fallback for other databases?
- Retention: how long, and does the archive follow the `MetricsArchiveScheduler` pattern?
- Is `row_data` on by default, or per-table?
- Does the audit table need to be tamper-evident (hash chain), or is append-only plus database
  permissions enough for the compliance story?
