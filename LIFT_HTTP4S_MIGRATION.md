# Lift → http4s Migration

## Principle

API version numbers reflect **API contract changes** (new/changed fields, new behaviour). The underlying framework is invisible to clients. Lift → http4s is a refactoring: it happens **in-place** inside the existing version file at the existing URL. No version bump.

Use a new version (e.g. v7.0.0) only when the API contract itself changes — new fields, changed request/response shape, new behaviour.

---

## Current Architecture

OBP-API runs as a **single http4s Ember server** (single process, single port). The application entry point is a Cats Effect `IOApp` (`Http4sServer`). Lift is no longer used as an HTTP server — Jetty and the servlet container have been removed.

Lift still plays two roles:

1. **ORM / Database** — Lift Mapper manages schema creation, migrations, and data access.
2. **Legacy endpoint dispatch** — Older API versions are handled through a bridge (`Http4sLiftWebBridge`) that converts http4s requests into Lift requests, runs them through Lift's dispatch tables, and converts the responses back.

New API versions are implemented as native http4s routes and do not pass through the bridge.

### Entry point — `Http4sServer.scala`

`Http4sServer` extends `IOApp`. On startup it:

1. Calls `bootstrap.liftweb.Boot().boot()` to initialise Lift Mapper, connectors, and OBP configuration.
2. Parses the configured `hostname` and `dev.port` props (defaults: `127.0.0.1`, `8080`).
3. Starts an Ember server with the application defined in `Http4sApp.httpApp`.

### Priority routing

Routes are tried in order: `corsHandler` (OPTIONS) → `AppsPage` → `StatusPage` → `Http4s510` → `Http4s600` → `Http4s500` → `Http4s700` → `Http4sBGv2` → `Http4s400` → `Http4s310` → `Http4s300` → `Http4s220` → `Http4s210` → `Http4s200` → `Http4s140` → `Http4s130` → `Http4s121` → `Http4sLiftWebBridge` (Lift fallback). Unhandled `/obp/vX.Y.Z/*` paths fall through silently to Lift — they do not 404. The non-numeric ordering (v510 before v600, v500 after v600 etc.) doesn't affect correctness because each per-version service gates on its own version prefix; the ordering only matters when two services overlap on the same URL pattern.

```
HTTP Request
    │
    ▼
Http4sServer (IOApp / Ember)
    │
    ▼
corsHandler → AppsPage → StatusPage → Http4s510 → Http4s600 → Http4s500 → Http4s700 → Http4sBGv2
                                                                                          │
          Http4s400 → Http4s310 → Http4s300 → Http4s220 → Http4s210 → Http4s200 → Http4s140 → Http4s130 → Http4s121 → Http4sLiftWebBridge
              │           │           │           │           │           │           │           │              │
          v4.0.0      v3.1.0      v3.0.0      v2.2.0      v2.1.0      v2.0.0      v1.4.0      v1.3.0        v1.2.1 routes
        own routes  own routes  own routes  own routes  own routes  own routes  own routes  own routes    (all 323 scenarios)
                bridge      bridge      bridge      bridge      bridge      bridge       bridge
                                                                                          │
                                                                                LiftRules.statelessDispatch
                                                                                LiftRules.dispatch (REST API)
    │
    ▼
HTTP Response (with standard headers)
```

### Lift bridge — `Http4sLiftWebBridge.scala`

Handles any request not matched by a native http4s route:

1. Reads the http4s request body.
2. Constructs a Lift `Req` from the http4s `Request[IO]`.
3. Creates a stateless Lift session.
4. Initialises a Lift `S` context and runs `LiftRules.statelessDispatch` / `LiftRules.dispatch`.
5. Handles Lift's `ContinuationException` pattern for async responses (timeout: `http4s.continuation.timeout.ms`, default 60 s).
6. Converts the Lift response back to http4s.

### What Lift still does

| Area | Role |
|------|------|
| **Mapper ORM** | Database schema creation, migrations, and all data access (`MappedBank`, `AuthUser`, etc.) |
| **Boot** | Initialises OBP configuration, connectors, resource docs, and Mapper schemifier |
| **Dispatch tables** | `LiftRules.statelessDispatch` / `LiftRules.dispatch` hold endpoint definitions for versions not yet ported |
| **JSON utilities** | Some serialisation helpers from `net.liftweb.json` are still in use |

---

## What "in-place migration" means per file

### `APIMethods{version}.scala`

| Before (Lift) | After (http4s) |
|---|---|
| `self: RestHelper =>` on the trait | removed |
| `lazy val xyz: OBPEndpoint` | `val xyz: HttpRoutes[IO]` |
| `case "path" :: Nil JsonGet _` | `case req @ GET -> \`prefixPath\` / "path"` |
| `authenticatedAccess(cc)` in for-comp | pick the right `EndpointHelpers.*` helper |
| `implicit val ec = EndpointContext(Some(cc))` | removed |
| `yield (json, HttpCode.\`200\`(cc))` | `yield json` |
| `ResourceDoc(root, ...)` | `ResourceDoc(null, ..., http4sPartialFunction = Some(root))` |

### `OBPAPI{version}.scala`

| Before | After |
|---|---|
| `extends OBPRestHelper` | removed |
| `registerRoutes(routes, allResourceDocs, apiPrefix)` | expose `val allRoutes: HttpRoutes[IO]` |
| registered via Boot / LiftRules | wired into `Http4sServer` chain |

See `CLAUDE.md § Migrating a Lift Endpoint to http4s` for the full Rule 1–5 reference.

---

## Migration Order

Bottom-up — each version depends on the one below it being done.

**Rule: one file = one PR. A file is either fully Lift or fully http4s — no half-converted state.**

**Note on `APIMethods121`**: v1.2.1 was implemented as a new parallel file `Http4s121.scala` (rather than converting the Lift trait in-place) because `APIMethods121` is a mixin trait inherited by `APIMethods130`, `APIMethods140`, etc. Converting the trait in-place would require all inheriting versions to be migrated simultaneously. The parallel file approach lets v1.2.1 go first — http4s routes take priority in the chain; the Lift trait remains until all inheriting versions are done, at which point the Lift trait can be deleted.

