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

**Lift DOES enforce ResourceDoc roles**: `OBPRestHelper.registerRoutes` wraps every endpoint in `ResourceDoc.wrappedWithAuthCheck` (`APIUtil.scala:1780`), which calls `checkRoles` whenever `_autoValidateRoles && rolesForCheck.nonEmpty` — i.e. whenever the doc declares `Some(List(...))` and the endpoint hasn't called `.disableAutoValidateRoles()` (rare). So Lift and `ResourceDocMiddleware` enforce doc roles **the same way** for the common case. The "Conditional / Disagreement / Bypass" gotchas below describe genuinely-quirky inline-check patterns — they are NOT about Lift skipping doc-role enforcement. Earlier revisions of this file said "Lift never enforced doc roles"; that was wrong. When migrating, copy the doc role list as-is unless you can show the inline check is doing something the doc role isn't.

**Conditional role check (403) — only for genuinely-conditional roles**: `NewStyle.function.hasEntitlement` uses `booleanToFuture` with default `failCode = 400`, which gives 400 instead of 403 when the role is missing. If the role is genuinely conditional (different role for different paths, e.g. `canCreateProductAtAnyBank` only when bank scope is global), keep ResourceDoc roles `None` and check inline with `booleanToFuture(failCode=403)`:
```scala
_ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
     else code.util.Helper.booleanToFuture(
       s"$UserHasMissingRoles $canCreateAccount", failCode = 403, cc = Some(cc)) {
       APIUtil.hasEntitlement(bankId, loggedInUserId, canCreateAccount)
     }
```
But: if the inline check uses the **same** role as the doc (e.g. v5 `createAccount` doc has `Some(List(canCreateAccount))` and the inline check also tests `canCreateAccount`), the inline check is dead code — Lift's `wrappedWithAuthCheck` already enforced the doc role before the handler ran. Mirror Lift exactly: keep the doc role AND keep the inline check (it's a no-op safety net when the doc role passes). Do NOT take the role out of the doc to "match Lift": that flips behaviour from "always required" to "only required when creating-for-another-user", which v5 `AccountTest`'s "user2 without role → 403" scenario will catch.

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

**Auth failure status code — Old Style vs New Style**: `ResourceDocMiddleware.authenticate` returns **400** for auth failures (locked user, invalid DAuth JWT, etc.) on Old Style endpoints (v1.2.1, v1.3.0, v1.4.0, v2.0.0) and **401** on New Style endpoints (v2.1.0+). Internally, `anonymousAccess` always converts Failure boxes to a thrown `Exception(json_of_APIFailureNewStyle)` with `failCode=401` via `fullBoxOrException`. The `case Left(e)` branch in `authenticate` parses the JSON, then overrides to 400 for Old Style versions via `oldStyleShortVersions.contains(resourceDoc.implementedInApiVersion.apiShortVersion)`. If a new version file returns the wrong code, check: (1) `implementedInApiVersion` is set correctly, and (2) the version is/isn't in `oldStyleShortVersions`.

**Prop check before role check (firehose-pattern)**: Some endpoints must enforce a feature-flag prop check (→ 400) *before* a role check (→ 403), and both *before* the bank/account lookup (→ 404). Middleware processes roles then bank, so putting roles in the ResourceDoc causes 403 before the prop runs; using `withUserAndBank` causes 404 for fake bank IDs before either check. The fix:
1. Use `withUser` (auth only — no bank/account resolution from middleware).
2. Use non-standard ALL_CAPS vars in the ResourceDoc URL template (`FIREHOSE_BANK_ID`, `FIREHOSE_VIEW_ID`) so middleware skips bank/view validation.
3. In the handler body: prop check first (booleanToFuture → 400), then role check with `booleanToFuture(failCode=403)` (→ 403), then manual `NewStyle.function.getBank(...)` (→ 404 for unknown bank).
4. Keep roles **out** of the ResourceDoc (`None` instead of `Some(List(...))`).
```scala
EndpointHelpers.withUser(req) { (user, cc) =>
  val roles = ApiRole.canUseAccountFirehose :: canUseAccountFirehoseAtAnyBank :: Nil
  val roleMsg = UserHasMissingRoles + roles.mkString(" or ")
  for {
    _ <- code.util.Helper.booleanToFuture(AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) { allowAccountFirehose }
    _ <- code.util.Helper.booleanToFuture(roleMsg, failCode = 403, cc = Some(cc)) {
           APIUtil.hasAtLeastOneEntitlement(bankIdStr, user.userId, roles) }
    (bank, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
    ...
  } yield ...
}
// ResourceDoc:
resourceDocs += ResourceDoc(null, ..., "/banks/FIREHOSE_BANK_ID/firehose/...", ..., None, ...)
```

