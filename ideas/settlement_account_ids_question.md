# For Marko — Settlement account IDs should become UUIDs

## TL;DR

Default settlement accounts are created with **hardcoded, non-UUID, globally-non-unique** `account_id` strings:

- `INCOMING_SETTLEMENT_ACCOUNT_ID = "OBP-INCOMING-SETTLEMENT-ACCOUNT"`
- `OUTGOING_SETTLEMENT_ACCOUNT_ID = "OBP-OUTGOING-SETTLEMENT-ACCOUNT"`

Every bank gets the **same** two strings as `account_id`. In a deployment with N banks, there are 2N rows in `mappedbankaccount` where the `account_id` collides across banks. The `(bank_id, account_id)` unique constraint is satisfied (different bank per row), but **globally on `account_id` alone, the strings collide**.

This violates the `Account.account_id` glossary contract ("MUST be a UUID") and undermines the federation-safety claim that justifies `(OBP_ACCOUNT_ID, account_id)` being a globally-unique routing pair.

## Why it matters

The whole reason `(OBP_ACCOUNT_ID, account_id)` is supposed to be a safe federated routing identifier is "account_ids are UUIDs, so collision probability ≈ 0." Settlement accounts demonstrably collide across banks. Any cross-instance routing logic that traverses a settlement account would need to carry bank context out-of-band, which defeats the point.

For now this is bounded — settlement accounts are internal plumbing, not user-facing routable accounts — but the carve-out should be documented and ideally closed.

## Where the literal IDs are referenced (today)

Audit run 2026-05-21. 45 hits across 7 files.

### Production code (3 files)

#### `obp-api/src/main/scala/bootstrap/liftweb/Boot.scala:820-865`

`createDefaultBankAndDefaultAccountsIfNotExisting` — creates the default sandbox bank's settlement pair on every API boot. Two hits.

#### `obp-api/src/main/scala/code/bankconnectors/LocalMappedConnector.scala` — 20 hits

**Creation (`:3411-3441`)** — per-bank: looks for each settlement account by `(bankId, theAccountId=literal)`, creates if missing. 8 hits. Runs whenever a bank is created via the local connector.

**Lookup** in two near-duplicate functions:

- `saveDoubleEntryBookTransactionByCounterparty` (`:2264-2310`)
- A second function with identical structure (`:2421-2466`)

Each function performs a **three-tier cascade**:

```scala
// Tier 1 — payment-system + currency (most specific, manually created)
BankAccountX(bankId, AccountId(transactionRequestType + "_SETTLEMENT_ACCOUNT_" + currency), ...)
// Tier 2 — currency-only (manually created)
.or(BankAccountX(bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + currency), ...))
// Tier 3 — global fallback (auto-created, the literal constant)
.or(BankAccountX(bankId, AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), ...))
```

After lookup, each function uses a **discrimination check** to know whether it landed on the global fallback (and therefore needs FX conversion because the global fallback is EUR-only):

```scala
if (settlementAccount._1.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != fromAccount.currency)
```

This check (4 occurrences across the two functions) is the **only place** the literal constant value is operationally used after lookup — and it's used purely to detect "we hit the fallback."

The discrimination check is the structural reason settlement accounts can't be cleanly migrated by just changing the literal: the lookup code needs *some* signal to detect tier-3-fallback. Today it's string equality. Going forward it should be a property on the account (e.g. a `is_default_settlement_fallback` flag, or a `SettlementTier` column in a dedicated table).

Plus 4 error-message hits at lines 2309, 2310, 2465, 2466 — diagnostic only, easy to update.

#### `obp-api/src/main/scala/code/api/util/migration/MigrationOfSettlementAccounts.scala`

Retroactive migration. ~10 hits. For every existing bank found in `MappedBank`, ensure the two literal-id settlement accounts exist. Already executed on existing deployments — historical.

### Test code (2 files)

- `obp-api/src/test/scala/code/api/v4_0_0/BankTests.scala`
- `obp-api/src/test/scala/code/api/v5_0_0/BankTests.scala`

Both assert that bank creation produces these settlement accounts. Tests will need updating in lockstep with whichever migration approach is chosen.