| # | File | Own endpoints | Notes |
|---|---|---|---|
| 1 | `APIMethods121` | 70 | **Done** — `Http4s121.scala` serves all endpoints; 323 tests pass |
| 2 | `APIMethods130` | 3 | **Done** — `Http4s130.scala`: 3 own endpoints + path-rewriting bridge to `Http4s121`; 2 PhysicalCardsTest scenarios pass |
| 3 | `APIMethods140` | 11 | **Done** — `Http4s140.scala`: 11 own endpoints + path-rewriting bridge to `Http4s130` |
| 4 | `APIMethods200` | 40 | **Done** — `Http4s200.scala`: 37 own endpoints + path-rewriting bridge to `Http4s140` |
| 5 | `APIMethods210` | 28 | **Done** — `Http4s210.scala`: 25 own endpoints + path-rewriting bridge to `Http4s200`; all 79 v2.1.0 tests pass |
| 6 | `APIMethods220` | 19 | **Done** — `Http4s220.scala`: 18 own endpoints + path-rewriting bridge to `Http4s210`; all 27 v2.2.0 tests pass |
| 7 | `APIMethods300` | 47 | **Done** — `Http4s300.scala`: 47 own endpoints + path-rewriting bridge to `Http4s220`; all 86 v3.0.0 tests pass |
| 8 | `APIMethods310` | 102 | **Done** — `Http4s310.scala` has all 100 functional endpoints (42 GET, 10 DELETE, 19 POST, 25 PUT, 1 GET-shaped revoke, 3 SCA aliases) + path-rewriting bridge to `Http4s300`; 181 v3.1.0 tests pass. `getMessageDocsSwagger` is shadowed in production by `Http4sResourceDocs.routes` but the Lift `lazy val` is intentionally kept — its deletion is caught by `FrozenClassTest` as a STABLE-API surface reduction. Both `getMessageDocsSwagger` and `getObpConnectorLoopback` retire together in the bridge-removal PR. |
| 9 | `APIMethods400` | 258 | **Done — 258 / 258 (100%)**. `Http4s400.scala` covers all 253 unique handlers (`lazy val NAME: HttpRoutes[IO]`) plus 8 ResourceDoc aliases for the transaction-request-type variants (ACCOUNT, ACCOUNT_OTP, SEPA, COUNTERPARTY, REFUND, FREE_FORM, SIMPLE, AGENT_CASH_WITHDRAWAL — handled by the shared `createTransactionRequest` wildcard handler; the `literalAllCapsSegments` set in `Http4sSupport.scala` dispatches the matcher to the per-type doc for swagger purposes). Adopts the **lazy val + helper-def init pattern** (Batches 1–19) introduced in v6 to dodge the JVM 64KB `<init>` method-size limit. **Bridge-cascade hijack** historically threatened v4's overrides; resolved by migrating all 35/35 v4-over-older URL+verb overrides. |
| 10 | `APIMethods500` | 10 | **Done** — `Http4s500.scala` (all v5.0.0 originals migrated) |
| 11 | `APIMethods510` | 111 | **Done** — `Http4s510.scala`. v5.1.0's `createConsent` Lift handler is exposed in Http4s510 under the alias name `createConsentImplicit` (a single handler with `if scaMethod == "EMAIL" \|\| scaMethod == "SMS" \|\| scaMethod == "IMPLICIT"` guard covers all three SCA-method URLs). |
| 12 | `APIMethods600` | 243 (35 overrides + 208 originals) | **Done — 243 / 243 (100%)**. `Http4s600.scala` covers all v6 originals and overrides. Wired into `Http4sApp.baseServices` ahead of the Lift bridge. Architecturally introduced the **lazy val + helper-def init pattern** to dodge the JVM 64KB `<init>` method-size limit (`val xxx: HttpRoutes[IO]` ⇒ `lazy val xxx`; `resourceDocs += ResourceDoc(...)` calls grouped into `private def initXxxResourceDocs(): Unit` blocks). Future per-version files should adopt the same pattern from the start. |

---

## Resource-docs (separate workstream)

Resource-docs endpoints are **version-polymorphic**: `GET /obp/v6.0.0/resource-docs/v3.0.0/obp` returns v3.0.0 docs. The URL prefix is cosmetically version-specific but functionally irrelevant — the `API_VERSION` path segment controls the output. This makes resource-docs a natural candidate for a single centralized http4s service rather than per-version handlers.

### Strategy: centralized `Http4sResourceDocs`

Add one service to `Http4sApp` (above the Lift bridge, before any per-version service) that handles:

```
GET  /obp/*/resource-docs/API_VERSION/obp           → version-dispatch via getResourceDocsList
GET  /obp/*/resource-docs/API_VERSION/openapi.yaml
GET  /obp/*/message-docs/CONNECTOR/swagger2.0       → absorbs APIMethods310.getMessageDocsSwagger
```

The wildcard prefix means all resource-doc requests are intercepted regardless of which version prefix the client uses. This workstream is **independent of the per-version migration order** — it can land at any time and immediately removes all resource-docs traffic from the Lift bridge.

### Prerequisite: fix the aggregation bug

`V7ResourceDocsAggregationTest` is intentionally failing. The current `getResourceDocsObpV700` has a broken branch for `requestedApiVersion == v7.0.0` that manually iterates `allResourceDocs` (~45 own docs) instead of calling `getResourceDocsList`, which aggregates all 500+. Fix this first — it is the same defect the centralized service must not repeat.

### `openapi.yaml`

Currently served via a raw Lift `serve { case Req(..., "openapi.yaml", ...) }` block that bypasses `registerRoutes` entirely. Needs a dedicated http4s route (no ResourceDocMiddleware) added to the centralized service.

### Caching

`Caching.getStaticSwaggerDocCache()` / `setStaticSwaggerDocCache()` are framework-agnostic and already used from within the http4s path. No migration work needed.

### Steps

1. Fix aggregation bug in `getResourceDocsObpV700` → make `V7ResourceDocsAggregationTest` pass.
2. Extract shared handler logic into `Http4sResourceDocs` service; wire into `Http4sApp`.
3. Add `openapi.yaml` route to the same service.
4. Port `getMessageDocsSwagger` from `APIMethods310` into the same service (currently still served by the Lift bridge — see "Per-version Lift leftovers" below).
5. Remove resource-docs from the per-version Lift objects (`ResourceDocs140`–`ResourceDocs600`) once the centralized service covers them.

---

## ResourceDoc parity (per-version drift from Lift)

Separate from the resource-docs **serving** workstream above, there is a parity workstream covering the **content** of each migrated ResourceDoc declaration. The goal is for every http4s `ResourceDoc(...)` to render identically to its Lift original, so the public API docs aren't silently degraded by migration.

### Principle

**`APIMethodsXYZ.scala` (Lift) is the source of truth for migration.** The commented-out Lift ResourceDocs and endpoints inside each `APIMethodsXYZ.scala` are the canonical reference for what the http4s version should render: URL templates, verb casing, summaries, descriptions, example bodies, error lists, tags. **Do NOT edit these files to make the audit pass** — the audit compares http4s against the Lift source-of-truth. When the audit flags a diff, the resolution is either (a) update http4s to match Lift, or (b) document the difference at the http4s site as a known intentional drift (placeholder rename for middleware, upstream-driven case-class shift, etc.). Rewriting the Lift comments runs the comparison backwards and erases the historical record. (Mistakes in commits `d95c1df01` and `6154bf2cc` did this; reverted in `27f48af72`.)

**Stub fidelity verified.** Commits `810589330` (v6) and `88f46f854` (v5.1) replaced the live Lift code with commented-out stubs. Comparing each stub's uncommented ResourceDoc bodies against the pre-stub live versions: **0 field diffs across 243/243 v6 docs and 111/111 v5.1 docs**. The non-ResourceDoc deltas (imports, etc., ~16KB v6 / ~5KB v5.1) are immaterial. The stubs are an exact preservation of the original Lift ResourceDocs.

### Tooling (`scripts/`)

| Script | Role |
|---|---|
| `check_lift_http4s_resource_doc_parity.py` | Read-only audit. Parses both files, matches by `nameOf(...)` (with `.replace("a","b")` evaluation for derived names), reports per-field diffs. `--field=X` to focus, `--list-only` for endpoint-presence summary. |
| `rehydrate_resource_docs.py` | Upstream (simonredfern, `67593ea28`). Lifts positional args 7/8/9 (description, exampleRequestBody, successResponseBody) from commented Lift blocks into http4s. Has a `split-init` subcommand for JVM 64KB method-size workaround. |
| `restore_resource_doc_bodies.py` | Companion to the above. Restores any subset of (summary, description, exampleRequestBody, successResponseBody, errorResponseBodies, tags) from Lift into http4s. Surgical per-field replacement preserves layout. `--fields=X,Y` to scope, `--only=ep` to target one endpoint. |

