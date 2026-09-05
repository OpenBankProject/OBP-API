# Project Instructions

## Working Style
- Never blame pre-existing issues or other commits. No excuses, no finger-pointing — diagnose and resolve.
- Never add `Co-Authored-By` trailers to commit messages.
- Commit messages, code comments, and PR titles/descriptions: no AI/tool names (Claude, GPT, Copilot, etc.), no AI-typical filler phrasing ("Certainly!", "I'll help you with..."), no emoji, no "AI-generated"/"LLM" labels. Use plain Conventional Commits style (`fix:`, `feat:`, `refactor:`, ...) and set commit author/committer to the actual person directing the work.
- **Goal is full http4s migration** — eliminate Lift Web and all deprecated libraries entirely. Treat Lift code as temporary scaffolding to be removed, not maintained. When fixing bugs or adding features, always prefer the http4s path.
- **Versioning is tech-agnostic** — API version numbers reflect API signature changes (new/changed fields, new behaviour), never the underlying framework. A framework migration (Lift → http4s) happens in-place at the existing version; it does not justify a version bump.
- **`APIMethodsXYZ.scala` (Lift) files are the source of truth for migration.** The commented-out Lift ResourceDocs and endpoints inside each `APIMethodsXYZ.scala` are the canonical reference for what the http4s version should match: URL templates, verb casing, summaries, descriptions, example bodies, error lists, tags. **Do NOT edit these files to make the parity audit pass.** The audit compares http4s against the Lift source-of-truth — when it flags a diff, the fix is to either (a) update http4s to match Lift, or (b) document the difference at the http4s site as a known intentional drift (e.g. a placeholder rename for `ResourceDocMatcher` middleware, or an upstream-driven case-class shape change). Rewriting the Lift comments to match http4s runs the comparison backwards and destroys the historical record. See `scripts/check_lift_http4s_resource_doc_parity.py` for the audit, and `scripts/rehydrate_resource_docs.py` / `scripts/restore_resource_doc_bodies.py` for the canonical Lift → http4s restoration tools.

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

**A date read back from Doobie must be converted to `java.util.Date`, not just typed as one**: the driver hands back `java.sql.Date` / `java.sql.Timestamp`, both subclasses of `java.util.Date`, so `value.map(ts => ts: Date)` type-checks and looks right. It is not: json4s serializes those subclasses as an **empty JSON object** rather than a date string, so an endpoint that puts the field straight into its response starts emitting `"start_date": {}`. The failure lands in the *test* as a `MappingException: Do not know how to convert JObject(List()) into class java.util.Date`, which points at the reader rather than at the store. Lift's `MappedDate`/`MappedDateTime` handed out a plain `java.util.Date`; convert explicitly on read:
```scala
private def readDate(value: Option[java.sql.Timestamp]): Date = value.map(t => new Date(t.getTime)).orNull
```
Targeted tests do not catch this — the transaction-request suites passed while the v1.4/v2.x transaction-request *listing* scenarios in another shard failed on it.

