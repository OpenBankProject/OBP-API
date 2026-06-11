# Concurrency Hazard Test Suite

This package simulates the database concurrency hazards in OBP-API: one-HTTP-request /
one-DB-transaction atomicity, concurrent read/write, and write contention. Each scenario
asserts the **theoretically correct** outcome, so a hazard surfaces as a **FAILED test** —
a red bar (with its "expected vs actual" clue) is the evidence the hazard is real.

The persistence layer is Lift Mapper over HikariCP. There is no `SELECT FOR UPDATE`, no
optimistic-locking version column, and no transaction guard around multi-step
read-modify-write sequences. `.save()`/`.saveMe()` is a blind UPDATE/INSERT by PK and does
not catch JDBC constraint-violation exceptions.

All scenarios are tagged `ConcurrencyRace` and isolated from the CI main flow:

```sh
# run only these:
mvn -pl obp-commons,obp-api scalatest:test -DtagsToInclude=code.concurrency.ConcurrencyRace -DfailIfNoTests=false
# exclude from CI:
mvn -pl obp-commons,obp-api scalatest:test -DtagsToExclude=code.concurrency.ConcurrencyRace
```

## Hazard taxonomy

| Shape | Meaning |
|---|---|
| **lost-update** | read a mutable field, mutate in memory, `.save()` the row; concurrent callers read the same start value and one overwrites the other |
| **check-then-act** | read a status/flag, branch, perform a side effect, then write a new status; the check and the write are not atomic |
| **check-then-insert** | `find()`-then-`create()` with **no** unique index; concurrent callers all miss the find and all insert |
| **unique-constraint-unhandled** | `find()`-then-`create()` where a UniqueIndex **does** back the table, but the JDBC violation is not caught → uncaught 500 (or, when wrapped in `tryo`, a swallowed `Failure` the caller cannot use) |
| **counter-sequence** | increment a counter by read-then-write → lost increments |

## Implemented scenarios (red bar = hazard confirmed)

| ID | Hazard | Shape | Source | Test |
|---|---|---|---|---|
| A | Balance lost-update (`saveTransaction`) | lost-update | `LocalMappedConnectorInternal.scala:510` | `ConcurrentTransferRaceTest` |
| B | Transaction-request challenge double-spend | check-then-act | `Http4s400.answerChallengeNormal` | `ConcurrentTransferRaceTest` |
| C | Entitlement duplicate | check-then-insert | `MappedEntitlementsProvider.addEntitlement` | `ConcurrentDuplicateCreationTest` |
| D | `getOrCreateAccountHolder` duplicate | check-then-insert | `MapperAccountHolders` | `ConcurrentDuplicateCreationTest` |
| F | `getOrCreateMetadata` (graceful, UniqueIndex present) | unique-constraint-unhandled | `MappedCounterpartyMetadata` | `ConcurrentDuplicateCreationTest` |
| G1 | Pool back-pressure (safeguard — PASSES) | — | `RequestScopeConnection` + Hikari | `ConcurrentConnectionMechanismTest` |
| G2 | Per-request context isolation (safeguard — PASSES) | — | `RequestScopeConnection` | `ConcurrentConnectionMechanismTest` |
| H | Bad-login counter lost-update (lockout bypass) | lost-update | `LoginAttempt.incrementBadLoginAttempts` | `ConcurrentSecurityRaceTest` |
| I | OAuth user duplicate → uncaught 500 | unique-constraint-unhandled | `LiftUsers.getOrCreateUserByProviderId` | `ConcurrentDuplicateCreationTest` |
| J | Consent scheduler stale-save (expired task) resurrects revoked consent | lost-update | `ConsentScheduler.expiredBerlinGroupConsents:117` | `ConcurrentConsentRaceTest` |
| K | Challenge attempt-counter lost-update (brute-force bypass) | lost-update | `MappedChallengeProvider.validateChallenge:78` | `ConcurrentSecurityRaceTest` |
| L | UserCustomerLink duplicate → uncaught 500 | unique-constraint-unhandled | `MappedUserCustomerLinkProvider.getOCreateUserCustomerLink` | `ConcurrentDuplicateCreationTest` |
| N | `getOrCreateCustomPublicView` duplicate → uncaught 500 | unique-constraint-unhandled | `MapperViews.createAndSaveDefaultPublicCustomView:1054` | `ConcurrentViewPermissionRaceTest` |
| O | `resetViewPermissions` delete-then-insert → uncaught 500 | unique-constraint-unhandled | `ViewPermission.resetViewPermissions:137` | `ConcurrentViewPermissionRaceTest` |
| R | `removeCustomView` check-then-delete orphans a grant | check-then-act | `MapperViews.removeCustomView:502` | `ConcurrentViewPermissionRaceTest` |
| S | Historical-payment balance lost-update | lost-update | `LocalMappedConnector.saveHistoricalTransaction:2351` | `ConcurrentTransferRaceTest` |
| U | Consent scheduler stale-save (unfinished task) overwrites status | lost-update | `ConsentScheduler.unfinishedBerlinGroupConsents:77` | `ConcurrentConsentRaceTest` |
| W | `getOrCreateConsumer` duplicate → swallowed `Failure` (tryo) | unique-constraint-unhandled | `OAuth.getOrCreateConsumer:535` | `ConcurrentDuplicateCreationTest` |
| AA | `incrementFutureCounter` non-atomic CHM read-modify-write | counter-sequence | `APIUtil.incrementFutureCounter:4853` | `ConcurrentProviderRaceTest` |

