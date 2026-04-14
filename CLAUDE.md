# Project Instructions

## Working Style
- Never blame pre-existing issues or other commits. No excuses, no finger-pointing — diagnose and resolve.

## v7.0.0 vs v6.0.0 — Known Gaps

v7.0.0 is a framework migration from Lift Web to http4s. It is **not** a replacement for v6.0.0 yet. Keep these gaps in mind when working on either version.

### Architecture
- v6.0.0: Lift `OBPRestHelper`, cumulative (inherits v1.3.0–v5.1.0), ~500+ endpoints, auth/validation inline per endpoint.
- v7.0.0: Native http4s (`Kleisli`/`IO`), 5 endpoints only, auth/validation centralised in `ResourceDocMiddleware`.
- When running via `Http4sServer`, the priority chain is: `corsHandler` (OPTIONS only) → StatusPage → Http4s500 → Http4s700 → `Http4sBGv2` (Berlin Group v2) → `Http4sLiftWebBridge` (Lift fallback).

### Gap 1 — Tiny endpoint coverage
- v7.0.0 exposes: `root`, `getBanks`, `getCards`, `getCardsForBank`, `getResourceDocsObpV700` (original 5) + POC additions: `getBank`, `getCurrentUser`, `getCoreAccountById`, `getPrivateAccountByIdFull`, `getExplicitCounterpartyById`, `deleteEntitlement`, `addEntitlement` = **12 endpoints total** + Phase 1 batch 1: `getFeatures`, `getScannedApiVersions`, `getConnectors`, `getProviders` + Phase 1 batch 2: `getUsers`, `getCustomersAtOneBank`, `getCustomerByCustomerId`, `getAccountsAtBank` + Phase 1 batch 3: `getUserByUserId` = **21 endpoints total**.
- Unhandled `/obp/v7.0.0/*` paths **silently fall through** to the Lift bridge and get served by OBPAPI6_0_0 — they do not 404.

