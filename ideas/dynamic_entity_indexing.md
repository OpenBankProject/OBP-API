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

### Option 3 — per-entity generated tables (dynamic DDL)
At definition time, `CREATE TABLE de_<entity>(...)` with real typed columns.
- **Pros:** cleanest, fastest SQL; best indexes.
- **Cons:** runtime DDL (locking, migrations), cross-DB DDL portability, schema evolution on definition change. Heaviest/riskiest; likely not worth it.

## Optional DB-native JSON accelerator

Independently of the above, a native-JSON path can be enabled by **capability detection**:
- Detect vendor at startup.
- Postgres + jsonb enabled → `jsonb` operators (`->>`, `@>`) + GIN/expression indexes.
- SQL Server → `JSON_VALUE` / `OPENJSON`, ideally persisted computed columns that can be indexed.
- Unknown / disabled → portable path (Option 1/2) or in-memory fallback.

Same API contract; conservative portable default; accelerated where available. `jsonb` stays opt-in, never on the critical path. (Storing as `jsonb` vs `nvarchar(max)` is itself vendor-specific DDL, so the canonical store should remain portable text and any jsonb use be additive.)

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

- Portable baseline first; `jsonb`/SQL-Server-JSON only as an optional, capability-gated accelerator.
- **Option 2 (EAV)** is the current preference for its flexibility (no DDL, unlimited indexed fields) — to be evaluated carefully against **Option 1 (slots)** on query/pagination performance and planner complexity before committing.
- Drive everything from declared `indexed` fields in the entity definition via the query planner above.

## Relationship to field-level permissions

This is **independent** of the field-level read/write permissions + per-field provenance work — that governs *who can read/write* fields; this governs *querying lists*. They compose: a field can be both permission-restricted and indexed.
