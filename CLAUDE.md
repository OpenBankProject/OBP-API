# Project Instructions

## Working Style
- Never blame pre-existing issues or other commits. No excuses, no finger-pointing — diagnose and resolve.
- Never add `Co-Authored-By` trailers to commit messages.
- Commit messages, code comments, and PR titles/descriptions: no AI/tool names (Claude, GPT, Copilot, etc.), no AI-typical filler phrasing ("Certainly!", "I'll help you with..."), no emoji, no "AI-generated"/"LLM" labels. Use plain Conventional Commits style (`fix:`, `feat:`, `refactor:`, ...) and set commit author/committer to the actual person directing the work.
- **Goal is full http4s migration** — eliminate Lift Web and all deprecated libraries entirely. Treat Lift code as temporary scaffolding to be removed, not maintained. When fixing bugs or adding features, always prefer the http4s path.
- **Versioning is tech-agnostic** — API version numbers reflect API signature changes (new/changed fields, new behaviour), never the underlying framework. A framework migration (Lift → http4s) happens in-place at the existing version; it does not justify a version bump.
- **`scripts/resource_doc_baseline/lift_resource_docs_vX_Y_Z.json` is the source of truth for migration.** The 12 `APIMethodsXYZ.scala` files that used to hold this as commented-out Lift `ResourceDoc` text have been deleted (they had shrunk to thin runtime shims plus ~60,000 lines of dead comments — see git history for their last content, or `scripts/resource_doc_baseline/README.md` for the full story). The JSON baseline is the canonical reference for what the http4s version should match: URL templates, verb casing, summaries, descriptions, example bodies, error lists, tags — each field stored as the literal, unevaluated Scala source snippet it always was. **Do NOT hand-edit this JSON to make the parity audit pass**, for the same reason you never edited the old Lift comments for that purpose: it's the historical record the audit compares http4s against. When the audit flags a diff, the fix is to either (a) update http4s to match the baseline, or (b) if it's a reviewed, intentional difference, add a digest-bound entry to `scripts/resource_doc_baseline/parity_allowlist.json` (see that directory's README for the exact workflow — use `allowlist_helper.py`, don't hand-compute digests). See `scripts/check_lift_http4s_resource_doc_parity.py` for the audit (now reads the JSON baseline on the Lift side, live `.scala` on the http4s side), and `scripts/rehydrate_resource_docs.py` / `scripts/restore_resource_doc_bodies.py` for the canonical baseline → http4s restoration tools (also JSON-sourced now).

## Architecture (Onboarding)

> **Migration status**: the Lift → http4s migration is complete — see the "CI (shard map + run tips)" section below for the historical-status note. The former in-place strategy/progress-tracker doc (`LIFT_HTTP4S_MIGRATION.md`) was retired once the migration finished; this file documents the resulting architecture and the gotchas encountered building it.

The goal is a full http4s migration — replace Lift Web across all version files and remove it entirely. **API versions are tech-agnostic**: a version bump means a changed/new API signature, never a framework change. Framework migration happens in-place inside the existing version file. v7.0.0 currently serves 46 endpoints; most arrived there for historical reasons and stay as-is.

**Request priority chain** (`Http4sApp.baseServices`): `corsHandler` (OPTIONS short-circuit) → `AppsPage` → `StatusPage` → `Http4sResourceDocs` → v510 → v600 → v500 → v700 → Berlin Group v2 → UK v2.0 → UK v3.1 → Berlin Group v1.3 (+Alias) → v400 → v310 → v300 → v220 → v210 → v200 → v140 → v130 → v121 → `dynamicEntityRoutes` → `dynamicEndpointRoutes` → DirectLogin → OpenIdConnect → AliveCheck → `notFoundCatchAll` (JSON 404). There is no Lift fallback — `Http4sLiftWebBridge` has been removed. Any unhandled `/obp/*` path returns a JSON 404 from `notFoundCatchAll`; it does not fall through to Lift.

