# Project Instructions

## Working Style
- Never blame pre-existing issues or other commits. No excuses, no finger-pointing — diagnose and resolve.
- Never add `Co-Authored-By` trailers to commit messages.
- **Goal is full http4s migration** — eliminate Lift Web and all deprecated libraries entirely. Treat Lift code as temporary scaffolding to be removed, not maintained. When fixing bugs or adding features, always prefer the http4s path.
- **Versioning is tech-agnostic** — API version numbers reflect API signature changes (new/changed fields, new behaviour), never the underlying framework. A framework migration (Lift → http4s) happens in-place at the existing version; it does not justify a version bump.

## Architecture (Onboarding)

> **Migration plan**: see [`LIFT_HTTP4S_MIGRATION.md`](LIFT_HTTP4S_MIGRATION.md) for the full in-place Lift → http4s strategy, file order, auth stack workstream, and progress tracker.

The goal is a full http4s migration — replace Lift Web across all version files and remove it entirely. **API versions are tech-agnostic**: a version bump means a changed/new API signature, never a framework change. Framework migration happens in-place inside the existing version file. v7.0.0 currently serves 45 endpoints; most arrived there for historical reasons and stay as-is.

**Request priority chain** (Http4sServer): `corsHandler` (OPTIONS) → StatusPage → Http4s500 → Http4s700 → Http4sBGv2 → Http4s200 → Http4s140 → Http4s130 → Http4s121 → Http4sLiftWebBridge (Lift fallback). Unhandled `/obp/v7.0.0/*` paths fall through silently to Lift — they do not 404.

**Key files**: `Http4s700.scala` (v7.0.0 endpoints), `Http4s200.scala` (v2.0.0 endpoints — 37 own + path-rewriting bridge to Http4s140), `Http4s140.scala` (v1.4.0 endpoints — 11 own + path-rewriting bridge to Http4s130), `Http4s130.scala` (v1.3.0 endpoints — 3 own + path-rewriting bridge to Http4s121), `Http4s121.scala` (v1.2.1 endpoints — all 323 API1_2_1Test scenarios), `Http4sSupport.scala` (EndpointHelpers + recordMetric), `ResourceDocMiddleware.scala` (auth, entity resolution, transaction wrapper), `IdempotencyMiddleware.scala` (Redis-backed idempotency, opt-in via `Idempotency-Key` header, nested inside ResourceDocMiddleware), `RequestScopeConnection.scala` (DB transaction propagation to Futures).

**Migrated endpoints** (45): root, getBanks, getCards, getCardsForBank, getResourceDocsObpV700, getBank, getCurrentUser, getCoreAccountById, getPrivateAccountByIdFull, getExplicitCounterpartyById, deleteEntitlement, addEntitlement, getAccountAccessTrace, getFeatures, getScannedApiVersions, getConnectors, getErrorMessages, getProviders, getUsers, getUserByUserId, getCustomersAtOneBank, getCustomerByCustomerId, getAccountsAtBank, createTradingOffer, getTradingOffer, getTradingOffers, cancelTradingOffer, createMarketOrder, getMarketOrder, cancelMarketOrder, createMarketMatch, getMarketTrade, requestSettlement, requestWithdrawal, getCacheConfig, getCacheInfo, getDatabasePoolInfo, getStoredProcedureConnectorHealth, getMigrations, getCacheNamespaces, createOrganisation, getOrganisations, getOrganisation, updateOrganisation, deleteOrganisation.

**Tests**: `Http4s700RoutesTest` (111 scenarios, port 8087). `makeHttpRequest` returns `(Int, JValue, Map[String, String])`. `makeHttpRequestWithBody(method, path, body, headers)` for POST/PUT.

## Migrating a Lift Endpoint to http4s

Rules apply regardless of which version file the endpoint lives in. Use v7.0.0 only when the API signature is new or changed; otherwise migrate in-place in the original version file.

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
**POST → 201**: `executeFutureWithBodyCreated[B,A]` / `withUserAndBodyCreated[B,A]` / `withUserAndBankAndBodyCreated[B,A]` / `withViewCreated[A]` (when view context is needed)  
**PUT → 200**: `executeFutureWithBody[B,A]` / `withUserAndBody[B,A]` / `withUserAndBankAndBody[B,A]`  
**DELETE → 204**: `executeDelete` / `withUserDelete` / `withUserAndBankDelete`

### Rule 5 — `allRoutes` ordering invariant (critical)
`val myEndpoint` MUST be declared BEFORE its `resourceDocs +=` line. If reversed, Scala's initializer stores `Some(null)` → NPE kills the entire `baseServices` chain → every request returns 500, including v6 fallback routes.

## Tricky Parts (Gotchas)