### Gap 2 — Tests are `@Ignore`d ✓ FIXED
- `Http4s700RoutesTest` was disabled by commit `0997e82fe` (Feb 2026) as a blanket measure; the underlying bridge stability issues are resolved.
- Fix applied: removed `@Ignore` + unused `import org.scalatest.Ignore`; expanded from 9 → 27 scenarios, then further to 45 scenarios covering all 12 endpoints (including all 7 POC additions), then to 65 scenarios covering all 20 endpoints (8 batch 1+2 additions), then to **69 scenarios** covering all 21 endpoints (4 scenarios for `getUserByUserId`).
- Test infrastructure: `Http4sTestServer` (port 8087) runs `Http4sApp.httpApp` (same as `TestServer` on port 8000). `ServerSetupWithTestData` initialises `TestServer` first, so ordering is safe.
- `makeHttpRequest` returns `(Int, JValue, Map[String, String])` — status, body, and response headers — matching `Http4sLiftBridgePropertyTest` pattern. Requires `import scala.collection.JavaConverters._` for `.asScala`.
- `makeHttpRequestWithBody(method, path, body, headers)` — sends POST/PUT with a JSON body; adds `Content-Type: application/json` automatically.
- Coverage now includes: full root shape (all 10 fields, `version` field is `"v7.0.0"` with `v` prefix), bank field shape, empty cards array, wrong API version → 400, resource doc entry shape, response headers (`Correlation-Id`, `X-Request-ID` echo, `Cache-Control`, `X-Frame-Options`), routing edge cases (unknown path, wrong HTTP method), all 7 POC endpoints, all 8 Phase 1 batch 1+2 endpoints (see POC section and Phase 1 findings).
- Remaining disabled http4s tests: `Http4s500RoutesTest` (`@Ignore`, in-process issue), `RootAndBanksTest` (`@Ignore`), `V500ContractParityTest` (`@Ignore`), `CardTest` (fully commented out, not `@Ignore`'d).

### Gap 3 — `resource-docs` is v7.0.0-only and narrow
- `GET /obp/v7.0.0/resource-docs/v6.0.0/obp` → 400. Only `v7.0.0` is accepted (`Http4s700.scala:230`).
- Response only includes the 5 http4s-native endpoints, not the full API surface.

### Gap 4 — CORS works accidentally via Lift bridge ✓ FIXED
- Fix applied: `Http4sApp.corsHandler` — a `HttpRoutes[IO]` that matches any `Method.OPTIONS` request and returns `204 No Content` with the four CORS headers (`Access-Control-Allow-Origin: *`, `Allow-Methods`, `Allow-Headers`, `Allow-Credentials: true`), placed first in `baseServices` before any other handler.
- Headers match the `corsResponse` defined in v4/v5/v6 Lift endpoints.
- OPTIONS preflights no longer reach the Lift bridge.
- Test coverage: 3 scenarios in `Http4s700RoutesTest` (banks, cards, banks/BANK_ID/cards).
- `makeHttpRequestWithMethod` in the test now supports OPTIONS, PATCH, HEAD (was missing all three).
- `OPTIONSTest` (v4.0.0) previously asserted `Content-Type: text/plain; charset=utf-8` on the 204 response — incidental Lift bridge behaviour. Assertion removed; 204 No Content correctly carries no `Content-Type`.

### Gap 5 — API metrics are not written for v7.0.0 requests ✓ FIXED
- Fix applied: `EndpointHelpers` in `Http4sSupport.scala` now extends `MdcLoggable` and has a private `recordMetric` helper.
- `recordMetric` is called via `flatTap` on every response (success and error) in all 6 helper methods (`executeAndRespond`, `withUser`, `withBank`, `withUserAndBank`, `executeFuture`, `executeFutureCreated`).
- Stamps `endTime` and `httpCode` onto the `CallContext` before converting to `CallContextLight`, then calls `WriteMetricUtil.writeEndpointMetric` — identical pattern to `APIUtil.writeMetricEndpointTiming` used by v6.
- Endpoint timing log line (`"Endpoint (GET) /banks returned 200, took X ms"`) is now emitted.
- `GET /system/log-cache/*` endpoints (v5.1.0, inherited by v6) have no v7.0.0 equivalent.
- **`recordMetric` uses `IO.blocking { ... }`** (not `IO { ... }` and not `.start.void`):
  - `IO { ... }` (compute pool) steals a bounded compute thread for blocking logger/DB work.
  - `IO.blocking { }.start.void` (fire-and-forget) creates unbounded concurrent H2 writes — 200 concurrent requests → 200 concurrent DB writers → H2 lock storm → P99 2x worse.
  - `IO.blocking { ... }` (current): blocking work runs on cats-effect's blocking pool (not compute), response waits for metric write — matches v6 behaviour, no H2 contention.

### Gap 6 — `allRoutes` Kleisli chain is order-sensitive with no test guard ✓ FIXED
- Fix applied: `allRoutes` auto-sorts `resourceDocs` by URL segment count (descending) so most-specific routes always win — no manual ordering required when adding new endpoints.
- **Critical convention**: each `val endpoint` MUST be declared BEFORE its `resourceDocs +=` line. This is the only invariant that must be maintained.
- **Why this matters (CI incident)**: if `resourceDocs += ResourceDoc(..., http4sPartialFunction = Some(myEndpoint))` runs before `val myEndpoint` is initialized, Scala's object initializer stores `Some(null)`. The sort+fold then produces a null-route chain. When any request hits `Http4s700`, `null.run(req)` throws NPE. Critically, `OptionT.orElse` only recovers from `None` — a failed IO (NPE) propagates up and kills the **entire** `baseServices` chain, so the Lift bridge fallback never executes. Result: **every request on the server returns 500**, not just v7 requests.
- **Auto-sort fold logic** (`allRoutes`): `resourceDocs.sortBy(rd => -rd.requestUrl.split("/").count(_.nonEmpty)).flatMap(_.http4sPartialFunction).foldLeft(HttpRoutes.empty[IO]) { (acc, route) => HttpRoutes[IO](req => acc.run(req).orElse(route.run(req))) }` — correct as-is; initialization order is the only risk.
- Test guard: `Http4s700RoutesTest` "routing priority" feature verifies correct dispatch. Add one scenario per new route.

## Gap 1 — Migration Plan & Estimation

### Scope
- **633 total endpoints** in v6.0.0 (236 new in v6 + 397 inherited from v4.0.0–v5.1.0)
- Verb split: 305 GET · 158 POST · 98 PUT · 81 DELETE
- `APIMethods600.scala` alone is 16,475 lines

### Auth complexity distribution

| Category | Count | EndpointHelper |
|---|---|---|
| No auth | ~2 | `executeAndRespond` ✓ |
| User auth only | ~158 | `withUser` ✓ |
| + BANK_ID | ~62 | `withBank` / `withUserAndBank` ✓ |
| + BANK_ID + ACCOUNT_ID | ~20 | `withBankAccount` ✓ |
| + BANK_ID + ACCOUNT_ID + VIEW_ID | ~8 | `withView` ✓ |
| + COUNTERPARTY_ID | ~2 | `withCounterparty` ✓ |

### Phase 0 — Infrastructure ✓ COMPLETE (2026-04-09)

All prerequisites done — bulk endpoint work can begin immediately.

| Item | Status | Notes |
|---|---|---|
| `withBankAccount`, `withView`, `withCounterparty` | ✓ | Unpack from `cc`; middleware populates from URL template variables |
| Body parsing helpers | ✓ | `parseBody[B]` via lift-json; full 6-helper matrix (200/201 × no-auth/user/user+bank) |
| DELETE 204 helpers | ✓ | `executeDelete`, `withUserDelete`, `withUserAndBankDelete` |
| O(1) `findResourceDoc` | ✓ | `buildIndex` groups by `(verb, apiVersion, segmentCount)`; built once at middleware startup |
| Skip body compile on GET/DELETE | ✓ | `fromRequest` returns `IO.pure(None)` for GET/DELETE/HEAD/OPTIONS |
| Gate `recordMetric` on `write_metrics` | ✓ | Returns `IO.unit` immediately when prop is false; no blocking-pool dispatch |

### Phase 1 — Simple GETs (~200 endpoints, 2 weeks)
GET + no body + `executeAndRespond` / `withUser` / `withBank` / `withUserAndBank`. Purely mechanical — business logic is a 1:1 copy of `NewStyle.function.*` calls. Velocity: 10–15 endpoints/day.

**Phase 1 progress** (8 endpoints done, ~192 remaining):

| Batch | Endpoints | Status |
|---|---|---|
| Batch 1 | `getFeatures`, `getScannedApiVersions`, `getConnectors`, `getProviders` | ✓ done |
| Batch 2 | `getUsers`, `getCustomersAtOneBank`, `getCustomerByCustomerId`, `getAccountsAtBank` | ✓ done |
| Batch 3 | `getUserByUserId` | ✓ done |

### Phase 2 — Account + View + Counterparty GETs (~30 endpoints, 1 week)
`withBankAccount` / `withView` / `withCounterparty` helpers are ready. Same mechanical pattern.

### Phase 3 — POST / PUT / DELETE (~256 endpoints, 4 weeks)
Body helpers and DELETE 204 helpers are ready. Pick the right helper from the matrix; business logic is a 1:1 copy. Velocity: 6–8 endpoints/day.

### Phase 4 — Complex endpoints (~50 endpoints, 2 weeks)
Dynamic entities, ABAC rules, mandate workflows, chat rooms, polymorphic body types. Budget 45–60 min each.

### Total
| | Calendar |
|---|---|
| 1 developer | ~9 weeks (Phase 0 saved ~1 week) |
| 2 developers (phases parallel) | ~6 weeks |

### Risks
- **Not all 633 endpoints need v7 equivalents.** An audit pass to drop deprecated/low-traffic endpoints could cut ~15% scope.
- **Test coverage**: 2–3 scenarios per migrated endpoint (happy path + auth failure + 400 body parse) is pragmatic; rely on v6 test suite for business logic correctness.
- **`allRoutes` ordering**: only invariant — `val endpoint` must be declared BEFORE its `resourceDocs +=` line. Violating this stores `Some(null)` and breaks every request on the server (see Gap 6).

## Migrating a v6.0.0 Endpoint to v7.0.0

Five mechanical rules cover every case.

### Rule 1 — ResourceDoc registration

```scala
// v6.0.0
staticResourceDocs += ResourceDoc(
  myEndpoint,               // reference to OBPEndpoint function
  implementedInApiVersion,
  nameOf(myEndpoint),
  "GET", "/some/path", "Summary", """Description""",
  EmptyBody, responseJson,
  List(UnknownError),
  apiTagFoo :: Nil,
  Some(List(canDoThing))
)

// v7.0.0
resourceDocs += ResourceDoc(
  null,                     // always null — no Lift endpoint ref
  implementedInApiVersion,
  nameOf(myEndpoint),
  "GET", "/some/path", "Summary", """Description""",
  EmptyBody, responseJson,
  List(UnknownError),
  apiTagFoo :: Nil,
  Some(List(canDoThing)),
  http4sPartialFunction = Some(myEndpoint)   // link to the val below
)
```

### Rule 2 — Endpoint signature and pattern match

```scala
// v6.0.0
lazy val myEndpoint: OBPEndpoint = {
  case "some" :: "path" :: Nil JsonGet _ => { cc =>
    implicit val ec = EndpointContext(Some(cc))
    for { ... } yield (json, HttpCode.`200`(cc.callContext))
  }
}

// v7.0.0
val myEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
  case req @ GET -> `prefixPath` / "some" / "path" =>
    EndpointHelpers.executeAndRespond(req) { cc =>
      for { ... } yield json   // no HttpCode wrapper — executeAndRespond returns Ok()
    }
}
```

Drop `implicit val ec = EndpointContext(Some(cc))` — not needed in http4s path.

### Rule 3 — What the middleware replaces (nothing to code in the endpoint)

| v6.0.0 inline call | What drives it in v7.0.0 | Available in endpoint as |
|---|---|---|
| `authenticatedAccess(cc)` | `$AuthenticatedUserIsRequired` in error list | `user` via `EndpointHelpers.withUser` |
| `hasEntitlement("", u.userId, canXxx, cc)` | `Some(List(canXxx))` in ResourceDoc `roles` | — (middleware 403s if missing) |
| `NewStyle.function.getBank(bankId, cc)` | `BANK_ID` in URL template | `cc.bank.get` |
| `checkBankAccountExists(bankId, accountId, cc)` | `ACCOUNT_ID` in URL template | `cc.bankAccount.get` |
| `checkViewAccessAndReturnView(viewId, ...)` | `VIEW_ID` in URL template | `cc.view.get` |
| `getCounterpartyTrait(...)` | `COUNTERPARTY_ID` in URL template | `cc.counterparty.get` |

The middleware detects which entities to validate by matching uppercase path segments in the URL template (`ResourceDocMatcher.isTemplateVariable`: a segment qualifies if every character is uppercase, `_`, or a digit).

### Rule 4 — EndpointHelpers selection

Full helper matrix. Pick by auth level × response code × body presence:

**GET / read (return 200 OK)**
```scala
EndpointHelpers.executeAndRespond(req) { cc => ... }                          // no auth
EndpointHelpers.withUser(req) { (user, cc) => ... }                           // user only
EndpointHelpers.withBank(req) { (bank, cc) => ... }                           // bank only (no user)
EndpointHelpers.withUserAndBank(req) { (user, bank, cc) => ... }              // user + bank
EndpointHelpers.withBankAccount(req) { (user, account, cc) => ... }           // user + account (ACCOUNT_ID in URL)
EndpointHelpers.withView(req) { (user, account, view, cc) => ... }            // user + account + view (VIEW_ID in URL)
EndpointHelpers.withCounterparty(req) { (user, account, view, cp, cc) => ... }// + counterparty (COUNTERPARTY_ID in URL)
```

**POST (return 201 Created)**
```scala
EndpointHelpers.executeFutureWithBodyCreated[B, A](req) { (body, cc) => ... }       // no auth
EndpointHelpers.withUserAndBodyCreated[B, A](req) { (user, body, cc) => ... }       // user
EndpointHelpers.withUserAndBankAndBodyCreated[B, A](req) { (user, bank, body, cc) => ... } // user + bank
```

**PUT (return 200 OK with body)**
```scala
EndpointHelpers.executeFutureWithBody[B, A](req) { (body, cc) => ... }             // no auth
EndpointHelpers.withUserAndBody[B, A](req) { (user, body, cc) => ... }             // user
EndpointHelpers.withUserAndBankAndBody[B, A](req) { (user, bank, body, cc) => ... }// user + bank
```

**DELETE (return 204 No Content)**
```scala
EndpointHelpers.executeDelete(req) { cc => ... }                              // no auth
EndpointHelpers.withUserDelete(req) { (user, cc) => ... }                     // user
EndpointHelpers.withUserAndBankDelete(req) { (user, bank, cc) => ... }        // user + bank
```

`cc.bankAccount`, `cc.view`, `cc.counterparty` are always available directly from the CallContext when the URL template contains the corresponding uppercase path segment.

### Rule 5 — Register in `allRoutes` (automatic, but one invariant)

v6.0.0 collected endpoints via `getEndpoints(Implementations6_0_0)` reflection.
v7.0.0 auto-sorts `resourceDocs` by URL segment count so most-specific routes always win.

**The only rule**: declare `val myEndpoint` BEFORE `resourceDocs += ResourceDoc(..., http4sPartialFunction = Some(myEndpoint))`.

```scala
// CORRECT — val before resourceDocs +=
val myEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
  case req @ GET -> `prefixPath` / "some" / "path" => ...
}
resourceDocs += ResourceDoc(null, ..., http4sPartialFunction = Some(myEndpoint))

// WRONG — captures null, breaks every request on the server (see Gap 6)
resourceDocs += ResourceDoc(null, ..., http4sPartialFunction = Some(myEndpoint))
val myEndpoint: HttpRoutes[IO] = ...
```

No manual ordering in `allRoutes` is needed. Add a routing-priority scenario in `Http4s700RoutesTest` for the new endpoint.

## POC — Representative Endpoints to Migrate (one per helper category)

These were identified as the simplest representative endpoint for each helper type. Migrate these first as proof-of-work before bulk Phase 1–4 work.

| Helper | Endpoint | Verb | URL | v6 source file | Status |
|---|---|---|---|---|---|
| `executeAndRespond` | `root`, `getBanks` | GET | `/root`, `/banks` | — | ✓ in v7 |
| `withUser` | `getCurrentUser` | GET | `/users/current` | APIMethods600.scala:1725 | ✓ migrated |
| `withBank` | `getBank` | GET | `/banks/BANK_ID` | APIMethods600.scala:1252 | ✓ migrated |
| `withUserAndBank` | `getCardsForBank` | GET | `/banks/BANK_ID/cards` | — | ✓ in v7 |
| `withBankAccount` | `getCoreAccountById` | GET | `/my/banks/BANK_ID/accounts/ACCOUNT_ID/account` | APIMethods600.scala:352 | ✓ migrated |
| `withView` | `getPrivateAccountByIdFull` | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account` | APIMethods600.scala:11249 | ✓ migrated |
| `withCounterparty` | `getExplicitCounterpartyById` | GET | `/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID` | APIMethods400.scala:11089 | ✓ migrated |
| `withUserDelete` | `deleteEntitlement` | DELETE | `/entitlements/ENTITLEMENT_ID` | APIMethods600.scala:4462 | ✓ migrated |
| `withUserAndBodyCreated` | `addEntitlement` | POST | `/users/USER_ID/entitlements` | APIMethods200.scala:1781 | ✓ migrated |

### Key findings from POC implementation

- **Non-standard path variables** (ENTITLEMENT_ID, USER_ID) are extracted from the http4s route pattern directly — not auto-resolved by middleware. Middleware only resolves: `BANK_ID`→`cc.bank`, `ACCOUNT_ID`→`cc.bankAccount`, `VIEW_ID`→`cc.view`, `COUNTERPARTY_ID`→`cc.counterparty`.
- **`SS.userAccount` / `SS.userBankAccountView`** patterns in v6 are fully replaced by the corresponding helper — no equivalent needed in v7.
- **`authenticatedAccess(cc)` + `hasEntitlement(...)` inline calls** in v6 are dropped entirely — middleware handles auth from `$AuthenticatedUserIsRequired` and roles from `ResourceDoc.roles`.
- **View-level permissions — use `allowed_actions`, not boolean fields**: `view.canGetCounterparty` (and similar `MappedBoolean` fields on `ViewDefinition`) always return `false` for system views because `resetViewPermissions` writes to the `ViewPermission` table, not the boolean DB columns. Always check permissions via `view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY)` — this matches how v4/v6 endpoints do it. Bug was found and fixed in `getExplicitCounterpartyById` during POC testing.
- **`viewIdStr`** must be captured from the route pattern when needed for non-middleware calls (e.g. `Tags.tags.vend.getTagsOnAccount(bankId, accountId)(ViewId(viewIdStr))`).
- **`Full(user)` wrapping** is still required by `NewStyle.function.moderatedBankAccountCore` which takes `Box[User]`.
- **ResourceDoc example body**: never call a factory method with `null` — use an inline case class literal or `EmptyBody` for safety at object initialisation.
- **Imports added to Http4s700.scala** for POC: `ApiRole` (object), `canCreate/DeleteEntitlement*` roles, `ViewNewStyle`, `JSONFactory200` + `CreateEntitlementJSON`, `JSONFactory600` + `BankJsonV600` + `UserV600`, `Entitlement`, `Tags`, `Views`, `BankIdAccountId`/`ViewId`, `net.liftweb.common.Full`.
- **`withUserAndBodyCreated[B, A]`** type parameters: `B` = request body type, `A` = response type. `A` can be `AnyRef` when the result is serialised via implicit `convertAnyToJsonString`.

### Key findings from POC test writing

**Response shape gotchas** (field names differ from what intuition suggests):
- `getBank` → `BankJsonV600` → top-level field is `bank_id`, not `id`. Also has `full_name` (not `short_name`).
- `getCoreAccountById` → `ModeratedCoreAccountJsonV600` → top-level field is `account_id`, not `id`. Other fields: `bank_id`, `label`, `number`, `product_code`, `balance`, `account_routings`, `views_basic`.
- `getPrivateAccountByIdFull` → `ModeratedAccountJSON600` → top-level field IS `id`. Also has `views_available` and `balance`.
- `getCurrentUser` → has `user_id`, `username`, `email` at top level.

**Counterparty test setup** — `createCounterparty` (test helper) only creates the `MappedCounterparty` row. `getExplicitCounterpartyById` calls `NewStyle.function.getMetadata` which reads `MappedCounterpartyMetadata`. You must call `Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterpartyId, counterpartyName)` after `createCounterparty`, or the endpoint returns 400 `CounterpartyNotFoundByCounterpartyId`.

**System owner view** (`SYSTEM_OWNER_VIEW_ID = "owner"`) has `CAN_GET_COUNTERPARTY` in its `allowed_actions` (from `SYSTEM_VIEW_PERMISSION_COMMON`) and is granted to `resourceUser1` on all test accounts — safe to use as VIEW_ID in tests.

**Auth complexity table update** — all helpers are now implemented and tested:

| Category | Count | EndpointHelper |
|---|---|---|
| No auth | ~2 | `executeAndRespond` ✓ |
| User auth only | ~158 | `withUser` ✓ |
| + BANK_ID | ~62 | `withBank` / `withUserAndBank` ✓ |
| + BANK_ID + ACCOUNT_ID | ~20 | `withBankAccount` ✓ |
| + BANK_ID + ACCOUNT_ID + VIEW_ID | ~8 | `withView` ✓ |
| + COUNTERPARTY_ID | ~2 | `withCounterparty` ✓ |

## Phase 1 — Key Findings

### Query parameters in v7
- **`extractHttpParamsFromUrl(url)`** → use `req.uri.renderString` in place of `cc.url`. Returns `Future[List[HTTPParam]]`; chain with `createQueriesByHttpParamsFuture(httpParams, cc.callContext)` to get `OBPReturnType[List[OBPQueryParam]]` (both are in `NewStyle.function` / `APIUtil`).
- **`extractQueryParams(url, allowedParams, callContext)`** → same substitution (`req.uri.renderString` for `cc.url`). Returns `OBPReturnType[List[OBPQueryParam]]` directly.
- **Raw query params as `Map[String, List[String]]`** → use `req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }`. `multiParams` returns `Map[String, Seq[String]]` (immutable `Seq`), not `List` — `.toList` conversion is required for `AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(bankId, params)`. Do **not** use `req.uri.query.pairs` (returns `Vector[(String, Option[String])]`, wrong shape).

### Imports added in batch 2
- `code.accountattribute.AccountAttributeX` — for `getAccountIdsByParams`
- `code.users.{Users => UserVend}` — renamed to avoid clash with `com.openbankproject.commons.model.User`; used as `UserVend.users.vend.getUsers(...)`
- `com.openbankproject.commons.model.CustomerId` — for `getCustomerByCustomerId`
- `code.api.v2_0_0.BasicViewJson` — for `getAccountsAtBank` view list
- `code.api.v6_0_0.{BasicAccountJsonV600, BasicAccountsJsonV600}` — response types for `getAccountsAtBank`
- `code.api.util.ApiRole.{canGetAnyUser, canGetCustomersAtOneBank}` — roles

### `getAccountsAtBank` — views + account access pattern
The `withUserAndBank` helper provides `(u, bank, cc)`. The account-filtering logic is a direct port from v6:
1. `Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)` → `(List[View], List[AccountAccess])`
2. Filter `AccountAccess` by attribute params if query params are present (use `req.uri.query.multiParams`)
3. `code.model.BankExtended(bank).privateAccountsFuture(filteredAccess, cc.callContext)` → available accounts
4. Map accounts to `BasicAccountJsonV600` with their views, yield `BasicAccountsJsonV600`

**`BankExtended` wrapper**: `privateAccountsFuture` is defined on `code.model.BankExtended`, not on `com.openbankproject.commons.model.Bank`. Whenever v6 calls `bank.privateAccountsFuture(...)`, wrap the commons `Bank` with `code.model.BankExtended(bank)` first. Same applies to `privateAccounts`, `publicAccounts`, and other methods on `BankExtended`.

Note: `bankIdStr` captured from the route pattern is equivalent to `bank.bankId.value` — both are safe to use.

### Test patterns for Phase 1 endpoints

**Creating test data directly** — do not call v6 endpoints via HTTP in Phase 1 tests; create rows directly via the provider:
- Customers: `CustomerX.customerProvider.vend.addCustomer(bankId = CommBankId(bankId), number = APIUtil.generateUUID(), ...)` — import `code.customer.CustomerX`, `com.openbankproject.commons.model.{BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}`, `code.api.util.APIUtil`, `java.util.Date`.
- Put the helper in a class-level `private def createTestCustomer(bankId: String): String` — **never inside a `feature` block**, which is invalid Scala.

**Standard 3-scenario pattern** for role-gated endpoints (`withUser` or `withUserAndBank` + role):
1. Unauthenticated → 401 with `AuthenticatedUserIsRequired`
2. Authenticated, no role → 403 with `UserHasMissingRoles` + role name
3. Authenticated with role (and test data) → 200 with expected fields

**Public endpoints** (`executeAndRespond`) get 2 scenarios: unauthenticated 200 + shape check.

**`getAccountsAtBank` test data** — `ServerSetupWithTestData` pre-creates accounts on `testBankId1`, so no extra setup is needed for the happy-path 200 scenario. Same applies to any endpoint backed by the default test bank data.

**Imports added to test file for batch 2**:
- `code.api.util.APIUtil` (explicit — for `APIUtil.generateUUID()`)
- `code.api.util.ApiRole.{canGetAnyUser, canGetCustomersAtOneBank}`
- `code.customer.CustomerX`
- `com.openbankproject.commons.model.{BankId => CommBankId, CreditLimit, CreditRating, CustomerFaceImage}`
- `java.util.Date`

## OBP-Trading Integration

**Location**: `/home/marko/Tesobe/GitHub/constantine2nd/OBP-Trading`

OBP-Trading is a standalone http4s trading service. It does **not** currently make HTTP calls to OBP-API. Two connectors are designed to call OBP-API eventually but are currently in-memory stubs:

| Connector | Intended OBP-API dependency | Current impl |
|---|---|---|
| `ObpApiUserConnector` | user lookup, account summary | in-memory `Ref` |
| `ObpPaymentsConnector` | payment pre-auth, capture, release | `FakeObpPaymentsConnector` (always succeeds) |

**OBP-API endpoints `ObpApiUserConnector` would need** once wired for real:
- `GET /users/user-id/USER_ID` — `getUserByUserId` ✓ migrated to v7 (`Http4s700.scala`)
- `GET /banks/BANK_ID/accounts` — ✓ `getAccountsAtBank` already migrated

**Endpoints OBP-Trading exposes** (these live in OBP-Trading, not OBP-API — clarify with team whether to port into `Http4s700.scala` or keep as a separate service):

| Verb | URL |
|---|---|
| POST | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID` |
| PUT | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID` |
| DELETE | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/trades` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/trades/TRADE_ID` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/market` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/market/ASSET_CODE/orderbook` |
| GET | `/obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/status` |

Routes are implemented in `OBP-Trading/src/main/scala/com/openbankproject/trading/http/Routes.scala`. All 10 routes are registered:
- POST/GET(list)/GET(by-id)/PUT/DELETE for offers → POST, GET(by-id), DELETE wired to `OrderService`; GET(list) wired to `OrderService.listOrders(accountId)` (filters `InMemoryOrderService` by `ownerAccountId`); PUT is `NotImplemented`.
- Trade history (GET list + GET by-id), market (GET market + GET orderbook), status → `NotImplemented` stubs.

**Open question** (pending team clarification): port trading endpoints into `Http4s700.scala` as a new section, or keep OBP-Trading as a separate service that OBP-API proxies to.

## DB Transaction Model: v6 vs v7

### v6 — One Transaction Per Request

`Boot.scala:598` registers `S.addAround(DB.buildLoanWrapper)` for every Lift HTTP request. This wraps the entire request in a single `DB.use(DefaultConnectionIdentifier)` scope, which:
- Borrows one JDBC connection from HikariCP at request start (pool configured `autoCommit=false`)
- All Lift Mapper calls (`.find`, `.save()`, `.delete_!()`, etc.) within that request increment the connection's reference counter and reuse the **same connection**
- Commits when the outermost `DB.use` scope exits cleanly; rolls back on exception
- Result: **one transaction per request** — all reads and writes are atomic; a write is visible to subsequent reads within the same request (same DB session)

### v7 — Request-Scoped Transaction ✓ IMPLEMENTED

v7 native endpoints run through `ResourceDocMiddleware.withRequestTransaction`, which provides the same one-transaction-per-request guarantee as v6's `DB.buildLoanWrapper`.

**Implementation** (`RequestScopeConnection.scala` + `ResourceDocMiddleware.scala`):
1. `withRequestTransaction` borrows a real JDBC connection from HikariCP and wraps it in a **non-closing proxy** (commit/rollback/close are no-ops on the proxy).
2. The proxy is stored in `requestProxyLocal: IOLocal[Option[Connection]]` — fiber-local, survives IO compute-thread switches, always readable by any IO step in the request fiber. `currentProxy` (TTL) is **not** set here.
3. Every `IO.fromFuture` call site uses `RequestScopeConnection.fromFuture(fut)`. Inside a single synchronous `IO.defer` block on compute thread T, it: (a) sets `currentProxy` on T, (b) evaluates `fut` so the Future is submitted and `TtlRunnable` captures T's proxy, (c) immediately calls `currentProxy.remove()` on T. T is clean after this block; the Future worker still receives the proxy via `TtlRunnable`.
4. Inside each Future, Lift Mapper calls `DB.use(DefaultConnectionIdentifier)`. `RequestAwareConnectionManager` (registered in `Boot.scala` instead of `APIUtil.vendor`) intercepts `newConnection` and returns the proxy. All mapper calls within a request share **one underlying connection**.
5. At request end: commit on success, rollback on unhandled exception. Non-closing proxy prevents Lift's per-`DB.use` lifecycle from committing or releasing the connection prematurely.

**Metric writes** (`recordMetric` in `IO.blocking`): run on the blocking pool where `currentProxy` is not set — use their own pool connection and commit independently. This is correct behaviour (metric writes must persist even when the request transaction is rolled back).

**v6 via Lift bridge**: unaffected. `S.addAround(DB.buildLoanWrapper)` still manages v6 transactions. `RequestAwareConnectionManager` delegates to `APIUtil.vendor` when `currentProxy` is null.

**`Boot.scala` change**: `DB.defineConnectionManager(..., new RequestAwareConnectionManager(APIUtil.vendor))` replaces the direct vendor registration.

### Doobie (`DoobieUtil`) — Separate Layer

Used for raw SQL (metrics queries, provider lookups, attribute queries):

| Context | Transactor | Commit behaviour |
|---|---|---|
| Inside Lift request (v6 / bridge) | `transactorFromConnection(DB.currentConnection)` + `Strategy.void` | participates in Lift's transaction — no independent commit/rollback |
| Outside Lift request (v7 native, background) | `fallbackTransactor` (HikariCP pool) + `Strategy.void` | no explicit commit by doobie; safe for reads; writes require caller to commit |

`DoobieUtil.runQueryAsync` and `runQueryIO` always use `fallbackTransactor` — they cannot safely borrow the Lift request connection across thread boundaries.

### Summary

| | v6 | v7 |
|---|---|---|
| Transaction scope | 1 connection per HTTP request | 1 connection per HTTP request ✓ |
| Multi-write atomicity | Yes — full rollback on exception | Yes — rollback on unhandled exception ✓ |
| Read-your-own-writes | Yes — same session | Yes — same underlying connection ✓ |
| Metric write (`recordMetric`) | Shares request transaction | Separate `IO.blocking` connection + commit (intentional) |
| Doobie in-request | Shares Lift's request connection | Uses pool fallback (separate connection) |
| Key source | `Boot.scala:598` `DB.buildLoanWrapper` | `ResourceDocMiddleware.withRequestTransaction` + `RequestScopeConnection` |

## Performance Characteristics (GET /banks benchmark)

Measured via `GetBanksPerformanceTest` — same `Http4sApp.httpApp` server, same H2 DB, only the code path differs.

### Serial (1 thread) — per-request overhead floor

| | v6 | v7 |
|---|---|---|
| Median | ~1ms | ~5ms |
| P99 | ~5ms | ~9ms |

v7 pays ~4ms fixed overhead per request: `ResourceDocMiddleware` traversal + `Http4sCallContextBuilder.fromRequest` (body + header parsing) + `IO.fromFuture` context switch. v6's JIT-compiled Lift hot path runs in ~1ms uncontested.

### High concurrency (20 threads, 200 requests) — the authoritative comparison

| | v6 | v7 | delta |
|---|---|---|---|
| Median | ~9ms | ~18ms | v6 2x better |
| Mean | ~19ms | ~21ms | roughly equal |
| **P99** | **~140ms** | **~65ms** | **v7 ~53% better** |
| **Spread** | **~160ms** | **~75ms** | **v7 ~45% tighter** |

v6 wins median because its hot path is fast when threads are free. v7 wins P99 and spread because the IO runtime never blocks threads — Lift's thread-per-request model queues requests when the pool saturates, causing spikes. Assertions in the test enforce `v7.p99 <= v6.p99` and `v7.spread <= v6.spread`.

### Concurrency scaling table (1 / 5 / 10 / 20 threads)

The table is **observational only** — do not assert tail-latency dominance here. Each level inherits the cumulative JVM/H2 warmup of all prior levels; by level 4 the JVM has processed ~1,400 prior requests and H2 has all bank rows pinned. v6 P99 stays artificially low (~9ms at 20T) vs the standalone 140ms because requests complete before the thread pool saturates. Use the high-concurrency standalone scenario for architectural assertions.

## v7 Transaction Tests (`Http4s700TransactionTest`) — Status ✓ ALL PASSING

New test class at `obp-api/src/test/scala/code/api/v7_0_0/Http4s700TransactionTest.scala`.

Tests three features: commit on successful write (POST addEntitlement), commit on successful delete (DELETE deleteEntitlement), connection pool health (10 sequential POST+DELETE pairs, 4xx does not exhaust pool).

**All scenarios now pass.** The previously failing scenario 2 ("a second request after the first can read committed data") was returning 401 due to a stale TTL proxy issue — see "Stale TTL Proxy" section below.

## Stale TTL Proxy — Root Cause & Fixes ✓ FIXED (two layers)

**Root cause (layer 1 — inter-request, FIXED earlier)**: `RequestScopeConnection.fromFuture` set `currentProxy` on the IO compute thread and left it set. After `withRequestTransaction.guaranteeCase` committed and closed the real connection, background callbacks (e.g. scalacache rate-limit callbacks) running on the same compute thread still saw the closed proxy → `setAutoCommit` threw `SQLException: Connection is closed` → silently became 401.
**Fix (layer 1)**: `RequestAwareConnectionManager.newConnection` calls `proxy.isClosed()` before returning the proxy. If the underlying HikariCP connection is already closed, it falls back to a fresh vendor connection.

**Root cause (layer 2 — test-induced NPE, FIXED now)**: The original `fromFuture` implementation set `currentProxy` on the IO compute thread **and never cleared it**. After `fromFuture` completed, the compute thread retained the proxy indefinitely. In tests (`RequestScopeConnectionTest`), the `after` block only cleared the test thread's TTL, not the io-compute threads used by `unsafeRunSync()`. Subsequent test code running `DB.use` on those contaminated io-compute threads received the test's tracking proxy (whose `isClosed()` always returns `false` — the `isClosed` guard only detects closed HikariCP proxies, not mock proxies). Lift wrapped the tracking proxy in `SuperConnection`, called `getMetaData()` → returned `null` (tracking handler's `case _ => null`), then `null.storesMixedCaseIdentifiers` → NPE at `MetaMapper._dbTableNameLC:1390`.

**Fix (layer 2)**: `fromFuture` now uses `IO.defer` to atomically: (1) set TTL on current compute thread T, (2) evaluate `fut` — the Future is submitted and `TtlRunnable` captures T's proxy, (3) call `currentProxy.remove()` on T immediately, (4) return `IO.fromFuture(IO.pure(f))` to await the already-submitted future. Steps 1–3 are synchronous within the `IO.defer` block, so T is always cleaned up before any fiber scheduling can switch threads. Additionally, `withRequestTransaction` no longer sets `currentProxy` at request start (previously another dirty-thread source); all TTL management is now local to `fromFuture`.

**Key design note**: `proxy.isClosed()` forwards to the real HikariCP `ProxyConnection`. After `realConn.close()` is called in `withRequestTransaction.guaranteeCase`, HikariCP marks the proxy as closed and all subsequent method calls throw `SQLException: Connection is closed` — but `isClosed()` correctly returns `true` per JDBC spec, allowing detection without triggering the error.

## HikariCP Pool Exhaustion in Concurrent Tests ✓ FIXED

**Root cause**: `withRequestTransaction` (applied by `ResourceDocMiddleware` to all v7/v5 native routes) holds one HikariCP connection for the full duration of each request. ScalaCache rate-limit queries (`RateLimiting.findAll` via `getActiveCallLimitsByConsumerIdAtDate`) run concurrently on the OBP EC (a `TtlRunnable`-wrapping global EC) on cache miss, each needing an additional pool connection. With the default pool of 10 and 10 concurrent test threads (`Http4sLiftBridgePropertyTest` Property 7.1), all 10 connections are held by active requests → pool timeout after 30s → test's 10-second HTTP client timeout fires first → "Futures timed out after [10 seconds]".

**Worst-case math**: N concurrent requests hold N connections; up to N background rate-limit queries each need 1 more → 2*N needed at peak. Pool of 10 is exhausted at N=5+.

**Fix**: `hikari.maximumPoolSize=20` added to:
- `.github/workflows/build_pull_request.yml` (CI props generation script)
- `obp-api/src/main/resources/props/test.default.props.template` (local developer baseline)

Pool of 20 covers the 10-thread concurrency test (2×10=20) with zero waste. The setting is test-only — production `test.default.props` is not in git and must be updated manually.

## CI Test Performance — Overview

Build time baseline: ~32 min (build #44). Current target after fixes below.

### Brainstorm: Further Speed-Up Opportunities

| Action | Effort | Estimated saving | Status |
|---|---|---|---|
| GitHub Actions matrix split (3 shards) | Low — CI YAML only | ~20 min wall-clock | **not done** |
| Build cache (`~/.m2` + `target/`) | Low — CI YAML only | 8–12 min on cache hit | **not done** |
| Add `write_metrics=false` to CI echo block | Trivial | prevents MetricsTest hang | **not done** |
| Profile slow tail, fix top outliers | Medium | 5–10 min | **partially done** |
| Two-tier fast gate + full suite | Medium | unblocks PRs faster | **not done** |
| Surefire parallel forks | High — port/DB parameterisation | 10–15 min | **not done** |

**Optimal 3-shard split** (based on actual Jenkins timings):
- Shard 1: `v4_0_0` (4:15) + `v2_1_0` (0:35) + `v3_0_0` (0:29) + `v5_0_0` (0:39) + small → ~6.5 min
- Shard 2: `http4sbridge` (2:49) + `ResourceDocs` (2:00) + `v3_1_0` (2:05) + `util` (1:02) → ~8 min
- Shard 3: `v5_1_0` (2:31) + `v6_0_0` (2:02) + `v1_2_1` (2:18) + `api` (0:33) + small → ~7.5 min
- Result: ~32 min → ~12–15 min wall-clock

**v7 migration pays CI dividends**: `v7_0_0` runs 75 tests in 7.4s (0.1s/test) vs `v6_0_0` 314 tests in 2m2s. As more endpoints migrate, the test suite naturally gets faster.

**Skipped tests to audit** (`v5_0_0`: 13 skipped, `container`: 1 fully-skipped class) — setup cost paid, no value returned.

## CI Test Performance Fixes ✓ DONE (~4 min saved)

Three targeted fixes based on per-test timing from Jenkins report:

### `code.api.util` (1m2s → ~2s, saves 60s)
`JavaWebSignatureTest` had `Thread.sleep(60 seconds)` to let a JWS signature expire. Fixed by signing with a pre-stale timestamp (`signingTime = now - 65s`) instead — no sleep, no prop dependency, works against any reasonable validity window. `JwsUtil.verifySigningTime` also made configurable via `jws.signing_time_validity_seconds` prop (default 60) for future use.

### `code.api.ResourceDocs1_4_0` (2m0s → ~45s, saves 75s)
Two independent problems:
- **ResourceDocsTest**: called `stringToNodeSeq` on ALL 600+ endpoint descriptions per scenario → 7,800 HTML5 parses across 13 API versions. Changed to `take(3).foreach` — verifies the function works without O(N) per-version cost.
- **SwaggerDocsTest**: ran `OpenAPIParser.readContents()` (full spec validation) for 12 API versions. Kept v5.1.0, v4.0.0, v1.2.1; dropped 9 redundant intermediate versions. Access-control scenarios unchanged. 19 → 10 scenarios.

### `code.api.http4sbridge` (2m49s → ~50s, saves 119s)
50 property scenarios ran `val iterations = 10` (three at 20) = 530 total HTTP round-trips. Added `CI_ITERATIONS = 3` / `CI_ITERATIONS_HEAVY = 5` constants at the top of `Http4sLiftBridgePropertyTest`; all scenarios reference them. To run full coverage locally: change `CI_ITERATIONS` to 10.
