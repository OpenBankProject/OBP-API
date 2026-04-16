# Project Instructions

## Working Style
- Never blame pre-existing issues or other commits. No excuses, no finger-pointing — diagnose and resolve.

## Architecture (Onboarding)

v7.0.0 is a Lift Web → http4s migration. Not a replacement for v6.0.0 yet — 21 of 633 endpoints migrated.

**Request priority chain** (Http4sServer): `corsHandler` (OPTIONS) → StatusPage → Http4s500 → Http4s700 → Http4sBGv2 → Http4sLiftWebBridge (Lift fallback). Unhandled `/obp/v7.0.0/*` paths fall through silently to Lift — they do not 404.

**Key files**: `Http4s700.scala` (endpoints), `Http4sSupport.scala` (EndpointHelpers + recordMetric), `ResourceDocMiddleware.scala` (auth, entity resolution, transaction wrapper), `RequestScopeConnection.scala` (DB transaction propagation to Futures).

**Migrated endpoints** (21): root, getBanks, getCards, getCardsForBank, getResourceDocsObpV700, getBank, getCurrentUser, getCoreAccountById, getPrivateAccountByIdFull, getExplicitCounterpartyById, deleteEntitlement, addEntitlement, getFeatures, getScannedApiVersions, getConnectors, getProviders, getUsers, getCustomersAtOneBank, getCustomerByCustomerId, getAccountsAtBank, getUserByUserId.

**Tests**: `Http4s700RoutesTest` (69 scenarios, port 8087). `makeHttpRequest` returns `(Int, JValue, Map[String, String])`. `makeHttpRequestWithBody(method, path, body, headers)` for POST/PUT.

## Migrating a v6.0.0 Endpoint to v7.0.0

### Rule 1 — ResourceDoc registration
```scala
// Declare val FIRST, then register — see Rule 5 why order matters
val myEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] { ... }

resourceDocs += ResourceDoc(
  null,                     // always null — no Lift endpoint ref
  implementedInApiVersion,
  nameOf(myEndpoint),
  "GET", "/some/path", "Summary", """Description""",
  EmptyBody, responseJson,
  List(UnknownError),
  apiTagFoo :: Nil,
  Some(List(canDoThing)),
  http4sPartialFunction = Some(myEndpoint)
)
```

### Rule 2 — Endpoint signature
```scala
val myEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
  case req @ GET -> `prefixPath` / "some" / "path" =>
    EndpointHelpers.executeAndRespond(req) { cc =>
      for { ... } yield json   // no HttpCode wrapper
    }
}
```
Drop `implicit val ec = EndpointContext(Some(cc))` — not needed in http4s path.

### Rule 3 — What middleware replaces

| v6.0.0 inline | v7.0.0 | Available as |
|---|---|---|
| `authenticatedAccess(cc)` | `$AuthenticatedUserIsRequired` in error list | `user` via `withUser` |
| `hasEntitlement(...)` | `Some(List(canXxx))` in ResourceDoc roles | — (middleware 403s) |
| `getBank(bankId, cc)` | `BANK_ID` in URL template | `cc.bank.get` |
| `checkBankAccountExists(...)` | `ACCOUNT_ID` in URL template | `cc.bankAccount.get` |
| `checkViewAccessAndReturnView(...)` | `VIEW_ID` in URL template | `cc.view.get` |
| `getCounterpartyTrait(...)` | `COUNTERPARTY_ID` in URL template | `cc.counterparty.get` |

Middleware resolves only these 4 uppercase segments. Non-standard path vars (USER_ID, ENTITLEMENT_ID, etc.) must be extracted from the route pattern directly.

### Rule 4 — EndpointHelper selection

