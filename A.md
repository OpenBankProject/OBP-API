# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

OBP-API (Open Bank Project API) is a Scala-based open-source banking API platform. It is dual-licensed under AGPL V3 and commercial licenses from TESOBE GmbH. The project is undergoing a migration from Lift/Jetty to http4s, with v7.0.0 endpoints using native http4s and older versions (v1.2 through v6.0.0) still using Lift, bridged through `Http4sLiftWebBridge`.

## Build System

Maven 3 is the primary build tool. There is also a `build.sbt` for IDE support (Metals/ZED), but **Maven is used for all builds and tests**.

Key versions: Scala 2.12.20, Java 11, Lift 3.5.0, http4s 0.23.30, Pekko 1.1.2.

### Common Commands

```sh
# Compile (must build obp-commons first)
mvn install -pl .,obp-commons && mvn compile -pl obp-api

# Run with Jetty (development)
mvn install -pl .,obp-commons && mvn jetty:run -pl obp-api

# Run with http4s server (production-like)
MAVEN_OPTS="-Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G" mvn -pl obp-http4s-runner -am clean package -DskipTests=true -Dmaven.test.skip=true && \
java -jar obp-http4s-runner/target/obp-http4s-runner.jar

# Run all tests
export MAVEN_OPTS="-Xss128m -Xms3G -Xmx6G -XX:MaxMetaspaceSize=2G --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
mvn clean test

# Run a single test suite
mvn -DwildcardSuites=code.api.directloginTest test

# Run all tests with the helper script (includes reporting)
./run_all_tests.sh
```

### Props Configuration

Runtime configuration uses `.props` files in `obp-api/src/main/resources/props/`:
- `default.props` — development (copy from `sample.props.template`)
- `test.default.props` — tests (copy from `test.default.props.template`), must set `connector=mapped`
- `production.default.props` — production

The `hostname` property is **required** for the API to start. The `connector` property selects the backend (e.g. `mapped`, `star`, `rest_vMar2019`).

## Module Structure

The project has three Maven modules:

- **obp-commons** — Shared models, utilities, and commons used across modules. Located in `obp-commons/`.
- **obp-api** — The main API server. All endpoint definitions, connectors, authentication, and business logic. Located in `obp-api/`.
- **obp-http4s-runner** — Fat-JAR packaging for running as a standalone http4s server (no Jetty). Located in `obp-http4s-runner/`.

## Architecture

### Dual Server Stack (Lift + http4s)

The system runs both Lift and http4s simultaneously. The unified entry point is `Http4sApp` (`code.api.util.http4s.Http4sApp`), which routes requests with this priority:

1. **v5.0.0 native http4s routes** (`Http4s500`)
2. **v7.0.0 native http4s routes** (`Http4s700`)
3. **Berlin Group v2 http4s routes** (`Http4sBGv2`)
4. **Http4sLiftWebBridge** — translates http4s requests into Lift `Req` objects, dispatches through `LiftRules`, and converts `LiftResponse` back to http4s. This is how all older API versions (v1.2 through v6.0.0) are served.

### API Version Pattern (Lift-based, v1.2–v6.0.0)

Each version has a directory under `obp-api/src/main/scala/code/api/vX_Y_Z/` containing:
- `APIMethodsXYZ.scala` — Trait with lazy val `OBPEndpoint` partial functions and `ResourceDoc` entries
- `JSONFactoryX.Y.Z.scala` — JSON serialization for that version's response types
- `OBPAPIX_Y_Z.scala` — Wires endpoints together, extends `OBPRestHelper`, and chains previous version routes

Versions are cumulative: `OBPAPI4_0_0` includes all routes from v1.3 through v4.0.0. Each `OBPAPIX_Y_Z` object calls `registerRoutes()` to register with Lift's dispatch.

### API Version Pattern (http4s-based, v5.0.0+, v7.0.0)

