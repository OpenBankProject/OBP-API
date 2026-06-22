# OBP-API Concurrency Hazard Test Suite

**Branch**: `feature/concurrency-hazard-tests`  
**Test run result**: 20 PASSED (all hazards fixed) · 0 FAILED · BUILD SUCCESS

---

## Overview

This suite systematically surfaces every known database concurrency hazard in OBP-API.
The persistence layer uses Lift Mapper over HikariCP. There is no `SELECT FOR UPDATE`, no
optimistic-locking version column, and no transaction guard around multi-step read-modify-write
sequences. `.save()` / `.saveMe()` issues a blind `UPDATE`/`INSERT` by primary key and does not
catch JDBC constraint-violation exceptions.

Each scenario **asserts the theoretically correct outcome**, so a hazard surfaces as a **FAILED
test** — a red bar (with its `expected vs actual` clue) is the evidence that the hazard is real.
When a path is fixed (atomic `UPDATE`, optimistic-lock version column, unique constraint +
conflict retry, conditional guarded update), the corresponding scenario flips from red to green
automatically.

---

## How to Run

```sh
# Run only concurrency tests
mvn -pl obp-commons,obp-api scalatest:test \
  -DtagsToInclude=code.concurrency.ConcurrencyRace \
  -DfailIfNoTests=false

# Exclude from CI main flow
mvn -pl obp-commons,obp-api scalatest:test \
  -DtagsToExclude=code.concurrency.ConcurrencyRace
```

> **Requirement**: `hikari.maximumPoolSize=20` in test props. Several scenarios hold connections
> across a `CyclicBarrier`; a pool of 10 exhausts at 5 concurrent requests.

---

## Test Files (8 classes · 20 scenarios)

| File | Scenarios |
|---|---|
| `ConcurrentRaceSetup.scala` | base trait |
| `ConcurrentTransferRaceTest.scala` | A, B, S |
| `ConcurrentDuplicateCreationTest.scala` | C, D, F, I, L, W |
| `ConcurrentConnectionMechanismTest.scala` | G1, G2 |
| `ConcurrentSecurityRaceTest.scala` | H, K |
| `ConcurrentConsentRaceTest.scala` | J, U |
| `ConcurrentViewPermissionRaceTest.scala` | N, O, R, migrate |
| `ConcurrentProviderRaceTest.scala` | AA |

---

## Hazard Taxonomy

| Shape | Meaning |
|---|---|
| **lost-update** | Read a mutable field → mutate in memory → `.save()` the row; concurrent callers read the same start value and one overwrites the other |
| **check-then-act** | Read a status/flag → branch → side-effect → write new status; the check and the write are not atomic |
| **check-then-insert** | `find()`-then-`create()` with **no** unique index; concurrent callers all miss the find and all insert |
| **unique-constraint-unhandled** | `find()`-then-`create()` where a `UniqueIndex` backs the table but the JDBC violation is not caught → uncaught 500 or swallowed `Failure` |
| **counter-sequence** | Increment a counter by read-then-write → lost increments |

---

## Scenario Results

### Money Movement — `ConcurrentTransferRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **A** | 🟢 PASSED | 10 concurrent SANDBOX_TAN transfers: all balance updates landed (Doobie SELECT FOR UPDATE on `mappedtransactionrequest`) | lost-update | `LocalMappedConnectorInternal.scala` `saveTransaction` |
| **B** | 🟢 PASSED | 8 concurrent challenge answers: payment executed exactly once (conditional status update guards against double-spend) | check-then-act | `Http4s400.answerChallengeNormal` |
| **S** | 🟢 PASSED | 8 concurrent `makeHistoricalPayment` calls: all balance updates landed (atomic Doobie balance update) | lost-update | `LocalMappedConnector.saveHistoricalTransaction` |

**Fix**: Doobie `SELECT FOR UPDATE` + atomic balance update for A/S; conditional `UPDATE WHERE status='INITIATED'` for B.

---

### Duplicate Creation — `ConcurrentDuplicateCreationTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **C** | 🟢 PASSED | 8 concurrent entitlement grants: exactly 1 row created (UniqueIndex + tryo + re-fetch) | check-then-insert | `MappedEntitlementsProvider.addEntitlement` |
| **D** | 🟢 PASSED | 8 concurrent `getOrCreateAccountHolder` calls: exactly 1 row created (UniqueIndex + tryo + re-fetch) | check-then-insert | `MapperAccountHolders.getOrCreateAccountHolder` |
| **F** | 🟢 PASSED | 8 concurrent `getOrCreateMetadata` calls: no exceptions, exactly 1 row (tryo + re-fetch on constraint violation) | unique-constraint-unhandled | `MappedCounterpartyMetadata.getOrCreateMetadata` |
| **I** | 🟢 PASSED | 2 concurrent first-time OAuth logins: both succeed (Try + re-fetch by provider/providerId) | unique-constraint-unhandled | `LiftUsers.getOrCreateUserByProviderId` |
| **L** | 🟢 PASSED | 8 concurrent `getOCreateUserCustomerLink` calls: no exceptions, exactly 1 row (Try + re-fetch) | unique-constraint-unhandled | `MappedUserCustomerLinkProvider.getOCreateUserCustomerLink` |
| **W** | 🟢 PASSED | 2 concurrent `getOrCreateConsumer` calls: both callers receive a usable Full(consumer) (re-fetch on Failure) | unique-constraint-unhandled | `OAuth.getOrCreateConsumer` |