**GET → 200**
```scala
EndpointHelpers.executeAndRespond(req) { cc => ... }                           // no auth
EndpointHelpers.withUser(req) { (user, cc) => ... }                            // user only
EndpointHelpers.withBank(req) { (bank, cc) => ... }                            // bank only
EndpointHelpers.withUserAndBank(req) { (user, bank, cc) => ... }               // user + bank
EndpointHelpers.withBankAccount(req) { (user, account, cc) => ... }            // + ACCOUNT_ID
EndpointHelpers.withView(req) { (user, account, view, cc) => ... }             // + VIEW_ID
EndpointHelpers.withCounterparty(req) { (user, account, view, cp, cc) => ... } // + COUNTERPARTY_ID
```
**POST → 201**: `executeFutureWithBodyCreated[B,A]` / `withUserAndBodyCreated[B,A]` / `withUserAndBankAndBodyCreated[B,A]`  
**PUT → 200**: `executeFutureWithBody[B,A]` / `withUserAndBody[B,A]` / `withUserAndBankAndBody[B,A]`  
**DELETE → 204**: `executeDelete` / `withUserDelete` / `withUserAndBankDelete`

### Rule 5 — `allRoutes` ordering invariant (critical)
`val myEndpoint` MUST be declared BEFORE its `resourceDocs +=` line. If reversed, Scala's initializer stores `Some(null)` → NPE kills the entire `baseServices` chain → every request returns 500, including v6 fallback routes.

## Tricky Parts (Gotchas)

**View permissions**: `view.canGetCounterparty` (MappedBoolean) always returns `false` for system views. Use `view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY)` instead.

**BankExtended**: `privateAccountsFuture`, `privateAccounts`, `publicAccounts` are on `code.model.BankExtended`, not `commons.Bank`. Wrap: `code.model.BankExtended(bank).privateAccountsFuture(...)`.

**Query params in v7**: Use `req.uri.renderString` in place of `cc.url`. For raw map: `req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }` — `.toList` required; don't use `req.uri.query.pairs` (wrong shape).

**Response field names** (non-obvious):
- `getBank` → `bank_id` (not `id`), `full_name` (not `short_name`)
- `getCoreAccountById` → `account_id` (not `id`); also: `bank_id`, `label`, `number`, `product_code`, `balance`, `account_routings`, `views_basic`
- `getPrivateAccountByIdFull` → `id` (correct); also: `views_available`, `balance`
- `getCurrentUser` → `user_id`, `username`, `email`

**Counterparty test setup**: `createCounterparty` only creates `MappedCounterparty`. Must also call `Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterpartyId, counterpartyName)` or endpoint returns 400 `CounterpartyNotFoundByCounterpartyId`.

**System owner view** (`"owner"`) has `CAN_GET_COUNTERPARTY` and is granted to `resourceUser1` on all test accounts — safe to use as VIEW_ID in tests.

**`Full(user)` wrapping**: `NewStyle.function.moderatedBankAccountCore` takes `Box[User]` — pass `Full(user)`.

**ResourceDoc example body**: never pass `null` to a factory method — use an inline literal or `EmptyBody`.

**Users import clash**: `code.users.{Users => UserVend}` to avoid clash with `commons.model.User`.

**Test helper placement**: `private def createTestCustomer(...)` must be at class level, never inside a `feature` block (invalid Scala).

**Standard 3-scenario pattern** for role-gated endpoints:
1. Unauthenticated → 401 (`AuthenticatedUserIsRequired`)
2. Authenticated, no role → 403 (`UserHasMissingRoles` + role name)
3. Authenticated with role + test data → 200 with field shape check

**Creating test data**: use provider directly — e.g. `CustomerX.customerProvider.vend.addCustomer(...)`. Do not call v6 endpoints via HTTP in v7 tests.

**CI**: Tests run with `mvn test -DwildcardSuites="..."`. `hikari.maximumPoolSize=20` required in test props for concurrent tests (`withRequestTransaction` holds 1 connection per request; rate-limit queries need a 2nd → pool of 10 exhausts at 5 concurrent requests).

## CI Performance Profile

Measured from a 3-shard run (2691 tests total, all passing). Numbers are stable across shards.

### Time budget per shard (~9–11 min total)