**Conditional role check (403)**: `NewStyle.function.hasEntitlement` uses `booleanToFuture` with default `failCode = 400`, which gives 400 instead of 403 when the role is missing. For conditional checks (e.g. only needed when creating for another user), keep ResourceDoc roles `None` and call `booleanToFuture` directly:
```scala
_ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
     else code.util.Helper.booleanToFuture(
       s"$UserHasMissingRoles $canCreateAccount", failCode = 403, cc = Some(cc)) {
       APIUtil.hasEntitlement(bankId, loggedInUserId, canCreateAccount)
     }
```

**View permissions**: `view.canGetCounterparty` (MappedBoolean) always returns `false` for system views. Use `view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY)` instead.

**BankExtended**: `privateAccountsFuture`, `privateAccounts`, `publicAccounts` are on `code.model.BankExtended`, not `commons.Bank`. Wrap: `code.model.BankExtended(bank).privateAccountsFuture(...)`.

**Query params in v7**: Use `req.uri.renderString` in place of `cc.url`. For raw map: `req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }` — `.toList` required; don't use `req.uri.query.pairs` (wrong shape).

**Response field names** (non-obvious):
- `getBank` → `bank_id` (not `id`), `full_name` (not `short_name`)
- `getCoreAccountById` → `account_id` (not `id`); also: `bank_id`, `label`, `number`, `product_code`, `balance`, `account_routings`, `views_basic`
- `getPrivateAccountByIdFull` → `id` (correct); also: `views_available`, `balance`
- `getCurrentUser` → `user_id`, `username`, `email`

**Counterparty test setup**: `createCounterparty` only creates `MappedCounterparty`. Must also call `Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterpartyId, counterpartyName)` or endpoint returns 400 `CounterpartyNotFoundByCounterpartyId`.

**`StoredProcedureUtils` in tests**: `StoredProcedureUtils` has a constructor block that requires `stored_procedure_connector.*` props. In the test environment these aren't set, so the first access to the object (inside `Future { StoredProcedureUtils.getHealth() }`) throws and returns 500. Only test the 401/403 scenarios for `getStoredProcedureConnectorHealth` — skip the 200 scenario.

**`resource-docs` version dispatch**: `GET /obp/v7.0.0/resource-docs/API_VERSION/obp` accepts any valid API version string. Delegates to `ResourceDocs140.ImplementationsResourceDocs.getResourceDocsList(requestedApiVersion)` which dispatches per version (v7.0.0 → `Http4s700.resourceDocs`, v6.0.0 → `OBPAPI6_0_0.allResourceDocs`, etc.). An invalid/unknown version string returns 400.

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

**`NewStyle.function.getBankAccount` returns 404**: The `unboxFullOrFail` inside hardcodes code 404. When your endpoint must return 400 for a missing account (e.g. v1.2.1 tests), bypass it: use `Connector.connector.vend.checkBankAccountExists(bankId, accountId, cc)` then `Future { unboxFullOrFail(rawBox, cc, msg) }` — the default code is 400.

**Middleware URL template bypass** (non-standard uppercase vars): `validateAccount` checks `pathParams.get("ACCOUNT_ID")` and `validateView` checks `pathParams.get("VIEW_ID")` by exact key. Any other all-caps segment (e.g. `BANK_ACCOUNT_ID`, `CUSTOM_VIEW_ID`, `GRANT_VIEW_ID`, `NEW_ACCOUNT_ID`, `VIEW_ACCOUNT_ID`, `UPD_VIEW_ID`) is still matched as a template variable (wildcard) but skips the 404/403 validation. Use this when your handler does inline validation returning 400 but middleware would return 404 or 403 first.

For IO-based handlers that bypass `ACCOUNT_ID`, look up the account inline and return 400 for missing accounts (matching Lift behaviour):
```scala
// ResourceDoc URL: "/banks/BANK_ID/accounts/VIEW_ACCOUNT_ID/views"
case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / accountIdStr / "views" =>
  implicit val cc: CallContext = req.callContext
  val io = for {
    bank    <- IO.fromOption(cc.bank)(new RuntimeException(BankNotFound))
    rawBox  <- IO.fromFuture(IO(Connector.connector.vend.checkBankAccountExists(bank.bankId, AccountId(accountIdStr), Some(cc)).map(_._1)))
    account <- IO(unboxFullOrFail(rawBox, Some(cc), BankAccountNotFound))   // default emptyBoxErrorCode=400
    ...
  } yield result
```
`checkBankAccountExists` returns `OBPReturnType[Box[BankAccount]]` = `Future[(Box[BankAccount], Option[CC])]`. Extract the `Box` with `.map(_._1)`. `unboxFullOrFail` with default `emptyBoxErrorCode=400` throws a JSON-encoded 400 exception that `ErrorResponseConverter` parses correctly.