**A row that a connector result carries must expose the trait's field names, not the column names**: `ConnectorUtils.proxyConnector` serializes a connector result to JSON and re-extracts it as the matching `InBound*` DTO, so a case class whose field is named after the column (`bankIdValue`) rather than after the trait member (`bankId`) comes back with that field **null** — `ProxyConnectorTest` fails with `NPE ... because the return value of Bank.bankId() is null`. Naming the row's fields after the trait it implements (and giving them the trait's types, e.g. `bankId: BankId`) is what keeps the round-trip working. This only bites entities that appear in a connector method's return type; a store-internal row can be named freely.

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

**Lift tolerated `null` query parameters; Doobie throws — bind `Option`, not the bare value**: `By(field, null)` in Lift renders `field = NULL`, which matches nothing and quietly returns an empty list. Doobie's `Put` for a non-nullable type instead throws `oops, null` (`doobie.util.Put.unsafeSetNonNullable`), which surfaces as a 500 far from the cause — the async stack trace contains only doobie frames, no OBP ones, so it does not point at the offending query. Callers really do pass nulls inside a `Some`: `getMethodRoutings(Some(methodName), Some(true), Some(bankId))` gets its `bankId` from a reflective scan of connector-method arguments, which yields `Some(null)` when the argument is absent. When migrating a filter whose value can be null, bind it as `Option` so SQL-NULL semantics are preserved:
```scala
methodName.map(v => fr"methodname = ${Option(v)}")   // null -> `= NULL`, matches nothing (as Lift did)
methodName.map(v => fr"methodname = $v")             // null -> throws at bind time, 500s
```
This bites hardest on tables read from a hot path where the null case is rare: the targeted suite passes and only the full suite, running a wider set of argument shapes, hits it.

The same trap exists on the **write** side and is easier to miss, because the null is a literal in the
caller rather than a value that arrived from data. `Http4s310.createProduct` passes
`termsAndConditionsUrl = null` directly to the connector; Lift's `MappedString` stored that as SQL
NULL and read it back as null, while a bare `String` binding throws at bind time. Worse, the throw is
usually swallowed: these writes sit inside `tryo`, so it becomes a `Failure` and surfaces as whatever
status the endpoint maps that to — the product case reported **404 instead of 201**, with no mention
of a null anywhere. When migrating a write, grep the endpoints for literal `null` arguments and bind
every free-text column as `Option`, reading it back with `.orNull`:
```scala
sql"... mtermsandconditionsurl = ${Option(termsAndConditionsUrl)} ..."   // null -> SQL NULL, as Lift did
sql"... mtermsandconditionsurl = $termsAndConditionsUrl ..."             // null -> throws, caught by tryo, wrong status
```
Columns that a code path treats as a sentinel are the exception and must stay non-null — e.g.
`mappedproduct.mparentproductcode`, where `""` terminates `getProductTree`'s walk and a null would
break it instead of ending it.

**Audit the callers before writing the store, not after the suite fails.** Three consecutive
migrations (products, branches, account holders) compiled, passed their targeted suites, and then
failed the full run on a null that arrived from a call site the store's author had not read. The
nulls are never in the table's own semantics — they come from the domain above it:
- a literal in the caller (`Http4s310.createProduct` passes `termsAndConditionsUrl = null`);
- an identifier that is optional for some rows (`canRevokeOwnerAccess` looks account holders up by a
  `ViewDefinition`'s `bankId`/`accountId`, and a SYSTEM view has neither);
- a value reflected out of connector-method arguments (`getMethodRoutings`' `bankId`).

So before writing a store: grep every caller for literal `null` arguments and for `.orNull`, and ask
of each identifier whether some row in the domain legitimately lacks it. Binding a string as `Option`
costs nothing when the value is never null; getting it wrong costs a full-suite round trip and a
stack trace with no OBP frames in it.

**A Mapper field type can carry validation and set-filters that the column does not show.** A
migration that reads the DDL and the entity's own `object` declarations still misses what the *field
type* did. `MappedEmail` is the worst offender: it lowercases and trims on every set
(`setFilter = notNull :: toLower :: trim`) and it validates the address on save — so a column that
looks like a plain `VARCHAR(100)` was in fact normalised on write and rejected when malformed. The
entity never mentions either behaviour. `MappedPassword` is the same story on a larger scale: it
writes two columns and bcrypts on set.

Before rewriting an entity, read the *field type's* source in `lift-persistence`, not just the
entity: `setFilter`, `validate`, `validations`, `dbColumnCount`. Then reproduce the filter where the
entity used to assign the field (so the stored value and the validated value are the same one), and
reproduce the validation in field-declaration order, because `MetaMapper.validate` concatenates
per-field errors in that order and callers join them into one message tests assert on.

Two ways this went wrong on the consumer table, both caught only by the full suite:
- `Consumer.validate(row.copy(name = ""))` — blanking a field to skip its uniqueness re-check also
  tripped its min-length rule, so *every* consumer creation failed with "Application name: must be
  at least 3 characters". 412 failures in one shard, all from one line, all in test setup
  (`DefaultUsers.testConsumer`) rather than in anything resembling the changed code.
- the developer-email validation was simply absent from the rewrite, because `MappedEmail` declares
  it in the framework rather than in the entity.

**`Option` is not enough on its own: `Some(null)` still throws.** Doobie's `Put` for `Option[A]`
writes SQL NULL only for `None` — a `Some` is unwrapped and its contents handed to the non-nullable
`Put`, so `Some(null)` fails exactly like a bare null. This bites when the Option is built from a
domain value rather than from a literal: `Some(view.bankId.value)` is `Some(null)` for a SYSTEM
view, and `getMethodRoutings(..., Some(bankId))` is `Some(null)` when the reflected argument is
absent. Collapse it before binding:
```scala
value.flatMap(Option(_)) match {          // Some(null) -> None -> `IS NULL`, as Lift rendered it
  case Some(v) => column ++ fr" = $v"
  case None    => column ++ fr" IS NULL"
}
```
Wrapping at the binding site (`${Option(v)}`) does the same job for a bare `String` parameter.

**Liquibase is the only schema authority, and the default is the CI configuration.**
`ToSchemify.models` is `Nil`, so Schemifier creates nothing: if Liquibase does not run, the
database has no tables at all. That makes `liquibase.enabled` (default **true**) a switch between
"the application manages the schema" and "you manage it yourself", not between two tools. The
default matters more than it looks, because the workflows write `test.default.props` from scratch
and never mention the prop — the code's default *is* what CI runs. It bit CI for the whole of PR
91's review under the old `flyway.enabled`: the local `test.default.props` is gitignored and had
the prop added by hand, so the local suite reported `ALL SHARDS PASSED` while every CI shard
aborted in under a minute on `Table "CHATROOM" not found (this database is empty)`. Before
reporting a suite green, check whether the behaviour depends on a prop, and diff the local props
against the workflow's Setup-props step; to prove a fix works under CI conditions, remove the line
locally and re-run.

**One changelog, every vendor — that is why Liquibase replaced Flyway.** Flyway applies
hand-written SQL, so a vendor is supported only once somebody writes its whole script set in that
dialect: it had 118 scripts for h2 and 118 for postgres and nothing for mysql, sqlserver or oracle,
three drivers its `vendorFolder` named and would have booted against silently, with no tables. OBP
does not choose the database; the bank's data source does. `db/changelog/db.changelog-master.yaml`
describes each change once and Liquibase emits the dialect per database.

The baseline was **generated from a Postgres database the Flyway scripts built**, not written by
hand, so it inherits Schemifier's exported DDL rather than somebody's type mapping — regenerate it
with `scripts/GenerateChangelog.java` + `scripts/normalise_generated_changelog.py`, never by hand.
From Postgres and not from H2 because H2 stores identifiers uppercase, and a changelog carrying
uppercase names becomes a case-sensitive `"MAPPEDATM"` on Postgres that every unquoted lowercase
query would never find. Three things the generator gets wrong or cannot see, all handled by the
normaliser and the master changelog:

- **timestamped changeset ids and the generating user as author** — both are the identity in
  `DATABASECHANGELOG`, so regenerating would make Liquibase re-apply the whole schema to a database
  that already has it. The normaliser derives them from the object created.
- **Postgres catalogue spellings** — `DOUBLE` reads back as `FLOAT8`, `TIMESTAMP` as `TIMESTAMP
  WITHOUT TIME ZONE`. Unbounded text is worse: it has *no* portable spelling, and Liquibase's own
  `TEXT` becomes `CHARACTER LARGE OBJECT` on H2 where the scripts declared
  `CHARACTER VARYING(1000000000)` — a CLOB rather than a varchar, on 36 columns. It is the
  `text.type` property the master changelog defines per vendor.
- **the eight `DELETE`s that collapse duplicates before a unique index can be built** —
  `generateChangeLog` snapshots a schema and a DELETE leaves nothing to snapshot. They are
  hand-written in `db.changelog-dedup.yaml`, guarded by a `tableExists` precondition so a fresh
  database marks them run without executing them, and frozen in
  `.github/scripts/check_changelog_data_migrations.py`.

**H2 now needs `NON_KEYWORDS=VALUE` in the URL.** The Flyway scripts quoted every identifier, so a
`"VALUE"` column never met the keyword; the changelog's unquoted `value` does, and `CREATE TABLE`
fails without it. Already in `test.default.props` and the sample template.

**Upgrading an existing deployment**: `LiquibaseSchemaSetup.bringUpToDate` decides from the state
of the database, because a deployment upgrading in place has no opportunity to run a command
first — tables but no `DATABASECHANGELOG` means `changelogSync` (Liquibase's counterpart of
Flyway's `baselineOnMigrate`) and then `update`. Two traps in that check, both real:

- reading JDBC metadata **unscoped** returns the database's own catalogue too — H2 reports its
  `INFORMATION_SCHEMA` tables — so a genuinely *empty* database looks populated, takes the adoption
  path, and has all 410 changesets marked applied without one of them running. Scope the lookup to
  the connection's own schema. `LiquibaseOnExistingSchemaTest` asserts the empty case for exactly
  this reason.
- a start killed part-way leaves its row in `DATABASECHANGELOGLOCK` and every later start waits on
  a lock nobody will release. `bringUpToDate` catches `LockException` and names the fix
  (`liquibase releaseLocks`, or `DELETE FROM DATABASECHANGELOGLOCK`); the default behaviour is a
  silence that reads as a hang.

**Liquibase creates two bookkeeping tables**, `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`.
Never add them to `ServerSetup.resetDatabaseForTestClass()`'s `DELETE FROM` list — for the same
reason `migrationscriptlog` is excluded there: clearing them makes every `mvn test` re-run every
changeset against objects that already exist and abort the boot.

**A nullable column must be read through `Option`; the compiler will not tell you.** Doobie's
`Get` for a non-nullable type throws `NonNullableColumnRead` on a SQL NULL and fails the *whole
query*, not the row — one legacy row turns a listing into a 500. Mapper never failed a read, and
its answer depended on the field type: `MappedString`/`MappedDateTime` returned null,
`MappedBoolean` returned **false** whatever `defaultValue` declared (the getter is
`data openOr false`; `defaultValue` only seeds a *new* instance), `MappedLong`/`MappedInt`
returned the declared default. Rows holding NULL are ordinary: Schemifier added fields to
existing tables with `ALTER TABLE ADD COLUMN` and no backfill. `scripts`-side guard:
`.github/scripts/check_nullable_column_reads.py` reads each column's nullability from the
changelog and holds it against the store's `Row` type; it runs in both workflows and in
`run_tests_parallel.sh`. It read the H2 `CREATE TABLE` with a regex until the changeover, and that
regex's type character class had no comma in it — so `NUMERIC(16, 10)` never matched and five
columns were silently exempt from the check. One of them, `productfee.amount`, was in fact bound
as a bare `BigDecimal` the whole time.

**Running the whole suite on Postgres**: `./run_tests_parallel.sh --db=postgres`. It passes -
3707 scenarios, 0 failures, the same count and the same per-shard split as H2 - and it is worth
re-running whenever the data layer changes, because H2 is forgiving in ways Postgres is not. More
so now that the Postgres DDL is generated from the changelog at boot rather than read from a
script somebody has checked.

Why a runner flag rather than a props edit: every test class opens with ~140 `DELETE FROM`, so
four shards pointed at one database wipe each other mid-run. The flag gives each shard
`obp_suite_shard_N`, creates them before the run and drops them after, including on Ctrl-C. The
`obp_suite_` prefix is what `DisposableDatabaseGuard` admits, so a typo cannot reach a real
database. For a single suite rather than the whole run, uncomment the two Postgres lines in
`test.default.props.template` and create `obp_test_only` with `scripts/create_test_db.sh`.

Two things that bite. `max_connections` defaults to 100 on a Homebrew Postgres, and four shards
at `hikari.maximumPoolSize=20` need 80 on top of whatever else is connected - a local OBP-API
holds 80 by itself. Raising the pool's own limit is not the fix; a pool of 10 exhausts at five
concurrent requests. And Postgres truncates identifiers at 63 bytes, so five of the index names
arrive shortened; `MigratedTablesExistTest` accepts a name or its truncation for that reason.
Checked at the time: no two names collide once truncated.

**The test total is the runner's `Surefire audit` line, not the sum of the shard logs.** Each
shard runs `mvn scalatest:test -pl obp-commons,obp-api`, so its log carries **two** `Run completed`
summaries - one per module. Summing the last `Tests: succeeded N` per shard therefore drops the
obp-commons half and undercounts by ~51. The runner already prints the authoritative figure, read
from the `<testsuite>` roots of both modules' surefire XMLs:

```
Surefire audit: 3758 tests, 0 failures, 0 errors, 0 skipped/canceled
```

This matters more than the arithmetic, because the undercount imitates the one symptom that means
something serious: a test count that moves without a matching change to the test files is the
`~/.m2` contamination signal above. Measured by hand as 3707 against a parallel checkout's 3760,
it looked exactly like contamination and was not - the two runs agreed at 3758 once both were read
off the audit line. Quote that line; never hand-sum the shards.

**Two runners cannot share `~/.m2` while both are running.** `obp-commons` installs to the same
coordinate for every checkout, and the shards resolve it *at run time* - so another checkout's
install swaps the jar under a run already in progress, some suites fail to load, and the
discovered test count silently drops while Maven still reports BUILD SUCCESS. The `OBC_LOCK` at
the top of the runner serialises the *installs*; it does nothing about a running shard's reads.
Observed from a parallel checkout as 3511 -> 3068 -> 1896 across three runs of one commit. Until
the coordinate is per-checkout, only one checkout runs the full suite at a time - and a test count
that moves without a matching change to the test files is the symptom to look for.

**The suite refuses to run against a database that is not disposable.**
`code.setup.DisposableDatabaseGuard`, called from `TestServer` before `Boot.boot()`, allows
`jdbc:h2:mem:*`, `obp_suite_*`, `obp_liquibase_migration_test` and `obp_test_only` (the name
`scripts/create_test_db.sh` creates), and throws on anything else -
`obp-mapped` included. It throws rather than halting the JVM deliberately: halting protected the
data but produced BUILD SUCCESS, because the root pom sets `maven.test.failure.ignore=true` and
the verdict actually comes from the runner grepping the log for `RUN ABORTED`. Note the boundary:
this guards the **Scala** suite. Anything that reaches the database without going through the JVM
- a psql script, a python harness, another running instance - is outside it.

**Postgres**: there is no per-vendor script set any more — Liquibase generates the DDL from the
one changelog. `PostgresMigrationTest` proves the result: it builds a database of its own, migrates
it with `bringUpToDate`, checks the table count against the H2 side, checks the names came through
lowercase and that unbounded text landed as `TEXT`, and drops it. It needs a reachable Postgres and
cancels itself when there is none, so it is a developer check rather than a CI one.

**Verifying the changelog is actually doing something — delete it from `target/classes`, not just `src`**: Liquibase loads from `classpath:db/changelog/`, i.e. `obp-api/target/classes/db/changelog/`. Maven's `process-resources` copies new files there but never deletes ones you removed from `src`. So the natural way to prove the changelog matters — move it out of `src` and re-run the test expecting red — gives a **false green**: the stale copy under `target/classes` is still on the classpath and still applies. (The deleted `db/migration/` scripts sat there for the same reason after they were removed from `src`.) Remove both:
```sh
rm -rf obp-api/src/main/resources/db/changelog \
       obp-api/target/classes/db/changelog
```
The test DB is `jdbc:h2:mem:` (see `test.default.props`), so it is genuinely fresh per JVM — nothing persists between runs, and if a table still appears after you stashed the changelog, the stale `target/classes` copy is why. Confirm with a throwaway probe against `information_schema` rather than assuming. This bites specifically on resource-only changes; Scala-side red/green is unaffected because recompilation overwrites the class files. Done properly the run does not merely fail an assertion — it `RUN ABORTED`s in `Boot`, with `db/changelog/db.changelog-master.yaml does not exist` in the log.

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

Shards are defined per-matrix-entry in `.github/workflows/build_pull_request.yml` and `.github/workflows/build_container.yml` (both files carry an identical 9-shard matrix — update both when reshaping). Shard 8 runs a **catch-all**: any `.scala` test file whose package is not covered by shards 1–7 and 9 is appended automatically at runtime — new packages are never silently skipped. Extras are printed in the step log under `"Catch-all extras added to shard 8"`. Shard 1 (`code.api.v4_0_0` non-Dynamic) is itself discovered at runtime rather than hand-listed — see the "Run tests" step's `matrix.shard = 1` branch — specifically so a newly added class in that package can never fall through both shard 1 and the catch-all.

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

To explicitly move a package to a different shard, add it to that shard's `test_filter` block — it will be excluded from the catch-all automatically. `run_tests_parallel.sh` (local runner) uses a coarser 4-shard layout that folds all 9 CI shards' coverage into 4 wildcardSuites groups — see its own header comment for the mapping.

> **Migration status**: the Lift → http4s migration is complete (`net.liftweb.http` is fully removed from `.scala` sources; there is no Lift fallback in the request chain — see the Architecture section above). The former progress-tracker docs (`LIFT_HTTP4S_MIGRATION.md`, `LIFT_HTTP4S_MIGRATION_V6_AUDIT.md`) were retired once the migration finished; this file (CLAUDE.md) remains the how-to + gotchas reference for the resulting http4s codebase.