| Phase | Time | % of total |
|---|---|---|
| Main compile (Zinc) | ~130s | ~22% |
| Test compile (Zinc) | ~68s | ~11% |
| Test discovery (ScalaTest) | ~20s | ~3% |
| **Test execution** | **~340–420s** | **~60–64%** |

Compile times are consistent across all three shards — Zinc cache restores correctly. Test execution is the dominant cost.

### http4s v7 vs Lift — per-test speed

| Category | Tests | Avg/test |
|---|---|---|
| http4s v7 — unit/pure (no server) | 172 | **0.008s** |
| http4s v7 — integration (real server) | 160 | 0.418s |
| Lift v4 | 515 | 0.448s |
| Lift v3 | 269 | 0.446s |
| Lift v5 | 337 | 0.432s |
| Lift v1 | 431 | 0.425s |
| Lift v2 | 124 | 0.414s |
| Lift v6 | 314 | 0.411s |

At the integration level both frameworks are similarly server/DB-bound (~0.32–0.45 s/test). The real http4s gain is the **unit/pure tier** — tests that don't need a running server are 54× faster. As more logic moves into pure functions (request parsing, response building, auth checks) these unit tests replace integration tests and the savings compound.

The 5 integration suites (160 tests, 66.9s total):
- `obp-api/src/test/scala/code/api/http4sbridge/Http4sLiftBridgePropertyTest.scala` — 51 tests, 31.9s
- `obp-api/src/test/scala/code/api/v7_0_0/Http4s700RoutesTest.scala` — 75 tests, 23.8s
- `obp-api/src/test/scala/code/api/http4sbridge/Http4sServerIntegrationTest.scala` — 16 tests, 5.0s
- `obp-api/src/test/scala/code/api/v5_0_0/Http4s500SystemViewsTest.scala` — 13 tests, 4.4s
- `obp-api/src/test/scala/code/api/v7_0_0/Http4s700TransactionTest.scala` — 5 tests, 1.9s

The 12 pure-unit suites (172 tests, 1.3s total):
- `obp-api/src/test/scala/code/api/util/http4s/Http4sCallContextBuilderTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/Http4sResponseConversionTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/Http4sResponseConversionPropertyTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/Http4sRequestConversionPropertyTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/ResourceDocMatcherTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/Http4sConfigUtilTest.scala`
- `obp-api/src/test/scala/code/api/util/http4s/RequestScopeConnectionTest.scala`
- `obp-api/src/test/scala/code/api/berlin/group/v2/Http4sBGv2AISTest.scala`
- `obp-api/src/test/scala/code/api/berlin/group/v2/Http4sBGv2PISTest.scala`
- `obp-api/src/test/scala/code/api/berlin/group/v2/Http4sBGv2ResourceDocTest.scala`
- `obp-api/src/test/scala/code/api/berlin/group/v2/Http4sBGv2PIISTest.scala`
- `obp-api/src/test/scala/code/api/v5_0_0/Http4s500RoutesTest.scala`

### Known bottlenecks

**`API1_2_1Test`** (Lift v1) — 143s for 323 tests, 36% of shard2's entire test time. Larger than the full http4s v7 budget. The first test in the suite (`"base line URL works"`) takes 0.97s — Lift's lazy init cost. Moving this suite to its own shard would reduce pipeline wall-clock by ~90s.

**`Http4sLiftBridgePropertyTest`** — 31.9s for 51 tests. Property 7 ("Session and Context Adapter Correctness") accounts for 13.4s of that: three ScalaCheck properties exercise concurrent requests through the Lift/http4s bridge, hitting real lock contention between Lift's session manager and the http4s fiber scheduler. Property 7.4 alone is 8.54s. These are the most meaningful slow tests — they exercise a genuine concurrency boundary.

