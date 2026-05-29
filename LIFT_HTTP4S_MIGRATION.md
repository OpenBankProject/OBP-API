# Lift → http4s Migration

Single source of truth for **migration status and open TODOs**. The how-to and the gotchas live in `CLAUDE.md` (§ Migrating a Lift Endpoint, § Tricky Parts).

## Principle

API version numbers reflect **API contract changes** (new/changed fields, new behaviour), never the framework. Lift → http4s is an **in-place** refactor at the existing version/URL — no version bump. Use a new version only when the contract itself changes.

## Architecture (current)

OBP-API runs as a single **http4s Ember server** (`Http4sServer`, a Cats Effect `IOApp`). Jetty / servlet container are gone. Lift now does only two things: (1) **Mapper ORM** — schema, migrations, all data access; (2) **legacy endpoint dispatch** for not-yet-migrated paths via `Http4sLiftWebBridge` (converts http4s ⇄ Lift `Req`, runs `LiftRules.dispatch`). Native http4s routes never touch the bridge.

**Priority routing** (`Http4sApp.baseServices`): `corsHandler` → AppsPage → StatusPage → LiftBridgeTraffic(audit) → Http4sResourceDocs → v510 → v600 → v500 → v700 → BGv2 → **ukV20 → ukV31** → v400 → v310 → v300 → v220 → v210 → v200 → v140 → v130 → v121 → dynamic-entity → dynamic-endpoint → `Http4sLiftWebBridge`. Each per-version service gates on its own prefix, so ordering only matters where URL patterns overlap. Unhandled `/obp/*` paths fall through to Lift (no 404).

**Version enable/disable**: `api_disabled_versions` / `api_enabled_versions` (allowlist; empty = all) are enforced once at startup by `Http4sApp.gate` (disabled → `HttpRoutes.empty`). The path-rewriting cascade between versions bypasses `gate` deliberately, so an endpoint declared in v2.0.0 stays reachable via `/obp/v4.0.0/...` even if v2.0.0's prefix is disabled. To kill an endpoint on every prefix use `api_disabled_endpoints` (enforced per-request by the middleware). `ResourceDocMiddleware.isEndpointEnabled` must **not** re-check version per request — a 2026-05 regression that did was reverted 2026-05-26; `ResourceDocMiddlewareEnableDisableTest` pins this.

## In-place migration, per file

`APIMethods{version}.scala`: drop `self: RestHelper =>`; `lazy val xyz: OBPEndpoint` → `lazy val xyz: HttpRoutes[IO]`; Lift `case "path" :: Nil JsonGet _` → `case req @ GET -> \`prefixPath\` / "path"`; auth via the right `EndpointHelpers.*`; `ResourceDoc(root, ...)` → `ResourceDoc(null, ..., http4sPartialFunction = Some(root))`. `OBPAPI{version}.scala`: drop `extends OBPRestHelper` / `registerRoutes`, expose routes wired into the `Http4sServer` chain. Full Rule 1–5 reference + gotchas in `CLAUDE.md`.

**One file = one PR; a file is fully Lift or fully http4s.** `APIMethods121` was done as a parallel `Http4s121.scala` (not in-place) because it's a mixin trait inherited by 130/140/etc.; http4s takes priority in the chain and the Lift trait is deleted once all inheritors are migrated.

## Per-version progress

All 12 APIMethods files **done** — every functional endpoint on http4s, test suites green:

| File | http4s | Notes |
|---|---|---|
| 121 | Http4s121 | 70 own; 323 API1_2_1Test scenarios |
| 130 | Http4s130 | 3 own + bridge→121 |
| 140 | Http4s140 | 11 own + bridge→130 |
| 200 | Http4s200 | 37 own + bridge→140 |
| 210 | Http4s210 | 25 own + bridge→200; 79 tests |
| 220 | Http4s220 | 18 own + bridge→210; 27 tests |
| 300 | Http4s300 | 47 own + bridge→220; 86 tests |
| 310 | Http4s310 | 100 own + bridge→300; 181 tests. See v3.1.0 leftovers below |
| 400 | Http4s400 | **258/258** (253 handlers + 8 txn-request-type doc aliases); lazy-val+init-def pattern; all 35 v4 overrides migrated |
| 500 | Http4s500 | 10 own |
| 510 | Http4s510 | 111 own; `createConsent` exposed as `createConsentImplicit` (EMAIL/SMS/IMPLICIT guard) |
| 600 | Http4s600 | **243/243** (35 overrides + 208 originals); introduced the lazy-val+init-def pattern |

**v3.1.0 bridge leftovers** (both off-bridge in production; Lift definitions deferred to the bridge-removal PR): `getMessageDocsSwagger` — real handler in `Http4sResourceDocs`; `Http4s310` keeps an `HttpRoutes.empty` stub only so `nameOf(...)` compiles for `FrozenClassTest`. `getObpConnectorLoopback` — native http4s route that always returns 400 NotImplemented. Deleting their Lift `lazy val`s shrinks the frozen STABLE surface → needs a snapshot refresh.

## Workstream status

| Workstream | Status |
|---|---|
| Resource-docs serving | **done** — `Http4sResourceDocs` serves `/obp/*/resource-docs/{ver}/{obp,swagger,openapi,openapi.yaml}`, the per-bank variant, and `/message-docs/{conn}/swagger2.0`; 10 Lift dispatch entries + the raw `openapi.yaml serve{}` block retired. ResourceDocsTest (63) + SwaggerDocsTest (10) green. |
| Resource-docs aggregation bug | **done** — `getResourceDocsObpV700` aggregates all versions; `V7ResourceDocsAggregationTest` passes. |
| Auth: DirectLogin | **done** — `DirectLoginRoutes` (bare `/my/logins/direct`, gated on `allow_direct_login`) + per-version paths; Lift dispatch removed. Migration gotcha: `createTokenFuture` ignored its args and re-read `S.request` — use `validatorFutureWithParams` instead. Dead `dlServe` block + `extends RestHelper` cleanup is a small follow-up PR. |
| Auth: GatewayLogin / DAuth / OAuth2 | **done** — all library-only validators (no routes); vestigial `extends RestHelper` removed. |
| Auth: OAuth 1.0a | **done — removed** (`51820c75e`): `oauth1.0.scala` deleted, `OAuthHandshake` unregistered, OAuth header parsing + dead fields removed. |
| Auth: OpenIdConnect | **blocked** — see Decision gates. The only auth handler still on Lift. |
| Dynamic-entity data plane | **done** — `Http4sDynamicEntity` serves `/obp/dynamic-entity/*` natively (`OBPAPIDynamicEntity` stays as a dormant Lift fallback, removed in the bridge-removal PR). |
| Dynamic-endpoint data plane | **todo (Phase 3)** — runtime Scala codegen (`DynamicEndpoints.scala`) emits Lift `OBPEndpoint`; still on the bridge. 3a (keep Lift-typed codegen behind an adapter) recommended for the Lift-removal milestone; 3b (emit `HttpRoutes[IO]`) deferred. The only remaining `real_work` bridge traffic. |
| Std: Berlin Group v2 | **done** — `Http4sBGv2`. |
| Std: UK Open Banking v2.0 + v3.1 | **done** — PR #2817 (merged 2026-05-29). v2.0: 5; v3.1: ~67 / 20 categories; Lift aggregators are `routes = Nil` stubs. 142 test scenarios pass. |
| Std: Berlin Group v1.3 | **todo** — 7 files still on active Lift (`code/api/berlin/group/v1_3/*`). |
| Std: Bahrain / AU / STET / MxOF / Polish | **retired** — commented out in PR #2814 (`d19af2b92`); `RetiredApiStandardsTest` guards against re-registration. |
| Std: Sandbox | **n/a** — `SandboxApiCalls.scala` fully commented out (dead code, registers no routes); deletion candidate. |