**Fix**: UniqueIndex constraints added where missing; `saveMe()`/`save` wrapped in `tryo`/`Try`; on constraint violation, re-fetch the row committed by the winning thread.

---

### Security — `ConcurrentSecurityRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **H** | 🟢 PASSED | 8 concurrent bad-login increments: all 8 landed (Doobie `UPDATE … SET counter = counter + 1` with row-level locking) | lost-update | `LoginAttempt.incrementBadLoginAttempts` |
| **K** | 🟢 PASSED | 8 concurrent wrong challenge answers: all 8 attempts counted (Doobie `UPDATE … SET counter = counter + 1 WHERE …`) | lost-update | `MappedChallengeProvider.validateChallenge` |

**Fix**: Atomic SQL increment (`SET counter = counter + 1`) via Doobie, replacing the read-modify-write that allowed lost-updates.

---

### Consent Scheduling — `ConcurrentConsentRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **J** | 🟢 PASSED | Scheduler no longer resurrects revoked consents (conditional Doobie `UPDATE WHERE status=<guard>`) | lost-update | `ConsentScheduler.expiredBerlinGroupConsents` |
| **U** | 🟢 PASSED | Scheduler no longer overwrites concurrent HTTP status changes (conditional Doobie `UPDATE WHERE status='received'`) | lost-update | `ConsentScheduler.unfinishedBerlinGroupConsents` |

**Fix**: `DoobieConsentSchedulerQueries` conditional UPDATE with a status guard — if HTTP already changed the status, the WHERE clause matches 0 rows and the stale save is silently a no-op.

---

### View Permissions — `ConcurrentViewPermissionRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **N** | 🟢 PASSED | 2 concurrent `getOrCreateCustomPublicView` calls: no exceptions, exactly 1 view (Try + re-fetch on constraint violation) | unique-constraint-unhandled | `MapperViews.getOrCreateCustomPublicView` |
| **O** | 🟢 PASSED | 2 concurrent `resetViewPermissions` calls: no exceptions, exactly 1 row per permission (`Try { .save }` ignores duplicate) | unique-constraint-unhandled | `ViewPermission.resetViewPermissions` |
| **R** | 🟢 PASSED | No orphaned `AccountAccess` after concurrent grant + view delete (`ViewDefinition.beforeDelete` cascade) | check-then-act | `MapperViews.removeCustomView` |
| **migrate** | 🟢 PASSED | 2 concurrent `migrateViewPermissions` calls: no exceptions, exactly 1 row per enabled permission (`Try { .save }` ignores duplicate) | unique-constraint-unhandled | `MapperViews.migrateViewPermissions` |

**Fix**: N/O/migrate — wrap inserts in `scala.util.Try`, ignore constraint violations. R — `ViewDefinition.beforeDelete` hook cascade-deletes `AccountAccess` rows so no orphans survive the delete. M and `getOrCreateSystemView` — same `Try` + re-fetch pattern as N (no standalone test; see Hazards Without Tests table).

---

### In-Memory Counter — `ConcurrentProviderRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **AA** | 🟢 PASSED | 8 concurrent `incrementFutureCounter` calls: all 8 increments landed (`ConcurrentHashMap.compute` is atomic) | counter-sequence | `APIUtil.incrementFutureCounter` |

**Fix**: Replaced `getOrDefault + put` (two separate CHM operations) with `ConcurrentHashMap.compute`, which holds the segment lock for the entire read-modify-write.

---

### Connection-Pool Safeguards — `ConcurrentConnectionMechanismTest`

| ID | Result | Description |
|---|---|---|
| **G1** | 🟢 PASSED | 30 concurrent requests against a pool of 20: all 200, no deadlock, no timeout — HikariCP back-pressure works correctly |
| **G2** | 🟢 PASSED | 20 concurrent requests each see their own `user_id` — `RequestScopeConnection` per-request isolation is intact |

---

## Three-Tier Protection Picture (post-fix)

