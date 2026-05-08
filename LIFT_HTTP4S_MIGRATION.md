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

Routes are tried in order: `corsHandler` (OPTIONS) → `StatusPage` → `Http4s500` → `Http4s700` → `Http4sBGv2` → `Http4s130` → `Http4s121` → `Http4sLiftWebBridge` (Lift fallback). Unhandled `/obp/v7.0.0/*` paths fall through silently to Lift — they do not 404.

```
HTTP Request
    │
    ▼
Http4sServer (IOApp / Ember)
    │
    ▼
corsHandler → StatusPage → Http4s500 → Http4s700 → Http4sBGv2 → Http4s130 → Http4s121 → Http4sLiftWebBridge
                                                                      │              │
                                                                  own routes    v1.2.1 routes
                                                                  (3 endpoints) (path-rewrite)
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
| 5 | `APIMethods210` | 28 | |
| 6 | `APIMethods220` | 19 | |
| 7 | `APIMethods300` | 47 | |
| 8 | `APIMethods310` | 102 | |
| 9 | `APIMethods400` | ~258 total | Largest file; may need splitting into sub-traits |
| 10 | `APIMethods500` | 37 | |
| 11 | `APIMethods510` | 111 | |
| 12 | `APIMethods600` | ~244 total | Final Lift endpoint file |

---

## Resource-docs (separate workstream)

Resource-docs endpoints are **version-polymorphic**: `GET /obp/v6.0.0/resource-docs/v3.0.0/obp` returns v3.0.0 docs. The URL prefix is cosmetically version-specific but functionally irrelevant — the `API_VERSION` path segment controls the output. This makes resource-docs a natural candidate for a single centralized http4s service rather than per-version handlers.

### Strategy: centralized `Http4sResourceDocs`

Add one service to `Http4sApp` (above the Lift bridge, before any per-version service) that handles:

```
GET  /obp/*/resource-docs/API_VERSION/obp   → version-dispatch via getResourceDocsList
GET  /obp/*/resource-docs/API_VERSION/openapi.yaml
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
4. Remove resource-docs from the per-version Lift objects (`ResourceDocs140`–`ResourceDocs600`) once the centralized service covers them.

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
| Version file done | All endpoints are `HttpRoutes[IO]`; `OBPRestHelper` removed from the file; existing tests pass |
| Lift bridge removable | All 12 APIMethods files done + auth stack done |
| Lift Web removed | `lift-webkit` removed from `pom.xml`; `Boot.scala` reduced to DB init + scheduler startup |
| `lift-mapper` | Separate long-term effort — not in scope here |

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
| `APIMethods210` | todo |
| `APIMethods220` | todo |
| `APIMethods300` | todo |
| `APIMethods310` | todo |
| `APIMethods400` | todo |
| `APIMethods500` | todo |
| `APIMethods510` | todo |
| `APIMethods600` | todo |
| Auth: DirectLogin | todo |
| Auth: GatewayLogin | todo |
| Auth: DAuth | todo |
| Auth: OAuth | todo |
| Resource-docs: aggregation bug fix | done |
| Resource-docs: `Http4sResourceDocs` service | todo |
| Resource-docs: `openapi.yaml` route | todo |

### Cleanup done

- `getCards` and `getCardsForBank` removed from `Http4s700` — these had the same API signature as the v1.3.0 originals and belonged in `APIMethods130`, not v7.0.0. The Lift implementation in `APIMethods130` serves them at `/obp/v1.3.0/` until that file is migrated.