**`ResourceDoc` description and `needsAuthentication`**: The `ResourceDoc` constructor removes `AuthenticatedUserIsRequired` from `errorResponseBodies` when `description.contains(authenticationIsOptional) && rolesIsEmpty`. `needsAuthentication = errorResponseBodies.contains($AuthenticatedUserIsRequired) || roles.nonEmpty`. If the description embeds `${userAuthenticationMessage(false)}` (which includes `authenticationIsOptional`) and roles are empty, the error is silently removed → `needsAuthentication=false` → anonymous access → unauthenticated requests reach the handler. Fix: remove `${userAuthenticationMessage(false)}` from the description when `AuthenticatedUserIsRequired` must remain in the error list.

**v1.2.1 test framework sends filter params as HTTP headers**: `makeGetRequest(req, params)` puts `params` into `extra_headers`, not the URL query string. This means `obp_limit`, `obp_sort_direction`, `obp_from_date`, etc. arrive as request headers. Do NOT use `createHttpParamsByUrl(req.uri.renderString)` — it only scans the URL for non-prefixed names. Instead: `req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value))`, then pass to `createQueriesByHttpParamsFuture`.

**CI**: Tests run with `mvn test -DwildcardSuites="..."`. `hikari.maximumPoolSize=20` required in test props for concurrent tests (`withRequestTransaction` holds 1 connection per request; rate-limit queries need a 2nd → pool of 10 exhausts at 5 concurrent requests).

**Running tests for a single API version locally**: `-DwildcardSuites="code.api.v3_1_0"` (just the package prefix, no `.*`) discovers zero tests — the prefix form only works in the CI workflow's piped invocation. From the shell, pass an explicit **comma-separated list of fully qualified suite class names**. Generate it by grepping each file for its declared class — a filename-based generator misses cases where the class name doesn't match the file (e.g. `RefreshObpDateTest.scala` declares `class RefreshUserTest`):
```sh
grep -l '^class.*extends.*ServerSetup' obp-api/src/test/scala/code/api/v3_1_0/*.scala \
  | xargs -I{} grep -hoP '^class \K[A-Z][A-Za-z0-9_]+' {} \
  | sed 's/^/code.api.v3_1_0./' | tr '\n' ',' | sed 's/,$//'
```
Pipe that into `-DwildcardSuites=`. Add `-DfailIfNoTests=false` so an empty match doesn't fail the build. The `extends.*ServerSetup` filter only keeps real suites (skips the abstract base trait itself and any utility helpers in the directory). Don't generate suite names from `basename` — that silently drops suites with class-vs-file name mismatches, which is exactly how a CI failure can slip past a green local run.

**Surefire reports beat truncated maven output**: When a `mvn test` invocation has hundreds of failures, the run summary at the tail says e.g. `*** 23 TESTS FAILED ***` but the individual failure messages are scrolled off. Don't re-run; mine `obp-api/target/surefire-reports/TEST-*.xml` instead. Suites with failures have `failures=` or `errors=` >0; per-testcase failures are `<failure message="...">` elements. Quick extract:
```sh
python3 -c "
import xml.etree.ElementTree as ET
t = ET.parse('TEST-code.api.v3_1_0.AccountTest.xml').getroot()
for tc in t.findall('testcase'):
    fail = tc.find('failure')
    if fail is not None:
        print(tc.get('name')[:120], '--', (fail.get('message') or '')[:200])
"
```
The `<failure>` element's *text* contains the full stack trace + the lift-json `MappingException` body dump — read that when the message alone (`"500 did not equal 400"`) isn't enough to find the failing assertion.

**Empty path segments fall into http4s patterns that should reject them**: A Lift test like `getSystemView("")` builds URL `/system-views/`. http4s's `Path` keeps the trailing empty segment, so `case GET -> prefixPath / "system-views" / viewIdStr` matches with `viewIdStr = ""`. Meanwhile `ResourceDocMatcher.matchesUrlTemplate` filters empty segments via `.split("/").filter(_.nonEmpty)`, so the matcher sees 1 segment vs the template's 2 — no doc match → middleware skips auth/role validation and falls through to your handler with `viewIdStr = ""`. The handler then throws inside the business logic → 500 (test expected 401/403 from middleware). Fix: add a pattern guard so empty viewId doesn't match and the request falls through to the Lift bridge: `case req @ GET -> prefixPath / "system-views" / viewIdStr if viewIdStr.nonEmpty =>`. Apply to GET/PUT/DELETE variants.

