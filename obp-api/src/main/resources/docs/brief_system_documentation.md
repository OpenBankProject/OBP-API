# Open Bank Project — Brief Documentation

_System Architecture, Workflows, Security, and API Reference_

---

## 1) System Architecture Description

**High‑level components**

- **OBP‑API** (Scala): core REST API supporting multiple versions (v1–v5), pluggable data connectors, views/entitlements, payments, metadata, KYC, etc.
- **API Explorer / API Explorer II**: interactive Swagger/OpenAPI front‑ends for testing and discovery.
- **API Manager** (Django): manage consumers, roles, metrics, and selected resources.
- **Connectors**: northbound to OBP APIs; southbound to data sources (core banking, caches). Kafka/RabbitMQ and Akka remote supported for decoupling and scale.
- **Consent & OAuth helpers**: example apps (e.g., OBP‑Hola) to demonstrate OAuth2/OIDC, consents, mTLS/JWS profiles.
- **Persistence**: PostgreSQL (production), H2 (dev); optional caches.
- **Runtime options**: Jetty (war), Docker, Kubernetes.

**Reference deployment views**

- _Monolith + DB_: OBP‑API on Jetty/Tomcat with PostgreSQL.
- _Containerised_: OBP‑API image + Postgres; optional API Explorer/Manager containers.
- _Kubernetes_: OBP‑API Deployment + Service, Postgres Stateful workload, optional Ingress & secrets, externalized config.
- _Decoupled storage_: OBP‑API (stateless) + Akka Remote storage node with DB access; optional Kafka/RabbitMQ between API and core adapters.

**Key integration points**

- **AuthN/AuthZ**: OAuth 1.0a (legacy), OAuth 2.0, OIDC, DirectLogin; role‑based entitlements; fine‑grained _Views_ for account/transaction level access; Consents for OB/PSD2 style access.
- **Standards**: UK OB, Berlin Group, Bahrain OBF mapping via endpoints/consents; JWS signatures, mTLS where required.

---

## 2) Core Workflows (step‑by‑step)

### A. Developer onboarding & first call

1. Register user in sandbox / local.
2. Create application → obtain consumer key.
3. Choose auth method (DirectLogin for dev, OAuth2/OIDC for production patterns).
4. Call `/obp/vX.X.X/root` to confirm version & status; explore endpoints in API Explorer.

### B. Entitlements (roles) & access

1. Admin (or super user during bootstrap) grants roles via `add entitlement` endpoints.
2. Typical roles: `CanCreateEntitlementAtAnyBank`, `CanCreateSandbox`, bank‑scoped operational roles, etc.
3. Apps consume bank/system resources according to entitlements.

### C. Views (fine‑grained account access)

1. Account owner uses default `owner` view.
2. Create custom views (e.g., `accountants`, `auditors`, `tagging-application`).
3. Grant/revoke a user’s access to a view; client calls read endpoints with a specific view.

### D. OAuth2/OIDC + Consent (OB/PSD2 style)

1. TPP/Client registers (with certs if mTLS is used).
2. User authN via authorisation server; client requests consent (scopes/accounts/permissions).
3. Consent resource & access token are returned (optionally JWS‑signed, certificate‑bound).
4. Client calls accounts/balances/transactions/payments with proof (mTLS/JWS), consent id, and token.

### E. Transaction Requests (PIS)

1. Create payment request with account + amount + creditor.
2. Strong customer authentication (SCA) if required.
3. Execute/submit and poll status.

---

## 3) Diagrams (text sketches)

**High-Level System Architecture**

