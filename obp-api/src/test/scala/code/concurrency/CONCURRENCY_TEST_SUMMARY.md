# OBP-API Concurrency Hazard Test Suite — Summary

**Branch**: `feature/concurrency-hazard-tests`  
**Commit**: `89e9753f9`  
**Test run result**: 16 FAILED (hazards confirmed) · 3 PASSED (safeguards verified) · BUILD SUCCESS

---

## Overview

This suite was created to systematically surface every known database concurrency hazard in OBP-API.
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

## Test Files (8 classes · 19 scenarios · 1,277 lines)

| File | Scenarios | Lines |
|---|---|---|
| `ConcurrentRaceSetup.scala` | base trait | 138 |
| `ConcurrentTransferRaceTest.scala` | A, B, S | 219 |
| `ConcurrentDuplicateCreationTest.scala` | C, D, F, I, L, W | 272 |
| `ConcurrentConnectionMechanismTest.scala` | G1, G2 | 86 |
| `ConcurrentSecurityRaceTest.scala` | H, K | 137 |
| `ConcurrentConsentRaceTest.scala` | J, U | 148 |
| `ConcurrentViewPermissionRaceTest.scala` | N, O, R | 205 |
| `ConcurrentProviderRaceTest.scala` | AA | 72 |

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
| **A** | 🔴 FAILED | 10 concurrent SANDBOX_TAN transfers lost 9 balance updates (`actualDebited=100 expectedDebited=1000`) | lost-update | `LocalMappedConnectorInternal.scala:510` `saveTransaction` |
| **B** | 🔴 FAILED | 8 concurrent challenge answers executed the payment 8 times (`mappedTxnCount=8`, expected 1) | check-then-act | `Http4s400.answerChallengeNormal` |
| **S** | 🔴 FAILED | 8 concurrent `makeHistoricalPayment` calls lost 4 balance updates (`actualDebited=200 expectedDebited=600`) | lost-update | `LocalMappedConnector.saveHistoricalTransaction:2351` |

**Impact**: Direct financial loss. A and S create phantom balances; B enables double-spend of a
single transaction request.

---

### Duplicate Creation — `ConcurrentDuplicateCreationTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **C** | 🔴 FAILED | 8 concurrent entitlement grants created 8 rows (expected 1) | check-then-insert | `MappedEntitlementsProvider.addEntitlement` |
| **D** | 🔴 FAILED | 8 concurrent `getOrCreateAccountHolder` calls created 8 rows (expected 1) | check-then-insert | `MapperAccountHolders.getOrCreateAccountHolder` |
| **F** | 🔴 FAILED | 8 concurrent `getOrCreateMetadata` calls threw an exception (UniqueIndex present but unhandled) | unique-constraint-unhandled | `MappedCounterpartyMetadata.getOrCreateMetadata` |
| **I** | 🔴 FAILED | 2 concurrent first-time OAuth logins: one got uncaught JDBC `23505` constraint-violation (500 at HTTP layer) | unique-constraint-unhandled | `LiftUsers.getOrCreateUserByProviderId` |
| **L** | 🔴 FAILED | 8 concurrent `getOCreateUserCustomerLink` calls: second concurrent insert threw uncaught JDBC exception | unique-constraint-unhandled | `MappedUserCustomerLinkProvider.getOCreateUserCustomerLink` |
| **W** | 🔴 FAILED | 2 concurrent `getOrCreateConsumer` calls: second insert swallowed into `Failure` box by `tryo` — caller receives no usable consumer | unique-constraint-unhandled | `OAuth.getOrCreateConsumer:535` |

**Impact**: C/D silently bloat entitlement and account-holder tables; I/L cause 500 for one of two
simultaneous new users; W silently breaks OAuth2 authentication for one caller.

---

### Security — `ConcurrentSecurityRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **H** | 🔴 FAILED | 8 concurrent bad-login increments: only 1 landed (`finalCounter=1`, expected 8) — account lockout can be bypassed | lost-update | `LoginAttempt.incrementBadLoginAttempts` |
| **K** | 🔴 FAILED | 8 concurrent wrong challenge answers: only 1 attempt counted (`finalCounter=1`, expected 8) — brute-force lockout can be bypassed | lost-update | `MappedChallengeProvider.validateChallenge:78` |

**Impact**: Critical. An attacker can saturate the challenge-answer endpoint with concurrent
requests, consuming only 1 of the permitted attempts per burst — effectively bypassing both
account-lockout and transaction-challenge brute-force protection.

---

### Consent Scheduling — `ConcurrentConsentRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **J** | 🔴 FAILED | Scheduler stale-save resurrected a revoked consent (`afterRevoke=terminatedByTpp finalStatus=expired`) | lost-update | `ConsentScheduler.expiredBerlinGroupConsents:117` |
| **U** | 🔴 FAILED | Unfinished-consent scheduler task overwrote a concurrent HTTP status change (`afterChange=REVOKED finalStatus=rejected`) | lost-update | `ConsentScheduler.unfinishedBerlinGroupConsents:77` |

**Impact**: PSD2 compliance breach. A consent the user or TPP explicitly revoked can be silently
resurrected as `expired` by a background scheduler task that holds a stale in-memory copy.

---

### View Permissions — `ConcurrentViewPermissionRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **N** | 🔴 FAILED | 2 concurrent `getOrCreateCustomPublicView` calls: second insert threw JDBC constraint violation on `ViewDefinition` unique index | unique-constraint-unhandled | `MapperViews.createAndSaveDefaultPublicCustomView:1054` |
| **O** | 🔴 FAILED | 2 concurrent `resetViewPermissions` calls: second insert threw JDBC constraint violation on `ViewPermission` unique index | unique-constraint-unhandled | `ViewPermission.resetViewPermissions:137` |
| **R** | 🔴 FAILED | `removeCustomView` emptiness check passed; concurrent grant committed `AccountAccess`; view deleted → 1 orphaned `AccountAccess` row pointing at non-existent view | check-then-act | `MapperViews.removeCustomView:502` |

**Impact**: N/O cause 500 errors during concurrent view provisioning (e.g. account onboarding);
R leaves orphaned permission rows that reference deleted views, potentially causing foreign-key
confusion or privilege-escalation edge cases.

---

### In-Memory Counter — `ConcurrentProviderRaceTest`

| ID | Result | Description | Hazard Shape | Source Location |
|---|---|---|---|---|
| **AA** | 🟢 PASSED\* | 8 concurrent `incrementFutureCounter` calls: all increments landed in this run | counter-sequence | `APIUtil.incrementFutureCounter:4853` |

\* AA uses `ConcurrentHashMap.getOrDefault + put` which is not atomic. The hazard is real but
timing-sensitive — with low thread count and fast CHM operations the race window is narrow and
may not trigger in every run. The source-level audit confirms the structural hazard.

---

### Connection-Pool Safeguards — `ConcurrentConnectionMechanismTest`

| ID | Result | Description |
|---|---|---|
| **G1** | 🟢 PASSED | 30 concurrent requests against a pool of 20: all 200, no deadlock, no timeout — HikariCP back-pressure works correctly |
| **G2** | 🟢 PASSED | 20 concurrent requests each see their own `user_id` — `RequestScopeConnection` per-request isolation is intact |

---

## Three-Tier Protection Picture

| Tier | DB constraint? | App guard? | Scenarios |
|---|:---:|:---:|---|
| **Silent data corruption** | ✗ | ✗ | A, S, H, K, AA, J, U, C, D, R |
| **Uncaught 500 / swallowed Failure** | ✓ | ✗ | I, L, N, O, W, F |
| **Gracefully handled** | ✓ | ✓ (`tryo`) | `createAccountIfNotExisting` (not broken) |
| **Safeguard verified** | — | ✓ | G1, G2 |

The most dangerous tier is **silent corruption**:
- **H and K** turn a counter lost-update into an authentication **lockout bypass / brute-force bypass**
- **J and U** silently **resurrect a revoked consent** — a PSD2 compliance breach
- **A and S** produce phantom account balances — direct financial loss

---

## Verified-Real Hazards Without Standalone Tests

These were confirmed real by source audit but are deliberately not given standalone tests (the
reason is noted to make coverage gaps explicit, not silent).

| ID | Hazard | Reason not tested |
|---|---|---|
| M | `getOrCreateSystemView` duplicate | Same `saveMe`-without-`tryo` root cause as N/O; system views are pinned to a global whitelist via `ViewDefinition.beforeSave` — deleting one would pollute other suites. **N** exercises the identical path on an isolated key. |
| P | `factoryResetSystemView` concurrent reset | Drives `ViewPermission.resetViewPermissions` insert — the exact code **O** already pins. |
| migrateViewPermissions | duplicate `ViewPermission` insert | Same insert-without-`tryo` root cause as **O**. |
| Q | `revokeAccess` vs `grant` check-then-act | Same `AccountAccess` check-then-act family as **R**; the window is narrow → non-deterministic barrier test would be flaky (false-green). The class is proven by **R**. |
| T | `createTransactionRequestBulk` per-leg balance | Verdict: unconfirmed intra-request self-race. `saveTransaction` mutates the passed object's `accountBalance` field — sequential legs may see the updated value, not a stale one. Writing a possibly-false test was rejected. |
| V | Berlin Group `usesSoFarTodayCounter` lost-increment | Same counter lost-update class as H/K; requires fully-signed recurring BG consent + TPP headers — disproportionate setup for a class already proven. |
| X | Consumer rate-limit `underConsumerLimits` TOCTOU | Real and high-impact (limit bypass), but active-limit lookup is cached ~1 hour → HTTP-layer timing unreliable → would be flaky. |
| Y | `AuthRateLimiter` cold-start SET-vs-INCR collision | Same rate-limit class as X; runs in shadow mode by default. Same flakiness concern. |
| Z | `MappedAgentProvider.updateAgentStatus` | Re-audited as **not a hazard**: sets both fields and `saveMe()`s the whole row — normal last-writer-wins PUT semantics, not field tearing. |

---

## Refuted by Audit (Genuinely Safe)

| Symbol | Why safe |
|---|---|
| `createAccountIfNotExisting` (`LocalMappedConnectorInternal.scala:283`) | The whole `find()`-then-`create()` is wrapped in `tryo`; the `UniqueIndex(bank, theAccountId)` violation is caught and converted to `Empty`/`Failure`. The caller handles `Empty` gracefully. This is the correct pattern that I/L/N/O are missing. |

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
