# Architecture: http4s and Lift Coexistence

## Current State

OBP-API runs as a **single http4s Ember server** (single process, single port). The application entry point is a Cats Effect `IOApp` (`Http4sServer`). Lift is no longer used as an HTTP server — Jetty and the servlet container have been removed.

Lift still plays two roles:

1. **ORM / Database** — Lift Mapper manages schema creation, migrations, and data access.
2. **Legacy endpoint dispatch** — Older API versions are handled through a bridge (`Http4sLiftWebBridge`) that converts http4s requests into Lift requests, runs them through Lift's dispatch tables, and converts the responses back.

New API versions are implemented as native http4s routes and do not pass through the Lift bridge.

## How It Works

### Entry Point — `Http4sServer.scala`

`Http4sServer` extends `IOApp`. On startup it:

1. Calls `bootstrap.liftweb.Boot().boot()` to initialise Lift Mapper, connectors, and OBP configuration.
2. Parses the configured `hostname` and `dev.port` props (defaults: `127.0.0.1`, `8080`).
3. Starts an http4s Ember server with the application defined in `Http4sApp.httpApp`.

### Priority Routing — `Http4sApp.scala`

`Http4sApp` builds the `HttpApp[IO]` that the Ember server uses. Routes are tried in priority order:

| Priority | Routes | Source |
|----------|--------|--------|
| 1 | v5.0.0 native http4s endpoints | `Http4s500.wrappedRoutesV500Services` |
| 2 | v7.0.0 native http4s endpoints | `Http4s700.wrappedRoutesV700Services` |
| 3 | Berlin Group v2 endpoints | `Http4sBGv2.wrappedRoutes` |
| 4 | **Lift bridge fallback** | `Http4sLiftWebBridge.routes` |

If none of the above match, a 404 is returned. The routing uses `OptionT`-based `orElse` chaining so that each layer can decline a request and pass it to the next.

Standard security headers (Cache-Control, X-Frame-Options, Correlation-Id, etc.) are applied to every response via `Http4sLiftWebBridge.withStandardHeaders`.

### Lift Bridge — `Http4sLiftWebBridge.scala`

The bridge handles any request that is not matched by a native http4s route. It:

1. Reads the http4s request body.
2. Constructs a Lift `Req` object from the http4s `Request[IO]`.
3. Creates a stateless Lift session.
4. Initialises a Lift `S` context and runs `LiftRules.statelessDispatch` / `LiftRules.dispatch` handlers.
5. Handles Lift's `ContinuationException` pattern for async responses (configurable timeout via `http4s.continuation.timeout.ms`, default 60 s).
6. Converts the Lift response back to an http4s `Response[IO]`.

```
HTTP Request
    |
    v
Http4sServer (IOApp / Ember)
    |
    v
Http4sApp.httpApp — priority router + standard headers
    |
    |---> v5.0.0 native routes
    |---> v7.0.0 native routes
    |---> Berlin Group v2 routes
    |---> Http4sLiftWebBridge (fallback)
    |         |
    |         +---> Lift statelessDispatch handlers
    |         +---> Lift dispatch handlers (REST API)
    |
    v
HTTP Response (with standard headers)
```

## What Lift Still Does

| Area | Role |
|------|------|
| **Mapper ORM** | Database schema creation, migrations, and all data access (`MappedBank`, `AuthUser`, etc.) |
| **Boot** | `bootstrap.liftweb.Boot` initialises OBP configuration, connectors, resource docs, and Mapper schemifier |
| **Dispatch tables** | `LiftRules.statelessDispatch` / `LiftRules.dispatch` hold the endpoint definitions for API versions not yet ported to native http4s |
| **JSON utilities** | Some serialisation helpers from `net.liftweb.json` are still in use |

## Why http4s?

- **Non-blocking I/O** — http4s with Cats Effect uses a small, fixed thread pool (typically equal to CPU cores) and suspends fibres on I/O instead of blocking threads. This allows thousands of concurrent requests without thread-pool tuning.
- **Lower memory** — No thread-per-request overhead.
- **Modern Scala ecosystem** — First-class support for Cats Effect, fs2 streaming, and functional programming patterns.
- **No servlet container** — Removes the Jetty dependency and WAR packaging.

## Future Direction

The plan is to gradually port remaining Lift-dispatched endpoints to native http4s routes. As each API version is migrated:

1. New native http4s routes are added to `Http4sApp` at a higher priority than the bridge.
2. The corresponding Lift dispatch registrations can be removed.
3. Once all endpoints are native, the Lift bridge and Lift dispatch dependencies can be removed entirely (Lift Mapper may remain for ORM).

## Running

```sh
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" \
  mvn -pl obp-api -am clean package -DskipTests=true -Dmaven.test.skip=true && \
  java -jar obp-api/target/obp-api.jar
```

The server binds to the `hostname` / `dev.port` values in your props file (defaults: `127.0.0.1:8080`).