### Current drift (audit re-run 2026-05-21 evening)

| Version | shared | mismatch | only-lift | only-http4s | Status |
|---|---|---|---|---|---|
| v1_2_1 | 70  | 6  | 0 | 0 | semantic fields restored; 6 structural drifts remain |
| v1_3_0 | 3   | 0  | 0 | 0 | clean |
| v1_4_0 | 10  | 1  | 0 | 0 | one minor |
| v2_0_0 | 37  | 1  | 0 | 0 | semantic fields restored; 1 structural drift remains |
| v2_1_0 | 23  | 1  | 5 | 2 | semantic fields restored; 1 structural drift remains |
| v2_2_0 | 18  | 0  | 0 | 18 | Lift trait fully retired upstream (commit `71892f5cb`); audited against pre-stub Lift via git history; 13 fields restored; 3 middleware URL renames remain |
| v3_0_0 | 47  | 4  | 0 | 0 | semantic fields restored; 4 middleware-driven URL renames remain |
| v3_1_0 | 102 | 5  | 0 | 0 | semantic fields restored; 5 structural drifts (placeholder renames) remain |
| v4_0_0 | 254 | 20 | 2 | 5 | semantic fields restored; 20 structural drifts (placeholder renames + 1 verb fix) remain |
| v5_0_0 | 39  | 8  | 0 | 3 | descriptions restored; structural/errors remain |
| v5_1_0 | 111 | 1  | 1 | 2 | one verb-casing drift to fix |
| v6_0_0 | 243 | 12 | 0 | 1 | 11 placeholder renames + 1 routing-shape upstream change |
| **Total** | **956** | **60** | | | |

### v6.0.0 — 12 specific drifts (each is a fix candidate)

These are the cases where http4s deviates from Lift. Under the source-of-truth rule, the default is to fix http4s; deliberate exceptions need to be documented at the http4s site.

| Endpoint | Field | Lift | http4s | Resolution |
|---|---|---|---|---|
| `createCounterpartyAttribute` | requestUrl | `…/counterparties/COUNTERPARTY_ID/attributes` | `…/COUNTERPARTY_ID_PARAM/…` | TBD — verify `ResourceDocMatcher` correctly handles `COUNTERPARTY_ID` as a wildcard (the literal set contains `COUNTERPARTY`, but `COUNTERPARTY_ID` is whole-segment-different). If safe, revert to Lift's name. |
| `deleteCounterpartyAttribute` | requestUrl | same | same | same as above |
| `getAllCounterpartyAttributes` | requestUrl | same | same | same as above |
| `getCounterpartyAttributeById` | requestUrl | same | same | same as above |
| `updateCounterpartyAttribute` | requestUrl | same | same | same as above |
| `createTransactionRequestCardano` | requestUrl | `…/ACCOUNT_ID/owner/transaction-request-types/CARDANO/…` | `…/ACCOUNT_ID/VIEW_ID/…/CARDANO/…` | **Functional broadening** — http4s lets any view, Lift hardcoded `owner`. Keep http4s; document at the http4s ResourceDoc site. |
| `createTransactionRequestHold` | requestUrl | `…/owner/…HOLD/…` | `…/VIEW_ID/…HOLD/…` | same as above |
| `getSystemViewById` | requestUrl | `/management/system-views/VIEW_ID` | `/management/system-views/SYS_VIEW_ID` | TBD — disambiguation rename. If `ResourceDocMatcher` handles both fine, revert. |
| `updateSystemView` | requestUrl | `/system-views/VIEW_ID` | `/system-views/UPD_VIEW_ID` | same as above |
| `removeBankReaction` | requestUrl | `…/reactions/EMOJI` | `…/reactions/EMOJI_REACTION` | `EMOJI` is NOT in `literalAllCapsSegments` (only `EMAIL`/`SMS`/`IMPLICIT` of the SCA cluster are). Rename may have been defensive; safe to revert. |
| `removeSystemReaction` | requestUrl | same | same | same as above |
| `getAccountDirectory` | successResponseBody | `FastFirehoseRoutings(bank_id, account_id)` | `AccountRoutingJsonV121(scheme, address)` | **Upstream functional change** (`9e151c524` / `9dc4c4c46` migrated the case class). Cannot revert; document. Also note: the same change broke `mvn test` (pre-existing upstream compile error in `JSONFactory6.0.0.scala:2934`). |

Also: 1 only-http4s (`createWebUiProps`) — genuinely http4s-only with no Lift counterpart. Document.

### v5.1.0 — 1 specific drift

| Endpoint | Field | Lift | http4s | Resolution |
|---|---|---|---|---|
| `revokeMyConsent` | requestVerb | `"Delete"` | `"DELETE"` | Trivial casing fix on the http4s side. |

Also:
- 1 only-lift (`createConsentImplicit`) + 1 only-http4s (`createConsent`) — Lift had `lazy val createConsentImplicit = createConsent` aliasing and registered the doc under the alias; http4s registers under the canonical name. Fix: in http4s, either rename the partial function to `createConsentImplicit` to match Lift, or register a second `nameOf(createConsentImplicit)` doc for the same handler.
- 1 only-http4s (`getBanks`) — kept in the v5.1.0 layer for metrics attribution (intentional addition; see comment at `Http4s510.scala:288`). Document.

### v2.2.0 — 3 specific drifts + 18 only-http4s

Upstream commit `71892f5cb` retired `APIMethods220.scala`'s Lift trait entirely (1361 lines → 9-line empty stub). For audit purposes, the Lift source-of-truth is preserved in git history at `71892f5cb^`. A one-off Python script using `restore_resource_doc_bodies.py` helpers (in `scripts/`) extracts the pre-stub `APIMethods220.scala` and runs the standard field restoration against `Http4s220.scala`.

After restoration (13 descriptions + 1 example body + 1 success body), only 3 middleware-driven URL renames remain:

| Endpoint | Field | Lift | http4s | Resolution |
|---|---|---|---|---|
| `createAccount` | requestUrl | `…/accounts/ACCOUNT_ID` | `…/accounts/NEW_ACCOUNT_ID` | PUT-creates-account bypass. **Document**. |
| `createViewForBankAccount` | requestUrl | `…/accounts/ACCOUNT_ID/views` | `…/accounts/VIEW_ACCOUNT_ID/views` | Account-validation bypass. **Document**. |
| `updateViewForBankAccount` | requestUrl | `…/views/VIEW_ID` | `…/views/UPD_VIEW_ID` | Disambiguation rename. **Document**. |

The 18 only-http4s entries are the actual v2.2.0 surface — there is no live Lift counterpart in the file anymore. They're audited indirectly against the git-history Lift.

Two supporting imports were added to `Http4s220.scala` so the restored descriptions / example bodies compile: `code.api.util.Glossary` and `java.util.Date`.

### v3.0.0 — 4 specific drifts

After semantic-field restoration, only middleware-driven URL renames remain.

