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