**Key files**: `Http4s700.scala` (v7.0.0 endpoints), `Http4s200.scala` (v2.0.0 endpoints — 37 own + path-rewriting bridge to Http4s140), `Http4s140.scala` (v1.4.0 endpoints — 11 own + path-rewriting bridge to Http4s130), `Http4s130.scala` (v1.3.0 endpoints — 3 own + path-rewriting bridge to Http4s121), `Http4s121.scala` (v1.2.1 endpoints — all 323 API1_2_1Test scenarios), `Http4sSupport.scala` (EndpointHelpers + recordMetric), `ResourceDocMiddleware.scala` (auth, entity resolution, transaction wrapper), `IdempotencyMiddleware.scala` (Redis-backed idempotency, opt-in via `Idempotency-Key` header, nested inside ResourceDocMiddleware), `RequestScopeConnection.scala` (DB transaction propagation to Futures).

**v7.0.0 native endpoints** (49 ResourceDocs): root, corePrivateAccountsAllBanks, createMyBank, getMyBanks, deleteEntitlement, addEntitlement, getAccountAccessTrace, getConsentsConfig, getPasswordPolicy, getErrorMessages, getUserByUserId, createTradingOffer, getTradingOffer, getTradingOffers, cancelTradingOffer, createMarketOrder, getMarketOrder, cancelMarketOrder, createMarketMatch, getMarketTrade, requestSettlement, notifyDeposit, requestWithdrawal, createPaymentAuth, capturePaymentAuth, releasePaymentAuth, getPaymentAuth, createTestEmail, createValidationEmail, createOrganisation, getOrganisations, getOrganisation, updateOrganisation, deleteOrganisation, createRoutingScheme, getRoutingSchemes, getRoutingScheme, updateRoutingScheme, deleteRoutingScheme, getBankSupportedRoutingSchemes, putBankSupportedRoutingScheme, createPayeeLookup, createTransactionRequestMobileWallet, createTransactionRequestUtility, createTransactionRequestOpenCorridor, createTransactionRequestBulk, factoryResetSystemView. These carry genuinely v7-specific signatures/behaviour. The 20 duplicate "POC" endpoints originally added as migration scaffolding (getBanks, getBank, getCurrentUser, getCoreAccountById, getPrivateAccountByIdFull, getExplicitCounterpartyById, getFeatures, getScannedApiVersions, getConnectors, getProviders, getUsers, getCustomersAtOneBank, getCustomerByCustomerId, getAccountsAtBank, getCacheConfig, getCacheInfo, getDatabasePoolInfo, getStoredProcedureConnectorHealth, getMigrations, getCacheNamespaces) were **removed** — they cascade to their v6 twin via `v700ToV600Bridge` (getExplicitCounterpartyById → v4, no v6/v5 twin), `X-OBP-Version-Served: v6.0.0`. Kept deliberately in v7: `deleteEntitlement` (204), `addEntitlement` (409), `getUserByUserId` (404) — intentional RESTful response-code improvements over the older v6 200/400 convention.

**Tests**: `Http4s700RoutesTest` (91 scenarios, port 8087). `makeHttpRequest` returns `(Int, JValue, Map[String, String])`. `makeHttpRequestWithBody(method, path, body, headers)` for POST/PUT.
## Migrating a Lift Endpoint to http4s

Rules apply regardless of which version file the endpoint lives in. Use v7.0.0 only when the API signature is new or changed; otherwise migrate in-place in the original version file.