| Tier | DB constraint? | App guard? | Scenarios | Status |
|---|:---:|:---:|---|---|
| **Silent data corruption** | ✗ | ✗ | A, S, H, K, AA, J, U, C, D, R | ✅ All fixed |
| **Uncaught 500 / swallowed Failure** | ✓ | ✗ | I, L, N, O, W, F | ✅ All fixed |
| **Gracefully handled** | ✓ | ✓ | All 19 scenarios | ✅ 19/19 green |
| **Safeguard verified** | — | ✓ | G1, G2 | ✅ Still passing |

Every scenario now lands in the **Gracefully handled** tier. The critical previously-unsafe paths:
- **H and K**: now use atomic SQL `SET counter = counter + 1` — lockout bypass eliminated
- **J and U**: now use conditional `UPDATE WHERE status=<guard>` — PSD2 compliance restored
- **A and S**: now use Doobie `SELECT FOR UPDATE` + atomic balance update — phantom balances eliminated
- **B**: now uses conditional status transition — double-spend eliminated

---

## Verified-Real Hazards Without Standalone Tests

These were confirmed real by source audit. M and migrateViewPermissions have been fixed in code;
their class is proven by N/O. The remaining entries (Q, T, V, X, Y) are intentionally untested.

| ID | Hazard | Fix status | Reason not tested |
|---|---|---|---|
| M | `getOrCreateSystemView` duplicate | ✅ Fixed (`scala.util.Try` + re-fetch) | System views are pinned to a global whitelist via `ViewDefinition.beforeSave` — deleting one would pollute other suites. **N** exercises the identical path on an isolated key. |
| P | `factoryResetSystemView` concurrent reset | ✅ Fixed (via O — calls `resetViewPermissions`) | Drives `ViewPermission.resetViewPermissions` insert — the exact code **O** already pins. |
| migrateViewPermissions | duplicate `ViewPermission` insert | ✅ Fixed (`scala.util.Try` on bare `.save`) + **migrate** scenario added | Standalone test added — see **migrate** scenario in `ConcurrentViewPermissionRaceTest`. |
| Q | `revokeAccess` vs `grant` check-then-act | — | Same `AccountAccess` check-then-act family as **R**; the window is narrow → non-deterministic barrier test would be flaky (false-green). The class is proven by **R**. |
| T | `createTransactionRequestBulk` per-leg balance | — | Verdict: unconfirmed intra-request self-race. `saveTransaction` mutates the passed object's `accountBalance` field — sequential legs may see the updated value, not a stale one. Writing a possibly-false test was rejected. |
| V | Berlin Group `usesSoFarTodayCounter` lost-increment | — | Same counter lost-update class as H/K; requires fully-signed recurring BG consent + TPP headers — disproportionate setup for a class already proven. |
| X | Consumer rate-limit `underConsumerLimits` TOCTOU | — | Real and high-impact (limit bypass), but active-limit lookup is cached ~1 hour → HTTP-layer timing unreliable → would be flaky. |
| Y | `AuthRateLimiter` cold-start SET-vs-INCR collision | — | Same rate-limit class as X; runs in shadow mode by default. Same flakiness concern. |
| Z | `MappedAgentProvider.updateAgentStatus` | — | Re-audited as **not a hazard**: sets both fields and `saveMe()`s the whole row — normal last-writer-wins PUT semantics, not field tearing. |

---

## Refuted by Audit (Genuinely Safe)

| Symbol | Why safe |
|---|---|
| `createAccountIfNotExisting` (`LocalMappedConnectorInternal.scala:283`) | The whole `find()`-then-`create()` is wrapped in `tryo`; the `UniqueIndex(bank, theAccountId)` violation is caught and converted to `Empty`/`Failure`. The caller handles `Empty` gracefully. This was the correct pattern; it has now been applied to all formerly-broken paths (C/D/F/I/L/W/N/O). |

---

## Fix Patterns

When fixing a confirmed hazard, the corresponding test flips from red to green automatically.

| Hazard shape | Recommended fix |
|---|---|
| **lost-update** (balance, counter, consent status) | Atomic `UPDATE … SET x = x + delta WHERE pk = ?` (raw SQL) or optimistic-lock version column with retry |
| **check-then-insert** (no unique index) | Add `UniqueIndex` on the natural key, then wrap the insert in `tryo` and re-fetch on `Failure` |
| **unique-constraint-unhandled** | Wrap the existing `.saveMe()` in `tryo`; on `Failure`, re-fetch with `find()` and return the existing row |
| **check-then-act** (state machine) | Move the status check + flip into a single conditional `UPDATE … WHERE status = 'old'`; check affected-rows count to detect a lost race |
| **scheduler stale-save** | Replace unconditional `.save()` with a conditional `UPDATE … WHERE status = 'expected_status'`; skip if 0 rows updated |
