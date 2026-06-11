# OBP-API Concurrency Race Hazards — ScalaTest Simulation Plan

> Date: 2026-06-11 · Branch: develop-obp · Testing preparation only (no business code fixes)

## Context (Why are we doing this?)

The API layer of OBP-API has been fully migrated to http4s, adopting the "1 HTTP request = 1 DB transaction" model (`RequestScopeConnection.withBusinessDBTransaction`, fully effective only for v7 native POST/PUT/DELETE; commit happens in the `guaranteeCase` after the response is generated). The underlying data access layer mixes Lift Mapper (for business writes), Doobie (for some queries), and a shared HikariCP connection pool (`autoCommit=false`).

Through investigation by 3 Explore agents + line-by-line verification, it is confirmed that this mechanism **relies entirely on optimistic concurrency + DB constraints**. There are **no** `SELECT ... FOR UPDATE`, pessimistic locks, or optimistic lock version columns anywhere in the code, nor is there any explicitly set transaction isolation level (using the DB defaults, which is READ COMMITTED for both H2/Postgres). Therefore, multiple classic concurrent write hazards exist:
**Lost updates on balances, double-spending on transaction request state machines, and check-then-insert duplicate creations.**

The goal of this plan is to **turn these theoretical hazards into runnable, observable evidence using a set of concurrent ScalaTests**.
As per the user's decision: **we will comprehensively cover 6-8 race conditions** and **assert the "theoretically correct behavior"** (if the current code has hazards, it will FAIL and print "expected vs actual"; the red light is the evidence). Fixing these issues (adding locks/unique constraints/optimistic locks) will be a separate follow-up task; business code will remain untouched for now.

## Verified Hazard Checklist (Code-level Evidence)

| # | Hazard | Code Location (Verified) | DB Protection | Exposure Value |
|---|---|---|---|---|
| A | **Balance lost updates** | `LocalMappedConnectorInternal.scala:510-513` `saveTransaction`: `read balance → +amount → .save()` full row write | **None** | Highest (Funds) |
| B | **Transaction Request double-spending** | `Http4s210.scala:502-504` check `status=="INITIATED"` → pay → update status, no atomicity | **None** | Highest (Funds) |
| C | **Entitlement duplicate grant** | `MappedEntitlements.scala:159-176` no find-first; `:264 dbIndexes=UniqueIndex(mEntitlementId)` UUID only | **None** | High (Permissions) |
| D | **Account holder duplicate creation** | `MapperAccountHolders.scala` getOrCreate find-then-create; `:34 Index(...)` **Non-unique** | **None** | High |
| E | **Consent state machine race** | `MappedConsent.scala:33-40` `updateConsentStatus` calls `saveMe` directly, no current status validation | Partial | Medium |
| F | **Counterparty metadata concurrency** | `MapperCounterparties.scala:71-88` check-then-insert; **BUT `:443 UniqueIndex(counterpartyId)` exists** | **Yes** | Medium (Test elegant conflict handling) |
| G | **Pool exhaustion / Cross-request crosstalk** | `RequestScopeConnection.scala:113-116` `childValue=null` prevents crosstalk; pool default is 20 | — | Medium (Mechanism layer) |

> **Correcting an agent false positive**: Explore agent 2 reported that counterparty metadata had "no UNIQUE constraint". In reality, `MapperCounterparties.scala:443` has a `UniqueIndex(counterpartyId)`. Thus, for point F, the DB will block the second insert, and the race condition will manifest as "the second request gets a constraint conflict" instead of silent duplication → The test is modified to verify whether the application gracefully handles the conflict (without throwing a 500 error).

## Testing Architecture

### 1. Infrastructure (base trait + helpers)
Create a new `code.concurrency` package. The core trait reuses existing testing mechanisms:

```scala
package code.concurrency

import code.setup.{DefaultUsers, ServerSetupWithTestData, APIResponse}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.scalatest.Tag

// Exclusive tag: these tests are expected to FAIL (exposing hazards), must be isolated from the CI main flow
object ConcurrencyRace extends Tag("code.concurrency.ConcurrencyRace")

trait ConcurrentRaceSetup extends ServerSetupWithTestData with DefaultUsers {
  // Fire n requests concurrently and wait for all (using Future[APIResponse] returned by makePostRequestAsync)
  def fireConcurrently(n: Int)(mk: Int => Future[APIResponse]): List[APIResponse] =
    Await.result(Future.sequence((0 until n).map(mk)), 60.seconds).toList

  // Read Mapper directly to assert true DB state (bypassing cache/HTTP)
  def accountBalance(bankId: String, accountId: String): Long =
    MappedBankAccount.find(By(MappedBankAccount.bank, bankId),
      By(MappedBankAccount.theAccountId, accountId)).map(_.accountBalance.get)
      .openOrThrowException("account not found")
}
```

Reusing off-the-shelf assets (**Do not reinvent the wheel**):
- Requests: `SendServerRequests.makePostRequestAsync / makeGetRequestAsync` (`:180/:240`, returns `Future[APIResponse]`)
- Authentication: `user1..user4` from `DefaultUsers` (OAuth) + `<@` signatures; or `DirectLogin` header (as used in `Http4s700RoutesTest`)
- Data Setup: `ServerSetupWithTestData.beforeEach` already creates bank/account (initial balance 900000000);
  For transfers, refer to `v4_0_0/TransactionRequestsTest.scala` (SANDBOX_TAN);
  For permissions, refer to `v2_0_0/EntitlementTests.scala`; For state machines, refer to `v2_1_0/TransactionRequestsTest.scala`
- Direct Data Creation: Provider layer `CustomerX.customerProvider.vend.addCustomer(...)`, etc. (idiomatic for v7 tests)

### 2. Test Suite Grouping (6-8 scenarios, divided into 3 tiers)

**Tier 1 — HTTP concurrency, fund/data integrity (Highest Value)**
`ConcurrentTransferRaceTest` (tag `ConcurrencyRace`)
- **A Balance lost updates**: Concurrent N=10 SANDBOX_TAN transfers of `amount` out of the same account,
  Assert `accountBalance == initial - N*amount` (when an update is lost, actual will be > expected, less deducted).
- **B State machine double-spending**: Create 1 TR (INITIATED) → Concurrent N answer-challenge on the same `TR_ID`,
  Assert "only 1 succeeds + only deducted once + only 1 MappedTransaction entry" (double-spending will cause multiple entries/deductions).

**Tier 2 — Duplicate creations / State machines**
`ConcurrentDuplicateCreationTest` (tag `ConcurrencyRace`)
- **C Entitlement**: Concurrent N identical `(userId, bankId, roleName)` grants,
  Assert `MappedEntitlement.count(By...)==1` (actual might be N).
- **D Account holder**: Concurrently trigger holder creation for the same `(user, bank, account)` (via grant account access),
  Assert `MapperAccountHolders.count==1`.
- **E Consent state machine**: Concurrent N answers to the same consent, assert state transition is valid + side-effect executes only once.
- **F Counterparty metadata (corrected)**: Concurrent initial access to the same counterparty,
  Assert "doesn't throw 500, ends up with exactly 1 record" — verifying that the DB `UniqueIndex(counterpartyId)` conflict is handled gracefully by the application layer.

**Tier 3 — Underlying transaction/connection mechanism (Mechanism layer, optional)**
`ConcurrentConnectionMechanismTest` (tag `ConcurrencyRace`)
- **G1 Connection pool queuing without deadlock**: Inside `beforeEach`, `setPropsValues("hikari.maximumPoolSize"->"3")`,
  Concurrent N=5 requests, assert all complete without a 30s timeout (verifying queuing rather than deadlocking); auto-restored in `afterEach`.
- **G2 Cross-request connection crosstalk**: Send two batches of requests sequentially, assert that the second batch reads the data it wrote itself,
  and does not read 0 rows due to `currentProxy` crosstalk (regression testing for the `RequestScopeConnection.childValue=null` safeguard).

## Key Engineering Constraints (Must be handled within tests)

