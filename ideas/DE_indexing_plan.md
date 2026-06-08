# DE Indexing — Implementation Plan

**Branch:** `DE_indexing`
**Design doc:** [`dynamic_entity_indexing.md`](dynamic_entity_indexing.md) (read first — this plan implements the "Approach A" decision there)
**Status:** Draft plan

## Progress

- **Phase 0 — DONE** (compiles). `indexed`/`index` declaration parsed+validated in `DynamicEntityProvider.scala`; query package `code/api/dynamic/entity/query/` (`QueryModel`, `OperatorMatrix`, `DynamicEntityQueryBackend` seam, `InMemoryQueryExecutor`).
- **Phase 1 — core DONE** (compiles; 15/15 unit tests green in `QuerySpec`). `QueryParamParser` (`obp_filter[FIELD]=OP:VALUE` + `obp_sort_by`/`obp_sort_direction`/`obp_offset`/`obp_limit`), `QueryPlanner` (4-check validation → 400), `DynamicEntityInfo.indexedFields`, wired into `genericGet`/`publicGet`/`communityGet` (in-memory backend).
- **Back-compat DECIDED — Option 1 (additive, no version):** `/obp/dynamic-entity/` is **unversioned and the bare-param filter is documented**, so the legacy contract is preserved **byte-for-byte**:
  - **Legacy** bare-param equality (`?name=A&number=1&number=2` => `name==A && (number==1 || number==2)`, deep-equality for json fields, dotted paths) is **kept** via the restored `filterDynamicObjects` (uses `JsonUtils.isFieldEquals`). Runs in-memory on any field. `locale` and `obp_*` excluded.
  - **New** capabilities are **additive**: `obp_filter[field]=op:value` (operators/range/spatial — require an `indexed` field, else 400), plus `obp_sort_*`/`obp_offset`/`obp_limit`. Composition per request: legacy filter → new operators → sort → paginate.
  - **Transparent acceleration (Phase 3):** both syntaxes compile to one `QueryPlan`; when **all** queried fields are `indexed`, the query routes to the SQL projection (fast, scales to millions) — the *same legacy URL* gets faster with no client change. If any field is unindexed → in-memory (status quo).
  - **No client breaks; G1 passes unchanged.**
- **Future direction (documented now to avoid surprise):** we reserve the right to **enforce the SQL-projection path even for small datasets** — i.e. require hot fields be `indexed` and cap/deprecate the unbounded in-memory legacy scan (returning a clear "declare this field indexed" error instead of risking RAM/OOM). In-memory is fine to ~thousands; large/GeoJSON entities degrade in the low tens-of-thousands and OOM under concurrency, so this enforcement will land before that bites. **Must be added to the public DE docs.**
- **Phase 1 — remaining:** legacy parity confirmed (`DynamicEntityFilterAndBankAccessTest` G1 passes **unchanged**, 5/5). Still to do: document the param contract + future-enforcement in public DE docs.
- **Phase 2 — skeleton DONE** (compiles; `ProjectionNamingSpec` 5/5 green). New package `code/api/dynamic/entity/projection/`:
  - `ProjectionNaming` — deterministic, hashed, length/charset-safe `de_<hash>` / `c_<hash>` identifiers (the only identifier-safety surface; `Fragment.const` injects them).
  - `IndexingCapabilities` — vendor detection (`DoobieUtil.isSqlServer` / `DBUtil.dbUrl`), PostGIS-extension probe, `dynamic_entity.indexing.backend` kill-switch (`inmemory` default | `auto`).
  - `DynamicEntityIndex` Mapper (registry) + `ProjectionState` constants; registered in `Boot.ToSchemify`.
  - `ProjectionDDL` — idempotent Doobie DDL (create table / add column / `CREATE INDEX CONCURRENTLY` / drop) + DE-type→SQL-type mapping. **Skeleton: not yet invoked.**
