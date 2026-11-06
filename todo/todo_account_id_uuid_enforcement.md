# TODO — Enforce `account_id` MUST-be-UUID at write time

## Context

The `Account.account_id` glossary entry in `obp-api/src/main/scala/code/api/util/Glossary.scala` was tightened from "SHOULD be a UUID" to **"MUST be a UUID"** as part of the v6.0.0 routing work (see also `Account.account_routings` — the implicit `(OBP, account_id)` routing is federation-safe *because* account_id is a UUID, which makes collision probability effectively zero across OBP instances).

The contract is now documented but **not yet enforced** in code. `MappedBankAccount.theAccountId` is declared `AccountIdString` (`obp-api/src/main/scala/code/model/dataAccess/MappedBankAccount.scala:43`), which is just `MappedString(fieldOwner, AccountIdString.MaxLength)` — a permissive string column whose length comes from the `account_id.length` prop (default 64, `code/util/UUIDString.scala:52-57`). Any value of any shape is accepted on write.

## Why this matters

- Federation/routing logic now treats `(OBP, account_id)` as a globally-safe identifier (`Constant.accountRoutingsWithImplicitOBP`, called from every v6 account-routings-returning factory). If a non-UUID account_id slips in, the implicit routing entry it produces may collide with another OBP instance's value, silently breaking cross-instance lookups.
- API consumers reading the glossary will write integration code that assumes a UUID. They'll be surprised when an older non-UUID identifier comes back from `getCoreAccountById` etc.
- Audit and observability tooling that parses account_ids as UUIDs (e.g. to dedupe or partition) will break on the first non-UUID.

## Scope of work

### Write paths that mint or accept `account_id`

Audit all endpoints and code paths that **create** an account or **accept** an `account_id` from the client:

- [ ] `createAccount` family. As of 2026-09-14 that is four sites, all in `Http4s*.scala` (the `APIMethods*.scala` files were deleted in the Lift teardown, and **v6.0.0 has no createAccount of its own** — it cascades to v5.0.0 through the version bridge):
  - `Http4s310.scala:4344` `createAccount` (PUT)
  - `Http4s400.scala:10353` `addAccount` (POST)
  - `Http4s500.scala:603` `createAccount` (PUT)
  - `Http4s700.scala:4677` `createAccountCommon`, shared by `createAccountV700` (`:4743`, POST) and `createAccountWithIdV700` (`:4750`, PUT)

  Confirm UUID generation when the client doesn't supply an id (all four already default to `APIUtil.generateUUID()`, but verify); reject non-UUID values when the client does supply one.
- [ ] `PUT /banks/BANK_ID/accounts/ACCOUNT_ID` "create with id" path — `ACCOUNT_ID` comes from the URL. Add UUID validation guard (`ACCOUNT_ID` must parse as a UUID).
- [ ] Sandbox data import paths — `OBPDataImport.scala`, `LocalMappedConnectorDataImport.scala`. Imported account_ids must be UUIDs; reject the whole import on first non-UUID.
- [ ] South-side adapter ingress — RabbitMQ/Kafka/StoredProcedure connectors that map core-banking identifiers to OBP account_ids. The contract is that the adapter emits a UUID; document this and add a `requireUUID(...)` guard at the OBP-API boundary so a misbehaving adapter fails loudly rather than corrupting the dataset.

### What already exists — this is a tightening, not a new check

An earlier revision of this note assumed there was no validation and no error constant. Both are
wrong, and it changes the shape of the work:

- **`APIUtil.isValidID`** (`APIUtil.scala:854`) already runs on every create path, but it validates
  `^([A-Za-z0-9\-_.]+)$` with `length < 256` — an *identifier* shape, not a UUID. `account_id =
  "my-account-1"` passes today.
- **`InvalidAccountIdFormat` already exists** — `ErrorMessages.scala:656`, **OBP-30110**, with 49
  uses across ~17 call sites.

So the work is to tighten the existing predicate at the create paths, not to introduce a helper
alongside an unused one.

**The catch**: OBP-30110's message text *describes the permissive rule* —

> `OBP-30110: Invalid Account Id. The ACCOUNT_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255.`

— so it cannot be reused verbatim for a UUID rejection without becoming misleading. Two options,
both needing a decision:

1. **Reword OBP-30110.** Error-code *numbers* are stable once committed; the message text is not
   under the same guarantee. But 17 call sites use this constant for non-create paths where the
   permissive rule is still the right one, so rewording it would make *those* messages wrong instead.
2. **Add a new constant** (e.g. `AccountIdMustBeUUID`) used only at the create paths, leaving
   OBP-30110 to keep meaning "identifier shape" everywhere else. Costs one error code; keeps both
   messages truthful. Probably the right answer.

Sketch, assuming option 2:

```scala
// code/util/Helper.scala or APIUtil
def requireUUIDAccountId(value: String, callContext: Option[CallContext]): Future[Box[Unit]] =
  Helper.booleanToFuture(s"$AccountIdMustBeUUID Got: $value", failCode = 400, cc = callContext) {
    scala.util.Try(java.util.UUID.fromString(value)).isSuccess
  }
```

Note `UUID.fromString` is lenient about field widths (it accepts `1-1-1-1-1`), so if strictness
matters, match on the canonical 8-4-4-4-12 hex regex instead.

### Pre-existing data

- [ ] Decide on policy for legacy non-UUID account_ids already in the database.
  - Option 1: grandfather — leave them as-is, enforce only on new writes. Federation logic must continue to tolerate non-UUID values forever.
  - Option 2: migration — assign UUIDs and update FK references (`bankaccountrouting.accountid`, `mappedaccountattribute.maccountid`, transactions, views, account access, etc.). High blast radius — every URL referencing the old id breaks unless an alias table is kept.
- [ ] Whichever option is chosen, document it in the `Account.account_id` glossary entry under "Migration of existing values".

### Bank-side companion work (related but separate)

The `Bank.bank_id` glossary was simultaneously tightened to **SHOULD** be `<human-friendly>-<UUID>`. That's a *new-banks-only* convention — see the glossary entry's "Earlier conventions" section. No enforcement code is needed yet (the rule is a SHOULD, not a MUST), but worth tracking together:

- [ ] When `createBank` next needs touching, add a soft-validation lint: if `bank_id` doesn't contain a UUID-shaped suffix, log a warning. Don't reject — keeps the migration tolerant.
- [ ] Update onboarding docs / sandbox seed scripts to emit the new shape for any newly-created sandbox banks.

## Test plan

- [ ] Unit test for `requireUUIDAccountId` covering: valid UUID v4, valid UUID v1, lowercase/uppercase, with/without hyphens, common invalid shapes (empty, numeric, name-like).
- [ ] Integration test against `createAccount` (or its v6 equivalent) confirming a 400 with `InvalidAccountIdFormat` for non-UUID input.
- [ ] Integration test confirming UUID input still succeeds.
- [ ] If grandfathering legacy data: integration test reading a legacy non-UUID account record returns its values intact (no validation on reads).

## Out of scope

- Renaming existing bank_ids — explicitly *not* doing this; the new bank_id convention applies to newly created banks only.
- Changing `(OBP, account_id)` routing semantics — already done.
- Cross-instance federation handshake — separate workstream.