| Endpoint | Field | Lift | http4s | Resolution |
|---|---|---|---|---|
| `createViewForBankAccount` | requestUrl | `…/accounts/ACCOUNT_ID/views` | `…/accounts/VIEW_ACCOUNT_ID/views` | Middleware account-validation bypass (see CLAUDE.md "Middleware URL template bypass" gotcha). **Document** — required. |
| `updateViewForBankAccount` | requestUrl | `…/views/VIEW_ID` | `…/views/UPD_VIEW_ID` | Disambiguation rename. **Document**. |
| `getFirehoseAccountsAtOneBank` | requestUrl | `/banks/BANK_ID/firehose/accounts/views/VIEW_ID` | `/banks/FIREHOSE_BANK_ID/firehose/accounts/views/FIREHOSE_VIEW_ID` | Firehose middleware bypass. **Document**. |
| `getFirehoseTransactionsForBankAccount` | requestUrl | `/banks/BANK_ID/firehose/accounts/ACCOUNT_ID/views/VIEW_ID/transactions` | `/banks/FIREHOSE_BANK_ID/firehose/accounts/FIREHOSE_ACCOUNT_ID/views/FIREHOSE_VIEW_ID/transactions` | Same firehose pattern. **Document**. |

No only-lift or only-http4s entries for v3.0.0.

### v3.1.0 — 5 specific drifts

After semantic-field restoration (commit `f4b9bd183`), only middleware-driven placeholder renames remain.

| Endpoint | Field | Lift | http4s | Resolution |
|---|---|---|---|---|
| `createAccount` | requestUrl | `/banks/BANK_ID/accounts/ACCOUNT_ID` | `…/NEW_ACCOUNT_ID` | PUT-creates-account pattern. Middleware would 404 on `ACCOUNT_ID` lookup before the handler. **Document** — required. |
| `deleteSystemView` | requestUrl | `/system-views/VIEW_ID` | `/SYS_VIEW_ID` | Disambiguation from other VIEW_ID usages. **Document**. |
| `getSystemView` | requestUrl | same | same | same |
| `updateSystemView` | requestUrl | same | same | same |
| `getFirehoseCustomers` | requestUrl | `/banks/BANK_ID/firehose/customers` | `…/FIREHOSE_BANK_ID/…` | Firehose middleware bypass — prop check must run before bank lookup (see CLAUDE.md). **Document** — required. |

No only-lift or only-http4s entries for v3.1.0.

### v4.0.0 — 20 specific drifts + 2 only-lift + 5 only-http4s

After semantic-field restoration (commit `2b24811e5`), the remaining drifts are all structural / functional:

| Category | Count | Endpoints | Resolution |
|---|---|---|---|
| requestVerb | 1 | `deleteExplicitCounterparty` (Lift `POST` → http4s `DELETE`) | http4s is REST-correct. **Document** as deliberate fix. |
| requestUrl — `VIEW_ID` → `GRANT_VIEW_ID` | 9 | `answerTransactionRequestChallenge` and 8 `createTransactionRequest*` variants (Account/AccountOtp/AgentCashWithDrawal/Counterparty/FreeForm/Refund/Sepa/Simple) | Middleware disambiguation rename. Verify if `VIEW_ID` collides in `ResourceDocMatcher`; if not, revert. If it does, **document**. |
| requestUrl — hyphen→underscore | 6 | `delete`/`get`/`update` × `BankLevelDynamicResourceDoc` / `DynamicResourceDoc` (Lift `DYNAMIC-RESOURCE-DOC-ID` → http4s `DYNAMIC_RESOURCE_DOC_ID`) | The matcher's ALL_CAPS-with-underscores wildcard requires underscores. **Fix Lift**? No — Lift is source-of-truth. **Document** at the http4s site as a required matcher constraint. |
| requestUrl — `COUNTERPARTY_ID` → `COUNTERPARTY_ID_PARAM` | 2 | `deleteExplicitCounterparty`, `getCounterpartyByIdForAnyAccount` | Same as v6's COUNTERPARTY rename family. Verify matcher behavior; revert if safe. |
| requestUrl — `COUNTERPARTY_ID` → `EXPLICIT_COUNTERPARTY_ID` | 1 | `getExplicitCounterpartyById` | Same defensive rename pattern. |
| requestUrl — firehose pattern | 1 | `getFirehoseAccountsAtOneBank` (Lift `BANK_ID/.../VIEW_ID` → http4s `FIREHOSE_BANK_ID/.../FIREHOSE_VIEW_ID`) | Middleware bypass for the prop-check-before-bank-lookup pattern (see CLAUDE.md "Prop check before role check" gotcha). **Document** — required for correctness. |
| requestUrl — Lift URL malformed | 1 | `deleteCustomerAttribute` (Lift `/banks/BANK_ID/CUSTOMER_ID/attributes/.../...` is missing `/customers/`; http4s uses `/banks/BANK_ID/customers/attributes/...`) | Lift URL was buggy. http4s fixed it. **Document** as deliberate URL fix; flag that the Lift comment preserves the original bug as historical record. |

Also: 2 only-lift (`getAllAuthenticationTypeValidationsPublic`, `getAllJsonSchemaValidationsPublic`) — these endpoints exist in Lift v4 but were not migrated to `Http4s400`. **Migration gap** — port them. 5 only-http4s (`createBankLevelDynamicEntity`, `createSystemDynamicEntity`, `updateBankLevelDynamicEntity`, `updateMyDynamicEntity`, `updateSystemDynamicEntity`) — dynamic-entity overrides added in http4s with no Lift equivalent. Document if intentional, or audit whether they should have Lift counterparts.

### v5.0.0 — 8 specific drifts + 3 only-http4s

| Category | Count | Endpoints | Resolution |
|---|---|---|---|
| requestUrl placeholder rename | 1 | `createAccount` (Lift `ACCOUNT_ID` → http4s `NEW_ACCOUNT_ID` for the PUT-creates pattern) | Verify matcher behavior; may be required for `ACCOUNT_ID` literal handling. |
| errorResponseBodies — SCA val-vs-inline | 3 | `createConsentByConsentRequestIdEmail` / `Sms` / `Implicit` | http4s uses `private val createConsentByConsentRequestIdCommonErrors = List(...)` for DRY; Lift inlined the list. Either inline the val in the 3 doc registrations to match Lift verbatim, or extend the audit script to expand simple `val X = List(...)` references. |
| errorResponseBodies — system-view accuracy | 4 | `createSystemView`, `deleteSystemView`, `getSystemView`, `updateSystemView` | http4s has more accurate errors (`SystemViewNotFound`, `SystemViewCannotBePublicError`, `InvalidSystemViewFormat`). Lift had wrong/legacy errors (`BankAccountNotFound`, `$BankNotFound`, `"user does not have owner access"`). **Genuine improvement** — document at http4s site. |

Also: 3 only-http4s (`getBanks`, `getProduct`, `getProducts`) — kept in this layer for metrics attribution. Document.

### Strategy summary

For each remaining drift on a migrated version:
1. **Default**: fix http4s to match Lift verbatim. Use `restore_resource_doc_bodies.py` for field-level restoration.
2. **Documented exceptions**: where the drift is a deliberate http4s improvement or required by middleware semantics, leave the drift and add a `// Lift had X; we use Y because Z` comment at the http4s ResourceDoc site.
3. **Never**: edit `APIMethodsXYZ.scala` to make the audit pass. The Lift comments are the canonical record.

Untouched versions (v1_2_1 through v4_0_0, plus v2_1_0) need the same treatment: run `rehydrate_resource_docs.py` then `restore_resource_doc_bodies.py`, then audit and address any residual drifts at the http4s site.

