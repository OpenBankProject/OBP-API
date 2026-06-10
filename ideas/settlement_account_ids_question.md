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

---

## As-is state and current issue (code-verified 2026-06-10)

### How settlement accounts work today

**What they are.** When OBP records a historical payment as a double-entry pair (`makeHistoricalPayment` / `savePayment` in `LocalMappedConnector.scala`), it needs a real account on each leg. If one side has **no corresponding OBP account** (e.g. an external counterparty), the connector debits/credits a stand-in **settlement account** on the relevant bank — an internal placeholder that absorbs that leg of the entry.

**Creation.** Every bank gets two settlement accounts:
- per-bank, on bank creation — `createOrUpdateBank` (`:3412-3442`): EUR, `kind="SETTLEMENT"`, account_ids = the two literals.
- the default sandbox bank, at boot — `createDefaultBankAndDefaultAccountsIfNotExisting` (`Boot.scala:625-647`): EUR, **but does not set `kind`**.
- retroactively for existing banks — `MigrationOfSettlementAccounts.scala`.

The two account_ids are hardcoded constants (`constant.scala:247-248`):
- `INCOMING_SETTLEMENT_ACCOUNT_ID = "OBP-INCOMING-SETTLEMENT-ACCOUNT"`
- `OUTGOING_SETTLEMENT_ACCOUNT_ID = "OBP-OUTGOING-SETTLEMENT-ACCOUNT"`

**Lookup — a 3-tier cascade keyed on the `account_id` string** (duplicated in `makeHistoricalPayment:2264-2294` and `savePayment:2421-2450`). For each leg:
1. **Tier 1** — `<TXTYPE>_SETTLEMENT_ACCOUNT_<CCY>` (most specific; manually created)
2. **Tier 2** — `DEFAULT_SETTLEMENT_ACCOUNT_<CCY>` (currency default; manually created)
3. **Tier 3** — the global EUR literal (`INCOMING_…`/`OUTGOING_…`; auto-created fallback)

**Direction is implicit in *which bank* is queried**, not in the name: the incoming leg looks up on `toAccount.bankId`, the outgoing leg on `fromAccount.bankId`. Only tier-3 uses direction-specific names.

**Tier-3 discrimination + FX.** Tier-3 accounts are EUR-only. After lookup, if it landed on the tier-3 fallback **and** the transaction currency ≠ EUR, the amount is FX-converted to EUR (`:2273-2276`, `:2298-2301`). That "did we hit the fallback?" decision is made by **string equality**: `settlementAccount.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID`.

**The `kind` column.** Set to `"SETTLEMENT"` on creation, and read in exactly one place — `getBankSettlementAccounts` (`:1513-1518`), a list endpoint. The cascade itself never consults `kind`; it relies entirely on the account_id string.

### Diagram 1 — where settlement accounts come in (the double-entry fallback)

```
makeHistoricalPayment(fromAccount, toAccount, amount, currency, txType)
│   records ONE payment as TWO half-entries (debit + credit)
│
├─ DEBIT leg ───────────────────────────────────────────────────────────┐
│    try:  saveHistoricalTransaction(fromAccount → toAccount)            │
│    └─ fromAccount has NO real OBP account?                             │
│         settlement = cascade(bank = toAccount.bankId,  ccy = from.ccy) │  ← INCOMING
│         if settlement is the tier-3 EUR fallback AND ccy ≠ EUR:        │
│              amount = FX.convert(amount → EUR)                         │
│         saveHistoricalTransaction(settlement → toAccount)             │
│                                                                        │
├─ CREDIT leg ──────────────────────────────────────────────────────────┤
│    try:  saveHistoricalTransaction(toAccount → fromAccount)           │
│    └─ toAccount has NO real OBP account?                               │
│         settlement = cascade(bank = fromAccount.bankId, ccy = to.ccy)  │  ← OUTGOING
│         if settlement is the tier-3 EUR fallback AND ccy ≠ EUR:        │
│              amount = FX.convert(amount → EUR)                         │
│         saveHistoricalTransaction(settlement → fromAccount)           │
└────────────────────────────────────────────────────────────────────────┘
       (identical logic is duplicated in savePayment)
```

### Diagram 2 — the lookup: a 3-tier cascade keyed on the `account_id` string