### Rule 1 — ResourceDoc registration
```scala
// Declare val FIRST, then register — see Rule 5 why order matters
val myEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] { ... }

resourceDocs += ResourceDoc(
  implementedInApiVersion,  // first param; ResourceDoc.partialFunction (OBPEndpoint) was removed in the Lift teardown
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

**`resource-docs` version dispatch**: `GET /obp/v7.0.0/resource-docs/API_VERSION/obp` accepts any valid API version string. Delegates to `ResourceDocs140.ImplementationsResourceDocs.getResourceDocsList(requestedApiVersion)` which dispatches per version (v7.0.0 → `Http4s700.resourceDocs`, v6.0.0 → `Http4sResourceDocAggregation.v600`, etc.). An invalid/unknown version string returns 400.

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
resourceDocs += ResourceDoc(implementedInApiVersion, ..., "/banks/FIREHOSE_BANK_ID/firehose/...", ..., None, ...)
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

**Empty path segments fall into http4s patterns that should reject them**: A Lift test like `getSystemView("")` builds URL `/system-views/`. http4s's `Path` keeps the trailing empty segment, so `case GET -> prefixPath / "system-views" / viewIdStr` matches with `viewIdStr = ""`. Meanwhile `ResourceDocMatcher.matchesUrlTemplate` filters empty segments via `.split("/").filter(_.nonEmpty)`, so the matcher sees 1 segment vs the template's 2 — no doc match → middleware skips auth/role validation and falls through to your handler with `viewIdStr = ""`. The handler then throws inside the business logic → 500 (test expected 401/403 from middleware). Fix: add a pattern guard so empty viewId doesn't match and the request falls through to `notFoundCatchAll` (JSON 404): `case req @ GET -> prefixPath / "system-views" / viewIdStr if viewIdStr.nonEmpty =>`. Apply to GET/PUT/DELETE variants.

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

**Reserved ALL_CAPS literals — don't use them as placeholders**: `ResourceDocMatcher` in `Http4sSupport.scala` keeps an explicit `literalAllCapsSegments` set: `SANDBOX_TAN`, `COUNTERPARTY`, `SEPA`, `FREE_FORM`, `ACCOUNT`, `ACCOUNT_OTP`, `REFUND`, `SIMPLE`, `AGENT_CASH_WITHDRAWAL`, `CARD`, `OPEN_CORRIDOR_PROMISE`, `OPEN_CORRIDOR_SETTLEMENT`, `EMAIL`, `SMS`, `IMPLICIT`, `NOT_EMAIL_NEITHER_SMS`. These are matched as **literals** (real Lift endpoints register them as concrete SCA-method / transaction-request-type segments — e.g. `/banks/BANK_ID/my/consents/EMAIL`). Any other ALL_CAPS segment is a wildcard. If you migrate an endpoint whose URL template uses one of these names as a *placeholder variable* (e.g. v3.0/v4.0 `getUsersByEmail` had `/users/email/EMAIL/terminator` with EMAIL meaning "any email value"), the matcher will only fire when the URL segment is literally `EMAIL` — real callers pass actual addresses and miss the doc entirely → middleware skips auth/role validation → handler 500s on the empty CallContext. Rename the placeholder to something outside the literal set (e.g. `EMAIL` → `USER_EMAIL`), and apply the rename in **both** the http4s `ResourceDoc` and the original Lift `ResourceDoc` (resource-docs aggregation reads both, and `collectResourceDocs` dedup keys off URL + verb).

**Bypass roles vs required roles**: Some Lift handlers check entitlements inline as **bypass** conditions inside authorisation helpers — e.g. `checkAuthorisationToCreateTransactionRequest` honours `canCreateAnyTransactionRequest` to let the caller skip the view-permission check, but the role is never a hard requirement. These roles are correctly absent from the Lift ResourceDoc role list — putting them in the doc would make Lift enforce them as required (since Lift DOES enforce doc roles by default), breaking the "view permission OR role" intent. The same holds for http4s middleware. So the trap on migration is the reflex copy: don't move a bypass role from inline-only into `Some(List(...))` just because it appears in the handler. Audit before copying: if the role appears in the Lift handler only inside an authorisation OR-chain ("has view permission OR has role X"), it belongs as `None` in the doc with the inline view/role logic preserved. Bypass roles must stay out of the doc.

**Bridge-cascade hijack**: when a new version (e.g. v4.0.0) *overrides* an endpoint from an earlier version with the same URL + verb (e.g. v4's `POST /banks` adds entitlement-granting that v2.2.0's `POST /banks` doesn't have), the v4 override **must** be migrated to `Http4s400`'s own-routes **before** wiring `Http4s400` into the chain. Otherwise the path-rewriting bridge cascade silently sends the request to the older handler:

```
POST /obp/v4.0.0/banks
  → Http4s400 own-routes  (no POST /banks match — falls through)
  → v400ToV310Bridge       (rewrites to /obp/v3.1.0/banks, calls Http4s310)
  → ... cascades down ...
  → Http4s220              (HAS POST /banks → executes v2.2.0 createBank ✗)