**Throwing a `RuntimeException` in Lift returns 500, not 400**: When porting Lift code like:
```scala
(fromAccount, _) <- if (...) for { ... } else if (...) for { ... }
                    else throw new RuntimeException(s"$InvalidJsonFormat ...")
```
the `throw` synthesises a 500 response in the http4s path (test expects 400). Lift sometimes converted these to 400 via its exception handler; the http4s migration does not. Replace the throw with an upfront `code.util.Helper.booleanToFuture(failMsg, cc = Some(cc)) { validShape }` *before* the if/else — `booleanToFuture` defaults to `failCode = 400`. This also flattens nested else-branch logic.

**Middleware role check runs before body parsing**: When a ResourceDoc declares `Some(List(canX))`, the middleware enforces the role in the **auth/role validation** phase, which precedes the handler. Tests that send malformed JSON expecting 400 (InvalidJsonFormat) instead get 403 (UserHasMissingRoles) because the user lacks the role. Fix: when a test asserts body-validation 400s should fire *before* role 403s, take the role out of the ResourceDoc (`None` for roles) and check it inline inside the for-comp with `code.util.Helper.booleanToFuture(failMsg, failCode = 403, cc = Some(cc)) { APIUtil.hasEntitlement(...) }`. This is a generalisation of the firehose-pattern documented above — it applies to any POST/PUT where the test ordering is "bad body → 400" before "missing role → 403."

**ResourceDoc role and handler role disagreement**: Some Lift endpoints declare role X in the `ResourceDoc(...)` metadata but ALSO check role Y inline via `NewStyle.function.hasEntitlement(Y, ...)`. Example: `updateCustomerBranch` Lift had `Some(canUpdateCustomerIdentity :: Nil)` in the doc and called `hasEntitlement(canUpdateCustomerBranch, ...)` in the handler. Since Lift enforces both, the effective Lift requirement was X **and** Y — and the test that "passed with only Y" likely did so because (a) the doc had `.disableAutoValidateRoles()` set, (b) the doc role list was actually `None`/different from what was assumed, or (c) the test granted both. The http4s middleware enforces doc roles the same way, so the contract is preserved if you copy the doc role list verbatim. The error-message wording can still drift (middleware says "$UserHasMissingRoles X", inline says "$UserHasMissingRoles Y") — if a test asserts on the message, copy the inline role to the doc OR set doc roles to `None` and rely on the inline check exclusively, then verify against the test's `.addEntitlement(...)` calls.

**Most v3.1.0 DELETEs return 200, not 204**: The CLAUDE.md helper matrix says "DELETE → 204" but in practice many v3.1.0 endpoints return `(Full(deletedThing), HttpCode.\`200\`(cc))` — 200 with a body. Mirror Lift: use `withUser` / `withUserAndBank` (which return 200) for these, **not** `withUserDelete` / `withUserAndBankDelete` (which return 204). Reserve the `*Delete` helpers for endpoints that genuinely return 204 (verified examples in v3.1.0: `deleteProductAttribute`, `deleteCardForBank`). The HTTP method comes from the route pattern (`case req @ DELETE -> ...`), not the helper name.