## ResourceDoc parity (content fidelity vs Lift)

Separate from serving: every migrated http4s `ResourceDoc(...)` should render identically to its Lift original. **`APIMethodsXYZ.scala` (the commented-out Lift) is the source of truth — never edit it to make the audit pass.** When the audit flags a diff, either fix http4s to match, or document a deliberate drift at the http4s site (placeholder rename for `ResourceDocMatcher`, upstream case-class shift, or a genuine improvement). Stub fidelity is verified: 0 field diffs across the v6 (243) and v5.1 (111) stubs vs their pre-stub Lift.

Run the audit for the **live** drift list — don't transcribe it here (it rots):
```
python3 scripts/check_lift_http4s_resource_doc_parity.py [--field=X] [--list-only]
```
Restoration tools: `rehydrate_resource_docs.py` (descriptions / example bodies from Lift comments) and `restore_resource_doc_bodies.py` (surgical per-field restore). At the last full audit ~60 drifts remained, almost all **middleware-driven placeholder renames** that are required and stay documented at the http4s site: `ACCOUNT_ID`→`NEW_ACCOUNT_ID` (PUT-creates-account), `VIEW_ID`→`*_VIEW_ID` (disambiguation), firehose `*_BANK_ID`/`*_VIEW_ID` (prop-before-bank bypass), `COUNTERPARTY_ID`→`COUNTERPARTY_ID_PARAM`, hyphen→underscore for `DYNAMIC_RESOURCE_DOC_ID`. Genuine fix candidates: verb-casing (`revokeMyConsent` Delete→DELETE), v4 `deleteExplicitCounterparty` POST→DELETE (REST-correct), and v4's two only-lift endpoints never ported (`getAllAuthenticationTypeValidationsPublic`, `getAllJsonSchemaValidationsPublic`).

## Open TODOs — master list for "remove Lift Web"

**Bridge-traffic audit (data-driven prioritisation).** Every bridge hit is tallied by `Http4sLiftBridgeTraffic`; `GET /admin/lift-bridge-traffic` returns `real_work` (non-404 = migration targets) vs `not_found` (stale/probes, ignore); `POST .../reset` clears it. Playbook: reset on a representative instance → run a normal traffic window (24h + scheduled jobs) → if `real_work[]` is empty the bridge is retirable (modulo documented leftovers). Last CI audit: the only `real_work` left is `/obp/dynamic-endpoint/...` (Phase 3); `/obp/dynamic-entity/...` is now native.