```

Before `Http4sLiftWebBridge` was removed, an un-migrated v4 override fell all the way through to the Lift bridge, which honoured the `collectResourceDocs` URL+verb dedup that keeps the highest-version handler for each route — so Lift's v4 createBank ran and the test passed. **That safety net is gone**: the chain now terminates in `notFoundCatchAll`, so a v4 path not matched by `Http4s400`'s own routes cascades down the http4s version bridges to an older handler (or 404s) — it never reaches a Lift v4 handler. Cure: before flipping a new version's `wrappedRoutesVxxxServices` into `Http4sApp.baseServices`, audit the version's overrides (Lift's `excludeEndpoints` is *not* the right list — it only names *removed* endpoints, not overrides) and migrate them too.

How to find overrides for a version: grep `lazy val (\w+)` in the target `APIMethods*.scala`, then check whether the same URL + verb also appears in any older `APIMethods*.scala`. The intersection is the override set. Migrate that set as part of the same PR that introduces the bridge; otherwise reviewers will see test failures whose proximate cause (a downstream version's handler running) doesn't match the file the migration touches.

Symptoms in tests: a v4-specific assertion fails (e.g. an entitlement should-be-granted check returns false). The HTTP response is usually a successful 200/201, just from the wrong handler — so it can look like a flaky failure on the surface.

**JVM 64KB `<init>` limit in per-version files**: around ~140 endpoints, an `Http4sXxx` object's `<init>` exceeds the JVM 64KB-bytecode-per-method limit and won't compile. Adopt from the start (don't wait for the wall): (1) declare endpoints as `lazy val xxx: HttpRoutes[IO] = HttpRoutes.of[IO] { ... }` (not `val`) so lambda materialisation moves out of `<init>` into per-field `lzycompute` methods; (2) group `resourceDocs += ResourceDoc(...)` calls into `private def initXxxResourceDocs(): Unit` blocks of ~10–15 endpoints, each called once from the object body. Each helper def gets its own 64KB budget. (Pattern shipped in `Http4s600.scala`.)

**`isStatisticallyTooPermissive` is sample-pool-dependent**: a fresh local test DB with a single user trips the ABAC-permissiveness check and causes spurious rejections. Seed enough users in any test exercising ABAC rules — it's a test-data issue, not a regression.

**The build stamp comes from a script, not a Maven plugin**: `git.properties` (what `/status` and the root endpoint's `git_commit` report) is written by `scripts/write_git_properties.sh`, invoked from `obp-api/pom.xml`'s `maven-antrun-plugin` execution `generate-git-properties` at `generate-resources`, straight into `target/classes`. It used to be `git-commit-id-maven-plugin`, which was wrong in two ways: its bundled JGit 6.7 has no `commondir` support, so `GitDirLocator.resolveWorktree()` redirects a linked worktree's gitdir to the *main* checkout's `.git` — every build run from `.claude/worktrees/*` stamped the main checkout's branch and commit — and its `PropertiesFileGenerator` skips rewriting when only `git.build.time` differs, freezing the timestamp. Add stamp fields by editing the script (keep the `git.*` key names; `StatusPage.scala` and `APIUtil.gitCommit` read them by name), and don't reintroduce a per-module generator: exactly one `git.properties` may be on the runtime classpath, otherwise which one is reported is incidental. `.github/workflows/test_worktree_build.yml` guards both failure modes.

## CI (shard map + run tips)

Perf note: integration tests are DB/HTTP-bound (~0.4 s/test) on both frameworks; the http4s win is the **pure-unit tier** (no running server, ~0.008 s/test). `ResourceDocsTest`/`SwaggerDocsTest` are the slowest per-test cost — they serialize the whole API surface, so cost grows with endpoint count. `Http4sResourceDocs` already caches the serialized output (`Caching.{getDynamic,getStatic,getAll}ResourceDocCache` + `getStaticSwaggerDocCache`, keyed via `APIUtil.createResourceDocCacheKey`), so repeat requests for the same version/params skip re-serialization.

### Shard assignment

Shards are defined per-matrix-entry in `.github/workflows/build_pull_request.yml` and `.github/workflows/build_container.yml` (both files carry an identical 10-shard matrix — update both when reshaping). Shard 8 runs a **catch-all**: any `.scala` test file whose package is not covered by shards 1–7, 9, and 10 is appended automatically at runtime — new packages are never silently skipped. Extras are printed in the step log under `"Catch-all extras added to shard 8"`. Shard 1 (`code.api.v4_0_0` non-Dynamic) is itself discovered at runtime rather than hand-listed — see the "Run tests" step's `matrix.shard = 1` branch — specifically so a newly added class in that package can never fall through both shard 1 and the catch-all.

| Package prefix | Shard |
|---|---|
| `code.api.v4_0_0` (non-`Dynamic*`, discovered at runtime) | 1 |
| `code.api.v1_2_1` | 2 |
| `code.api.v6_0_0` | 3 |
| `code.api.v5_1_0`, `code.api.v5_0_0`, `code.api.v3_0_0` | 4 |
| `code.api.ResourceDocs1_4_0`, `code.api.v3_1_0`, `code.api.v1_4_0`, `code.api.v1_3_0` | 5 |
| `code.api.v7_0_0`, `code.api.http4sbridge`, `code.api.UKOpenBanking` | 6 |
| `code.model`, `code.views`, `code.customer`, `code.usercustomerlinks`, `code.api.util`, `code.errormessages`, `code.atms`, `code.branches`, `code.products`, `code.crm`, `code.accountHolder`, `code.api.berlin`, `code.api.v2_*` | 7 |
| `code.connector`, `code.util`, `code.api.Authentication*`, `code.api.dauthTest`, `code.api.DirectLoginTest`, `code.api.gateWayloginTest`, `code.api.OBPRestHelperTest`, `code.entitlement`, `code.bankaccountcreation`, `code.bankconnectors`, `code.container`, `code.management`, `code.metrics`, `code.concurrency` | 8 |
| anything else | **8** (catch-all) |
| `code.api.v4_0_0.Dynamic*` | 9 |
| `code.api.v4_0_0.JsonSchemaValidationPublicPropTrueTest`, `code.api.v4_0_0.AuthenticationTypeValidationPublicPropTrueTest` (tagged `PropGatedPublicEndpoint`) | 10 |

Shard 10 is a special case: it's the only shard that overrides pom.xml's default `tagsToExclude` (which otherwise skips `PropGatedPublicEndpoint` everywhere) and sets `OBP_READ_JSON_SCHEMA_VALIDATION_REQUIRES_ROLE`/`OBP_READ_AUTHENTICATION_TYPE_VALIDATION_REQUIRES_ROLE=true`, because those two props are baked into `Http4s400`'s `ResourceDoc` error lists at object-init time — a single JVM can only ever observe one value of each, so the `true` branch needs its own shard while every other shard (which boots with the props unset, i.e. `false`) exercises the default branch. `run_tests_parallel.sh` (local runner) mirrors this with a dedicated sequential step after its 4 main shards — see that script's own comment near `PropGatedPublicEndpoint`.

To explicitly move a package to a different shard, add it to that shard's `test_filter` block — it will be excluded from the catch-all automatically. `run_tests_parallel.sh` (local runner) uses a coarser 4-shard layout that folds all 9 CI shards' coverage into 4 wildcardSuites groups — see its own header comment for the mapping.

> **Migration status**: the Lift → http4s migration is complete (`net.liftweb.http` is fully removed from `.scala` sources; there is no Lift fallback in the request chain — see the Architecture section above). The former progress-tracker docs (`LIFT_HTTP4S_MIGRATION.md`, `LIFT_HTTP4S_MIGRATION_V6_AUDIT.md`) were retired once the migration finished; this file (CLAUDE.md) remains the how-to + gotchas reference for the resulting http4s codebase.