See the detailed architecture diagram in [introductory_system_documentation.md](introductory_system_documentation.md#21-high-level-architecture) (Section 2.1).

**Views & Entitlements**

```
User ──(has roles/entitlements)──► Bank/System actions
  │
  └─(granted view)──► Account/Transaction subset (e.g., accountants)
```

---

## 4) Component Logic

- **Views**: declarative access lists (what fields/transactions are visible, what actions permitted) bound to an account. Grants are user↔view.
- **Entitlements**: role assignments at _system_ or _bank_ scope; govern management operations (create banks, grant roles, etc.).
- **Connectors**: adapter pattern; map OBP domain to underlying core data sources. Kafka/RabbitMQ optional for async decoupling; Akka Remote to separate API and DB hosts.
- **Security**: OAuth2/OIDC (with JWKS), optional mTLS + certificate‑bound tokens; JWS for request/response signing as required by OB/FAPI profiles.

---

## 5) Installation, Configuration & Updates

### Option A — Quick local & development (IntelliJ / `mvn`)

- Clone `OBP-API` → open in IntelliJ (Scala/Java toolchain).
- Create `default.props` (dev) and choose connector (`mapped` for demo) and DB (H2 or Postgres).
- `mvn package` → produce `.war`; run with Jetty or use IntelliJ runner.

### Option B — Docker (recommended for evaluation)

- Pull `openbankproject/obp-api` image.
- Provide config via env vars: prefix `OBP_`, replace `.` with `_`, uppercase (e.g., `openid_connect.enabled=true` → `OBP_OPENID_CONNECT_ENABLED=true`).
- Wire Postgres; expose 8080.

### Option C — Kubernetes

- Apply manifest (`Deployment`, `Service`, `ConfigMap`/`Secret` for props, `StatefulSet` for Postgres, optional `Ingress`).
- Externalise DB creds, JWT/keystore, and OAuth endpoints; configure probes.

### Databases

- **Dev**: H2 (enable web console if needed).
- **Prod**: PostgreSQL recommended; set SSL if required; grant schema/table privileges.\
  Any JDBC-compliant DB is supported (e.g. MS SQL, Oracle DB, etc.)

### Updating

- Track OBP‑API tags/releases; OBP supports multiple API versions simultaneously. For minor updates, roll forward container with readiness checks; for major schema changes, follow release notes and backup DB.

---

## 6) Access Control & Security Mechanisms

- **Authentication**: OAuth 1.0a (legacy), OAuth 2.0, OIDC, DirectLogin (automation/dev only).
- **Authorisation**: Role‑based **Entitlements** (system/bank scope) + account‑level **Views**.
- **Consents**: OB/PSD2 style consent objects with permissions/scopes, linked to tokens.
- **Crypto**: JWS request/response signing where profiles demand; JWKS for key discovery.
- **mTLS / PoP**: Certificate‑bound tokens for higher assurance profiles (FAPI/UK OB), TLS client auth at gateway.
- **Secrets**: JKS keystores for SSL and encrypted props values.

---

## 7) Monitoring, Logging & Troubleshooting

**Logging**

- Copy `logback.xml.example` to `logback.xml`; adjust levels (TRACE/DEBUG/INFO) per environment.
- In Docker/K8s, logs go to stdout/stderr → aggregate with your stack (e.g., Loki/Promtail, EFK).

**Health & metrics**

- K8s liveness/readiness probes on OBP‑API root/version or lightweight GET; external synthetic checks via API Explorer smoke tests.

**Troubleshooting checklist**

- **Auth failures**: verify JWKS URL reachability, clock skew, audience/scope, mTLS cert chain.
- **Permissions**: confirm entitlements vs. views; bootstrap `super_admin_user_ids` only for initial admin then remove.
- **DB issues**: check Postgres grants; enable SSL and import server cert into JVM truststore if needed.
- **Connector errors**: raise logging for connector package; verify message bus (Kafka/RabbitMQ) SSL settings if enabled.

---

## 8) Service Documentation (operators’ quick sheet)

**Day‑1**

- Provision Postgres; create db/user; load props via Secret/ConfigMap; start OBP‑API.
- Create first admin: set `super_admin_user_ids`, login, grant `CanCreateEntitlementAtAnyBank`, then remove bootstrap prop.

**Day‑2**

- Rotate keys (JKS) and tokens; manage roles & views via API Manager; enable audit trails.
- Backups: nightly Postgres dump + config snapshot; test restore monthly.
- Upgrades: blue/green or rolling on K8s; verify `/root` endpoints across versions.

**Incident runbook (snippets)**

- Increase log level via `logback.xml` reload or environment toggle; capture thread dumps.
- Check API error payloads for `error_code` and `bank_id` context; correlate with gateway logs.
- For SSL issues to DB or brokers, use `SSLPoke` and `openssl s_client` to diagnose.

---

## 9) Quick Commands & Config Snippets

```bash
docker run -p 8080:8080 \
  -e OBP_DB_DRIVER=org.postgresql.Driver \
  -e OBP_DB_URL='jdbc:postgresql://db:5432/obpdb?user=obp&password=******&ssl=true' \
  -e OBP_OPENID_CONNECT_ENABLED=true \
  openbankproject/obp-api:latest
```

```bash
kubectl apply -f obpapi_k8s.yaml   # Deployment, Service, Postgres PVC
```

```properties
JAVA_OPTIONS="-Drun.mode=production -Xmx768m -Dobp.resource.dir=$JETTY_HOME/resources -Dprops.resource.dir=$JETTY_HOME/resources"
```

```sql
GRANT USAGE, CREATE ON SCHEMA public TO obp_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO oobp_userbp;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO obobp_userp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO obp_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO obp_user;
```

---

## 10) Pointers to Further Docs (by topic)

- API Explorer & endpoints (roles, views, consents)
- OBP‑API README (install, DB, Jetty, logging, production options)
- API Manager (roles/metrics)
- OBP‑Hola (OAuth2/mTLS/JWS consent flows)
- Docker images & tags
- K8s quickstart manifests
- ReadTheDocs guide (auth methods, connectors, concepts)

---

© TESOBE GmbH 2025 AGPL V3