1. **Dynamic-endpoint Phase 3** — see workstream table.
2. **OpenIdConnect** — blocked on the OIDC portal-session decision (gate 1).
3. **Bridge-removal PR** — delete `Http4sLiftWebBridge` + the request-path `Boot.scala` hooks: the `LiftRules.statelessDispatch.append(...)` registrations (DirectLogin, ResourceDocs140–600, aliveCheck), `LiftRules.dispatch.append(OpenIdConnect)`, `addToPackages("code")`, the global exception + 404 handlers, the `early`/`supplementalHeaders`/`localeCalculator` request hooks, and `unloadHooks`. The Mapper schemifier stays (that's lift-mapper, not the bridge). Plan a `FrozenClassTest` snapshot refresh in the same PR.
4. **Open-banking standards** — decide BG v1.3's fate (gate 2).
5. **`lift-mapper`** — separate long-term ORM replacement; out of scope here.
6. **Misc**: OBP-Trading payment-auth endpoints (notifyDeposit, create/capture/release/getPaymentAuth) still commented out in `Http4s700` (see `ideas/CAPTURE_RELEASE_TRANSACTION_REQUEST_TYPES.md`); CI speed-up (two-tier fast gate + surefire parallel forks) not done.

**Small singletons:** `aliveCheck` **done** (`AliveCheckRoutes`, `GET /alive`); `ImporterAPI` **retired** (endpoint + `TransactionInserter` + connector helpers removed).

**Disabled / ignored tests to revisit:** `Http4s500RoutesTest`, `RootAndBanksTest`, `V500ContractParityTest` (`@Ignore`); `CardTest` (commented out); v5.0.0 13 skipped; `AbacRuleTests` 6 local fails are environment-dependent (too few users → `isStatisticallyTooPermissive`), not a regression. The `MakerCheckerTransactionRequestTest` proxy/TTL race is **resolved** by the `RequestScopeConnection` hardening on `develop` (regression-guarded by its "Stress: repeated multi-challenge creates" scenario); if it ever flakes again, route DB Futures through `RequestScopeConnection.fromFuture`.

## Decision gates (stakeholder calls, before the bridge-removal PR)

1. **OIDC portal-session strategy.** The OIDC callback's success path calls `AuthUser.logUserIn` / `S.redirectTo`, which mutate Lift `SessionVar`s the portal reads. Forks: **(a) drop portal-login** — pure http4s callback issues a token but seeds no portal session (behaviour change; needs sign-off from any OIDC portal-UI users); **(b) Lift-session shim** — keep `lift-webkit` for this one callback (cheapest code; "Lift Web removed" never actually ships); **(c) replace portal session** (Redis/JWT-backed; months, but also unblocks lift-mapper later). No tests cover the callback success path. **This is the only thing blocking bridge removal.**
2. **Open-banking standards.** Not required for bridge removal, but for the public claim: if the headline is "Lift Web removed", a feature-flagged Lift remnant (BG v1.3) is acceptable; if "Lift Web removed *from this repo*", BG v1.3 must be migrated or extracted as a plugin project.

## Risks

| Risk | Mitigation |
|---|---|
| `FrozenClassTest` ratchet — deleting any Lift `lazy val ... : OBPEndpoint` shrinks the STABLE surface and trips the test | Refresh the frozen snapshot inside the bridge-removal PR; list each removed `lazy val` in the PR body. |
| OIDC callback success path has no tests | Write a Keycloak-container integration test before picking a fork. |
| `S.request` translation — handlers re-read `S.request`/`S.param` invisibly (bit DirectLogin's `createTokenFuture`) | Audit for `S.request`/`S.param`/`S.queryString` before designing the http4s entry; replicate the `validatorFutureWithParams` pattern. |
| Bridge-cascade hijack on partial migrations (see CLAUDE.md) | When wiring a new `Http4sXxx` into `baseServices`, migrate its URL+verb overrides vs older versions first. |
| `isStatisticallyTooPermissive` flakiness — too few local users | Seed enough users in any ABAC test. |

## Done criteria

| Milestone | Condition |
|---|---|
| Version file done | All functional endpoints are `HttpRoutes[IO]`; suite green. Workstream-owned stubs (resource-docs / auth / leftovers) don't block. |
| Lift bridge removable | All 12 APIMethods done + auth stack done + resource-docs done + dynamic-endpoint ported; remaining stubs deleted in the bridge-removal PR. |
| Lift Web removed | `lift-webkit` out of `pom.xml`; `Http4sLiftWebBridge` deleted; `Boot.scala` reduced to DB init + scheduler. |
| Lift removed | `net.liftweb:*` fully out — requires the multi-month `lift-mapper` replacement (Doobie/Slick or similar). |

**"Lift Web removed" ≠ "Lift removed".** The first means the HTTP path no longer touches Lift (lift-mapper still the ORM); the second means no `net.liftweb:*` at all. Decide which bar a release hits before announcing — conflating them invites an overstatement or an avoidable months-long delay.

## Running

```sh
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" \
  mvn -pl obp-api -am clean package -DskipTests=true -Dmaven.test.skip=true && \
  java -jar obp-api/target/obp-api.jar
```
Binds to `hostname` / `dev.port` from your props file (defaults `127.0.0.1:8080`).