---

## Auth Stack (separate workstream)

Token-generation paths — not version-file endpoints. Each `extends RestHelper` and needs to become an http4s route or middleware independently. Can run in parallel with the APIMethods migration.

| Component | Path | Notes |
|---|---|---|
| `DirectLogin` | `POST /my/logins/direct` | Done — served by `Http4s600.directLoginEndpoint` (versioned) and `DirectLoginRoutes` (bare path). |
| `GatewayLogin` | gateway JWT exchange | Library-only validator (no routes). |
| `DAuth` | dAuth JWT exchange | Library-only validator (no routes). |
| `OpenIdConnect` | OIDC callback | Blocked — last hard dependency on Lift Web in the request path. See auth-stack leftovers table. |

OAuth 1.0a token endpoints were removed entirely in commit `51820c75e` (2026-02-20); the workstream collapsed.

---

## Per-version Lift leftovers

An `APIMethods{version}` file is marked **done** in the progress table when every *functional* endpoint is on http4s and the version's test suite is green. A small number of endpoints are deliberately *not* migrated inline because they belong to a different workstream or have no behaviour worth porting. They continue to be served by the Lift bridge until the workstream that owns them lands; they do **not** create new follow-up work on the per-version file.

| Endpoint | Origin | Why on Lift | Retired by |
|---|---|---|---|
| `getMessageDocsSwagger` (`GET /message-docs/CONNECTOR/swagger2.0`) | `APIMethods310` | The URL is already served by `Http4sResourceDocs.routes` (`handleGetMessageDocsSwagger`), so the Lift `lazy val` is shadowed dead code. **But** deleting it reduces v3.1.0's STABLE API surface, which `FrozenClassTest` correctly rejects (the frozen-API guard sees a `lazy val ... : OBPEndpoint` go missing from `Implementations3_1_0`). Refreshing the frozen snapshot via `FrozenClassUtil.main` is the documented way out, but doing so also requires touching `GetMessageDocsSwaggerTest`, which is below v7.0.0. Kept as-is until the bridge-removal PR retires it. | The bridge-removal PR. |
| `getObpConnectorLoopback` (`GET /connector/loopback`) | `APIMethods310` | Deprecated stub that unconditionally throws `IllegalStateException(NotImplemented)`; no functional behaviour | Either a 3-line native http4s route that throws the same exception or outright deletion, decided when the Lift bridge is removed |
| ~~`testResourceDoc`~~ | ~~`APIMethods140`~~ | Dev-mode-only `/dummy` stub deleted — returned a dummy `APIInfoJSON`, no production behaviour. Removed from `OBPAPI1_4_0.routes` and `Implementations1_4_0`. `FrozenClassTest` did not flag it because v1.4.0's `testResourceDoc` ResourceDoc was registered behind `if (Props.devMode)` — the frozen snapshot (captured in test mode) never contained it. | **Done.** |

Track new leftovers here when later version files are migrated — the bridge-removal milestone in "Done Criteria" only requires the per-version files to be **done** in this table's sense (functional endpoints migrated, tests green). Leftovers folded into the Resource-docs or Auth-stack workstreams retire via those workstreams.

---

## Migration leftovers (full landscape, beyond per-version files)

Things still on Lift that block the `Http4sLiftWebBridge` from being removed. Use this section as the master TODO for the "remove Lift Web" milestone.

### Bridge-traffic audit (data-driven prioritisation)

Every request that reaches `Http4sLiftWebBridge.dispatch` is tallied in-memory by `Http4sLiftBridgeTraffic` so we can see exactly what still needs migrating before the bridge can be retired.

- **First hit of any (method, path-bucket)** is logged at INFO: `[BRIDGE-AUDIT] first hit: METHOD /path/bucket   (original path: /actual/path)`. Subsequent hits only increment an `AtomicLong`.
- **Snapshot endpoint** — `GET /admin/lift-bridge-traffic` returns the full tally as JSON, sorted by hit count desc:
  ```json
  {
    "unique_buckets": 3,
    "total_hits": 142,
    "entries": [
      {"bucket": "GET /auth/openid-connect/callback", "count": 99},
      {"bucket": "POST /obp/v6.0.0/banks/{id}/x", "count": 41},
      {"bucket": "GET /favicon.ico", "count": 2}
    ]
  }
  ```
- **Reset** — `POST /admin/lift-bridge-traffic/reset` clears the tally (handy for taking a baseline before a load test).
- **Path normalisation** collapses opaque IDs so the map doesn't fill up: UUIDs → `{uuid}`, all-digits → `{n}`, anything with a dot or 12+-char alnum mixed → `{id}`. API-version strings (`v6.0.0`, `v1_2_1`) are kept verbatim. Unit-tested in `Http4sLiftBridgeTrafficTest`.

Operator playbook for "is the bridge ready to retire?":
1. Reset the tally on a representative instance.
2. Let it run through a normal traffic window (e.g. 24h + the daily/weekly jobs).
3. Query `/admin/lift-bridge-traffic` — every bucket left is either a known leftover (see tables below) or a new migration target.

### Auth stack — every handler is its own `RestHelper`

