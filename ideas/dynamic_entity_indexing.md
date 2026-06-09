# Dynamic Entity indexing & querying

**Status:** Draft / exploration
**Scope:** OBP-API — making Dynamic Entity (DE) list reads filterable, sortable and paginatable, portably across Postgres and Microsoft SQL Server.

---

## Problem

DE data is stored as a JSON blob (`MappedDynamicData.dataJson`) in a single generic table keyed by `DynamicEntityName`. Reads are **get-by-id** or **get-all** only — there is no field filtering, sorting, pagination, or cross-field querying at the storage layer. Lists are therefore fetched whole and filtered in application memory, which does not scale.

We want generic, declarative querying — `GET /obp/dynamic-entity/<entity>?filter=…&sort=…&page=…` — **without** depending on a specific SQL dialect (OBP runs mostly on Postgres, sometimes on SQL Server), and without per-request RPC to external systems.

## Principle: separate contract from implementation

The query **contract** (filter/sort/paginate declared fields) must be identical on every database. Only the **implementation** of how the DB satisfies it varies. DB-native JSON (Postgres `jsonb`, SQL Server `JSON_VALUE`/`OPENJSON`) is therefore an **optional accelerator behind capability detection**, never a requirement. The portable baseline must work with plain relational SQL through the existing Lift Mapper DSL.

## How a generic endpoint knows what to query: a definition-driven planner

Queryability is **declared in the entity definition**, not guessed. A field is filterable/sortable only if its property schema marks it (e.g. `indexed: true`) with a declared type. The generic DE GET then:

1. Loads the definition for `entityName`.
2. Reads the set of `indexed` fields, their types, and their storage mapping.
3. Validates incoming `filter`/`sort` params against that allow-list — rejects (or in-memory-fallback) anything not declared queryable.
4. Emits portable `WHERE` / `ORDER BY` / `LIMIT/OFFSET` against the mapped storage.

So the planner reads the schema to translate query params → SQL. Queryability is declared, discoverable and validated.

## Storage options (where the value physically lands)

### Option 1 — generic "slot" columns on `MappedDynamicData`
Add a fixed set of typed, indexed slots once: `idx_str_1..k`, `idx_num_1..k`, `idx_ts_1..k`, `idx_bool_1..k`. The definition stores a per-entity **field→slot map** (e.g. `price→idx_num_1`).
- **Write:** the DE write path writes `dataJson` **and** the mapped slot columns in one transaction.
- **Query:** planner rewrites `price < 10` → `idx_num_1 < 10`, using a `(DynamicEntityName, idx_num_1)` composite index. Plain portable SQL.
- **Cost:** cap on #indexed fields per entity; type→slot coercion; slot indexes shared across entities (mitigated by the entity-name-leading composite index).

### Option 2 — EAV side-index table (current lean; needs careful future consideration)
A side table, e.g. `DynamicDataIndex(entityName, dataId, fieldName, numVal, strVal, tsVal, boolVal)`, one row per indexed field per record, with indexes on `(entityName, fieldName, numVal)` etc.
- **Write:** the DE write path upserts index rows alongside the blob (delete+reinsert or upsert per indexed field).
- **Query:** each predicate becomes a join / `EXISTS` against the index table; multiple predicates = multiple joins; sort = join + `ORDER BY`; pagination over the joined result.
- **Pros:** unlimited indexed fields, fully generic, **no DDL** per entity, portable.
- **Cons / open concerns to evaluate:**
  - Multi-predicate queries become **join-heavy**; query-planner complexity and performance need careful design (one self-join per filtered field).
  - **Pagination + sorting** across multiple joined attributes is the tricky part to get correct and fast.
  - Type handling: separate typed value columns vs a single stringified value (affects range/numeric/date comparisons and index usage).
  - Index strategy on the EAV table (composite `(entityName, fieldName, <typedVal>)`).
  - Write amplification: N index-row upserts per record write.
  - Consistency between blob and index rows (same transaction).

