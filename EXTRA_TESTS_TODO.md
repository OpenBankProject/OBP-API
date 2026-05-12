# Tests to Add — Coverage Gaps from Dependency Bumps

Tracks dependency bumps where compile + the standard 4-suite smoke test passed, but the code paths that actually exercise the bumped library aren't covered. Production deploys against real backends should smoke-test each item before going live.

Test suites currently used as the smoke gate:
- `code.api.v7_0_0.Http4s700RoutesTest`
- `code.api.v7_0_0.Http4s700TransactionTest`
- `code.api.http4sbridge.Http4sLiftBridgePropertyTest`
- `code.api.http4sbridge.Http4sServerIntegrationTest`

Test DB is H2; many integrations are stubbed or absent.

---

## Open coverage gaps

### `mysql-connector-j` 8.0.33 → 8.1.0
- **Untested path:** any code that actually opens a MySQL connection. Tests run on H2.
- **Risk:** Oracle renamed the artifact at this boundary and adopted the "innovation release" cadence. Cross-version protocol regressions are uncommon but possible.
- **Suggested smoke test:** start OBP-API against a MySQL 8 database (matching whatever a typical deployment runs), exercise a few core read/write endpoints, check transactions commit and connection pool cycles.

### `mssql-jdbc` 11.2.0.jre11 → 12.6.4.jre11
- **Untested path:** any code that opens a real MSSQL connection. Tests run on H2.
- **Risk:** major-version bump (11 → 12). Microsoft's JDBC driver is API-stable across major lines, but driver-level protocol/TLS behaviour, prepared-statement caching, and connection-string parsing have all evolved between 11 and 12. The new driver also defaults to encrypted connections (`encrypt=true` is the new default) — pre-12 deploys connecting to an MSSQL server without a trusted TLS cert may now fail unless `encrypt=false` or `trustServerCertificate=true` is set in the connection URL.
- **Suggested smoke test:** open a connection against a real MSSQL instance (matching whatever deployments use), confirm the encryption-default change doesn't break existing connection strings; run a few read/write endpoints; verify connection-pool cycling.

### `msal4j` 1.13.0 → 1.16.2
- **Untested path:** Azure AD integrated authentication for MSSQL. Pulled in via `mssql-jdbc`. No Azure tenant in tests.
- **Risk:** breakage would manifest only when an MSSQL deployment uses `Authentication=ActiveDirectoryIntegrated` / `ActiveDirectoryPassword` / `ActiveDirectoryServicePrincipal`.
- **Suggested smoke test:** if any production deploy uses Azure AD auth for MSSQL, run a real connection attempt against the tenant before promoting the build.

### `bcprov-jdk15on:1.70` excluded (web3j now uses `bcprov-jdk18on:1.78.1`)
- **Untested path:** web3j's signing / keccak / secp256k1 calls. No web3 tests in the suite.
- **Risk:** BouncyCastle keeps the `org.bouncycastle.*` package stable across the `15on` → `18on` rename, so this *should* be transparent. But web3j 4.9.8 was tested against 1.70.
- **Suggested smoke test:** if any deploy uses the OBP web3 / Ethereum endpoints, sign + recover a known message round-trip; verify a signed transaction is byte-identical to what web3j 1.70 + bcprov 1.70 produced.

### `protobuf-java` 3.21.9 → 3.25.5 (and `protobuf-java-util:3.21.1` still on the old line)
- **Untested path:** gRPC traffic. No gRPC integration tests are wired into the project.
- **Risk:** wire-format and reflection APIs are stable, but Descriptor / TextFormat edge cases changed across 3.21 → 3.25.
- **Suggested smoke test:** if any deploy uses the gRPC connector for chat/streaming, run a round-trip RPC against the chat service and verify message framing + field round-trip.

### `snappy-java` 1.1.1.3 → 1.1.10.4
- **Untested path:** snappy compression as used by Avro and the Kafka client. Compression isn't exercised by integration tests.
- **Risk:** 1.1.x API has been stable since 2014 — low. But the JNI native loader changed in 1.1.8 (more permission-strict on some JVMs).
- **Suggested smoke test:** trigger an Avro serialization that uses snappy codec; check the load-and-decompress roundtrip on each deploy OS/JDK combination.

### `log4j-api` / `log4j-core` 2.19.0 → 2.24.3
- **Coverage status:** *adequate*. Tests log heavily through Log4j 2 — appender + formatter paths are well exercised. Listed here for completeness only; no extra test needed.

### `commons-beanutils` 1.9.2 → 1.10.1
- **Untested path:** the deserialization paths the CVE fixes. The `everit json-schema` → `commons-validator` → `commons-beanutils` chain is exercised in JSON schema validation tests, but the specific CVE-2025-48734 input shape isn't reproduced.
- **Risk:** the public bean-introspection API is stable across 1.9 → 1.10. Low.
- **Suggested smoke test:** none required; trust the upstream test suite for this one.

### `postgresql` 42.7.3 → 42.7.7
- **Untested path:** real Postgres connections. Tests run on H2.
- **Risk:** patch-level bump within 42.7.x — JDBC API surface unchanged.
- **Suggested smoke test:** any deploy on Postgres exercises this naturally on first request; no dedicated test needed.

### `commons-lang3` 3.14.0 → 3.18.0
- **Coverage status:** *adequate*. Heavily exercised across the codebase. No extra test needed.

---

## Pending bumps with the same caveat

Listed for future reference — these will likely need entries here when applied:

- `hydra-client` 1.7.0 → 2.x or 25.x (CVE-2026-33504) — ORY rewrote the SDK API at both major boundaries. Used in load-bearing OAuth code (`HydraUtil.scala`, `OAuth2.scala`, `OAuth.scala`, `AuthUser.scala`). Requires a proper SDK migration, not a bump.
- `jackson-databind` 2.12.7.1 → 2.17.x (CVE-2023-35116) — wide blast radius across JSON deserialization
- `protobuf-java-util` 3.21.1 → 3.25.5 (matches main protobuf, deferred pending gRPC bump)
- `oauth2-oidc-sdk` 9.27 → 11.x + `json-smart` 2.4.7 → 2.5.2 (must be coordinated)
- `avro` 1.8.2 → 1.11.x (major; aligns with snappy bump)
- `netty-transport` 4.1.42 → 4.1.118+ (23 CVEs; coordinated with http4s / gRPC)
- `grpc-core` / `grpc-protobuf` 1.48.1 → 1.66+ (6 CVEs; coordinated with protobuf-java-util)

---

## Suggested next steps for closing these gaps

1. **Add a DB matrix test profile.** A Maven profile that swaps H2 for MySQL / Postgres / MSSQL via Testcontainers would catch driver-level regressions for free on the next bump. One-time setup cost.
2. **Add a `WebhookHttpClient` integration test** that runs against a local OkHttp / wiremock server. Would cover the OkHttp + Kotlin stdlib runtime path and any future OkHttp bump.
3. **Add a tiny web3j signing round-trip test** (deterministic input → known signature). Cheap, catches BouncyCastle regressions.
4. **Add a gRPC ping test** if/when the chat-service gRPC path lands. Pre-requisite for any future grpc-core / protobuf-java-util bump.
