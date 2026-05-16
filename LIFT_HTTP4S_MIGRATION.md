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
| 8 | `APIMethods310` | 102 | **Done** — `Http4s310.scala` has all 100 functional endpoints (42 GET, 10 DELETE, 19 POST, 25 PUT, 1 GET-shaped revoke, 3 SCA aliases) + path-rewriting bridge to `Http4s300`; 181 v3.1.0 tests pass. Two endpoints tracked separately in "Per-version Lift leftovers" (`getMessageDocsSwagger`, `getObpConnectorLoopback`) — they retire via the Resource-docs workstream / bridge-removal PR, not as v3.1.0 follow-up. |
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

## Auth Stack (separate workstream)

Token-generation paths — not version-file endpoints. Each `extends RestHelper` and needs to become an http4s route or middleware independently. Can run in parallel with the APIMethods migration.

| Component | Path | Notes |
|---|---|---|
| `DirectLogin` | `POST /my/logins/direct` | |
| `GatewayLogin` | gateway JWT exchange | |
| `DAuth` | dAuth JWT exchange | |
| `OAuth` | OAuth 1.0a token endpoints | Most complex |

These are the last hard dependency on Lift Web in the request path. The Lift bridge cannot be removed until all four are done.

---

## Per-version Lift leftovers

An `APIMethods{version}` file is marked **done** in the progress table when every *functional* endpoint is on http4s and the version's test suite is green. A small number of endpoints are deliberately *not* migrated inline because they belong to a different workstream or have no behaviour worth porting. They continue to be served by the Lift bridge until the workstream that owns them lands; they do **not** create new follow-up work on the per-version file.

| Endpoint | Origin | Why on Lift | Retired by |
|---|---|---|---|
| `getMessageDocsSwagger` (`GET /message-docs/CONNECTOR/swagger2.0`) | `APIMethods310` | Same shape as `getResourceDocsObpV700` / `openapi.yaml` — runtime Swagger generation with shared caching | The **Http4sResourceDocs** workstream (step 4) |
| `getObpConnectorLoopback` (`GET /connector/loopback`) | `APIMethods310` | Deprecated stub that unconditionally throws `IllegalStateException(NotImplemented)`; no functional behaviour | Either a 3-line native http4s route that throws the same exception or outright deletion, decided when the Lift bridge is removed |
| `testResourceDoc` (`GET /dummy`) | `APIMethods140` | Dev-only stub gated behind `if (Props.devMode) { ... }`. Returns a dummy `APIInfoJSON` payload for testing the resource-doc renderer. Has no production behaviour worth porting. | Deleted in the bridge-removal PR (no native equivalent needed). |

Track new leftovers here when later version files are migrated — the bridge-removal milestone in "Done Criteria" only requires the per-version files to be **done** in this table's sense (functional endpoints migrated, tests green). Leftovers folded into the Resource-docs or Auth-stack workstreams retire via those workstreams.

---

## Migration leftovers (full landscape, beyond per-version files)

Things still on Lift that block the `Http4sLiftWebBridge` from being removed. Use this section as the master TODO for the "remove Lift Web" milestone.

### Auth stack — every handler is its own `RestHelper`

| Handler | File | Routes | Status |
|---|---|---|---|
| `DirectLogin` | `code/api/directlogin.scala` | `POST /my/logins/direct` | http4s version inside `Http4s600.scala`; Lift dispatch **still registered** as fallback. Earlier-version paths still hit Lift. The key gotcha: `createTokenFuture(allParameters)` ignores its argument and re-reads from Lift's `S.request` via `getAllParameters`. Use `validatorFutureWithParams(...)` + `createTokenCommonPart(...)` instead — this is the http4s-friendly entry point. |
| `GatewayLogin` | `code/api/GatewayLogin.scala` | Gateway JWT exchange | Lift only |
| `DAuth` | `code/api/dauth.scala` | dAuth JWT exchange | Lift only |
| `OAuth 1.0a` | OAuth files | OAuth 1.0a token endpoints | Lift only |
| `OAuth2` | `code/api/OAuth2.scala` | OAuth 2.0 token & callback | Lift only |
| `OpenIdConnect` | `code/api/openidconnect.scala` | OIDC callback — registered via `LiftRules.dispatch.append` | Lift only |

These four (DirectLogin/GatewayLogin/DAuth/OAuth) are the most-complex remaining dependencies on Lift `S.request` and they collectively block bridge removal.

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
| `aliveCheck` | `code/api/aliveCheck.scala` | One-line liveness probe. Trivial port. |
| `ImporterAPI` | `code/api/ImporterAPI.scala` (gated by props) | Sandbox data-import endpoint. Single Lift file. |
| `OpenIdConnect` | (auth-stack table above) | OIDC callback, registered separately from OAuth2. |

### Open-banking standards (large, deferred indefinitely)

Lift implementations of 3rd-party regulatory standards. All currently pass through `Http4sLiftWebBridge` and continue to work; they are *not* OBP API per se but optional regulatory shims. Migrating them is out of scope for the "remove Lift Web" milestone if you accept keeping the bridge for these stacks only. If total Lift removal is the goal, each needs its own workstream.

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

1. `LiftRules.statelessDispatch.append(...)` registrations: `DirectLogin`, `ImporterAPI`, `ResourceDocs140`–`ResourceDocs600`, `aliveCheck`.
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

### Suggested ordering for the remaining work

1. ~~**v4.0.0 bulk port**~~ — done (258/258, 100%).
2. **`aliveCheck`, `ImporterAPI`** — easiest wins, retire two `LiftRules.statelessDispatch` entries.
3. **`Http4sResourceDocs` centralised service** — single PR removes 6 dispatch entries + the `openapi.yaml` raw-serve block.
4. **Auth stack: OAuth2 / OpenIdConnect** — smaller and fewer call sites than the others.
5. **DirectLogin** — already half done in v6; needs to cover earlier versions and retire the `LiftRules.statelessDispatch.append(DirectLogin)` entry.
6. **GatewayLogin + DAuth + OAuth 1.0a** — biggest remaining auth work.
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

---

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
| `APIMethods310` | done — `Http4s310.scala` (100 own endpoints + `updateCustomerAddress`; path-rewriting bridge to Http4s300; 2 endpoints intentionally left on Lift: `getMessageDocsSwagger`, `getObpConnectorLoopback`) |
| `APIMethods400` | **done — 258 / 258 (100%)**. `Http4s400.scala` covers all 253 unique handlers + 8 ResourceDoc aliases for transaction-request-type variants (served by the shared wildcard handler). |
| `APIMethods500` | done — `Http4s500.scala` (all 10 v5.0.0 originals on http4s) |
| `APIMethods510` | done — `Http4s510.scala` (all 111 v5.1.0 originals on http4s; `createConsent` exposed as `createConsentImplicit` with a guard covering EMAIL/SMS/IMPLICIT SCA methods) |
| `APIMethods600` | **done — 243 / 243 (100%)**. `Http4s600.scala` covers all 35 overrides + 208 originals. |
| Auth: DirectLogin | todo |
| Auth: GatewayLogin | todo |
| Auth: DAuth | todo |
| Auth: OAuth | todo |
| Resource-docs: aggregation bug fix | done |
| Resource-docs: `Http4sResourceDocs` service | todo |
| Resource-docs: `openapi.yaml` route | todo |

### Cleanup done

- `getCards` and `getCardsForBank` removed from `Http4s700` — these had the same API signature as the v1.3.0 originals and belonged in `APIMethods130`, not v7.0.0. The Lift implementation in `APIMethods130` serves them at `/obp/v1.3.0/` until that file is migrated.