| Handler | File | Routes | Status |
|---|---|---|---|
| `DirectLogin` | `code/api/directlogin.scala` | `POST /my/logins/direct` | **Done.** Versioned path (`/obp/v6.0.0/my/logins/direct`) served by `Http4s600.directLoginEndpoint`; bare path (`/my/logins/direct`) served by `code.api.DirectLoginRoutes` wired into `Http4sApp.baseServices` just before the Lift bridge. `LiftRules.statelessDispatch.append(DirectLogin)` removed from `Boot.scala`. The `allow_direct_login` prop gate moved into `DirectLoginRoutes`. The `dlServe { case Req("my" :: "logins" :: "direct" :: Nil, …) }` block inside `directlogin.scala` is now dead code (no longer registered with `LiftRules`); the surrounding `DirectLogin` object stays — its `getUserFromDirectLoginHeaderFuture` etc. are still called from auth flows. Cleanup of the dead `dlServe` block + `extends RestHelper` is a separate small PR. Key migration gotcha (kept for the auth-stack workstream): `createTokenFuture(allParameters)` ignores its argument and re-reads from Lift's `S.request` via `getAllParameters` — use `validatorFutureWithParams(...)` + `createTokenCommonPart(...)` instead. |
| `GatewayLogin` | `code/api/GatewayLogin.scala` | Gateway JWT exchange | **No routes.** Library only — same shape as `OAuth2Login`. Consumed via `GatewayLogin.getUserFromGatewayLoginHeaderFuture` etc. from auth flows. `extends RestHelper` was vestigial and was removed (Formats implicit re-declared locally). |
| `DAuth` | `code/api/dauth.scala` | dAuth JWT exchange | **No routes.** Library only — same shape as `OAuth2Login`/`GatewayLogin`. `extends RestHelper` was vestigial and was removed (object-level `implicit val formats` added for the `.extract[...]` call sites). |
| `OAuth 1.0a` | — | OAuth 1.0a token endpoints | **Done — removed.** Commit `51820c75e` (2026-02-20, "refactor/(auth): Remove OAuth 1.0a support and consolidate authentication") deleted `oauth1.0.scala`, unregistered `OAuthHandshake` from `Boot.scala`'s `LiftRules.statelessDispatch`, removed OAuth header detection from `OBPRestHelper.scala`, and added `getConsumerFromDirectLoginToken` / `getUserFromDirectLoginToken` to take over the consumer/user-lookup responsibilities previously held by `OAuthHandshake`. **Dead-code follow-up cleanup also done**: `AuthHeaderParser.parseOAuthHeader`, the `oAuthParams` field on `ParsedAuthHeader` / `CallContext`, the `oAuthToken` field on `CallContextLight`, `extractOAuthParams` in `Http4sSupport`, `APIUtil.hasAnOAuthHeader`, and stale `OAuthHandshake` comments in `directlogin.scala` and `AuthUser.scala` all removed. `OpenAPI31JSONFactory`'s phantom OAuth2 `authorizationCode` flow (which pointed at the deleted `/oauth/authorize` and `/oauth/token` URLs) replaced with `type: http, scheme: bearer, bearerFormat: JWT` — accurate for OBP's actual OAuth2 model (Bearer-token validation against external IdPs; OBP does not issue its own OAuth2 tokens). Kept on purpose: `code/model/OAuth.scala` (backs the general `Consumer` entity used by all auth methods, not OAuth 1.0a-specific) and `APIUtil.OAuth` (misnamed but live test infrastructure — the `<@` signer adds `Authorization: DirectLogin token=...` headers and is imported by ~hundreds of test files; renaming is a separate cleanup). |
| `OAuth2` | `code/api/OAuth2.scala` (`OAuth2Login`) | **No routes.** Library only — Bearer-token validator (Google / Yahoo / Azure / Keycloak / OBPOIDC / Hydra) consumed by `APIUtil.getUserFuture` and `OBPRestHelper.OAuth2.getUser`. Both Lift and http4s endpoints already call it. The `extends RestHelper` mixin was vestigial and was removed (the only thing it provided was an implicit `Formats`, now declared locally at the one `extract[List[String]]` site). No remaining auth-stack work in this file. |
| `OpenIdConnect` | `code/api/openidconnect.scala` | OIDC callback — registered via `LiftRules.dispatch.append` | **Lift only — blocked on a portal-session decision.** 3 callback routes (`/auth/openid-connect/callback`, `…/callback-1`, `…/callback-2`) all funnel into `callbackUrlCommonCode`, whose success branch calls `AuthUser.logUserIn(user, () => S.redirectTo(...))`. `logUserIn` is inherited from `MetaMegaProtoUser` and writes the logged-in user into Lift `SessionVar`s that the portal reads; `S.redirectTo` sets Lift's session cookie. No tests cover the callback success path. Three forks: (a) **Drop portal-login** — pure http4s callback that issues a token but doesn't seed a portal session. Behaviour change for anyone using OIDC to sign into the portal UI; cheap if that user is nobody, surprising if it isn't. Needs a stakeholder check. (b) **Lift-session shim** — keep `lift-webkit` forever for this one callback. Cheapest in code, but "Lift Web removed" never actually ships. (c) **Replace portal session entirely** (e.g. Redis/JWT-backed). Months of work; also decouples session storage from Lift, which makes the lift-mapper conversation easier later. |

DirectLogin's request-path is now off Lift. `OAuth2Login`, `GatewayLogin`, and `DAuth` turned out to be library-only (no routes); their vestigial `extends RestHelper` mixins were dropped. OAuth 1.0a was removed entirely in commit `51820c75e`. OpenIdConnect remains on Lift pending a portal-session decision — it is now the **only** auth handler still blocking bridge removal.

### Resource-docs workstream

Already partly described in the next major section, but counted here for completeness:

- `ResourceDocs140` … `ResourceDocs600` — six separate Lift files, each registered via `LiftRules.statelessDispatch.append` in `Boot.scala`.
- `getResourceDocsObpV700` aggregation bug fix — landed (`V7ResourceDocsAggregationTest` passes).
- `openapi.yaml` route — raw `Lift serve { ... }` block, no native http4s handler.
- `getMessageDocsSwagger` (v3.1.0) — folds into the centralised `Http4sResourceDocs` service when it ships.
- One-PR opportunity: build `Http4sResourceDocs` above the Lift bridge in `Http4sApp`, intercept all `/obp/*/resource-docs/*` traffic, retire six Lift dispatch entries in a single change.

### Small singleton Lift endpoints

| Endpoint | File | Notes |
|---|---|---|
| `aliveCheck` | `code/api/aliveCheck.scala` → `code/api/AliveCheckRoutes.scala` | **Done.** Native http4s route serves `GET /alive`; `LiftRules.statelessDispatch.append(aliveCheck)` removed from `Boot.scala`. |
| `ImporterAPI` | (deleted) | **Retired.** The legacy `POST /obp_transactions_saver/api/transactions` shared-secret bulk-insert endpoint, its `TransactionInserter` LiftActor, and the connector helpers it relied on (`createImportedTransaction`, `getMatchingTransactionCount`, `updateAccountBalance`, `setBankAccountLastUpdated`) have been removed entirely. Modern callers use connector-driven flows or the `/obp/vX.X.X/transaction-requests/...` endpoints. |
| `OpenIdConnect` | (auth-stack table above) | OIDC callback, registered separately from OAuth2. |

### Open-banking standards (large, deferred indefinitely)

Lift implementations of 3rd-party regulatory standards. All currently pass through `Http4sLiftWebBridge` and continue to work; they are *not* OBP API per se but optional regulatory shims. Migrating them is out of scope for the "remove Lift Web" milestone if you accept keeping the bridge for these stacks only. If total Lift removal is the goal, each needs its own workstream.

Three forks for how this workstream resolves:

- **(a) Migrate each to http4s.** Weeks per standard × 7 standards. Highest cost; cleanest end state.
- **(b) "Regulatory mode" feature-flagged Lift.** Keep `Http4sLiftWebBridge` wired in only when an `obf-*` / standards prop is set; otherwise the bridge is unregistered at boot. Lets "Lift Web removed from the OBP API path" ship, but Lift Web stays in the codebase as an opt-in shim. Defeats the milestone technically; ships the headline.
- **(c) Extract as plugin projects.** Move each standard out of this repo into its own project that depends on OBP API. Probably right long-term — these are optional, externally-governed standards on different release cadences — but socially expensive and reshapes the build.

| Standard | Files / location | Status |
|---|---|---|
| Berlin Group v1.3 | `code/api/berlin/group/v1_3/*` — 7 files (AIS / PIS / PIIS / signing baskets / common) | Lift |
| **Berlin Group v2** | `code/api/berlin/group/v2/Http4sBGv2.scala` | ✅ already on http4s |
| UK Open Banking v2.0.0 + v3.1.0 | `code/api/UKOpenBanking/*` — ~20 files | Lift |
| Bahrain OBF v1.0.0 | `code/api/BahrainOBF/*` — ~20 files | Lift |
| AU OpenBanking v1.0.0 | `code/api/AUOpenBanking/*` — ~10 files | Lift |
| STET v1.4 | `code/api/STET/v1_4/*` — 4 files | Lift |
| MxOF v1.0.0 | `code/api/MxOF/*` — 2 files | Lift |
| Polish v2.1.1.1 | `code/api/Polish/v2_1_1_1/*` — 4 files | Lift |
| Sandbox / `SandboxApiCalls.scala` | `code/api/sandbox/*` | Lift |

### `Boot.scala` scaffolding