### Option 3 — per-entity generated tables (dynamic DDL) — **CHOSEN (as "Approach A")**
At definition time, `CREATE TABLE de_<entity>(...)` with real typed columns, provisioned by **automatic DDL** (see the lifecycle section below).
- **Pros:** cleanest, fastest SQL; best indexes; range/sort/keyset pagination is just ordinary typed SQL with no cast roulette and no joins.
- **Cons (now acceptable):** the original objections were runtime-DDL locking, cross-DB DDL portability, and schema evolution. In our deployment profile these are manageable: **few entities (≈30), some with many rows.** Few entities → no catalog-sprawl cost. The lock blast radius of any DDL is **one entity's table**, not the global blob table, and every heavy operation (backfill, index build, type change) is done **online + batched + resumable** (see lifecycle). Cross-DB DDL is emitted per-backend behind Shape B. **This is the chosen direction.**

## Optional DB-native JSON accelerator

Independently of the above, a native-JSON path can be enabled by **capability detection**:
- Detect vendor at startup.
- Postgres + jsonb enabled → `jsonb` operators (`->>`, `@>`) + GIN/expression indexes.
- SQL Server → `JSON_VALUE` / `OPENJSON`, ideally persisted computed columns that can be indexed.
- Unknown / disabled → portable path (Option 1/2) or in-memory fallback.

Same API contract; conservative portable default; accelerated where available. `jsonb` stays opt-in, never on the critical path. (Storing as `jsonb` vs `nvarchar(max)` is itself vendor-specific DDL, so the canonical store should remain portable text and any jsonb use be additive.)

## Approach A — per-entity projection tables: lifecycle (CHOSEN)

This is the concrete plan for the chosen direction. It fits under Shape B as a `PerEntityTableBackend` with a `provision(definition)` hook.

### Canonical store: JSON stays primary, the table is a derived projection

**Decision: the JSON blob (`MappedDynamicData.dataJson`) remains canonical; `de_<entity>` is a rebuildable typed projection of only the *indexed* fields.** We do **not** (yet) make the per-entity tables the primary data source. Reasons:

- **DEs are schemaless with an *indexed subset*.** The projection only carries fields marked `indexed` — it is not a complete representation of a record, so it cannot be the sole store without either materialising *every* field as a column or adding a JSON overflow column. Keeping the blob canonical sidesteps that.
- **Automatic DDL stays safe because canonical data never moves.** Every operation (create, add field, type change, rename, rebuild) is online and reversible precisely because the source of truth is untouched. The worst-case recovery is always "drop the projection and rebuild from the blob" — never data loss.
- **Smaller blast radius.** `get-by-id` / `get-all` and the generic DE write path keep working against the single blob table unchanged; only the *query* path consults the projection. A DE with **no** indexed fields needs no table and no DDL at all.
- **No big-bang migration.** Existing data already lives in the blob; projections are built incrementally per indexed field.