```
cascade(bankId, direction, ccy, txType):

   ┌── Tier 1 ──  BankAccountX(bankId, "<txType>_SETTLEMENT_ACCOUNT_<ccy>")   most specific
   │                  │ miss
   │                  ▼
   ├── Tier 2 ──  BankAccountX(bankId, "DEFAULT_SETTLEMENT_ACCOUNT_<ccy>")    currency default
   │                  │ miss
   │                  ▼
   └── Tier 3 ──  BankAccountX(bankId, INCOMING_/OUTGOING_SETTLEMENT_ID)      global fallback
                      (auto-created for EVERY bank · EUR-only · forces FX)

   direction is NOT in the name — it's which bank you query, plus the tier-3 id:
     INCOMING → bankId = toAccount.bankId    · tier-3 id = "OBP-INCOMING-SETTLEMENT-ACCOUNT"
     OUTGOING → bankId = fromAccount.bankId  · tier-3 id = "OBP-OUTGOING-SETTLEMENT-ACCOUNT"

   "did we hit tier-3?"  decided by STRING EQUALITY:
       settlementAccount.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID
```

### Diagram 3 — the `account_id` string is overloaded as a classifier

```
   SEPA_SETTLEMENT_ACCOUNT_USD          tier 1   ┌ txType = SEPA   ┌ ccy = USD
   └──┬─┘                └─┬─┘                    └ both present  → most specific
      txType              ccy

   DEFAULT_SETTLEMENT_ACCOUNT_USD       tier 2   ( ccy only        → currency default )

   OBP-INCOMING-SETTLEMENT-ACCOUNT      tier 3   ( direction in name, EUR, global )

   ⇒ tier + txType + currency + direction are all encoded INTO the id string,
     and the cascade + FX check read them back out of the string.
     This is why the id cannot become a UUID without first moving the classifier out.
```

### Diagram 4 — the actual problem: global collision across banks

```
  mappedbankaccount
  ┌────────────┬─────────────────────────────────────┬─────────────┐
  │ bank_id    │ account_id (theAccountId)            │ kind        │
  ├────────────┼─────────────────────────────────────┼─────────────┤
  │ bank.uk    │ OBP-INCOMING-SETTLEMENT-ACCOUNT   ◄─┐│ SETTLEMENT  │
  │ bank.uk    │ OBP-OUTGOING-SETTLEMENT-ACCOUNT   ◄┐││ SETTLEMENT  │
  │ bank.de    │ OBP-INCOMING-SETTLEMENT-ACCOUNT   ◄┼┼┤ SETTLEMENT  │ same two strings,
  │ bank.de    │ OBP-OUTGOING-SETTLEMENT-ACCOUNT   ◄┘││ SETTLEMENT  │ repeated per bank
  │ bank.fr    │ OBP-INCOMING-SETTLEMENT-ACCOUNT   ◄─┼┘ SETTLEMENT  │
  │ bank.fr    │ OBP-OUTGOING-SETTLEMENT-ACCOUNT   ◄─┘  SETTLEMENT  │
  │ …          │ …                                   │             │
  └────────────┴─────────────────────────────────────┴─────────────┘

   (bank_id, account_id)  UNIQUE  ✔  — one row per bank, constraint holds
    account_id  alone             ✘  — collides 2N times across N banks
                                       ⇒ violates "account_id MUST be a UUID"
                                       ⇒ breaks (OBP_ACCOUNT_ID, account_id) federation safety
```

### The issue, in one paragraph

