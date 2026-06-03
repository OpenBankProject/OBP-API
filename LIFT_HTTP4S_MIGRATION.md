# Lift → http4s Migration — COMPLETE

## Status

**The Lift → http4s migration of the HTTP request path is complete.** Every OBP API endpoint is served by native http4s `HttpRoutes[IO]`:

- All version files **v1.2.1 → v7.0.0**
- **Berlin Group** v1.3 + v2
- **UK Open Banking** v2.0 + v3.1
- **Dynamic Entity / Dynamic Endpoint** runtime dispatch
- **Resource-docs / message-docs / openapi.yaml** (centralized `Http4sResourceDocs`)
- **Auth handlers**: DirectLogin, OpenID Connect, AliveCheck

`Http4sLiftWebBridge` has been **deleted**; `lift-webkit` has been **removed from `pom.xml`**. There is no Lift fallback in the request path — any unmatched `/obp/*` path returns a JSON 404 from `notFoundCatchAll`. The **"Lift Web removed"** milestone is therefore achieved.

The remaining Lift dependencies are the **non-web libraries** — `lift-mapper` (ORM / database layer), plus `lift-json` / `lift-common` / `lift-util` — kept deliberately. Replacing `lift-mapper` is a separate long-term effort tracked under [What remains](#what-remains--lift-mapper).

---

## Principle

API version numbers reflect **API contract changes** (new/changed fields, new behaviour). The underlying framework is invisible to clients. Lift → http4s was a refactoring: it happened **in-place** inside the existing version file at the existing URL. No version bump.

A new version (e.g. v7.0.0) is used only when the API contract itself changes — new fields, changed request/response shape, new behaviour.

---

## Current Architecture

OBP-API runs as a **single http4s Ember server** (single process, single port). The application entry point is a Cats Effect `IOApp` (`Http4sServer`). Lift is no longer an HTTP server — Jetty, the servlet container, and the request bridge have all been removed.

Lift now plays exactly one role:

- **`lift-mapper` ORM / Database** — Mapper manages schema creation, migrations, and all data access (`MappedBank`, `AuthUser`, etc.). A handful of `net.liftweb.json` / `net.liftweb.common` (`Box`/`Full`/`Empty`) serialisation helpers are also still used; these are library utilities, not the Lift web stack.

### Entry point — `Http4sServer.scala`

`Http4sServer` extends `IOApp`. On startup it:

1. Calls `bootstrap.liftweb.Boot().boot()` to initialise Lift Mapper, connectors, and OBP configuration (DB/ORM init only — no `LiftRules` request-path registrations remain active).
2. Parses the configured `hostname` and `dev.port` props (defaults: `127.0.0.1`, `8080`).
3. Starts an Ember server with the application defined in `Http4sApp.httpApp`.

### Priority routing

Routes are tried in order (see `Http4sApp.baseServices`): `corsHandler` (OPTIONS) → `AppsPage` → `StatusPage` → `Http4sResourceDocs` → `Http4s510` → `Http4s600` → `Http4s500` → `Http4s700` → `Http4sBGv2` → `Http4sUKOBv200` → `Http4sUKOBv310` → `Http4sBGv13` (+`Http4sBGv13Alias`) → `Http4s400` → `Http4s310` → `Http4s300` → `Http4s220` → `Http4s210` → `Http4s200` → `Http4s140` → `Http4s130` → `Http4s121` → `dynamicEntityRoutes` → `dynamicEndpointRoutes` → `DirectLoginRoutes` → `Http4sOpenIdConnect` → `AliveCheckRoutes` → `notFoundCatchAll` (JSON 404).

There is **no Lift fallback** — the chain terminates in `notFoundCatchAll`, which returns a JSON 404 for any unmatched path. The non-numeric ordering (v510 before v600, v500 after v600, etc.) doesn't affect correctness because each per-version service gates on its own version prefix; ordering only matters when two services overlap on the same URL pattern.

```
HTTP Request
    │
    ▼
Http4sServer (IOApp / Ember)
    │
    ▼
corsHandler → AppsPage → StatusPage → Http4sResourceDocs
    → Http4s510 → Http4s600 → Http4s500 → Http4s700
    → Http4sBGv2 → Http4sUKOBv200 → Http4sUKOBv310 → Http4sBGv13(+Alias)
    → Http4s400 → Http4s310 → Http4s300 → Http4s220 → Http4s210 → Http4s200
    → Http4s140 → Http4s130 → Http4s121
    → dynamicEntityRoutes → dynamicEndpointRoutes
    → DirectLoginRoutes → Http4sOpenIdConnect → AliveCheckRoutes
    → notFoundCatchAll  (JSON 404 — no Lift fallback)
    │
    ▼
HTTP Response (with standard headers)
```

### Body caching

http4s request bodies are single-shot streams. The first version's `ResourceDocMiddleware.fromRequest` consumes the body to build the CallContext; any later path-rewriting bridge hop (v400→v310→…→v210) that re-reads `req.bodyText` would get an empty stream and the handler would 500. `Http4sApp.cacheBodyOnce` pre-reads the body and stashes it in `cachedBodyKey`, so every downstream `fromRequest` reads from the attribute instead of the drained stream. GET/DELETE/HEAD/OPTIONS skip this.

### Version enable/disable semantics

Two Props govern which API versions are served: `api_disabled_versions` and `api_enabled_versions` (allowlist; empty means "all"). They are enforced **once at startup**, by `Http4sApp.gate`:

```scala
private def gate(version: ScannedApiVersion, routes: HttpRoutes[IO]): HttpRoutes[IO] =
  if (APIUtil.versionIsAllowed(version)) routes else HttpRoutes.empty[IO]
```

A disabled version's top-level routes are replaced with `HttpRoutes.empty[IO]`, so a direct `GET /obp/vX.Y.Z/...` falls through the chain to `notFoundCatchAll` (JSON 404).

**Cascade is intentionally unaffected.** Each `Http4sXxx` has a path-rewriting bridge to the next-lower version that calls `code.api.vN.HttpNxx.wrappedRoutesVNxxServices` *directly*, bypassing `Http4sApp.gate`. `ResourceDocMiddleware` does **not** re-check `implementedInApiVersion` per request either (`ResourceDocMiddleware.isEndpointEnabled` deliberately has no `versionAllowed` parameter — `ResourceDocMiddlewareEnableDisableTest` pins this). So an endpoint originally declared in v2.0.0 stays reachable via `/obp/v4.0.0/...` even when v2.0.0 is disabled, as long as v4.0.0 is enabled.

This preserves the documented OBP-API contract: newer versions act as the supported entry point for older endpoints' functionality. Operators can retire a version's *URL prefix* with `api_disabled_versions` without losing the underlying endpoints from newer prefixes. To retire a specific endpoint everywhere, use `api_disabled_endpoints` (operationId list) — that **is** enforced per request by the middleware and so kills the endpoint on every prefix it would otherwise be reachable from.

A brief regression in early 2026-05 inverted this: a `versionAllowed` check was added inside the middleware, making `api_disabled_versions` kill cascaded reachability too. Restored 2026-05-26. If you're tempted to put the per-request version check back, read the `isEndpointEnabled` docstring first — it spells out the design rationale, and the "version-level gating is delegated to Http4sApp.gate" feature in the unit test will fail loudly.

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
| `ResourceDoc(root, ...)` | `ResourceDoc(implementedInApiVersion, ..., http4sPartialFunction = Some(root))` |

### `OBPAPI{version}.scala`

| Before | After |
|---|---|
| `extends OBPRestHelper` | removed |
| `registerRoutes(routes, allResourceDocs, apiPrefix)` | expose `val allRoutes: HttpRoutes[IO]` |
| registered via Boot / LiftRules | wired into `Http4sApp.baseServices` chain |

See `CLAUDE.md § Migrating a Lift Endpoint to http4s` for the full Rule 1–5 reference. The Lift `APIMethodsXYZ.scala` files are retained as **commented-out source-of-truth** for the ResourceDoc parity audit (see below) and as the frozen STABLE API surface for `FrozenClassTest`; they are comments, not active routes.

---

## What was migrated

### Per-version files (bottom-up; each has a path-rewriting bridge to the version below)

| # | File | Own endpoints | http4s file |
|---|---|---|---|
| 1 | `APIMethods121` | 70 | `Http4s121.scala` — all 323 API1_2_1Test scenarios pass |
| 2 | `APIMethods130` | 3 | `Http4s130.scala` — bridge to `Http4s121` |
| 3 | `APIMethods140` | 11 | `Http4s140.scala` — bridge to `Http4s130` |
| 4 | `APIMethods200` | 40 | `Http4s200.scala` — 37 own + bridge to `Http4s140` |
| 5 | `APIMethods210` | 28 | `Http4s210.scala` — 25 own + bridge to `Http4s200`; 79 tests pass |
| 6 | `APIMethods220` | 19 | `Http4s220.scala` — 18 own + bridge to `Http4s210`; 27 tests pass |
| 7 | `APIMethods300` | 47 | `Http4s300.scala` — bridge to `Http4s220`; 86 tests pass |
| 8 | `APIMethods310` | 102 | `Http4s310.scala` — 100 functional + bridge to `Http4s300`; 181 tests pass. `getObpConnectorLoopback` is a native http4s route (returns 400 NotImplemented); `getMessageDocsSwagger` routing is owned by `Http4sResourceDocs` (in-file `HttpRoutes.empty` stub kept only so `nameOf(...)` compiles for `FrozenClassTest`). |
| 9 | `APIMethods400` | 258 | **258 / 258 (100%)**. `Http4s400.scala` — 253 unique handlers + 8 ResourceDoc aliases for transaction-request-type variants (shared `createTransactionRequest` wildcard handler; `literalAllCapsSegments` in `Http4sSupport.scala` dispatches the matcher to the per-type doc). All 35/35 v4-over-older URL+verb overrides migrated (avoids the bridge-cascade hijack). |
| 10 | `APIMethods500` | 10 | `Http4s500.scala` — all v5.0.0 originals |
| 11 | `APIMethods510` | 111 | `Http4s510.scala` — `createConsent` exposed as `createConsentImplicit` (one handler, `scaMethod ∈ {EMAIL, SMS, IMPLICIT}` guard covers all three SCA-method URLs) |
| 12 | `APIMethods600` | 243 (35 overrides + 208 originals) | **243 / 243 (100%)**. `Http4s600.scala` — introduced the **lazy val + helper-def init pattern** to dodge the JVM 64KB `<init>` method-size limit (`val xxx` ⇒ `lazy val xxx`; `resourceDocs += ResourceDoc(...)` grouped into `private def initXxxResourceDocs(): Unit` blocks). All later per-version files adopt this from the start. |

> **JVM 64KB `<init>` limit**: around the 140-endpoint mark a per-version object's `<init>` hits the JVM method-size limit. The fix (shipped in `Http4s600`/`Http4s400`): `lazy val` endpoints (lambda materialisation moves into per-field `lzycompute`) + `resourceDocs +=` grouped into `initXxx()` helper defs (each with its own 64KB budget).

### Open-banking standards

| Standard | Location | Status |
|---|---|---|
| **Berlin Group v1.3** | `code/api/berlin/group/v1_3/Http4sBGv13{,AIS,PIS,PIIS,SigningBaskets,Alias}.scala` — 6 http4s files | ✅ http4s, wired into `Http4sApp` (`Http4sBGv13` + `Http4sBGv13Alias`) |
| **Berlin Group v2** | `code/api/berlin/group/v2/Http4sBGv2.scala` | ✅ http4s |
| **UK Open Banking v2.0.0** | `code/api/UKOpenBanking/v2_0_0/Http4sUKOBv200{,AIS}.scala` | ✅ http4s (`/open-banking/v2.0/*`) |
| **UK Open Banking v3.1.0** | `code/api/UKOpenBanking/v3_1_0/Http4sUKOBv310*.scala` — 21 http4s files | ✅ http4s (`/open-banking/v3.1/*`) |
| Bahrain OBF v1.0.0 | `code/api/BahrainOBF/v1_0_0/*` — 22 files | 🗑 commented-out dead code (whole files `//`-commented in `d19af2b92`, 2026-05-22). No routes, no http4s port. Since the bridge is gone, these are unreachable. |
| AU OpenBanking v1.0.0 | `code/api/AUOpenBanking/v1_0_0/*` — 11 files | 🗑 commented-out dead code (`d19af2b92`) |
| STET v1.4 | `code/api/STET/v1_4/*` — 5 files | 🗑 commented-out dead code (`d19af2b92`) |
| MxOF / CNBV9 v1.0.0 | `code/api/MxOF/*` — 4 files | 🗑 commented-out dead code (`d19af2b92`) |
| Polish v2.1.1.1 | `code/api/Polish/v2_1_1_1/*` — 5 files | 🗑 commented-out dead code (`d19af2b92`) |
| Sandbox | `code/api/sandbox/SandboxApiCalls.scala` | 🗑 commented-out dead code (`7f3c51f5e`) |

The five retired standards + Sandbox are **commented-out source files with no route registration**. The `code.api.*.ApiCollector` / `OBP_*` `ScannedApis` classes inside them are inert (the code is `//`-commented, so `ClassScanUtils` can't discover them and `APIUtil`'s `allResourceDocs` aggregation no longer references them). A future cleanup PR can delete the files outright, or — if any standard is wanted back — port it to http4s the way BG v1.3 / UK OB were.

### Auth stack

| Handler | Path | Status |
|---|---|---|
| `DirectLogin` | `POST /my/logins/direct` | ✅ `code.api.DirectLoginRoutes` serves the bare path (gated on `allow_direct_login`); versioned path served by each `Http4sXxx`. `LiftRules.statelessDispatch.append(DirectLogin)` removed from `Boot.scala`. |
| `OpenIdConnect` | `GET\|POST /auth/openid-connect/callback{,-1,-2}` | ✅ **`code.api.Http4sOpenIdConnect`** — native http4s. **Portal-session decision resolved as fork (a) "drop portal-login":** the success branch no longer calls `AuthUser.logUserIn` / `S.redirectTo`; it issues an OBP DirectLogin token via `DirectLogin.issueTokenForUser(...)` and returns it. The old `openidconnect.scala` is fully commented out. Pure route tests live in `Http4sOpenIdConnectRoutesTest`. |
| `AliveCheck` | `GET /alive` | ✅ `code.api.AliveCheckRoutes`; Lift dispatch removed. |
| `GatewayLogin` | gateway JWT exchange | ✅ Library-only validator (no routes). Vestigial `extends RestHelper` removed. |
| `DAuth` | dAuth JWT exchange | ✅ Library-only validator (no routes). Vestigial `extends RestHelper` removed. |
| `OAuth2` (`OAuth2Login`) | Bearer-token validator | ✅ Library-only (Google / Yahoo / Azure / Keycloak / OBPOIDC / Hydra). Vestigial `extends RestHelper` removed. |
| `OAuth 1.0a` | — | ✅ **Removed entirely** in `51820c75e` (2026-02-20). `oauth1.0.scala` deleted, `OAuthHandshake` unregistered, header detection removed from `OBPRestHelper.scala`. `getConsumerFromDirectLoginToken` / `getUserFromDirectLoginToken` took over consumer/user lookup. |
### ~~Prerequisite~~ Prerequisite (done): aggregation bug fix

> Kept on purpose: `code/model/OAuth.scala` (backs the general `Consumer` entity used by all auth methods) and `APIUtil.OAuth` (misnamed but live **test** infrastructure — the `<@` signer adds `Authorization: DirectLogin token=...` headers and is imported by hundreds of test files; renaming is a separate cleanup).
~~`V7ResourceDocsAggregationTest` is intentionally failing.~~ **Fixed in `efb97531e` (2026-05-19)** — *"fix(resource-docs): correct v7 aggregation specifiedUrl and remove shadowed v7 handler"*. Two root causes addressed: (1) `ResourceDocs1_4_0` registered the same `(GET, /resource-docs/API_VERSION/obp)` doc twice, so v7 aggregation surfaced a duplicate; (2) `getAllResourceDocsObpCached` cached `specifiedUrl` per dynamic-endpoint doc with `case Some(_) => it`, so the first caller froze the URL and every later request inherited it. `getResourceDocsObpV700` now calls `getResourceDocsList`, which aggregates the full cascade (~949 docs on a live server). The centralized service must preserve this contract — `V7ResourceDocsAggregationTest` now acts as the regression guard.

### Dynamic dispatch, resource-docs, and singletons

| Component | Status |
|---|---|
| **DynamicEntity** (`/obp/dynamic-entity/*`) | ✅ `code.api.dynamic.entity.Http4sDynamicEntity` — native http4s, replaces the Lift `OBPAPIDynamicEntity` dispatch. |
| **DynamicEndpoint** (`/obp/dynamic-endpoint/*`) | ✅ `code.api.dynamic.endpoint.Http4sDynamicEndpoint` — fully native (no Lift `Req`, `S.init`, `buildLiftReq`, or `liftResponseToHttp4s`). |
| **Resource-docs** (`/obp/*/resource-docs/{API_VERSION}/{obp,swagger,openapi,openapi.yaml}`) | ✅ Centralized `code.api.util.http4s.Http4sResourceDocs`, matched before any per-version service (version-polymorphic: the `API_VERSION` path segment controls output). Retired 10 `LiftRules.statelessDispatch.append(ResourceDocs140..600)` entries + the raw `openapi.yaml` Lift `serve {...}` block. The `getResourceDocsObpV700` aggregation bug is fixed (`V7ResourceDocsAggregationTest` passes). ResourceDocsTest (63) + SwaggerDocsTest (10) green. |
| **message-docs** (`/obp/*/message-docs/{CONNECTOR}/swagger2.0`) | ✅ `Http4sResourceDocs.handleGetMessageDocsSwagger` via wildcard route. |
| `ImporterAPI` | ✅ **Retired** — legacy `POST /obp_transactions_saver/api/transactions` shared-secret bulk-insert endpoint, its `TransactionInserter` LiftActor, and the connector helpers it relied on all removed. |
| `testResourceDoc` (`APIMethods140` `/dummy`) | ✅ Removed — dev-mode-only stub, no production behaviour. |
Currently served via a raw Lift `serve { case Req(..., "openapi.yaml", ...) }` block that bypasses `registerRoutes` entirely. Needs a dedicated http4s route (no ResourceDocMiddleware) added to the centralized service.

### Caching

`Caching.getStaticSwaggerDocCache()` / `setStaticSwaggerDocCache()` are framework-agnostic and already used from within the http4s path. No migration work needed.

### Steps

1. ~~Fix aggregation bug in `getResourceDocsObpV700` → make `V7ResourceDocsAggregationTest` pass.~~ **Done** in `efb97531e` (2026-05-19). See the Prerequisite section above.
2. Extract shared handler logic into `Http4sResourceDocs` service; wire into `Http4sApp`.
3. Add `openapi.yaml` route to the same service.
4. ~~Port `getMessageDocsSwagger` from `APIMethods310` into the same service~~ — **Done.** Now served by `Http4sResourceDocs.handleGetMessageDocsSwagger` via the wildcard `/obp/*/message-docs/{CONNECTOR}/swagger2.0` route matched before any per-version service. The `val getMessageDocsSwagger: HttpRoutes[IO] = HttpRoutes.empty` stub in `Http4s310.scala` exists only to satisfy the `FrozenClassTest` API-surface check.
5. Remove resource-docs from the per-version Lift objects (`ResourceDocs140`–`ResourceDocs600`) once the centralized service covers them.

---

## ResourceDoc parity (content workstream — independent of serving)

This is a **separate workstream** from the serving migration above (which is complete). It covers the **content** of each migrated `ResourceDoc(...)` declaration: the goal is for every http4s `ResourceDoc(...)` to render identically to its Lift original, so the public API docs aren't silently degraded. The figures below are the last recorded audit (2026-05-21) and may have moved since; re-run the audit script for current numbers.

### Principle

**`APIMethodsXYZ.scala` (Lift) is the source of truth.** The commented-out Lift ResourceDocs inside each `APIMethodsXYZ.scala` are the canonical reference for what the http4s version should render: URL templates, verb casing, summaries, descriptions, example bodies, error lists, tags. **Do NOT edit those files to make the audit pass** — the audit compares http4s against the Lift source-of-truth. When the audit flags a diff, the resolution is either (a) update http4s to match Lift, or (b) document the difference at the http4s site as a known intentional drift (placeholder rename for middleware, upstream-driven case-class shift, etc.). Rewriting the Lift comments runs the comparison backwards and erases the historical record. (Mistakes in commits `d95c1df01` and `6154bf2cc` did this; reverted in `27f48af72`.)

**Stub fidelity verified.** Commits `810589330` (v6) and `88f46f854` (v5.1) replaced live Lift code with commented-out stubs: **0 field diffs across 243/243 v6 docs and 111/111 v5.1 docs**. The stubs are an exact preservation of the original Lift ResourceDocs.

### Tooling (`scripts/`)

| Script | Role |
|---|---|
| `check_lift_http4s_resource_doc_parity.py` | Read-only audit. Parses both files, matches by `nameOf(...)`, reports per-field diffs. `--field=X`, `--list-only`. |
| `rehydrate_resource_docs.py` | Lifts positional args 7/8/9 (description, exampleRequestBody, successResponseBody) from commented Lift blocks into http4s. `split-init` subcommand for the JVM 64KB workaround. |
| `restore_resource_doc_bodies.py` | Restores any subset of (summary, description, exampleRequestBody, successResponseBody, errorResponseBodies, tags) from Lift into http4s. `--fields=X,Y`, `--only=ep`. |

### Last recorded drift (audit 2026-05-21)

| Version | shared | mismatch | only-lift | only-http4s | Status |
|---|---|---|---|---|---|
| v1_2_1 | 70  | 6  | 0 | 0 | semantic fields restored; 6 structural drifts remain |
| v1_3_0 | 3   | 0  | 0 | 0 | clean |
| v1_4_0 | 10  | 1  | 0 | 0 | one minor |
| v2_0_0 | 37  | 1  | 0 | 0 | 1 structural drift remains |
| v2_1_0 | 23  | 1  | 5 | 2 | 1 structural drift remains |
| v2_2_0 | 18  | 0  | 0 | 18 | Lift trait fully retired upstream (`71892f5cb`); audited via git history; 3 middleware URL renames remain |
| v3_0_0 | 47  | 4  | 0 | 0 | 4 middleware-driven URL renames remain |
| v3_1_0 | 102 | 5  | 0 | 0 | 5 placeholder renames remain |
| v4_0_0 | 254 | 20 | 2 | 5 | 20 structural drifts (placeholder renames + 1 verb fix) remain |
| v5_0_0 | 39  | 8  | 0 | 3 | descriptions restored; structural/errors remain |
| v5_1_0 | 111 | 1  | 1 | 2 | one verb-casing drift |
| v6_0_0 | 243 | 12 | 0 | 1 | 11 placeholder renames + 1 routing-shape upstream change |
| **Total** | **956** | **60** | | | |

The per-version drift breakdowns (v6 COUNTERPARTY_ID renames, v4 GRANT_VIEW_ID / DYNAMIC_RESOURCE_DOC_ID, v3 firehose `FIREHOSE_*` renames, v5 system-view error-accuracy improvements, etc.) are middleware-driven placeholder renames or deliberate http4s improvements. The two only-lift v4 endpoints (`getAllAuthenticationTypeValidationsPublic`, `getAllJsonSchemaValidationsPublic`) are a known **migration gap** — port them or confirm they're intentionally dropped.

### Strategy for each remaining drift

1. **Default**: fix http4s to match Lift verbatim (`restore_resource_doc_bodies.py`).
2. **Documented exception**: where the drift is a deliberate http4s improvement or required by middleware semantics, leave it and add a `// Lift had X; we use Y because Z` comment at the http4s ResourceDoc site.
3. **Never**: edit `APIMethodsXYZ.scala` to make the audit pass — the Lift comments are the canonical record.

Reserved ALL_CAPS placeholders in middleware (`BANK_ID`, `ACCOUNT_ID`, `VIEW_ID`, `COUNTERPARTY_ID`) plus the literal SCA/transaction-type segments in `literalAllCapsSegments` drive most renames: when an endpoint needs a same-shape var without middleware lookup, it's renamed to a non-reserved variant (e.g. `COUNTERPARTY_ID_PARAM`, `NEW_ACCOUNT_ID`, `FIREHOSE_BANK_ID`) in **both** the http4s and Lift ResourceDocs.

---

## Lift Web teardown — completed

The full "remove Lift Web" milestone is done. For the record, what landed:

1. **`Http4sLiftWebBridge` deleted** — there is no request bridge; the chain ends in `notFoundCatchAll`. The bridge-traffic audit instrumentation (`Http4sLiftBridgeTraffic`, `GET /admin/lift-bridge-traffic`) that was used to prove `real_work[]` had drained is gone with it.
2. **`lift-webkit` removed from `pom.xml`** — the Lift web library is no longer a dependency.
3. **`Boot.scala` request-path hooks removed** — all `LiftRules.statelessDispatch.append(...)` (DirectLogin, ResourceDocs140–600, aliveCheck), `LiftRules.dispatch.append(OpenIdConnect)`, `addToPackages`, `exceptionHandler`/`uriNotFound`/`early`/`supplementalHeaders` request-path hooks are gone. Boot now does ORM init + connector/config setup + the Mapper schemifier + shutdown hooks only.
4. **OpenID Connect migrated** (fork a — drop portal-login). The one hard Lift-Web dependency in the request path (`AuthUser.logUserIn` / `S.redirectTo` seeding a Lift `SessionVar` portal session) was resolved by issuing a DirectLogin token instead.
5. **0 `net.liftweb.http` references anywhere in `obp-api/src`** — code, dead import comments, and doc-strings all removed. The previously-vestigial `net.liftweb.http.S.redirectTo(homePage)` in `AuthUser.logout` (dead code, never called) has been deleted, together with the 91 commented-out `//import net.liftweb.http...` lines. The only `net.liftweb.http.*` left in the running system is the inherited, unreachable `MegaProtoUser.logout` inside the Lift library jar — not OBP source.

> `APIUtil.SS.init(...)` wrappers (e.g. in `Http4s400.scala`) are **not** Lift-Web code — `SS` is a thread-local that the `lift-mapper`-based `LocalMappedConnectorInternal` reads (`SS.user`). It's a legitimate adapter for the ORM layer, which stays until lift-mapper is replaced.

---

## What remains — `lift-mapper`

**Out of scope for this migration.** `net.liftweb.mapper.*` is still the ORM across the codebase (100+ files): `AuthUser extends MegaProtoUser`, `Schemifier.schemify` in `Boot.scala`, all `MappedXxx` entities. Replacing it (with Doobie / Slick or similar) is a separate multi-month effort.

**"Lift Web removed" ≠ "Lift removed."**

- *Lift Web removed* (✅ **done**) — the HTTP request path no longer touches Lift: `lift-webkit` out of `pom.xml`, `Http4sLiftWebBridge` deleted, `Boot.scala` request-path hooks gone. `lift-mapper` is still the ORM.
- *Lift removed* (not done) — `net.liftweb:*` fully out of the dependency graph; requires the lift-mapper replacement above.

Decide which bar a release is hitting before announcing it; conflating them invites either an overstatement or an avoidable months-long delay.

---

## Reusable lessons

1. **JVM 64KB `<init>` limit** — adopt `lazy val xxx: HttpRoutes[IO] = ...` + `private def initXxxResourceDocs(): Unit` blocks in every per-version file from the start; don't wait until you hit the wall.
2. **`S.request`-bound Lift handlers** need an http4s-friendly entry point that accepts pre-parsed parameters. DirectLogin's `createTokenFuture` ignored its argument and re-read from `S.request` via `getAllParameters`; the fix threaded params through `validatorFutureWithParams`. Audit any handler for `S.request`/`S.param`/`S.queryString` reads before designing its http4s entry point.
3. **`Future.failed(new Exception)` produces 500** — use `unboxFullOrFail(Empty, ..., 400)` or `NewStyle.function.tryons(msg, 400, ...)` to return the intended 4xx.
4. **`isStatisticallyTooPermissive` is sample-pool-dependent** — locally, a fresh test DB with a single user causes spurious ABAC rejections. Seed enough users.
5. **Reserved ALL_CAPS placeholders** in middleware — when an endpoint needs a same-shape var without middleware lookup, rename to a non-reserved variant (e.g. `COUNTERPARTY_ID_PARAM`) in both the http4s and Lift ResourceDocs.
6. **Bridge-cascade hijack** — when a new version overrides an older URL+verb, migrate the override into the new version's own routes *before* wiring it into the chain, or the request cascades down the path-rewriting bridges to the older handler. (Now that the chain ends in `notFoundCatchAll`, an un-migrated override cascades to an older http4s handler or 404s — there is no Lift safety net.)

---

## Why http4s?

- **Non-blocking I/O** — small fixed thread pool (CPU cores), fibres suspend on I/O. Thousands of concurrent requests without thread-pool tuning.
- **Lower memory** — no thread-per-request overhead.
- **Modern Scala ecosystem** — first-class Cats Effect, fs2 streaming, functional patterns.
- **No servlet container** — Jetty and WAR packaging gone entirely.

---

## Running

```sh
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" \
  mvn -pl obp-api -am clean package -DskipTests=true -Dmaven.test.skip=true && \
  java -jar obp-api/target/obp-api.jar
```

Binds to `hostname` / `dev.port` from your props file (defaults: `127.0.0.1:8080`).

---

## Done Criteria

| Milestone | Condition | Status |
|---|---|---|
| Version file done | All functional endpoints are `HttpRoutes[IO]`; the version's test suite is green. | ✅ all 12 |
| Lift bridge removed | All APIMethods files + auth stack + resource-docs done; `Http4sLiftWebBridge` deleted. | ✅ done |
| Lift Web removed | `lift-webkit` out of `pom.xml`; `Boot.scala` reduced to DB init + scheduler/shutdown. | ✅ done |
| `lift-mapper` removed | `net.liftweb:*` fully out of the dependency graph. | ⏳ separate long-term effort |