**When it *would* make sense to flip to tables-as-primary:** only if DEs evolve toward **fully-declared schemas** (every field typed and declared, not a schemaless bag). Then `de_<entity>` could hold typed columns for all fields **plus a JSON overflow column** for any undeclared extras, and the blob could be retired. The trade is real: tables-as-primary means **DDL-before-any-write** (you can't store a record until its table exists), schema evolution now mutates canonical data (losing the "reversible because canonical never moves" safety net), and the get-by-id/get-all machinery must become per-entity-table-aware. Until the schema model changes, **canonical JSON + derived projection wins.** Recorded as an open revisit, not a near-term change.

### Metadata registry

A small registry table (managed by the provisioner, **outside** Lift's schemifier) drives everything, per entity / per indexed field: `entity_name, field_name (JSON key), field_type, safe_table_name, safe_column_name, state, backfill_checkpoint, row_count_expected, coercion_errors, last_error, provisioner_version`. `safe_table_name` / `safe_column_name` are **generated** (`de_<hash>`, `c_<hash>`) — raw user-supplied entity/field names never reach a DDL string (injection + identifier-length/collision safety). The registry maps the safe name back to the JSON key.

### Field-type → column mapping (and the `json` exclusion)

A DE field's declared `type` is one of `DynamicEntityFieldType`: `number`, `integer`, `boolean`, `string`, `DATE_WITH_DAY`, `json` — plus the reference types (string IDs underneath). They fall into two categories:

**Scalar types → clean typed B-tree columns (indexable):**

| DE field type | Projection column | Coercion note |
|---|---|---|
| `string` (+ reference types) | `text` / `varchar` | direct |
| `number` | `numeric` / `double precision` | value may arrive as `JDouble` **or** `JInt` — accept both |
| `integer` | `bigint` | from `JInt` |
| `boolean` | `boolean` / `bit` | value may be `JBool` **or** the strings `"true"`/`"false"` — handle both |
| `DATE_WITH_DAY` | `date` | source is a `yyyy-MM-dd` string → cast to `date` |

These support filter/range/sort/keyset pagination natively. The lifecycle's coerce-or-null policy applies (a non-coercible value → `NULL` + `coercion_errors++`, never fails the backfill).

**`json` type (JObject / JArray) → NOT eligible for a typed column:**
- A whole object/array is not a scalar, so it cannot become a B-tree-orderable column. **Reject `indexed: true` on a `json`-typed field at definition time** with a clear error — an extension of the declared-indexed allow-list the planner already enforces.
- `json` fields stay in the canonical blob and remain fully readable via `get-by-id` / `get-all`; they are simply not filterable/sortable through the projection.
- Querying *into* a `json` field (array-contains-X, nested-key=Y) is a **containment** query — the GIN/`jsonb` equality case, explicitly **outside** Approach A. Route it to the jsonb accelerator or in-memory fallback under Shape B, never to a `de_` column.

**Middle case — a scalar at a known nested path (optional extension):** a scalar buried inside a `json` field can be indexed by declaring a **dotted index path with a scalar type** (e.g. `path: "address.city", type: string`), projected to a `text` column from `dataJson #>> '{address,city}'`. Nested *scalars* are reachable this way; nested *objects/arrays as a whole* are not.

> Only fields marked `indexed` get a column at all. Non-indexed scalars (like `json` fields) stay canonical-only in the blob.
>
> **Spatial carve-out:** the "`json` is not indexable" rule has one exception — a `json` field holding **GeoJSON** *is* indexable, via a **`spatial`** index kind (not a B-tree). See the next subsection.

### Spatial / GIS fields (PostGIS & SQL Server spatial) — primary GIS driver

**Context: the most important `json` data is geospatial** — e.g. land-parcel geometries — and the headline query is *"find parcels within 100 km of a point"* (and similar within-an-area predicates). Spatial is a **fourth query family**, distinct from equality / range-sort / containment: it needs a **spatial index (R-tree / GiST)**, which a B-tree and a GIN both cannot serve. This is the **single strongest justification for Approach A**: performant spatial querying *requires* a real geometry-typed column with a spatial index — unobtainable from EAV, slots, jsonb-as-text, or in-memory-at-scale. The canonical GeoJSON stays in the blob; a materialized geometry column is the derived, rebuildable spatial projection. The standard lifecycle (provision column → backfill → online index build → dual-write) applies unchanged, with a geometry type and spatial index instead of a B-tree.

**A new index *kind*: `spatial`.** A field declared `type: json, index: spatial` (i.e. "this json is a geometry") provisions a geometry column:

| Backend | Column + conversion | Index | Predicates |
|---|---|---|---|
| **Postgres + PostGIS** | `geography(…, 4326)` via `ST_GeomFromGeoJSON(dataJson->'geom')` (native GeoJSON, WGS84) | **GiST** (`CONCURRENTLY` ok) | `ST_DWithin`, `ST_Within`, `ST_Contains`, `ST_Intersects` |
| **SQL Server** | `geography` — **no native GeoJSON parser**; convert GeoJSON→WKT/WKB in app code, then `geography::STGeomFromText(…, 4326)` | spatial index | `.STDistance()`, `.STWithin()`, `.STContains()`, `.STIntersects()` |
| **In-memory floor** | parse GeoJSON with JTS | none (full scan) | JTS predicates — fine for small N, **slow for large parcel sets** |

**`geography(4326)` is the chosen default** (not `geometry`). Rationale: `geography` measures distance on the Earth's curved surface and returns **real metres**, so "within 100 km" is literally `100000` — no conversion. `geometry` treats lon/lat as planar degrees (a degree of longitude is ~111 km at the equator, ~0 at the poles), which makes radius queries wrong and latitude-distorted. SRID **4326 = WGS84** matches GeoJSON's default coordinate system, so source data needs no reprojection.

**The headline query:**
```sql
-- parcels (polygons) within 100 km of a home point
SELECT * FROM de_parcel
WHERE ST_DWithin(
        geom,                                          -- geography(Polygon, 4326)
        ST_MakePoint(home_lon, home_lat)::geography,   -- home point
        100000                                         -- metres
      );
```
- `ST_DWithin` works polygon-to-point (nearest edge within 100 km) and is **index-accelerated** via GiST. Always use `ST_DWithin` for radius queries — a naïve `ST_Distance(...) < 100000` does **not** use the index.

**A new operator class: spatial predicates** (`within`, `contains`, `intersects`, `dwithin`), declared in the contract and validated by the planner. `range`/`sort` stay N/A for spatial fields; spatial fields accept only spatial predicates. Same Shape-B parity rule: each backend satisfies the predicate its own way, in-memory as the universal floor.
```
GET /obp/dynamic-entity/parcel?filter[geom][dwithin]=<lon>,<lat>;100000
GET /obp/dynamic-entity/parcel?filter[geom][within]=<GeoJSON polygon or bbox>
```

**Decisions this surfaces:**
- **PostGIS is an *extension*, not core Postgres.** Capability detection must check "postgres **and** PostGIS installed" (`CREATE EXTENSION postgis`) — finer-grained than the jsonb switch.
- **GeoJSON→geometry conversion is vendor-specific** and lives inside the backend (Postgres native; SQL Server needs an app-side GeoJSON→WKT step). Canonical store stays GeoJSON.
- **Geometry validity** is the coerce-or-null analog: invalid/self-intersecting polygons → `ST_MakeValid`, or null-with-error-count at backfill time.
- **Escape hatch for planar analytics:** because Approach A materializes columns, a *second* projected `geometry` column (e.g. local UTM SRID) can be added alongside the `geography` one when precise **area/overlap** math is needed — distance uses geography, area uses geometry, both indexed, same GeoJSON source.
- **Honest floor caveat:** on a non-PostGIS, non-SQL-Server deployment, "parcels within an area" degrades to a slow in-memory full scan. Acceptable for a rarely-hit fallback; worth stating.

### State machine (per indexed field)

```
        (field marked indexed)
none ─────────────────────────▶ provisioning ──▶ backfilling ──▶ verifying ──▶ ready
                                     │                │              │            │
                                     └────────────────┴──────────────┴────▶ failed (retryable)
ready ──(field unmarked / removed)──▶ retiring ──▶ none
ready ──(type / rename change)──────▶ rebuilding ──▶ backfilling ──▶ … ──▶ ready
```

**Query gating:** the planner routes a `filter`/`sort` to the projection only if **every** field it touches is `ready`. If any is `provisioning`/`backfilling`, config policy decides: reject with "field not yet queryable, retry shortly", or in-memory fallback with a documented perf caveat. Never serve partial/wrong results from a half-built column.

### Lifecycle events

**1. Entity created / first field marked indexed** — ordered, every step idempotent and resumable:
1. Register field as `provisioning`.
2. `CREATE TABLE IF NOT EXISTS de_<entity>` with `data_id` PK + FK to the blob row (`ON DELETE CASCADE`) and one typed column per indexed field. New table = empty = instant DDL, no lock concern.
3. **Turn on dual-write now, before backfill** — the app write path begins upserting into the projection on every create/update. This closes the race: rows written *during* backfill land via dual-write, and backfill upserts, so the two converge.
4. **Backfill historical rows in batches** — walk existing blob rows by PK range, decompose JSON, `INSERT … ON CONFLICT DO UPDATE` (PG) / `MERGE` (SQL Server) in bounded, throttled chunks, persisting `backfill_checkpoint` after each batch (resumable). This is the only step that scales with row count.
5. **Build indexes after backfill, online**: `CREATE INDEX CONCURRENTLY` (PG) / `WITH (ONLINE = ON)` (SQL Server Enterprise). Indexing post-load is far faster and non-blocking. Reap a failed PG `CONCURRENTLY` (`INVALID` index) and retry.
6. **Verify**: projection row count == blob row count for the entity. Mismatch → re-scan or `failed` with reason.
7. **Flip to `ready`** in a single metadata update. Only now does the planner use the projection.

A non-coercible value (e.g. `"price":"free"`) stores `NULL` for that cell and increments `coercion_errors` — one junk row never fails a large backfill.

**2. Steady-state writes** — write path is unchanged in contract (still writes the canonical blob); in the **same transaction**, using the already-parsed object, it upserts the typed columns into `de_<entity>` by `data_id` (so the projection can't diverge from a committed blob write). Do this in app code, **not** a JSON-parsing DB trigger. Deletes cascade via the FK. Cost: one extra single-row upsert per write — negligible.

**3. Add an indexed field to an existing entity** — `ALTER TABLE … ADD COLUMN c_<hash> <type> NULL` (nullable, no default → metadata-only / instant on PG 11+ and SQL Server) → dual-write the new column → batched backfill of just that column → `CREATE INDEX CONCURRENTLY/ONLINE` → verify → `ready`. Lock blast radius is one entity's table.

**4. Unmark / remove an indexed field** — `retiring`: drop the index, then drop the column (lazily — an unused nullable column is harmless and the `DROP COLUMN` can wait for a maintenance window). The JSON key in the blob is untouched.

**5. Field type change** — never `ALTER TYPE` in place. Add a new column of the new type → backfill + index → atomically flip `safe_column_name` old→new in the registry → retire old column lazily. Online and reversible because canonical data never moved.

**6. Field rename (JSON key change)** — the column is keyed by `safe_column_name` mapped to the JSON key in the registry; update the mapping and re-backfill the column from the new key. (Migrating old rows still carrying the old key is a separate DE-data concern.)

**7. Entity deleted** — `DROP TABLE de_<entity>` (optionally after a soft-retire grace period); remove registry rows.

**8. Rebuild / recovery (safety net)** — one admin action: drop the projection table and re-run the create+backfill lifecycle from the canonical blob. Nothing is lost because the blob is the source of truth — this is what makes automatic DDL operationally safe.

### What "automatic DDL" means here

- The provisioner **emits and runs** `CREATE TABLE` / `ALTER` / `CREATE INDEX` itself, triggered by DE-definition changes — no human-authored migration.
- All DDL is **idempotent** (`IF NOT EXISTS`, registry-guarded) and **resumable** (checkpointed) — a process restart mid-provision is safe.
- All DDL uses **generated, sanitised identifiers** — user strings never reach raw DDL.
- Gated by **capability detection + an operator kill-switch prop**; the backend is selected at startup (Shape B). Online/concurrent builds keep it non-blocking on large tables — the only place the "many rows" reality bites.

## Query backend abstraction (Shape B)

The storage options above and the native-JSON accelerator are **implementations**, not interfaces. They sit behind a single swappable seam so the public API never leaks which database (or which storage option) is in use.

**One contract, many backends.** There is exactly **one** endpoint and **one** filter/sort/paginate grammar, identical on every database:

```
GET /obp/dynamic-entity/<entity>?filter[price][lt]=10&sort=-created&page=2&per_page=20
```

Clients never see vendor syntax — no `->>`, `@>`, `JSON_VALUE`, joins, or slot column names appear in any URL, ResourceDoc, Swagger entry, or response. The request is parsed into an abstract query **plan** (validated against the entity's declared `indexed` fields), and only the final compile step is vendor-specific:

```
parse request → planner builds abstract query plan        ← vendor-independent
                 (validated against declared `indexed` fields)
                 │
                 ▼
   QueryBackend.compile(plan)                              ← the ONLY vendor-specific part
                 ├── PostgresJsonbBackend     → ->> / @> / GIN + B-tree expr indexes
                 ├── SqlServerJsonBackend      → JSON_VALUE / OPENJSON / persisted computed cols
                 ├── PortableRelationalBackend → slot columns (Opt 1) or EAV (Opt 2), Lift Mapper DSL
                 └── InMemoryFallbackBackend   → fetch + filter in app (last resort)
```

**Selection is server config, not per-request.** Capability detection picks the backend once at startup from the detected vendor, with an operator override prop (so the portable path can be forced even on Postgres). A client can never choose the backend.

**This is the load-bearing decision — independent of jsonb.** Adopting the backend seam does not commit us to building `PostgresJsonbBackend`. jsonb is just one backend that plugs in later, behind capability detection, *if* measured equality-filter performance justifies it — with no change to the endpoint contract, docs, or tests. Sequence: (1) commit to the backend seam + declared-`indexed` contract now; (2) build the portable backend first (Opt 1 or 2); (3) add the jsonb accelerator later only if needed.

**Contract parity, not just code parity.** The same `filter`/`sort` must return the **same results** on every backend, or this becomes vendor-coupling by the back door. Guard rails:
- The declared `indexed` allow-list **is** the contract — only fields the definition marks queryable are filterable/sortable, identical on every backend (no backend "accidentally" supports a field another can't).
- **One conformance test suite**, run against every backend (Postgres, SQL Server, portable, in-memory), asserting identical responses for identical requests.
- Watch the known drift points: type coercion + null/missing-key handling, text-sort collation/case-sensitivity, and numeric/date cast edge cases (see the range/sort notes above).

## Cross-cutting costs (any option)

- **Backfill:** marking a field `indexed` on an entity that already has rows requires a one-time backfill from existing `dataJson`.
- **Definition changes:** adding/removing an indexed field means provisioning/freeing storage (slot/EAV rows) + backfill or tombstone.
- **Non-indexed filters:** reject with a clear error, or fall back to in-memory with a documented perf caveat — never silently allow arbitrary-field filtering.
- **Write-path consistency:** index storage must be updated in the same transaction as the blob.

## Recommendation / next steps

- **Chosen: Approach A — per-entity projection tables with automatic DDL** (Option 3 revisited). Justified by the deployment profile (≈30 entities, some with many rows): no catalog sprawl, per-entity lock isolation, and online/batched/resumable provisioning. See the lifecycle section.
- **Canonical JSON stays primary; the per-entity table is a derived, rebuildable projection of indexed fields.** Tables-as-primary is an open revisit, only attractive if DEs move to fully-declared schemas.
- Everything sits behind the **Shape B** backend seam, so the portable (slots/EAV) and in-memory paths remain available as fallbacks where DDL is disabled or the vendor is unknown; `jsonb`/SQL-Server-JSON are still possible accelerators but are no longer the primary plan.
- Drive everything from declared `indexed` fields in the entity definition via the query planner above.

## Relationship to field-level permissions

This is **independent** of the field-level read/write permissions + per-field provenance work — that governs *who can read/write* fields; this governs *querying lists*. They compose: a field can be both permission-restricted and indexed.
