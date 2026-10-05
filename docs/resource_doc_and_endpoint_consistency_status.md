# ResourceDoc and endpoint consistency: status

This document records how OBP-API uses ResourceDocs, which endpoints are served without going
through them, and what that costs. It is a status record, not a work plan.

## 1. What a ResourceDoc is

A ResourceDoc describes one endpoint: its verb and URL template, summary and description, example
request and response bodies, possible errors, tags, the Roles it requires, and (for the http4s
routes) the handler that serves it (`http4sPartialFunction`). OBP's principle is that every
endpoint has one.

## 2. What ResourceDocs are used for

**Documentation.** The resource-docs, Swagger and OpenAPI endpoints, API Explorer, OBP-MCP's
endpoint discovery and the Glossary's endpoint links are all generated from ResourceDocs.

**Building the routes.** In every versioned API group, the routes are built from the ResourceDocs
themselves: `allRoutes` is the list of each doc's `http4sPartialFunction`, ordered by the number of
path segments (for example `Http4s700.scala`, `val allRoutes`). So in those groups a route cannot
exist without a ResourceDoc. The same construction is used in 11 route groups.

**Checking every request, in `ResourceDocMiddleware`.** Each versioned group is wrapped in
`ResourceDocMiddleware.apply(resourceDocs)` (`obp-api/src/main/scala/code/api/util/http4s/ResourceDocMiddleware.scala`).
For each request it finds the matching ResourceDoc and then, in this order
(`validateOnly`, which follows the order Lift used):

1. rejects duplicated query parameters;
2. authenticates the caller, if the doc requires it (the doc's error list contains
   `AuthenticatedUserIsRequired`, or it declares Roles);