Currently runs on startup and goes away once the Lift bridge is removable:

1. `LiftRules.statelessDispatch.append(...)` registrations: `DirectLogin`, `ResourceDocs140`–`ResourceDocs600`, `aliveCheck`.
2. `LiftRules.dispatch.append(OpenIdConnect)`.
3. `LiftRules.addToPackages("code")` — Lift package scanner.
4. `LiftRules.exceptionHandler.prepend { ... }` — global exception handler.
5. `LiftRules.uriNotFound.prepend { ... }` — 404 handler.
6. `LiftRules.early`, `LiftRules.supplementalHeaders`, `LiftRules.localeCalculator`, etc. — request-path hooks.
7. `LiftRules.unloadHooks.append(...)` — shutdown hooks (DB pool, Redis).
8. **Mapper schemifier** — DB schema init. Belongs to the long-term `lift-mapper` removal effort, not the bridge milestone.

Everything in lines 1–7 is request-path-related and will go in the bridge-removal PR. Line 8 stays until lift-mapper is replaced.

### Tests

| Item | Status |
|---|---|
| `Http4s500RoutesTest`, `RootAndBanksTest`, `V500ContractParityTest` | `@Ignore`. |
| `CardTest` | Commented out. |
| v5.0.0: 13 skipped tests | Setup cost paid, no value. |
| `V7ResourceDocsAggregationTest` | Was intentionally failing; aggregation bug fix landed → now passes. |
| `AbacRuleTests` (6 local fails) | Environment-dependent — too few users in local DB triggers `isStatisticallyTooPermissive`. Not a regression. |

### Reusable lessons from v6.0.0

1. **JVM 64KB `<init>` limit** — see CLAUDE.md. Adopt `lazy val xxx: HttpRoutes[IO] = ...` plus `private def initXxxResourceDocs(): Unit` blocks in every per-version file from the start; don't wait until you hit the wall.
2. **DirectLogin pattern** — `S.request`-bound Lift handlers need an http4s-friendly entry point that accepts pre-parsed parameters. `validatorFutureWithParams` is the model; replicate this for `GatewayLogin` / `OAuth` when their migration starts.
3. **`Future.failed(new Exception)` produces 500** — use `unboxFullOrFail(Empty, ..., 400)` or `NewStyle.function.tryons(msg, 400, ...)` to return the intended 4xx. Pattern showed up in WebUiProps and RetailCustomer fixes.
4. **`isStatisticallyTooPermissive` is sample-pool-dependent** — locally, a fresh test DB with a single user causes spurious rejections. Tests built against this check must seed enough users.
5. **Reserved ALL_CAPS placeholders** in middleware (`BANK_ID`, `ACCOUNT_ID`, `VIEW_ID`, `COUNTERPARTY_ID`) — when an endpoint needs a same-shape var without middleware lookup, rename to a non-reserved variant (e.g. `COUNTERPARTY_ID_PARAM`) in both the http4s and Lift ResourceDocs.

### Decision gates

Two non-engineering decisions must land before the bridge-removal PR can be cut. They are stakeholder calls, not author calls — making either of them in code reviews tends to surface objections after the fact.

1. **OIDC portal-session strategy** (auth-stack OpenIdConnect row, options a/b/c). Until one of the three forks is picked, the OIDC callback can't be migrated and the bridge can't be removed. The cheapest option (drop portal-login) is a behaviour change and needs explicit sign-off from anyone using OIDC as a portal-UI sign-in. **This is now the only auth-handler decision blocking bridge removal.**

A second decision is *not* required for bridge removal, but is required for the public claim that follows it:

2. **Open-banking standards strategy** (forks a/b/c above). If "Lift Web removed" is the headline, fork (b) is acceptable. If "Lift Web removed *from this repo*" is the headline, only (a) or (c) qualify. Cf. the "Lift Web removed vs. Lift removed" note under Done Criteria.

### Suggested ordering for the remaining work

1. ~~**v4.0.0 bulk port**~~ — done (258/258, 100%).
2. ~~**DirectLogin**~~ — done. `code.api.DirectLoginRoutes` serves the bare `/my/logins/direct`; per-version paths served by their own `Http4sXxx`. `LiftRules.statelessDispatch.append(DirectLogin)` retired.
3. ~~**`aliveCheck`**~~ — done. `code.api.AliveCheckRoutes` serves `GET /alive`; Lift dispatch retired. **`ImporterAPI`** — retired entirely (no http4s replacement); the legacy shared-secret bulk-transaction-importer endpoint has been removed along with `TransactionInserter` and the connector helpers it relied on.
4. ~~**`Http4sResourceDocs` centralised service**~~ — done. `code.api.util.http4s.Http4sResourceDocs` serves `/obp/*/resource-docs/{API_VERSION}/{obp,swagger,openapi,openapi.yaml}`, `/obp/*/banks/{BANK_ID}/resource-docs/{API_VERSION}/obp`, and `/obp/*/message-docs/{CONNECTOR}/swagger2.0`. 10 `LiftRules.statelessDispatch.append(ResourceDocs140..600)` retired + `openapi.yaml` raw `serve { ... }` block removed. ResourceDocsTest (63) + SwaggerDocsTest (10) green.
5. **Auth stack: OAuth2 / GatewayLogin / DAuth** — done. All three turned out to be library-only token validators (no `serve` blocks, no `LiftRules` registration). Vestigial `extends RestHelper` mixins removed.
6. **OpenIdConnect** — the only remaining auth-stack work. Blocked on a portal-session decision (its success path calls `AuthUser.logUserIn` / `S.redirectTo`, which mutate Lift `SessionVar`s — see auth-stack table). OAuth 1.0a was removed entirely in commit `51820c75e`; no migration needed.
7. **Bridge-removal PR** — delete `Http4sLiftWebBridge` + the request-path entries from `Boot.scala` (lines 1–7 above).
8. **Open-banking standards** — decide whether to migrate or keep a thin Lift remnant. Weeks of work if migrating.
9. **`lift-mapper`** — separate long-term effort, out of scope here.

---

## Server Chain After Full Migration

```
corsHandler
  → Http4sResourceDocs  (/obp/*/resource-docs/*)   ← centralized, all version prefixes
  → Http4s700  (/obp/v7.0.0/*)
  → Http4s600  (/obp/v6.0.0/*)
  → Http4s510  (/obp/v5.1.0/*)
  → Http4s500  (/obp/v5.0.0/*)
  → Http4s400  (/obp/v4.0.0/*)
  → Http4s310  (/obp/v3.1.0/*)
  → Http4s300  (/obp/v3.0.0/*)
  → Http4s220  (/obp/v2.2.0/*)
  → Http4s210  (/obp/v2.1.0/*)
  → Http4s200  (/obp/v2.0.0/*)
  → Http4s140  (/obp/v1.4.0/*)   ← done
  → Http4s130  (/obp/v1.3.0/*)   ← done
  → Http4s121  (/obp/v1.2.1/*)   ← done
  → Http4sBGv2
  ← Lift bridge removed
```

---

## Done Criteria

| Milestone | Condition |
|---|---|
| Version file done | All *functional* endpoints are `HttpRoutes[IO]`; the version's test suite is green. Endpoints folded into the Resource-docs / Auth-stack workstreams or marked as non-functional stubs are listed in "Per-version Lift leftovers" rather than blocking the file's done status. |
| Lift bridge removable | All 12 APIMethods files done (per the row above) + auth stack done + Resource-docs workstream done. Any remaining stubs from "Per-version Lift leftovers" are ported or deleted in the bridge-removal PR. |
| Lift Web removed | `lift-webkit` removed from `pom.xml`; `Boot.scala` reduced to DB init + scheduler startup. |
| `lift-mapper` | Separate long-term effort — not in scope here. |

