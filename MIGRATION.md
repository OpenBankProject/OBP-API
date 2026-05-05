# Lift → http4s Migration Plan

## Principle

API version numbers reflect **API contract changes** (new/changed fields, new behaviour). The underlying framework is invisible to clients. Lift → http4s is a refactoring: it happens **in-place** inside the existing version file at the existing URL. No version bump.

Use a new version (e.g. v7.0.0) only when the API contract itself changes — new fields, changed request/response shape, new behaviour.

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

## Migration order

Bottom-up — each version depends on the one below it being done.

**Rule: one file = one PR. A file is either fully Lift or fully http4s — no half-converted state.**

| # | File | Own endpoints | Notes |
|---|---|---|---|
| 1 | `APIMethods121` | 70 | Largest; everything inherits from it |
| 2 | `APIMethods130` | 3 | Small; good smoke-test after #1 |
| 3 | `APIMethods140` | 11 | |
| 4 | `APIMethods200` | 40 | |
| 5 | `APIMethods210` | 28 | |
| 6 | `APIMethods220` | 19 | |
| 7 | `APIMethods300` | 47 | |
| 8 | `APIMethods310` | 102 | |
| 9 | `APIMethods400` | ~258 total | Largest file; may need splitting into sub-traits |
| 10 | `APIMethods500` | 37 | |
| 11 | `APIMethods510` | 111 | |
| 12 | `APIMethods600` | ~244 total | Final Lift endpoint file |

---

## Auth stack (separate workstream)

These are token-generation paths, not version-file endpoints. Each `extends RestHelper` and needs to become an http4s route or middleware independently. Can run in parallel with the APIMethods migration.

| Component | Path | Notes |
|---|---|---|
| `DirectLogin` | `POST /my/logins/direct` | |
| `GatewayLogin` | gateway JWT exchange | |
| `DAuth` | dAuth JWT exchange | |
| `OAuth` | OAuth 1.0a token endpoints | Most complex |

These are the last hard dependency on Lift Web in the request path. The Lift bridge cannot be removed until all four are done.

---

## Server chain after full migration

```
corsHandler
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
  → Http4s140  (/obp/v1.4.0/*)
  → Http4s130  (/obp/v1.3.0/*)
  → Http4s121  (/obp/v1.2.1/*)
  → Http4sBGv2
  ← Lift bridge removed
```

---

## Done criteria

| Milestone | Condition |
|---|---|
| Version file done | All endpoints are `HttpRoutes[IO]`; `OBPRestHelper` removed from the file; existing tests pass |
| Lift bridge removable | All 12 APIMethods files done + auth stack done |
| Lift Web removed | `lift-webkit` removed from `pom.xml`; `Boot.scala` reduced to DB init + scheduler startup |
| `lift-mapper` | Separate long-term effort — not in scope here |

---

## Progress

| File | Status |
|---|---|
| `APIMethods121` | todo |
| `APIMethods130` | todo |
| `APIMethods140` | todo |
| `APIMethods200` | todo |
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