The settlement `account_id`s are **hardcoded, non-UUID, and globally collide across banks** (2N colliding rows for N banks). The `(bank_id, account_id)` constraint still holds, but `account_id` alone is no longer unique — violating the glossary contract that `Account.account_id` MUST be a UUID, and breaking the federation-safety guarantee of the `(OBP_ACCOUNT_ID, account_id)` routing pair. It can't be trivially fixed because the `account_id` string is **overloaded as a classifier** (tier + txType + currency + direction encoded in the name, read back by the cascade and the FX check) — so the id can't become a UUID until that classifier is moved out. Secondary issues in the same area: the tier-3 detection is brittle string equality; `Boot` doesn't set `kind="SETTLEMENT"` on the default bank's settlement accounts (so they're invisible to `getBankSettlementAccounts`); and the full cascade is duplicated across two functions.

---

## Refined plan (code-verified 2026-06-10)

The original audit (below) was re-verified against the current `develop` (post-merge: upstream merges + the v7 UTILITY vend-result work + scheduler fix — **none of which touched any settlement code**). The strategy holds; the **dedicated lookup table** approach is chosen. This section records the corrections, resolves the one open design question, and locks the plan. **Plan-only — no code changed; for brainstorming.**

### Corrections to the audit (drift since 2026-05-21)

All settlement line numbers below were re-confirmed exact on 2026-06-10.

- The two cascade functions are **`makeHistoricalPayment`** (`LocalMappedConnector.scala:2227-2334`) and private **`savePayment`** (`:2385-2489`) — still two near-identical copies of the three-tier cascade. (The audit's `saveDoubleEntryBookTransactionByCounterparty` name is stale.)
- Discrimination checks live at `:2273`, `:2298` (in `makeHistoricalPayment`) and `:2429`, `:2454` (in `savePayment`).
- New-bank creation is **`createOrUpdateBank`** (`:3367-3445`), settlement block `:3412-3442`.
- Boot creation is **`createDefaultBankAndDefaultAccountsIfNotExisting`** (`Boot.scala:603-648`), settlement pair `:625-647`.
- Constant definitions: `constant.scala:247-248`. **43** references across **6** files (constant.scala, LocalMappedConnector.scala, Boot.scala, MigrationOfSettlementAccounts.scala, v4 BankTests.scala, v5 BankTests.scala).
- **The `kind` column IS queried** (the audit's "never queried" is stale): `getBankSettlementAccounts` (`LocalMappedConnector.scala:1513-1518`) filters `By(MappedBankAccount.kind, "SETTLEMENT")`. So `kind` is already load-bearing on a read path and can be leaned on.
- **Latent bug to fix in passing**: `Boot.scala`'s default-bank settlement accounts do **not** set `kind="SETTLEMENT"` (lines 631/643 set only `theAccountId`), whereas per-bank `createOrUpdateBank` (`:3420`, `:3436`) and the migration both do. So the default bank's settlement accounts are invisible to `getBankSettlementAccounts`. The consolidated creation helper in PR 1 fixes this.

### Resolved: the "tier-1/2 direction" question

The audit flagged this as undecided. The code answers it: **direction is encoded by which bank's account is queried, not by the name.** Incoming → settlement account on `toAccount.bankId`; outgoing → on `fromAccount.bankId`. Only **tier-3** uses direction-specific literal names (`INCOMING_…`/`OUTGOING_…`). Therefore a single bank's `DEFAULT_SETTLEMENT_ACCOUNT_<CCY>` (tier-2) or `<TX>_SETTLEMENT_ACCOUNT_<CCY>` (tier-1) serves **both** directions intentionally.

**Design consequence:** in the new table, **tier-1/2 rows are direction-agnostic (`direction = NULL`); only tier-3 rows carry a direction.** The lookup for direction `D` matches rows where `direction = D OR direction IS NULL`, most-specific tier winning.

### Locked design — dedicated lookup table

```
MappedBankSettlementAccount
  bank_id   direction  tx_type  currency  account_id(UUID)  tier
  b.uk      INCOMING   null     null      9f2c-…             3
  b.uk      OUTGOING   null     null      a17e-…             3
  b.uk      null       SEPA     USD       c40b-…             1
lookup(bankId, direction=D, txType, currency):
  match rows where direction = D OR direction IS NULL,
  return the highest-specificity (lowest tier number) match + its SettlementTier
```

The table is an index/classifier over existing `MappedBankAccount` rows; it frees `account_id` to be a UUID. The returned `tier` replaces the `accountId.value == CONSTANT` discrimination check.

### PR 1 — lookup table + UUIDs for new banks (the real fix)

1. **`obp-commons`**: add `SettlementDirection { INCOMING, OUTGOING }` and `SettlementTier { Specific=1, CurrencyDefault=2, GlobalFallback=3 }` enums (same mechanism as the `UTILITY` enum addition in `TransactionRequestTypes`).
2. **New mapper** `code.bankconnectors.settlement.MappedBankSettlementAccount` with columns `(bank_id, direction nullable, tx_type nullable, currency nullable, account_id, tier)`, unique index `(bank_id, direction, tx_type, currency)`; register in `Boot` schemify; add `MigrationOfBankSettlementAccountTable` DDL.
3. **`findSettlementAccount(bankId, direction, txType, currency): Option[(BankAccount, SettlementTier)]`** — most-specific match, `direction = D OR NULL`.
4. **Consolidate creation** (`createOrUpdateBank` + `Boot`) into one helper: mint **UUID** account_ids, set `kind="SETTLEMENT"` (fixes the Boot bug), write two tier-3 rows (one per direction).
5. **Deduplicate + refactor** `makeHistoricalPayment` and `savePayment` into one shared cascade helper that calls `findSettlementAccount`, replacing the discrimination check with `tier == GlobalFallback`. **Keep the legacy string cascade as a fallback** when the table has no row → zero behaviour change for unmigrated banks.
6. **Tests**: existing v4/v5 `BankTests` assertions still pass for legacy banks via fallback; add UUID-path assertions.

Federation-safety is satisfied for all **new** deployments at the end of PR 1.

### PR 2 — backfill table for legacy banks (optional, low risk)

For each `kind="SETTLEMENT"` account, decode its literal id → table row; **tier-1/2 rows written with `direction = NULL`** per the resolved question above. Underlying `mappedbankaccount.theaccountid` untouched. After this, every settlement account is reachable via the table and PR 1's legacy-fallback path becomes effectively dead (kept for safety). Federation-safety then holds for existing banks too.

### PR 3 — rename legacy account_ids to UUIDs (far future, high risk)

FK cascade across `mappedtransaction`, `mappedaccountattribute`, `viewdefinition`, `accountaccess`, … Probably never needed once the indirection exists; the legacy account_ids become an internal implementation detail.

### Open questions for brainstorming

- **`tier` column vs deriving tier from which columns are null**: storing `tier` explicitly is redundant with `(tx_type, currency)` nullness (tier-1 = both set, tier-2 = currency set, tier-3 = both null). Keep it for clarity/index, or derive it?
- **Should `findSettlementAccount` live on `Connector` (so non-local connectors can override) or in a standalone `code.bankconnectors.settlement` service?** The two call sites are in `LocalMappedConnector`, but settlement is a connector concern.
- **EUR-only global fallback**: tier-3 accounts are hardcoded EUR. Should the new model let a deployment declare a non-EUR global fallback, or is EUR-fallback-then-FX a permanent assumption?
- **Backfill direction for tier-1/2 (PR 2)**: confirmed `direction = NULL` is correct — but do we also want to *stop* relying on bank-context-implies-direction long-term, i.e. eventually make direction explicit everywhere? (Probably out of scope.)

### Updated file-by-file checklist

- [ ] `obp-commons/.../model/enums/Enumerations.scala` — add `SettlementDirection`, `SettlementTier`
- [ ] `obp-api/.../bankconnectors/settlement/MappedBankSettlementAccount.scala` — new mapper + provider + `findSettlementAccount`
- [ ] `obp-api/.../api/util/migration/MigrationOfBankSettlementAccountTable.scala` — DDL
- [ ] `obp-api/.../bootstrap/liftweb/Boot.scala` — register table in schemify; consolidate default-bank creation into the shared helper (mint UUIDs, set `kind`)
- [ ] `obp-api/.../bankconnectors/LocalMappedConnector.scala:3412-3442` — use shared helper (UUIDs + table rows)
- [ ] `obp-api/.../bankconnectors/LocalMappedConnector.scala:2227-2334, 2385-2489` — dedupe into one cascade helper; `findSettlementAccount` + `tier` check; legacy fallback retained
- [ ] `obp-api/.../api/util/Glossary.scala` — "Known exception" note on `Account.account_id` until PR 2/3 land
- [ ] `obp-api/src/test/scala/code/api/v4_0_0/BankTests.scala`, `v5_0_0/BankTests.scala` — update assertions (legacy stays; add UUID path)
- [ ] (PR 2) backfill migration over `kind="SETTLEMENT"` accounts
- [ ] (PR 3) rename + FK cascade

---

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