**"Lift Web removed" ≠ "Lift removed."** The two are distinct milestones and the difference matters for public claims:

- *Lift Web removed* means the HTTP request path no longer touches Lift — `lift-webkit` is out of `pom.xml`, `Http4sLiftWebBridge` is deleted, `Boot.scala` request-path hooks are gone. `lift-mapper` is still present and still the ORM.
- *Lift removed* means `net.liftweb:*` is fully out of the dependency graph — requires the multi-month `lift-mapper` replacement (Doobie/Slick or similar).

Decide which bar a release is hitting before announcing it; conflating them invites either an overstatement or an avoidable months-long delay before the announcement.

---

## Risks

Things that can derail the remaining workstreams. The facts behind each are documented in the relevant section above; collected here so the bridge-removal PR author doesn't have to rediscover them.

| Risk | Detail | Mitigation |
|---|---|---|
| `FrozenClassTest` ratchet | Every deletion of a Lift `lazy val ... : OBPEndpoint` reduces the STABLE-API surface and trips `FrozenClassTest`. The v3.1.0 leftovers (`getMessageDocsSwagger`, `getObpConnectorLoopback`) are deferred to the bridge-removal PR specifically because of this; subsequent ports may surface more. | Plan the frozen-snapshot refresh as part of the bridge-removal PR, not as a follow-up. Document each removed `lazy val` in the PR description. |
| OIDC callback success path has no tests | Whichever of the three OIDC forks ships, there is no automated safety net. Manual integration test against a real OIDC provider is the only verification. | Before picking a fork, write at least one integration test against a test OIDC provider (Keycloak in a container is the established pattern in this repo). |
| `S.request` translation gotchas | DirectLogin's `createTokenFuture` ignored its parameters and re-read from `S.request` via `getAllParameters`; the http4s migration needed `validatorFutureWithParams` to thread parsed params through. If a future auth/handshake handler is migrated (e.g. OIDC's callback), expect the same shape — its handlers will reference `S.request` in ways the existing function signatures hide. | Audit the handler for `S.request`/`S.param`/`S.queryString` reads before designing the http4s entry point. Replicate the DirectLogin pattern. |
| Bridge-cascade hijack on partial migrations | Documented in CLAUDE.md. Surfaced once during v4 migration; can resurface anywhere a new version is wired into the chain before its overrides are migrated. | When adding a new `Http4sXxx` to `baseServices`, audit URL+verb overrides against older versions first. |
| `isStatisticallyTooPermissive` flakiness | Local test DB with too few users trips the ABAC permissiveness check. Not a regression, but easy to misdiagnose during the bridge-removal PR's full test run. | Seed enough test users in any test that exercises ABAC rules. Document in the suite, not as a runtime mitigation. |

## Why http4s?

- **Non-blocking I/O** — Uses a small fixed thread pool (CPU cores) and suspends fibres on I/O. Thousands of concurrent requests without thread-pool tuning.
- **Lower memory** — No thread-per-request overhead.
- **Modern Scala ecosystem** — First-class Cats Effect, fs2 streaming, and functional patterns.
- **No servlet container** — Removes Jetty and WAR packaging entirely.

---

## Running

```sh
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" \
  mvn -pl obp-api -am clean package -DskipTests=true -Dmaven.test.skip=true && \
  java -jar obp-api/target/obp-api.jar
```

Binds to `hostname` / `dev.port` from your props file (defaults: `127.0.0.1:8080`).

---

## Progress

| File | Status |
|---|---|
| `APIMethods121` | done — `Http4s121.scala` (all 323 API1_2_1Test scenarios pass) |
| `APIMethods130` | done — `Http4s130.scala` (2 PhysicalCardsTest scenarios pass) |
| `APIMethods140` | done — `Http4s140.scala` (all 11 own endpoints; path-rewriting bridge to Http4s130) |
| `APIMethods200` | done — `Http4s200.scala` (37 own endpoints; path-rewriting bridge to Http4s140) |
| `APIMethods210` | done — `Http4s210.scala` (25 own endpoints; path-rewriting bridge to Http4s200) |
| `APIMethods220` | done — `Http4s220.scala` (18 own endpoints; path-rewriting bridge to Http4s210) |
| `APIMethods300` | done — `Http4s300.scala` (47 own endpoints; path-rewriting bridge to Http4s220; all 86 v3.0.0 tests pass) |
| `APIMethods310` | done — `Http4s310.scala` (100 own endpoints + `updateCustomerAddress`; path-rewriting bridge to Http4s300; 2 endpoints still on Lift: `getMessageDocsSwagger` (shadowed by `Http4sResourceDocs.routes`, kept to satisfy `FrozenClassTest`) and `getObpConnectorLoopback`) |
| `APIMethods400` | **done — 258 / 258 (100%)**. `Http4s400.scala` covers all 253 unique handlers + 8 ResourceDoc aliases for transaction-request-type variants (served by the shared wildcard handler). |
| `APIMethods500` | done — `Http4s500.scala` (all 10 v5.0.0 originals on http4s) |
| `APIMethods510` | done — `Http4s510.scala` (all 111 v5.1.0 originals on http4s; `createConsent` exposed as `createConsentImplicit` with a guard covering EMAIL/SMS/IMPLICIT SCA methods) |
| `APIMethods600` | **done — 243 / 243 (100%)**. `Http4s600.scala` covers all 35 overrides + 208 originals. |
| Auth: DirectLogin | done — `code.api.DirectLoginRoutes` serves the bare `/my/logins/direct` (gated on `allow_direct_login`); per-version paths served by their own `Http4sXxx`; `LiftRules.statelessDispatch.append(DirectLogin)` removed from `Boot.scala` |
| Auth: GatewayLogin | done — library-only (no `serve` block, no `LiftRules` registration). Vestigial `extends RestHelper` removed. |
| Auth: DAuth | done — library-only (no `serve` block, no `LiftRules` registration). Vestigial `extends RestHelper` removed. |
| Auth: OAuth2 | done — library-only Bearer-token validator. Vestigial `extends RestHelper` removed. |
| Auth: OAuth 1.0a | done — removed entirely in commit `51820c75e` (2026-02-20). `oauth1.0.scala` deleted, `OAuthHandshake` unregistered from `Boot.scala`, header detection removed from `OBPRestHelper.scala`. See "Per-version Lift leftovers → Auth stack" for surviving dead-code references that are cleanup candidates. |
| Auth: OpenIdConnect | blocked — callback success path calls `AuthUser.logUserIn` / `S.redirectTo` (Lift `SessionVar`s). Needs a portal-session decision before migration. |
| Resource-docs: aggregation bug fix | done |
| Resource-docs: `Http4sResourceDocs` service | todo |
| Resource-docs: `openapi.yaml` route | todo |

### Cleanup done

- `getCards` and `getCardsForBank` removed from `Http4s700` — these had the same API signature as the v1.3.0 originals and belonged in `APIMethods130`, not v7.0.0. The Lift implementation in `APIMethods130` serves them at `/obp/v1.3.0/` until that file is migrated.