### Docs / definition

- `obp-api/src/main/scala/code/api/constant/constant.scala:247-248` — the `final val` definitions themselves.
- `obp-api/src/main/scala/code/api/util/Glossary.scala` — passing reference.

## What the lookup actually depends on

The lookup logic doesn't care about the strings per se — it cares about a **classifier**: given `(bank, direction, transactionRequestType, currency)`, which account is the settlement target? Today the classifier is encoded into the `account_id` string via three naming conventions. If we move the classifier out of the `account_id`, the `account_id` is free to be a UUID.

Also worth noting: the `kind` column on `MappedBankAccount` is set to `"SETTLEMENT"` for these accounts but **is never queried**. It exists as metadata only. We could lean on it as part of any new lookup.

## Recommended approach — lower-disruption variant

Avoid renaming existing settlement accounts (which would require cascading FK updates across transactions, account_attributes, views, account_access rows, etc.). Instead, **stop creating new non-UUID settlement accounts** and introduce a proper lookup mechanism that works for both legacy and new accounts.

### PR 1 — Introduce dedicated lookup, switch new banks to UUIDs

1. **New table `MappedBankSettlementAccount`**:

   | column | type | note |
   |---|---|---|
   | `bank_id` | string | FK to `mappedbank.permalink` |
   | `direction` | enum `INCOMING` / `OUTGOING` | required |
   | `tx_type` | string nullable | e.g. `SEPA`, null = any |
   | `currency` | string nullable | ISO 4217, null = any |
   | `account_id` | string | FK to `mappedbankaccount.theaccountid` |
   | `tier` | int | 1=most-specific, 3=global fallback. Indexed. |

   Unique index on `(bank_id, direction, tx_type, currency)`.

2. **Lookup function** in a new `code.bankconnectors.settlement` (or extension of `LocalMappedConnector`):

   ```scala
   def findSettlementAccount(
     bankId: BankId,
     direction: SettlementDirection,
     txType: Option[String],
     currency: Option[String]
   ): Option[(BankAccount, SettlementTier)]
   ```

   Returns the highest-tier matching row. `SettlementTier` lets callers know whether they hit the global fallback (replacing the current `accountId.value == CONSTANT` check).

3. **New bank creation** (in `LocalMappedConnector.scala:3411-3441` and `Boot.scala:820-865`):
   - Mint UUIDs for the two settlement accounts.
   - Record them in `MappedBankSettlementAccount` as `(direction, tx_type=null, currency=null, tier=3)`.

4. **Refactor the two cascade sites** in `LocalMappedConnector.scala:2264-2306` and `:2421-2462`:
   - Replace the `.or` cascade with a single `findSettlementAccount(...)` call.
   - Replace the `settlementAccount._1.accountId.value == CONSTANT` discrimination with `tier == SettlementTier.GlobalFallback`.

5. **Lookup fallback**: if `findSettlementAccount` finds nothing, fall back to the legacy string-name lookup. This preserves all existing behaviour for unmigrated banks.

6. **Tests**: assertions stay valid for legacy banks; add new ones for the UUID path.

### PR 2 (optional) — Backfill `MappedBankSettlementAccount` for legacy banks

Migration script:

1. For each existing bank, find all accounts with `kind="SETTLEMENT"`.
2. Decode each account's literal `account_id` to determine `(direction, tx_type, currency, tier)`:
   - `OBP-INCOMING-SETTLEMENT-ACCOUNT` → `(INCOMING, null, null, 3)`
   - `OBP-OUTGOING-SETTLEMENT-ACCOUNT` → `(OUTGOING, null, null, 3)`
   - `DEFAULT_SETTLEMENT_ACCOUNT_<CCY>` → `(direction=both? or inferred?, null, <CCY>, 2)`
   - `<TXTYPE>_SETTLEMENT_ACCOUNT_<CCY>` → `(direction=both? or inferred?, <TXTYPE>, <CCY>, 1)`
3. Insert rows into `MappedBankSettlementAccount`.
4. Leave the underlying `mappedbankaccount.theaccountid` values alone.