**Auth failure status code — Old Style vs New Style**: `ResourceDocMiddleware.authenticate` returns 400 for auth failures (locked user, invalid DAuth JWT) on Old Style endpoints (v1.2.1, v1.3.0, v1.4.0, v2.0.0) and 401 on New Style endpoints (v2.1.0+). The version check is in the `case Left(e)` branch: `oldStyleShortVersions.contains(resourceDoc.implementedInApiVersion.apiShortVersion)`. `anonymousAccess` always converts Failure boxes to `Exception(json_of_APIFailureNewStyle)` with `failCode=401` via `fullBoxOrException`. The Old Style 400 is a deliberate override for backward compatibility.

**`ResourceDoc` description and `needsAuthentication`**: The `ResourceDoc` constructor removes `AuthenticatedUserIsRequired` from `errorResponseBodies` when `description.contains(authenticationIsOptional) && rolesIsEmpty`. `needsAuthentication = errorResponseBodies.contains($AuthenticatedUserIsRequired) || roles.nonEmpty`. If the description embeds `${userAuthenticationMessage(false)}` (which includes `authenticationIsOptional`) and roles are empty, the error is silently removed → `needsAuthentication=false` → anonymous access → unauthenticated requests reach the handler. Fix: remove `${userAuthenticationMessage(false)}` from the description when `AuthenticatedUserIsRequired` must remain in the error list.

**v1.2.1 test framework sends filter params as HTTP headers**: `makeGetRequest(req, params)` puts `params` into `extra_headers`, not the URL query string. This means `obp_limit`, `obp_sort_direction`, `obp_from_date`, etc. arrive as request headers. Do NOT use `createHttpParamsByUrl(req.uri.renderString)` — it only scans the URL for non-prefixed names. Instead: `req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value))`, then pass to `createQueriesByHttpParamsFuture`.

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

The 6 integration suites (pre-merge timings; Http4s700RoutesTest has grown to 111 scenarios):
- `obp-api/src/test/scala/code/api/http4sbridge/Http4sLiftBridgePropertyTest.scala` — 51 tests, 31.9s
- `obp-api/src/test/scala/code/api/v7_0_0/Http4s700RoutesTest.scala` — 111 tests (was 75, 23.8s pre-merge)
- `obp-api/src/test/scala/code/api/v7_0_0/V7ResourceDocsAggregationTest.scala` — intentionally failing until resource-docs aggregation bug is fixed
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

**`API1_2_1Test`** (now http4s-backed via `Http4s121`) — was 143s for 323 tests on the Lift path; expected to improve as Lift bridge overhead is eliminated. The suite is in shard 3 (`code.api.v1_2_1` prefix).

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

### Phase 1 — Simple GETs (98 remaining in v6.0.0)
GET + no body. Purely mechanical — 1:1 copy of `NewStyle.function.*` calls, pick helper from Rule 4 matrix, 3 test scenarios per endpoint (401 / 403 / 200).

| Batch | Endpoints | Status |
|---|---|---|
| Batches 1–3 | 9 endpoints | ✓ done |
| Batch 4 | getCacheConfig, getCacheInfo, getDatabasePoolInfo, getStoredProcedureConnectorHealth, getMigrations, getCacheNamespaces | ✓ done |
| Remaining | 98 GETs | todo |

### Phase 2 — Account/View/Counterparty GETs (subset of the 98 above)
`withBankAccount` / `withView` / `withCounterparty` helpers ready. Same mechanical pattern.

### Phase 3 — POST / PUT / DELETE (57 + 33 + 26 = 116 endpoints in v6.0.0)
Body helpers and DELETE 204 helpers ready. Velocity: 6–8 endpoints/day.

### Phase 4 — Complex endpoints (~50 endpoints)
Dynamic entities, ABAC rules, mandate workflows, polymorphic bodies. ~45–60 min each.

### Other TODOs
- **OBP-Trading**: trading endpoints (createTradingOffer, getTradingOffer, getTradingOffers, cancelTradingOffer, createMarketOrder, getMarketOrder, cancelMarketOrder, createMarketMatch, getMarketTrade, requestSettlement, requestWithdrawal) are now in `Http4s700.scala`. 5 payment-auth endpoints remain commented out (notifyDeposit, createPaymentAuth, capturePaymentAuth, releasePaymentAuth, getPaymentAuth) — see `ideas/CAPTURE_RELEASE_TRANSACTION_REQUEST_TYPES.md`.
- **CI speed-up** (not done): two-tier fast gate + full suite; surefire parallel forks.
- **Disabled tests to fix**: `Http4s500RoutesTest` (@Ignore, in-process issue), `RootAndBanksTest` (@Ignore), `V500ContractParityTest` (@Ignore), `CardTest` (fully commented out). `v5_0_0`: 13 skipped tests (setup cost paid, no value).
- **`V7ResourceDocsAggregationTest`**: intentionally failing — encodes the fix for the resource-docs aggregation bug (v7 endpoint returns only ~10 own docs instead of 500+ aggregated). Fix the bug to make this suite pass.