**`ResourceDocsTest` / `SwaggerDocsTest`** — 34s + 24s = 58s, averaging 0.85s/test — the slowest per-test cost in the suite. Each test serializes the entire API surface (633+ endpoints) into JSON/Swagger. Cost scales linearly with endpoint count. Will worsen as the http4s migration adds endpoints unless ResourceDoc serialization is cached or the heavy tests are isolated.

### Shard assignment

Shards are defined by explicit package-prefix allowlists in `.github/workflows/build_pull_request.yml` (lines 89–143). Shard 4 also runs a **catch-all**: any `.scala` test file whose package is not covered by shards 1–3 is appended automatically at runtime — new packages are never silently skipped. Extras are printed in the step log under `"Catch-all extras added to shard 4:"`.

| Package prefix | Shard |
|---|---|
| `code.api.v4_0_0` | 1 |
| `code.api.v6_0_0`, `code.api.v5_0_0`, `code.api.v3_0_0`, `code.api.v2_*`, `code.api.v1_[34]_0`, `code.api.UKOpenBanking`, `code.atms`, `code.branches`, `code.products`, `code.crm`, `code.accountHolder`, `code.entitlement`, `code.bankaccountcreation`, `code.bankconnectors`, `code.container` | 2 |
| `code.api.v1_2_1`, `code.api.ResourceDocs1_4_0`, `code.api.util`, `code.api.berlin`, `code.management`, `code.metrics`, `code.model`, `code.views`, `code.usercustomerlinks`, `code.customer`, `code.errormessages` | 3 |
| `code.api.v5_1_0`, `code.api.v3_1_0`, `code.api.http4sbridge`, `code.api.v7_0_0`, `code.api.Authentication*`, `code.api.DirectLoginTest`, `code.api.dauthTest`, `code.api.gateWayloginTest`, `code.api.OBPRestHelperTest`, `code.util`, `code.connector` | 4 |
| anything else | **4** (catch-all) |

To explicitly move a package to a different shard, add it to that shard's `test_filter` block — it will be excluded from the catch-all automatically.

### Implication for the migration

Per-endpoint integration test cost stays roughly constant as endpoints move Lift → http4s (both bound by DB + HTTP). Gains appear from: (1) pure unit tests replacing integration tests, (2) eventual removal of Lift endpoint tests when v6 is retired. ResourceDocs overhead is the one cost that compounds — needs caching before the migration is complete.

## TODO / Phase Progress

### Phase 1 — Simple GETs (~192 remaining)
GET + no body. Purely mechanical — 1:1 copy of `NewStyle.function.*` calls, pick helper from Rule 4 matrix, 3 test scenarios per endpoint (401 / 403 / 200).

| Batch | Endpoints | Status |
|---|---|---|
| Batches 1–3 | 9 endpoints | ✓ done |
| Remaining | ~192 endpoints | todo |

### Phase 2 — Account/View/Counterparty GETs (~30 endpoints)
`withBankAccount` / `withView` / `withCounterparty` helpers ready. Same mechanical pattern.

### Phase 3 — POST / PUT / DELETE (~256 endpoints)
Body helpers and DELETE 204 helpers ready. Velocity: 6–8 endpoints/day.

### Phase 4 — Complex endpoints (~50 endpoints)
Dynamic entities, ABAC rules, mandate workflows, polymorphic bodies. ~45–60 min each.

### Other TODOs
- **OBP-Trading** (at `/home/marko/Tesobe/GitHub/constantine2nd/OBP-Trading`): pending team decision — port trading endpoints into `Http4s700.scala` or keep as a separate service that OBP-API proxies to. Connectors (`ObpApiUserConnector`, `ObpPaymentsConnector`) are currently in-memory stubs.
- **CI speed-up** (not done): two-tier fast gate + full suite; surefire parallel forks.
- **Disabled tests to fix**: `Http4s500RoutesTest` (@Ignore, in-process issue), `RootAndBanksTest` (@Ignore), `V500ContractParityTest` (@Ignore), `CardTest` (fully commented out). `v5_0_0`: 13 skipped tests (setup cost paid, no value).