After PR 2, every settlement account is reachable both via legacy string lookup and via the new table. PR 1's "fallback to legacy" code path effectively becomes dead but stays for safety.

**Important decision point for PR 2**: tier 1 and tier 2 settlement accounts (`<TXTYPE>_SETTLEMENT_ACCOUNT_<CCY>` and `DEFAULT_SETTLEMENT_ACCOUNT_<CCY>`) carry no encoded direction in their literal name — the existing code uses the same account_id for both incoming and outgoing in the tier-1/2 paths. This needs clarification before the backfill: is a single account intended to handle both directions, or is the absence of `INCOMING`/`OUTGOING` in those names a latent bug?

### PR 3 (optional, far future) — Rename legacy settlement account_ids to UUIDs

Only after PR 1 + PR 2 are stable and the legacy-string lookup path is confirmed unused.

1. For each `MappedBankSettlementAccount` row pointing at a non-UUID `account_id`, mint a UUID.
2. Cascade-update every FK reference: `mappedtransaction`, `mappedaccountattribute`, `viewdefinition`, `accountaccess`, anything else. **Enumerate carefully**.
3. Update `mappedbankaccount.theaccountid` to the UUID.
4. Update `MappedBankSettlementAccount.account_id` to the UUID.
5. Remove the literal constants from `constant.scala`. Compile errors flush out any remaining references.

This is the scary one. Possibly never worth doing — the lookup-table indirection means legacy account_ids are an internal implementation detail that nobody outside settlement code needs to know about.

## Decisions needed before starting

- **Tier-1 / tier-2 direction**: do `SEPA_SETTLEMENT_ACCOUNT_USD` etc. need separate INCOMING / OUTGOING entries, or is one account meant to handle both? (Audit production data, or ask whoever set this up.)
- **Boot.scala vs LocalMappedConnector.scala duplication**: `Boot.scala:820-865` creates default-bank settlement accounts; `LocalMappedConnector.scala:3411-3441` does the same on per-bank creation. They duplicate logic. Worth consolidating into a single helper as part of PR 1.
- **The two `LocalMappedConnector` cascade functions** (`:2264-2306` and `:2421-2462`) look near-identical. Worth deduplicating during the refactor.

## Out of scope for this work

- **The bank_id `<friendly>-<UUID>` convention** discussed separately — settlement accounts live within a bank, so they ride on whatever bank_id shape the deployment has chosen. Tracked in `todo_account_id_uuid_enforcement.md`.
- **Validating UUID account_ids at the API boundary** — separate workstream tracked in `todo_account_id_uuid_enforcement.md`.
- **Settlement account creation via the public API** — currently settlement accounts are only created by Boot/migration/connector init. If/when the API surface admits user-driven settlement-account creation, the lookup table needs an API too.

## File-by-file checklist (for whoever picks this up)

- [ ] `obp-commons/src/main/scala/com/openbankproject/commons/model/` — new `SettlementDirection` and `SettlementTier` enums
- [ ] `obp-api/src/main/scala/code/bankconnectors/settlement/MappedBankSettlementAccount.scala` — new Mapper
- [ ] `obp-api/src/main/scala/code/api/util/migration/MigrationOfBankSettlementAccountTable.scala` — DDL for the new table
- [ ] `obp-api/src/main/scala/code/bankconnectors/LocalMappedConnector.scala:2264-2310, 2421-2466` — refactor cascade
- [ ] `obp-api/src/main/scala/code/bankconnectors/LocalMappedConnector.scala:3411-3441` — mint UUIDs for new settlement accounts, record in the new table
- [ ] `obp-api/src/main/scala/bootstrap/liftweb/Boot.scala:820-865` — same change for default bank
- [ ] `obp-api/src/test/scala/code/api/v4_0_0/BankTests.scala` and `v5_0_0/BankTests.scala` — update assertions
- [ ] `obp-api/src/main/scala/code/api/util/Glossary.scala` — add a "Known exception" note to `Account.account_id` until/unless PR 3 happens
- [ ] (PR 2) Backfill migration
- [ ] (PR 3) Rename + FK cascade
