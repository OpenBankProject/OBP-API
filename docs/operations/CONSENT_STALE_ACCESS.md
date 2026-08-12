# Runbook: "could not revoke ... which consent ... no longer declares"

## The log line

```
WARN code.api.util.Consent$ -- grantAccessToViews: could not revoke owner on gh.29.uk/1
from user e326edaf-9783-4fe8-8d87-4a65115defdf, which consent 84d5a583-cad9-4f5c-989b-b874d13b877a
no longer declares. The access is still held: Failure(access cannot be revoked)
```

Emitted from [`ConsentUtil.scala:455`](../../obp-api/src/main/scala/code/api/util/ConsentUtil.scala#L455).

## What it means

Every time a consent is used, `grantAccessToViews`
([`ConsentUtil.scala:419`](../../obp-api/src/main/scala/code/api/util/ConsentUtil.scala#L419))
reconciles the consent's shadow user against the views the consent's JWT declares: it grants what is
missing and revokes what is no longer declared.

This line means the revoke half failed. **The access named in the message is still in place**, and
the request was served anyway.

**It does not self-heal.** Every subsequent use of the consent will retry the same revoke, fail the
same way, and log the same line. The row stays until someone removes it.

## Why the request still succeeds

This is deliberate, not an oversight. Failing the request would make the consent unusable
altogether — including every view it still legitimately holds — and leave no way back except
editing rows by hand.

The two failure directions are not symmetric:

| | effect | who notices |
|---|---|---|
| a **grant** fails | the consent is denied access it asked for | the caller, immediately (the request fails) |
| a **revoke** fails | the consent keeps access it gave up | nobody — until this log line existed |

So the request is served and the discrepancy is recorded. The cost is real: **the un-revoked access
keeps serving data the consent does not cover.** In the reproduction for this behaviour, a consent
naming one account returned a *different* account in `/my/accounts`, purely on the strength of the
stale row. Treat the WARN as live over-exposure, not as a cosmetic inconsistency.

## Why the revoke was refused

`revokeAccess` ([`MapperViews.scala:270`](../../obp-api/src/main/scala/code/views/MapperViews.scala#L270))
delegates to `canRevokeOwnerAccess`
([`MapperViews.scala:405`](../../obp-api/src/main/scala/code/views/MapperViews.scala#L405)), which
refuses in exactly two cases, and **only for the `owner` view**:

1. the user is an account holder on that account (`MapperAccountHolders`), or
2. that `AccountAccess` row is the **only** `owner` row on the account —
   `findAllByBankIdAccountIdViewId(...).length > 1` is false

Any other view id returns `true` unconditionally, so a WARN naming a view other than `owner` means
something outside these two rules failed — read the `Failure(...)` text at the end of the line
rather than following this runbook.

### Case 2 is a signal about the account, not just the consent

A shadow user is never an account holder, so in practice you are looking at case 2. And case 2 says
something stronger than "this consent is stuck":

> **Nobody else holds `owner` on that account — not even the PSU.**

A healthy account has the PSU's own `owner` row, which makes the count 2 and lets the revoke
succeed. If a consent's shadow user is the *sole* `owner` holder, the account has lost its real
owner access. **Investigate that before deleting anything**, or you will clear the symptom and leave
an account nobody owns.

## Diagnosis

Substitute the consent id, user id, bank and account from the log line.

```sql
-- 1. The offending row. Confirm it exists and is the one named.
SELECT aa.id, aa.bank_id, aa.account_id, aa.view_id, aa.consumer_id, aa.createdat
FROM accountaccess aa
JOIN resourceuser ru ON ru.id = aa.user_fk
WHERE ru.userid_ = '<user id from the log>'
  AND aa.bank_id = '<bank>' AND aa.account_id = '<account>' AND aa.view_id = 'owner';

-- 2. Who else holds owner on this account? An empty result besides row 1 is case 2,
--    and is the finding that matters.
SELECT aa.id, ru.userid_, ru.createdbyconsentid, aa.consumer_id
FROM accountaccess aa
JOIN resourceuser ru ON ru.id = aa.user_fk
WHERE aa.bank_id = '<bank>' AND aa.account_id = '<account>' AND aa.view_id = 'owner';

-- 3. Is this account holder-less too? (the deeper problem, if row 2 came back thin)
SELECT user_c FROM mapperaccountholders
WHERE accountbankpermalink = '<bank>' AND accountpermalink = '<account>';

-- 4. State of the consent itself. A REVOKED/EXPIRED consent whose row survived is a
--    different fault -- see "If the consent is already gone" below.
SELECT mconsentid, mstatus, mvaliduntil, mlastactiondate
FROM mappedconsent WHERE mconsentid = '<consent id from the log>';

-- 5. Confirm the user really is this consent's shadow user and nothing else.
--    createdbyconsentid must equal the consent in the log; a shadow user is 1:1 with its consent.
SELECT id, userid_, provider_, providerid, createdbyconsentid
FROM resourceuser WHERE userid_ = '<user id from the log>';
```

## Resolution

**Preferred — have the PSU re-authorise.** A fresh consent creates a fresh shadow user, so the stale
row is orphaned rather than reused. This does not remove the row; it stops it being reachable
through a live consent. Follow with the cleanup below.

**If query 3 showed the account has no holder / no other owner:** fix that first. Restoring the
PSU's own `owner` access makes the count exceed 1, at which point the *next use of the consent
revokes the stale row on its own* and the WARN stops without any manual deletion. This is the only
resolution that lets the code finish its own job — prefer it whenever the account is genuinely
missing its owner.

**Manual removal**, when the two above do not apply. Take a backup first; there is no undo.

```sql
-- Verify exactly one row matches BEFORE deleting.
SELECT count(*) FROM accountaccess aa JOIN resourceuser ru ON ru.id = aa.user_fk
WHERE ru.userid_ = '<user id>' AND aa.bank_id = '<bank>'
  AND aa.account_id = '<account>' AND aa.view_id = 'owner';

DELETE FROM accountaccess
WHERE id IN (
  SELECT aa.id FROM accountaccess aa JOIN resourceuser ru ON ru.id = aa.user_fk
  WHERE ru.userid_ = '<user id>' AND aa.bank_id = '<bank>'
    AND aa.account_id = '<account>' AND aa.view_id = 'owner'
);
```

Delete by `id` from a verified `SELECT`. Do not delete by `user_fk` alone: a shadow user legitimately
holds the rows for every account the consent *does* name, and those are the consent's actual grants.

### If the consent is already revoked

There is a second, more serious line, from the sweep that runs when a consent is revoked:

```
WARN code.api.util.Consent$ -- revokeConsentAccountAccess: could not revoke owner on gh.29.uk/1
for revoked consent 84d5a583-…. The access outlives the consent: Failure(access cannot be revoked)
```

`revokeConsentAccountAccess`
([`ConsentUtil.scala:920`](../../obp-api/src/main/scala/code/api/util/ConsentUtil.scala#L920)) goes
through `revokeAccessToViewForUserAndConsumer`, which applies the same `canRevokeOwnerAccess` rule,
so it can be refused for exactly the reasons above.

**Treat this as higher priority than the `grantAccessToViews` line.** In that case a live consent is
over-serving, and revoking the consent would still clean up. Here the consent is *already gone* and
the access it created has outlived it — nothing in the system will come back for that row. Manual
removal is the only resolution; the "have the PSU re-authorise" option does not apply.

> **Note on the neighbouring info line.** `revokeConsentAccountAccess: dropped N account access rows`
> counts successful revokes. Before the fix that added the WARN above, it counted *attempts*, so on
> an affected server it reported rows as dropped that were still present. If you are triaging on a
> build that predates it, do not take that count as evidence the rows are gone — check the table.

## Verification

```sql
-- The row is gone.
SELECT count(*) FROM accountaccess aa JOIN resourceuser ru ON ru.id = aa.user_fk
WHERE ru.userid_ = '<user id>' AND aa.bank_id = '<bank>' AND aa.account_id = '<account>';
```

Then use the consent once and confirm no new WARN appears with that consent id, and that the
account named in the original log line is **absent** from the consent's account listing. The second
check is the one that matters: it is the over-exposure closing, not merely the log going quiet.

## Monitoring

Alert on the string `could not revoke` from `code.api.util.Consent$`. Every occurrence is a
consent serving data it does not declare, so this warrants a ticket rather than a dashboard counter.
Because each use of a stuck consent re-logs it, alert on *distinct* consent ids rather than raw line
count — one stuck consent under load produces a large number of identical lines.