**Bug-compatibility with Lift error strings**: Some Lift endpoints have copy-paste bugs in their error messages that tests assert on verbatim. Example: `getFirehoseCustomers` (customer firehose) uses the constant `AccountFirehoseNotAllowedOnThisInstance` (account firehose's error message). The test asserts on this exact string. Preserve the bug in the http4s migration — adding a `// Lift used X here despite this being Y — preserve the message verbatim (the test asserts it).` comment is the right move. Fixing the bug means also patching the test, which expands the PR scope.

**`extract[List[X]]` requires a JArray at the top level**: lift-json's extraction is strict about the root shape. If a Lift endpoint returns `Extraction.decompose(myList: List[X])` (root JArray) and the http4s migration changes it to `myList.wrappedIn(Container)` (root JObject), tests doing `response.body.extract[List[X]]` fail with `MappingException: Expected collection but got JObject`. Cross-reference Lift's JSON factory exactly — pay attention to whether it wraps in a case class (`{accounts: [...]}`) or decomposes a raw list (`[...]`). Two examples that look identical but aren't:
- `/banks/BANK_ID/accounts` → Lift returns raw `List[BasicAccountJSON]` (JArray)
- `/banks/BANK_ID/accounts/private` → Lift returns `BasicAccountsJSON(accounts)` (JObject)

**Missing-role error message: `" or "` not `", "`**: The middleware joins multiple missing roles with `" or "` to match `NewStyle.function.hasAtLeastOneEntitlement`'s convention, which every test asserts as `UserHasMissingRoles + roles.mkString(" or ")`. If you add a new role-check path bypassing the middleware (e.g. inline `booleanToFuture`), use the same `" or "` joiner.

**Custom JSON body parse error format**: Some tests assert the parse-failure message starts with a specific string like `"OBP-10001: Incorrect json format. The Json body should be the CreateMeetingJson "`. The standard `withUserAndBankAndBodyCreated[B, A]` helper produces a different format (`"$InvalidJsonFormat ${classSimpleName}"` — `"CreateMeetingJsonV310"`, no leading "The Json body should be the..."). When a test asserts the Lift wording verbatim, bypass the body helper and parse manually:
```scala
EndpointHelpers.executeFutureCreated(req) {
  implicit val cc: CallContext = req.callContext
  val rawBody = cc.httpBody.getOrElse("")
  for {
    parsed <- NewStyle.function.tryons(
      s"$InvalidJsonFormat The Json body should be the ${classOf[ExpectedType].getSimpleName} ",
      400, Some(cc)) { net.liftweb.json.parse(rawBody).extract[ExpectedType] }
    ...
  } yield ...
}
```
Note: `executeFutureCreated` returns 201; pair it with `cc.user.openOrThrowException(...)` / `cc.bank.getOrElse(...)` since middleware has already validated auth/bank.

**Use `NEW_ACCOUNT_ID` for PUT-creates-account URLs**: When a `PUT /banks/BANK_ID/accounts/ACCOUNT_ID` *creates* the account (it doesn't exist yet), the middleware's `validateAccount` keys off the literal `ACCOUNT_ID` template var and tries to look it up → 404 before the handler runs. Change the ResourceDoc URL template to `/banks/BANK_ID/accounts/NEW_ACCOUNT_ID` (or any non-standard ALL_CAPS variant) — middleware treats it as a wildcard and skips the lookup, but the path still matches the route pattern. The handler can check "already exists" inline with `Connector.connector.vend.checkBankAccountExists(...)` and return 409/400 as needed.

**Reserved ALL_CAPS literals — don't use them as placeholders**: `ResourceDocMatcher` in `Http4sSupport.scala` keeps an explicit `literalAllCapsSegments` set: `SANDBOX_TAN`, `COUNTERPARTY`, `SEPA`, `FREE_FORM`, `ACCOUNT`, `ACCOUNT_OTP`, `REFUND`, `SIMPLE`, `AGENT_CASH_WITHDRAWAL`, `CARD`, `EMAIL`, `SMS`, `IMPLICIT`, `NOT_EMAIL_NEITHER_SMS`. These are matched as **literals** (real Lift endpoints register them as concrete SCA-method / transaction-request-type segments — e.g. `/banks/BANK_ID/my/consents/EMAIL`). Any other ALL_CAPS segment is a wildcard. If you migrate an endpoint whose URL template uses one of these names as a *placeholder variable* (e.g. v3.0/v4.0 `getUsersByEmail` had `/users/email/EMAIL/terminator` with EMAIL meaning "any email value"), the matcher will only fire when the URL segment is literally `EMAIL` — real callers pass actual addresses and miss the doc entirely → middleware skips auth/role validation → handler 500s on the empty CallContext. Rename the placeholder to something outside the literal set (e.g. `EMAIL` → `USER_EMAIL`), and apply the rename in **both** the http4s `ResourceDoc` and the original Lift `ResourceDoc` (resource-docs aggregation reads both, and `collectResourceDocs` dedup keys off URL + verb).

**Bypass roles vs required roles**: Some Lift handlers check entitlements inline as **bypass** conditions inside authorisation helpers — e.g. `checkAuthorisationToCreateTransactionRequest` honours `canCreateAnyTransactionRequest` to let the caller skip the view-permission check, but the role is never a hard requirement. These roles are correctly absent from the Lift ResourceDoc role list — putting them in the doc would make Lift enforce them as required (since Lift DOES enforce doc roles by default), breaking the "view permission OR role" intent. The same holds for http4s middleware. So the trap on migration is the reflex copy: don't move a bypass role from inline-only into `Some(List(...))` just because it appears in the handler. Audit before copying: if the role appears in the Lift handler only inside an authorisation OR-chain ("has view permission OR has role X"), it belongs as `None` in the doc with the inline view/role logic preserved. Bypass roles must stay out of the doc.

**Bridge-cascade hijack**: when a new version (e.g. v4.0.0) *overrides* an endpoint from an earlier version with the same URL + verb (e.g. v4's `POST /banks` adds entitlement-granting that v2.2.0's `POST /banks` doesn't have), the v4 override **must** be migrated to `Http4s400`'s own-routes **before** wiring `Http4s400` into the chain. Otherwise the path-rewriting bridge cascade silently sends the request to the older handler:

```
POST /obp/v4.0.0/banks
  → Http4s400 own-routes  (no POST /banks match — falls through)
  → v400ToV310Bridge       (rewrites to /obp/v3.1.0/banks, calls Http4s310)
  → ... cascades down ...
  → Http4s220              (HAS POST /banks → executes v2.2.0 createBank ✗)
```

Without the v4 work the chain falls all the way through to the Lift bridge, which honours the `collectResourceDocs` URL+verb dedup that keeps the highest-version handler for each route — so Lift's v4 createBank runs and the test passes. Once you add an `Http4sXYZ` for an in-flight migration, that "Lift dedup" no longer protects you. Cure: before flipping a new version's `wrappedRoutesVxxxServices` into `Http4sApp.baseServices`, audit the version's overrides (Lift's `excludeEndpoints` is *not* the right list — it only names *removed* endpoints, not overrides) and migrate them too.

How to find overrides for a version: grep `lazy val (\w+)` in the target `APIMethods*.scala`, then check whether the same URL + verb also appears in any older `APIMethods*.scala`. The intersection is the override set. Migrate that set as part of the same PR that introduces the bridge; otherwise reviewers will see test failures whose proximate cause (a downstream version's handler running) doesn't match the file the migration touches.

Symptoms in tests: a v4-specific assertion fails (e.g. an entitlement should-be-granted check returns false). The HTTP response is usually a successful 200/201, just from the wrong handler — so it can look like a flaky failure on the surface.

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

### Per-version completeness (from `comm -23 lift http4s` on each version's `lazy val ... : OBPEndpoint` declarations)

| Version | Genuine Lift handlers still on the bridge |
|---|---|
| v1.2.1, v1.3.0, v2.0.0, v2.1.0, v2.2.0, v3.0.0, v4.0.0, v5.0.0, v5.1.0, v6.0.0 | 0 — fully on http4s |
| v1.4.0 | `testResourceDoc` (dev-mode-only stub; tracked in "Per-version Lift leftovers") |
| v3.1.0 | `getMessageDocsSwagger`, `getObpConnectorLoopback` (both tracked as leftovers; retire via Resource-docs / bridge-removal workstreams) |

### v6.0.0 migration — done (243 / 243)
Phase 1 (35 overrides) and Phase 2 (208 originals) both complete. All v6 routes live in `Http4s600.scala`, wired into `Http4sApp.baseServices` ahead of the Lift bridge.

Architectural note from the v6 migration: around the 140-endpoint mark `Implementations6_0_0`'s `<init>` hit the JVM 64KB bytecode-per-method limit. The fix that ships in `Http4s600.scala` — and that future per-version files should adopt — is two-part:

1. Declare endpoints as `lazy val xxx: HttpRoutes[IO] = HttpRoutes.of[IO] { ... }` instead of `val`. Lambda materialisation moves out of `<init>` into per-field `lzycompute` methods (each with its own 64KB budget).
2. Group `resourceDocs += ResourceDoc(...)` calls into `private def initXxxResourceDocs(): Unit` blocks of ~10–15 endpoints, called once each from the object body. Each helper def gets its own 64KB.

### Other TODOs
- **OBP-Trading**: trading endpoints (createTradingOffer, getTradingOffer, getTradingOffers, cancelTradingOffer, createMarketOrder, getMarketOrder, cancelMarketOrder, createMarketMatch, getMarketTrade, requestSettlement, requestWithdrawal) are now in `Http4s700.scala`. 5 payment-auth endpoints remain commented out (notifyDeposit, createPaymentAuth, capturePaymentAuth, releasePaymentAuth, getPaymentAuth) — see `ideas/CAPTURE_RELEASE_TRANSACTION_REQUEST_TYPES.md`.
- **CI speed-up** (not done): two-tier fast gate + full suite; surefire parallel forks.
- **Disabled tests to fix**: `Http4s500RoutesTest` (@Ignore, in-process issue), `RootAndBanksTest` (@Ignore), `V500ContractParityTest` (@Ignore), `CardTest` (fully commented out). `v5_0_0`: 13 skipped tests (setup cost paid, no value).
- **`V7ResourceDocsAggregationTest`**: intentionally failing — encodes the fix for the resource-docs aggregation bug (v7 endpoint returns only ~10 own docs instead of 500+ aggregated). Fix the bug to make this suite pass.
