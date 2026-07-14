# TODO: single shared `ensureJsonContentType` (DRY the JSON content-type guarantee)

## Context

OBP guarantees `Content-Type: application/json` on http4s responses in **three independent
places**, each with its own private copy of the same `Content-Type(MediaType.application.json)`
value and/or the same "if not JSON, force JSON" logic:

1. `code/api/util/http4s/ResourceDocMiddleware.scala`
   - `jsonContentType` (~line 52) + `ensureJsonContentType(response)` (~line 561), applied to
     every response from the per-version services (v1.2.1 → v7, UK OB, Berlin Group).
2. `code/api/util/http4s/ErrorResponseConverter.scala`
   - `jsonContentType` (~line 40), set on every error response branch (~lines 122, 156, 171,
     188, 204).
3. `code/api/util/http4s/Http4sSupport.scala` (`EndpointHelpers`)
   - `jsonContentType` (added when fixing the dynamic-entity `text/plain` bug), applied in
     `toJsonOk` / `executeFutureCreated` / `executeFutureWithStatus`.

This duplication is why the `text/plain` bug was possible: the per-version services were
silently saved by `ResourceDocMiddleware.ensureJsonContentType`, while the services that
**bypass** that middleware leaked `text/plain` until `EndpointHelpers` was fixed at the source.

## Why it matters

The services that intentionally bypass `ResourceDocMiddleware` (runtime-mutable / lighter
gates) have **no runtime net** equivalent to the version services — they rely entirely on every
response being built through `EndpointHelpers` or `ErrorResponseConverter`. Today that holds
(verified: `Http4sDynamicEntity` builds no raw responses of its own), and it is now pinned by
content-type assertions in `DynamicEntityTest` (success path + 403 error path). But a future
contributor adding a new response path that skips both helpers would regress silently for:

- `dynamicEntityRoutes`   (`Http4sApp.scala:148`)
- `dynamicEndpointRoutes` (`Http4sApp.scala:149`)
- `DirectLoginRoutes`     (`Http4sApp.scala:150`)
- `AliveCheckRoutes`      (`Http4sApp.scala:151`)

## Proposed work

1. Extract one shared helper (e.g. `Http4sJson.ensureJsonContentType(resp): Response[IO]` and a
   single shared `jsonContentType` val) into a small util, and have all three sites above call it
   instead of their private copies. Pure refactor, no behaviour change.
2. (Optional, decided separately) Give the middleware-bypassing services the same runtime net the
   version services have: wrap the four routes at the `Http4sApp` wiring (lines 148–151) with the
   shared `ensureJsonContentType`, rather than minting a per-service wrapper. This makes the
   source-level fix in `EndpointHelpers`/`ErrorResponseConverter` belt-and-suspenders rather than
   load-bearing for those routes.

## Acceptance

- Exactly one definition of `jsonContentType` and one `ensureJsonContentType` in the codebase.
- `ResourceDocMiddleware`, `ErrorResponseConverter`, and `EndpointHelpers` all delegate to it.
- Existing content-type assertions (`Http4sJsonContentTypeTest`, `DynamicEntityTest` success +
  error path, `Http4sResponseConversionTest`, `AliveCheckRoutesTest`) stay green.

## Origin

Follow-up to the dynamic-entity `text/plain` content-type fix in `Http4sSupport.scala`
(JSON responses were mislabelled because http4s' default `EntityEncoder[String]` sets
`text/plain`; the dynamic-entity service bypasses `ResourceDocMiddleware` so nothing
re-normalised it).