`E` (consent status race) was deferred earlier due to `consumer`/JWT setup complexity and is not part of this set.

## Verified-real but not given a standalone test (and why)

These were confirmed real by source audit but a standalone red-bar test would either duplicate
an already-covered root cause, be flaky, or require disproportionate setup. They are documented
here so coverage gaps are explicit, not silent.

| ID | Hazard | Why no standalone test |
|---|---|---|
| M | `getOrCreateSystemView` duplicate | Same `saveMe`-without-`tryo` root cause as **N/O** (unique-constraint-unhandled). System views are pinned to a global whitelist by `ViewDefinition.beforeSave`/`isValidSystemViewId`, so an isolated test would have to delete a globally-shared system view and pollute other suites (forkMode=once). **N** exercises the identical unguarded path on an isolated custom view. |
| P | `factoryResetSystemView` concurrent reset | Drives `ViewPermission.resetViewPermissions`'s insert path — the exact code **O** already pins. |
| migrateViewPermissions | duplicate ViewPermission insert | Same `ViewPermission` insert-without-`tryo` root cause as **O**. |
| Q | `revokeAccess` vs `grant` check-then-act | Same `AccountAccess` check-then-act family as **R**; the revoke-vs-grant window is narrow, so a non-deterministic barrier test would be flaky (false-green). The check-then-act class is already proven by **R** (orphan) and **J/U** (stale-save). |
| T | `createTransactionRequestBulk` per-leg balance | The verdict's "deterministic intra-request self-race" is **unconfirmed**: `saveTransaction` writes `fromAccount.asInstanceOf[MappedBankAccount].accountBalance(newBalance)` back onto the passed object, so sequential legs see the updated balance, not a stale one. Whether `BulkPaymentHandler.executeAllItems` re-resolves the account per leg needs further investigation before asserting. The concurrent-reuse mechanism it shares with **S** is already proven; writing a possibly-false test here was rejected. |
| V | Berlin Group `usesSoFarTodayCounter` lost-increment | Same counter lost-update class as **H/K**. Needs a fully-signed recurring Berlin Group consent + TPP headers to reach the increment branch — disproportionate setup for a class already proven. |
| X | Consumer rate-limit `underConsumerLimits` check-then-INCR (TOCTOU) | Real and high-impact (limit bypass), but the active-limit lookup is cached for ~1 hour (`RateLimitTest` documents this) and the Redis TTL+GET+INCR timing makes an HTTP-layer test unreliable (flaky). Confirmed by source audit; a flaky test would undermine "red bar = reliable evidence." |
| Y | `AuthRateLimiter` cold-start SET-vs-INCR collision | Same rate-limit class as **X**; depends on Redis timing and runs in shadow mode by default (non-blocking). Same flakiness concern. |
| Z | `MappedAgentProvider.updateAgentStatus` | Re-audited as **not a hazard**: it sets both fields and `saveMe()`s the whole row; an H2 single-row UPDATE is atomic, so concurrent calls are normal last-writer-wins PUT semantics, not field tearing or data loss. A genuine lost-update would need multiple partial-update endpoints doing read-modify-write on the same row — no such code path exists. |

## Refuted by audit (genuinely safe — documents what is NOT broken)

| Symbol | Why safe |
|---|---|
| `createAccountIfNotExisting` (`LocalMappedConnectorInternal.scala:283`) | The whole `find()`-then-`create()` is wrapped in `tryo`; the `UniqueIndex(bank, theAccountId)` violation on the second concurrent insert is caught and converted to `Empty`/`Failure`, not an uncaught 500. The caller handles `Empty` gracefully. This is the correct pattern that **I/L/M/N/O** are missing. |

## The three-tier protection picture

| Tier | DB constraint? | App guard? | Scenarios |
|---|:---:|:---:|---|
| Silent data corruption | ✗ | ✗ | A, S, H, K, AA, J, U, C, D, R |
| Uncaught 500 / swallowed Failure | ✓ | ✗ | I, L, N, O, W |
| Gracefully handled | ✓ | ✓ (`tryo`) | F, `createAccountIfNotExisting` |
| Safeguard verified | — | ✓ | G1, G2 |

The most dangerous tier is silent corruption: H and K turn a balance/counter lost-update into an
authentication **lockout / brute-force bypass**; J and U silently **resurrect a revoked consent**
(a PSD2 compliance breach). When any of these is fixed (atomic UPDATE, optimistic-lock version
column, unique constraint + conflict retry, or a conditional/guarded update), the corresponding
scenario flips from red to green automatically.