- **Phase 3 — IN PROGRESS.** Test suite now runs on Postgres (`obp_test_only`, confirmed via boot log `DatabaseInfoJson(PostgreSQL,14.23)`; `db.url` set locally in `test.default.props`, uncommitted). All Phase 3 work is **guarded by `projectionEnabled` (prop default `inmemory` → off)**, so default behaviour is unchanged.
  - **3.4 (core) DONE** — `ProjectionSql` compiles a `QueryPlan` → `SELECT data_id FROM <table> WHERE … ORDER BY … LIMIT ? OFFSET ?`. Operands bind as text + `CAST` to the column type in-SQL (no per-type `Put`, no value-injection); identifiers via `Fragment.const` on hashed names; scalar ops only (spatial = Phase 4). `ProjectionSqlSpec` 3/3 green.
  - **Data-plane + provisioner + backend DONE & proven on Postgres** (`ProjectionDataPlaneIntegrationTest` 1/1 green on PG 14):
    - `ProjectionDb` — committing Doobie transactor over the shared pool, for independent provisioner/backend ops (vs `DoobieUtil`'s `Strategy.void` which shares Lift's txn — that's for the dual-write).
    - `ProjectionCoerce` — JValue→column-text coerce-or-null (mirrors planner rules).
    - `ProjectionStore` — upsert / delete / `readBlobRows` + access `scope` (mirrors `MappedDynamicDataProvider` get-all scoping), identifiers from the live `DynamicData` mapper.
    - `ProjectionProvisioner` — `ensureProvisioned` (create table → add columns → app-side backfill coerce-or-null → `CREATE INDEX` → mark `ready` in registry) + `readyFields`. (Scalar only; spatial = Phase 4. Plain `CREATE INDEX`; `CONCURRENTLY` = prod refinement.)
    - `PostgresProjectionBackend.query` — one JOIN query (projection ⋈ canonical) doing scope + filter + sort + paginate, returning blobs in order.
  - **Live-path wiring DONE & no-regression-verified** (compiles; DynamicEntityFilterAndBankAccessTest + ProjectionDataPlaneIntegrationTest 6/6 green with projection off):
    - `ProjectionDualWrite.onSave/onDelete` hooked into `MappedDynamicDataProvider.saveOrUpdate`/`delete` — txn-unified via `DoobieUtil.runQuery`; guarded + no-op unless `projectionEnabled` and the entity has a `ready` projection.
    - **Read-path backend selection** in `genericGet`: `decideProjection` → projection when `projectionEnabled` + no legacy bare params + every plan field indexed & ready (skips the fetch-all connector call, serves from SQL); indexed-but-not-ready field → **409** (`ProjectionPendingMsg`); else in-memory. (`cats.effect` IORuntime aliased to avoid clashing with the file's EC `global`.)
    - **Provisioning trigger** in `MappedDynamicEntityProvider.createOrUpdate` (`ensureProvisionedFields`, fields passed explicitly since the new definition isn't committed yet) — guarded, best-effort (failure logs, leaves definition saved + queries pending).
    - Default off (`dynamic_entity.indexing.backend=inmemory`) → entire wiring is inert; existing DE behavior unchanged.
  - **Remaining:** projection-**ON** end-to-end test through the HTTP handlers (needs `backend=auto`; create indexed entity → records → query routes to SQL); extend selection to `publicGet`/`communityGet` (different scoping — deferred); legacy bare-param → SQL unification (deferred); `CREATE INDEX CONCURRENTLY` + batched/resumable backfill (prod refinements); spatial = Phase 4.
  - **Test-infra caveat:** the suite now points at Postgres (`test.default.props`, local). Persistent Postgres + OBP's re-schemify/migrations (a view/matview) means **repeated full-suite runs need a clean schema** — `psql "<obp_test_only url>" -c "DROP OWNED BY obp_test_only CASCADE;"` (PostGIS objects are owned by `postgres`, untouched), or `DROP_EXISTING=true ./scripts/create_test_db.sh`. Pure unit tests (`QuerySpec`, `ProjectionSqlSpec`, `ProjectionNamingSpec`) are unaffected and also run on H2.

## Guiding principle

**Dependable, predictable, simple.** This is a banking API also used by EU academic projects — it must not surprise operators. Concretely: **a query is either served by a ready, bounded SQL path or it returns a clear error — never a silent in-memory fallback that could spike RAM on a large entity.** Prefer the simplest design that meets the contract; reject anything we can't serve predictably.

Add declarative **filter / sort / paginate** and **spatial** querying to Dynamic Entity list reads, behind a stable, vendor-neutral API contract (Shape B), with **Approach A** (per-entity typed projection tables, automatic DDL) as the accelerated backend and an **in-memory backend** as the portable floor.

## Grounding — what exists today (from code audit)

| Concern | Current state | File |
|---|---|---|
| Canonical store | `DynamicData` Mapper, `DataJson` text col, keyed by `DynamicEntityName` | `code/dynamicEntity/MapppedDynamicDataProvider.scala` |
| Write path | `saveOrUpdate(...)` → `saveMe()`; `delete_!` | same, ~L202 / L127 |
| Read path | `find()` / `findAll()` with `By()` — **no LIMIT/OFFSET/ORDER BY** | same, L46–172 |
| Filtering | **in-memory only** via `filterDynamicObjects()` | `code/api/dynamic/entity/Http4sDynamicEntity.scala` ~L108 |
| Definition | `DynamicEntity.MetadataJson` (nested JSON); per-field flags already parsed | `code/dynamicEntity/MapppedDynamicEntityProvider.scala`, `DynamicEntityProvider.scala` L76–105, L597+ |
| Field types | `number, integer, boolean, string, DATE_WITH_DAY, json` (+ reference types) | `obp-commons/.../enums/Enumerations.scala` L199–275 |
| Endpoints | http4s only; `genericGet/Post/Put/Patch/Delete`, `publicGet`, `communityGet` | `Http4sDynamicEntity.scala` L231–403 |
| DDL/migration | Lift Schemifier (static models); legacy `migration.Migration` uses Lift `DB.use`+JDBC | `bootstrap/liftweb/Boot.scala`, `code/api/util/migration/Migration.scala` |
| **Raw SQL / DDL library** | **Doobie** (`doobie-core`/`doobie-hikari` 1.0.0-RC4) via `DoobieUtil` — already in use | `obp-api/pom.xml` L450–459, `code/api/util/DoobieTransactor.scala` |
| Spatial | none — greenfield | — |

**Two architectural consequences:**
1. Projection tables are dynamic, so they live **outside Lift Schemifier** — provisioned and queried via **Doobie** (`DoobieUtil`), not Lift Mapper (which can't model runtime tables). `DoobieUtil.runQuery` reuses Lift's request Connection (transaction unification — perfect for dual-write); `runQueryIO` returns `IO` (perfect for the query backend); both share the Hikari pool. **Caveat:** Doobie parameterizes *values* but not *identifiers* — `de_<hash>`/`c_<hash>` go through `Fragment.const`, so identifier safety rests on our hashing, not Doobie.
2. The in-memory backend can reproduce today's exact behaviour, so Phases 0–1 ship the contract + validation **with zero DDL and zero regression**.

## Naming constraints (per project feedback)

- New Mapper subclasses must **not** start with `Mapped`; column objects must **not** be `m`+Uppercase; no `X` suffix on new vendor objects.
- Registry mapper: `DynamicEntityIndex` / provider `DynamicEntityIndexProvider`. Backend objects: `PostgresProjectionBackend`, `InMemoryQueryBackend`, etc.

---

## Phases (each independently shippable)

### Phase 0 — Contract scaffolding (no storage, no DDL)
**Outcome:** the query grammar, the abstract plan, the backend seam, and the `indexed` field declaration exist; behaviour unchanged.

- **0.1** Extend definition parsing (`DynamicEntityProvider.scala`) to read optional per-field `indexed` metadata: `indexed: true`, `index: "scalar"|"spatial"` (default `scalar`), optional `path: "a.b"` (nested scalar). Validate at definition time:
  - `indexed:true` on a `json` field is **only** allowed with `index:"spatial"` (GeoJSON) — else reject.
  - `index:"spatial"` is **only** allowed on a `json` field — else reject.
  - `path` only with a scalar type.
- **0.2** Define the abstract query model (new package `code/api/dynamic/entity/query/`): `QueryPlan(filters: List[Filter], sort: List[SortKey], page: Page)`, `Filter(field, op, value)`, operator enum (`eq, ne, in, lt, gt, le, ge, between, like, within, contains, intersects, dwithin`).
- **0.3** Define `DynamicEntityQueryBackend` trait (Shape B seam): `def query(entity, definition, plan): Future[(List[JObject], Long /*total*/)]` + `def provision(definition): Future[Unit]` (no-op default).
- **0.4** Implement `InMemoryQueryBackend` that fetches via the existing provider and applies filter/sort/paginate in memory (supersedes `filterDynamicObjects`). This is the portable floor and the test oracle.

### Phase 1 — Planner + validation + wire-in (still in-memory)
**Outcome:** real filter/sort/pagination on every endpoint, validated, served by the in-memory backend.

- **1.1** Query-param parser. **Pagination + sort reuse OBP's standard params** (`obp_offset`, `obp_limit`, `obp_sort_by`, `obp_sort_direction`) — reuse the existing `createQueriesByHttpParamsFuture` / `OBPQueryParam` machinery rather than inventing new param names. **Field filtering** uses our own grammar `filter[field][op]=value` (not covered by the standard params). (DE is http4s-native, so params are in the query string.)
- **1.2** The 4-check planner: (a) field declared `indexed`? (b) operator legal for the field's **type** (operator matrix); (c) value coerces to type; (d) sortable type? → clear `400`s naming field+type+operator. Closed allow-list.
- **1.3** **No response envelope, no total count.** List reads keep returning a **bare `JArray`** (no breaking change). Pagination is **offset/limit only** — deliberately *no* `COUNT(*)` / total-pages (offset/limit doesn't need it, and we often can't know the total cheaply). Clients page by advancing `obp_offset`.
- **1.4** Wire planner+backend into `genericGet` / `communityGet` / `publicGet`; delete `filterDynamicObjects`.
- **1.5** Tests: validation 400s (bad field / bad op-for-type / uncoercible value / sort on json), filter/sort/paginate correctness, no-regression on existing DE tests.

### Phase 2 — Registry + provisioner skeleton + capability detection
**Outcome:** the machinery to manage projection tables exists; not yet wired to writes/reads.

- **2.1** `DynamicEntityIndex` Mapper (registry): `entityName, fieldName, fieldType, indexKind, safeTableName, safeColumnName, state, backfillCheckpoint, rowCountExpected, coercionErrors, lastError, provisionerVersion`. Add to `ToSchemify`.
- **2.2** Identifier safety: deterministic `de_<hash>` / `c_<hash>` generation + collision/length handling.
- **2.3** Capability detection at startup: vendor (`DoobieUtil.isSqlServer` / `DBUtil.dbUrl` already exist), **PostGIS extension present?** (`SELECT 1 FROM pg_extension WHERE extname='postgis'`), plus an operator **kill-switch prop** (`dynamic_entity.indexing.backend = auto|inmemory`). Selects the backend once.
- **2.4** Idempotent, resumable DDL runner via **Doobie** (`DoobieUtil.runQueryIO`): `CREATE TABLE IF NOT EXISTS`, `ALTER … ADD COLUMN`, `CREATE INDEX CONCURRENTLY`, reaping `INVALID` indexes. Identifiers via `Fragment.const` on hashed names only. Note: `CONCURRENTLY` must run **outside** a transaction (autocommit connection on the fallback pool).

### Phase 3 — Postgres projection backend, scalar fields
**Outcome:** Approach A live for scalar indexed fields on Postgres.

- **3.1** Field-type → column mapping (string→text, number→numeric, integer→bigint, boolean→boolean, DATE_WITH_DAY→date; reference→text). Coerce-or-null on backfill.
- **3.2** Provisioning lifecycle (state machine): provision column → **dual-write on** → batched resumable backfill (`INSERT … ON CONFLICT`) → `CREATE INDEX CONCURRENTLY` → verify count → flip `ready`.
- **3.3** Dual-write hook in `saveOrUpdate` / `delete` via `DoobieUtil.runQuery` (reuses Lift's request Connection → **same transaction** as the blob write, same commit/rollback; FK `ON DELETE CASCADE`). Uses the already-parsed JObject — no DB trigger.
- **3.4** `PostgresProjectionBackend.query(plan)` → Doobie `ConnectionIO` (`WHERE`/`ORDER BY`/`LIMIT/OFFSET`; keyset option) against `de_<hash>` via `runQueryIO`, returning `data_id`s → hydrate JObjects from canonical blob (blob stays source of truth). Bound params for values; `Fragment.const` for the (hashed) table/column identifiers.
- **3.5** Readiness gating: planner routes to projection only when every touched field is `ready`; otherwise in-memory fallback (configurable) — never partial results.
- **3.6** Tests (gated on Postgres; in-memory equivalence as oracle).

### Phase 4 — Spatial (PostGIS, `geography(4326)`)
**Outcome:** "parcels within 100 km" and within-area predicates.

- **4.1** `spatial` index kind → `geography(…, 4326)` column via `ST_GeomFromGeoJSON(dataJson->'geom')`; **GiST** index; `ST_MakeValid` on invalid geometries.
- **4.2** Spatial predicates in planner: `dwithin` (metres, → `ST_DWithin`), `within`, `contains`, `intersects`. URL: `filter[geom][dwithin]=<lon>,<lat>;100000`. Range/sort N/A for spatial.
- **4.3** Capability gate: spatial only when PostGIS detected; else reject (or in-memory JTS floor — decide).
- **4.4** Tests (need PostGIS in CI — see Risks).

### Phase 5 — Schema evolution, recovery, (optional) SQL Server
- **5.1** Add/remove indexed field; **type change** (add-new-column→swap→retire); field rename (remap+rebackfill); entity delete (drop table).
- **5.2** Rebuild/recovery admin action: drop projection, rebuild from blob.
- **5.3** *(Deferred / optional)* `SqlServerProjectionBackend` (JSON_VALUE/computed columns + SQL Server spatial). Only if a SQL Server deployment needs it.

---

## Decisions

**Resolved:**
1. **Pagination / response shape — DECIDED.** Keep returning a **bare `JArray`** (no envelope, no breaking change). **Offset/limit only**, reusing OBP's `obp_offset` / `obp_limit` (+ `obp_sort_by` / `obp_sort_direction`). **No total count / no page-number** scheme — offset/limit doesn't need it and avoids a `COUNT(*)`.
2. **Non-indexed filter policy — DECIDED.** **Reject with 400** (closed allow-list). No per-query in-memory fallback.
3. **Pending-field query policy — DECIDED.** A query touching a field whose projection is not `ready` (`provisioning`/`backfilling`) **returns a clear error** (e.g. 409 "field not yet queryable; retry shortly"). **Never** an in-memory fallback — predictability over availability, no RAM surprises.
4. **SQL Server — DECIDED: defer.** Build Postgres + in-memory now; add `SqlServerProjectionBackend` (Phase 5.3) only when a real MSSQL deployment needs it.

**Role of the in-memory backend (clarified):** it is the **portable floor for deployments with no projection backend** (non-Postgres, or DDL/indexing disabled), serving the same contract best-effort and honouring `obp_limit`/`obp_offset`. On a projection-capable deployment it is **not** used as a per-query fallback — unservable queries (non-indexed or pending field) return an error instead.

## Risks / notes

- **CI PostGIS**: Phases 3–4 tests need Postgres (and PostGIS for 4). The in-memory backend keeps the bulk of tests vendor-free; gate the projection/spatial tests on capability so they skip cleanly where the extension is absent.
- **Doobie identifier safety**: Doobie binds *values* safely but not *identifiers*. All projection SQL goes through one small helper; table/column names are hashed (`de_<hash>`/`c_<hash>`) and injected via `Fragment.const`; all user-supplied values are bound params. Never interpolate a raw user string into an identifier.
- **Transaction scope**: dual-write uses `DoobieUtil.runQuery` (reuses Lift's request Connection via transaction unification — see `DoobieTransactor.scala` / `RequestScopeConnection`), so it commits/rolls back with the blob write.
- **`CONCURRENTLY` outside a transaction**: `CREATE INDEX CONCURRENTLY` (and reindex) cannot run inside a txn block — provisioner must use an autocommit connection on the fallback pool, not the request connection.
- **ResourceDocs**: the new query params (filter/sort/page) should be documented on the DE endpoints' ResourceDocs.