1. **H2 in-memory limitations (Most important, label honestly)**: The test DB is H2 (`test.default.props`).
   - Balance lost updates/duplicate creations are **application-layer** read-modify-write / check-then-insert issues, **independent of DB isolation levels**,
     as long as both requests' "reads" happen before the other's "write commits", they can be reproduced → H2 **can** reproduce them.
   - However, H2's table-level locks might serialize some writes, lowering the reproduction probability. Countermeasures: ① Concurrency N≥8; ② If necessary, when instrumentation on the request path is impossible, increase N or repeat rounds; ③ **Print actual observed values when assertions fail** ("expected balance X, actual Y, lost Z transfers"), the red light is the evidence.
   - Honest conclusion phrasing: Reproduced in H2 → Postgres will definitely have it (maybe even worse); Not reproduced in H2 ≠ Postgres is safe.

2. **dispatch HttpClient pool pollution**: Concurrent sharing of `Http.default` sporadically causes `"invalid version format"`,
  A retry-once fallback is already in place (`SendServerRequests.scala:154`). → Keep concurrency N around 5-10, tolerate sporadic retries.

3. **Shared server/DB/pool (`forkMode=once`)**: All suites share a single H2 + Hikari pool.
   → Isolate using dedicated prefixes for bank/account/user; cleanup using `wipeTestData()` in `afterEach`; Changing pool size for G1 must use `setPropsValues` (`PropsReset` automatically restores it in `afterEach`, preventing leaks to other suites).

4. **Red light isolation (Assertion stance = necessary companion to exposing hazards)**: These tests are **expected to FAIL**.
   - Tag all with `ConcurrencyRace`.
   - Running manually locally: `-n code.concurrency.ConcurrencyRace` (only run these diagnostic tests).
   - **Exclude** from CI main flow: The catch-all shard will automatically pick up the `code.concurrency` package → You must add `-l code.concurrency.ConcurrencyRace` (ScalaTest exclude tag) to the CI scalatest invocation, otherwise the catch-all shard will go red.
     (This CI change is listed as a follow-up; we will deliver the tests themselves first.)

5. **request-scoped transaction scope**: Full transactions apply only to v7 native POST/PUT/DELETE; v1-v6 routed through the bridge are committed independently per `DB.use` (the race window is more obvious). The race condition happens at the connector layer, independent of the API version → Just pick any practically available endpoint.

## File Checklist

Added (tests only, no business code touched):
- `obp-api/src/test/scala/code/concurrency/ConcurrentRaceSetup.scala` — base trait + `ConcurrencyRace` tag + helpers
- `obp-api/src/test/scala/code/concurrency/ConcurrentTransferRaceTest.scala` — A Balance / B State machine double-spending
- `obp-api/src/test/scala/code/concurrency/ConcurrentDuplicateCreationTest.scala` — C/D/E/F Duplicate creation and state machines
- `obp-api/src/test/scala/code/concurrency/ConcurrentConnectionMechanismTest.scala` — G Connection pool/crosstalk (optional tier)

Package `code.concurrency` → Falls into the CI catch-all shard (requires exclude tag configuration, see Constraint 4).

## How to Verify (How to run)

1. Compile and run this batch of diagnostic tests independently (local, JDK 11):
   ```sh
   env JAVA_HOME=$JDK11 PATH=$JDK11/bin:$PATH \
     mvn -q -pl obp-commons,obp-api process-resources scalatest:test \
     -DfailIfNoTests=false \
     -Dsuites="code.concurrency.ConcurrentTransferRaceTest code.concurrency.ConcurrentDuplicateCreationTest code.concurrency.ConcurrentConnectionMechanismTest"
   ```
2. Read the output: The failure message for each race scenario should clearly print "expected vs actual" (e.g., "expected balance 899999...000, got 899999...XXX, lost N transfers"), the red light acts as evidence of the hazard.
3. If a scenario sporadically goes green on H2 (masked by table lock serialization), increase the concurrency N / repetition rounds, and annotate the H2 limitation in the test comments.
4. Confirm no pollution to other suites: `afterEach` cleanup + `PropsReset` pool size restoration.

## Next Steps (Not doing now, just recording the direction)

Fix directions (separate PR): A/B → `SELECT ... FOR UPDATE` or optimistic lock version column + state machine atomic CAS (INITIATED→PROCESSING);
C/D → Add `UniqueIndex` + application layer catches constraint conflict and returns gracefully; F → Application layer catches existing unique conflict.
The red-light tests produced by this plan will serve as the regression baseline for these fixes.