Native http4s endpoints are in files like `Http4s700.scala`:
- Endpoints are `HttpRoutes[IO]` values using http4s DSL
- `ResourceDoc` entries are registered in an `ArrayBuffer[ResourceDoc]`
- `ResourceDocMiddleware` wraps routes with automatic validation: authentication, role authorization, bank/account/view validation — all driven by `ResourceDoc` metadata
- Use `EndpointHelpers.executeAndRespond(req)`, `EndpointHelpers.withUser(req)`, `EndpointHelpers.withUserAndBank(req)` to reduce boilerplate
- Validated entities (user, bank, account, view) are stored in `CallContext` via http4s request attributes

### Connector System

`Connector` (`code.bankconnectors.Connector`) is a trait abstraction over backend data sources. Key implementations:
- `LocalMappedConnector` — Direct JDBC via Lift Mapper ORM (connector name: `mapped`)
- `RestConnector_vMar2019` — Remote REST calls
- `AkkaConnector_vDec2018` — Akka remoting
- `RabbitMQConnector_vOct2024` — RabbitMQ messaging
- `StoredProcedureConnector_vDec2019` — Database stored procedures
- `StarConnector` — Meta-connector that delegates to multiple connectors based on method routing

The active connector is selected by the `connector` prop. The `Connector.connector.vend` pattern is used throughout to access the current connector instance.

### Authentication

Multiple auth mechanisms coexist, all resolved through `APIUtil.authenticatedAccess()`:
- **DirectLogin** — Token-based, header: `DirectLogin token=...`
- **OAuth 2.0 / OpenID Connect** — JWT validation via JWKS
- **Gateway Login** — For trusted gateway proxies
- **DAuth** — Distributed auth

### ResourceDoc

Every endpoint has a `ResourceDoc` entry that describes its HTTP method, path, summary, request/response bodies, error codes, required roles, and API tags. `ResourceDoc` drives:
- Auto-generated API documentation (`/resource-docs/VERSION/obp`)
- OpenAPI/Swagger spec generation
- The http4s `ResourceDocMiddleware` validation chain
- Frozen API tests (`FrozenClassTest`)

### Frozen APIs

API versions marked STABLE have their metadata frozen. Changing request/response bodies, adding/removing endpoints, or changing `versionStatus` will cause `FrozenClassTest` to fail. To update frozen metadata after an intentional change, run `FrozenClassUtil` to regenerate `obp-api/src/test/resources/frozen_type_meta_data`.

## Test Infrastructure

Tests use **ScalaTest** (FeatureSpec style) with Maven's scalatest-maven-plugin. The embedded test server uses Jetty on port 8018 (configured in `test.default.props`).

Key test base classes in `obp-api/src/test/scala/code/setup/`:
- `ServerSetup` — Base trait, starts `TestServer`, provides `baseRequest`, resets DB before each test class
- `ServerSetupWithTestData` — Extends `ServerSetup` with fake banks, accounts, transactions, and test users
- `DefaultUsers` — Creates test users with DirectLogin tokens (`token1`, `token2`, etc.)

For http4s-specific tests, `Http4sTestServer` (`code.Http4sTestServer`) provides a separate http4s server instance.

Test naming convention: tag tests with API version and endpoint name using ScalaTest `Tag`:
```scala
object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.checkFundsAvailable))
```

## Coding Conventions

- **UTF-8 encoding** for all source files. No emojis in source code (only in `.md` files).
- **camelCase** for variable names (e.g. `myUrl` not `myURL`) — enables automatic camelCase to snake_case conversion for JSON output.
- **Endpoint check order**: 1) `authorizedAccess`, 2) role/entitlement checks, 3) business constraints. Never leak resource existence info to unauthorized users.
- **Git commit messages**: Use prefixes: `bugfix/`, `feature/`, `docfix/`, `refactor/`, `performance/`, `test/`, `enhancement/`, `security/`. Tag with `api_change` if endpoints change.
- **NewStyle functions**: Use `NewStyle.function.*` for business logic calls in endpoints. These return `Future` and integrate with `CallContext`.
- **Error messages**: Defined in `code.api.util.ErrorMessages`. Use the constant (e.g. `UserNotLoggedIn`, `BankNotFound`) rather than raw strings.

## Database

Default test database is H2 (in-memory). Production typically uses PostgreSQL. Also supports MS SQL Server.