3. refuses an unresolved UK Open Banking consent;
4. resolves `BANK_ID` (404 when unknown);
5. checks the doc's Roles (403);
6. resolves `ACCOUNT_ID`, `VIEW_ID` (and the caller's access to the view) and `COUNTERPARTY_ID`;
7. applies Force-Error, the authentication-type validation and the JSON Schema validation
   configured for the endpoint's operation id.

It then runs the handler: inside a database transaction for POST, PUT, DELETE and PATCH, and
on auto-commit for GET and HEAD. It also applies the endpoint timeout, honours the
enable/disable props by operation id, puts the doc and its operation id on the `CallContext`, and
records Telemetry for the request. `IdempotencyMiddleware` is nested inside it.

**Other uses.** The JSON baseline under `scripts/resource_doc_baseline/` and the parity audit
compare ResourceDocs with their Lift originals. Guard tests such as `AnyBankScopeSweepTest` read
the ResourceDoc catalogue to check Role scoping. API Metrics records carry the endpoint's name and
version taken from the doc.

## 3. Where the pairing holds

Every group below builds its routes from its ResourceDocs and is wrapped by
`ResourceDocMiddleware`:

- OBP API v1.2.1, v1.3.0, v1.4.0, v2.0.0, v2.1.0, v2.2.0, v3.0.0, v3.1.0, v4.0.0, v5.0.0, v5.1.0,
  v6.0.0, v7.0.0, including the bridges that pass a request from one version to the one below;
- Berlin Group v1.3 (and its alias path) and v2;
- UK Open Banking v2.0, v3.1 and v4.0.1.

One narrow exception inside these groups: when no ResourceDoc matches (for example a URL with an
empty segment such as `/banks//accounts`), the middleware still lets the group's routes try the
request, after resolving the caller but without the doc-based checks. That branch exists so a
malformed URL gets 403 or 404 rather than a misleading 401. It is documented in `CLAUDE.md`
("Empty path segments").

## 4. Where it does not hold

These routes are served from `Http4sApp`'s chain outside any `ResourceDocMiddleware`
(`obp-api/src/main/scala/code/api/util/http4s/Http4sApp.scala`, `baseServices`).

| Route | Paths | ResourceDoc? | How it checks requests instead | Stated reason |
|---|---|---|---|---|
| Dynamic Entity records | `/obp/dynamic-entity/...`, and the v7.0.0 form `/obp/v7.0.0/banks/BANK_ID/dynamic-entities/...` (served by `wrappedRoutesDynamicEntityV700`, `Http4s700.scala:7294`) | Generated at runtime, one set per entity; they show only the unversioned URLs so far | Inline authentication, Role and bank checks, ported from the Lift handlers, plus the before/after interceptors called inline (`Http4sDynamicEntity.scala`, header comment) | Entities are created at runtime, and the middleware builds its ResourceDoc index once, at start-up |
| Dynamic Endpoints | `/obp/dynamic-endpoint/...` | Generated at runtime | Proxy endpoints: `APIMethodsDynamicEndpoint.proxyHandle`. Compiled endpoints: `ResourceDoc.authCheckIO` (`APIUtil.scala:1835`), a copy of the doc-driven checks | Not stated; the same runtime-definition problem applies |
| Resource docs, Swagger, OpenAPI | `/obp/PREFIX/resource-docs/API_VERSION/{obp,swagger,openapi}` and the bank-scoped form | Yes (`ResourceDocs1_4_0/ResourceDocsAPIMethods.scala`) | Inline; `resource_docs_requires_role` is checked in the route (`Http4sResourceDocs.scala:108`) | Not stated |
| OpenAPI as YAML | `/obp/PREFIX/resource-docs/API_VERSION/openapi.yaml` | **No** | As above | Not stated |
| DirectLogin, unversioned | `POST /my/logins/direct` | The same endpoint is documented and served at `/obp/v6.0.0/my/logins/direct`, behind the middleware (`Http4s600.scala:13117`) | `DirectLoginRoutes` | Clients use the unversioned path |
| SIWE login | `POST /my/logins/siwe/challenge`, `POST /my/logins/siwe` | **No** | `SIWERoutes` | Not stated |
| Server pages | `/`, `/apps`, `/status`, `/health`, `/alive` | **No** | None needed: no authentication | Not API endpoints in the usual sense |
| CORS preflight and the JSON 404 | any `OPTIONS`; any unmatched path | Not applicable | `corsHandler`, `notFoundCatchAll` | Infrastructure |

What these routes do not get from the middleware (checked by reading the code, 2026-09-28):

| Route | API Metrics | Request transaction | Endpoint timeout | Idempotency | Telemetry timing |
|---|---|---|---|---|---|
| Dynamic Entity records | yes (through `EndpointHelpers`) | yes (inline) | no | no | no |
| Dynamic Endpoints | yes (through `EndpointHelpers`) | compiled: yes; proxy: auto-commit, as before the migration | no | no | no |
| Resource docs, Swagger, OpenAPI | no | not needed (read only) | no | not applicable | no |
| DirectLogin, SIWE | yes (through `EndpointHelpers`) | no | no | no | no |

Whether the inline checks give exactly the same answers as the middleware (the same status codes,
the same error messages, the same check order) has not been verified route by route. The Dynamic
Entity checks were ported to match the Lift handlers, and its test suites pin that behaviour.

## 5. What the inconsistency costs, and when to act

**Costs.**
- The checks exist in more than one place and can drift apart. A drift of this kind, between a
  doc's Roles and what the handler enforced, is what let three endpoints lose their Role in the
  migration (fixed in `785f1a4b7`). Those were inside the middleware groups, but the risk is the
  same wherever a check is copied.
- A feature added to the middleware (the endpoint timeout, idempotency, Telemetry timing) does not
  reach these routes unless someone adds it a second time.
- Three endpoints have no ResourceDoc at all, so they appear in no documentation, no API Explorer
  and no MCP listing.

**Triggers for changing a route listed in section 4.** Change one when:
- a defect is traced to its checks differing from the middleware's;
- it needs something only the middleware gives (for example Dynamic Entity requests need the
  endpoint timeout, or Telemetry timing, to diagnose an incident);
- it is being changed for another reason anyway, and the change can be tested with the route's
  existing suites.

**Changes that are safe now because they change no behaviour** (candidates, not scheduled):
- ResourceDocs for SIWE and `openapi.yaml`. Care is needed: in a version file, a ResourceDoc
  with an `http4sPartialFunction` also adds a route under that version's prefix, which is a
  behaviour change. A doc that only documents the existing unversioned path must not register a
  new route by accident.
- A guard test that lists the route groups in `Http4sApp.baseServices` and fails when a new group
  is added outside `ResourceDocMiddleware` without being added to an allowlist. The allowlist
  starts as section 4 and only shrinks. It changes no runtime behaviour and stops the list from
  growing while it stays as it is.

**Changes not to make now:** moving Dynamic Entities or Dynamic Endpoints behind the middleware.
It needs the middleware to accept ResourceDocs that change at runtime, and Dynamic Entities have
deliberate behaviour (anonymous access where the entity allows it, personal entities, access
through Consents) that a move would put at risk. There is no reported defect that it would fix.

**The resource-docs routes belong behind the middleware in principle, and stay outside it for
now.** They were built when ResourceDocs were documentation served beside the API. Now that
ResourceDocs drive routing, authentication, Roles, validation and Telemetry, the endpoints that
publish them are the exception. Moving them (decided against, 2026-09-28) meets four obstacles,
each of which changes what callers receive:

1. The middleware sets `Content-Type: application/json` on every response not already marked as
   JSON (`ensureJsonContentType` in `ResourceDocMiddleware.scala`). `openapi.yaml`, and the
   routes' plain-text error responses, would be relabelled.
2. The docs are declared once, at v1.4.0, but the routes answer under every version prefix, and
   the output depends on the prefix: v4.0.0 and later get the newer shape, v6.0.0 also adds the
   technology field (`Http4sResourceDocs.scala`, `includeTechnologyForPrefix` and the
   `isVersion4OrHigher` choice in `routes`). The middleware matches a doc by the version in the
   path, so it would need a copy of each doc in every version group, or a matcher that ignores
   versions, and either change reaches beyond these routes.
3. They are open by default and need a Role only when `resource_docs_requires_role=true`, checked
   inline (`withOptionalRoleCheck`). A doc can declare a Role conditionally, but the choice is
   fixed when the docs are built at start-up (see the shard 10 note in `CLAUDE.md`).
4. The middleware's endpoint timeout could turn a slow first render of the whole API's
   documentation, with a cold cache, into a 504 where today it eventually succeeds. API Explorer
   and the Portal load these documents on start-up.

What the move would bring (Telemetry timing, API Metrics records, the shared Role check,
enable/disable by operation id) can mostly be added without moving them. Telemetry timing and
counters for their Redis caches were added that way (see `docs/telemetry_conventions.md`,
section 13).

## 6. Related

- `docs/telemetry_conventions.md`, section 13: which of these routes Telemetry does not time.
- `CLAUDE.md`: the migration rules (ResourceDoc registration order, the middleware's handling of
  `BANK_ID`, `ACCOUNT_ID`, `VIEW_ID`, `COUNTERPARTY_ID`, and the gotchas).
- Stale comment: `Http4sDynamicEndpoint.scala`'s header still says an unmatched request falls
  through to "the Lift bridge"; since the bridge was removed it reaches `notFoundCatchAll` (JSON
  404).
