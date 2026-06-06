# Alternatives to Lift Mapper

## The problem is maintenance, not capability

Lift Mapper is a capable, battle-tested ORM. After a decade and ~130 entities it does what OBP needs and does it well: typed fields, a clean query DSL (`By` / `OrderBy` / `findAll` / `count`), automatic DDL via `Schemifier`, per-field validation, and lifecycle hooks (`beforeSave` / `CreatedUpdated`). **The API is not the problem, and nobody should pretend it is.**

The problem is upstream cadence. Lift is sparsely maintained and effectively frozen — OBP is pinned to `lift-mapper 3.5.0` — and that staleness has two concrete downstream costs:

1. **Stale transitive dependencies we cannot drop** — most visibly the old `lift-webkit` web framework, which OBP no longer uses a line of but still ships (see the next section).
2. **A latent Scala 3 blocker** — Mapper's implicit-heavy `MappedField` machinery is non-trivial to port (see "The forcing function isn't here yet").

This document is the menu of responses to that staleness, cheapest to heaviest: **keep Mapper and maintain it ourselves** (Options A–D), **narrower or orthogonal moves** that target just the stale dependency or the schema layer rather than the whole ORM (Options E–I), or **exit it entirely** to Doobie or another ORM (the migration playbook that makes up the bulk of this file). It does **not** assume migration is the answer — read the Conclusion first. Today's recommendation is *design around the constraint*, not *migrate*.

---

## Dependency-surface coupling: `lift-webkit` is hostage to Mapper (verified 2026-06-05)

This is the clearest concrete symptom of the staleness problem. The Lift Web teardown (PR #2828) removed every `net.liftweb.http` import from OBP source (128 files → 0) and deleted `Http4sLiftWebBridge`. That shrank the *reachable* attack surface, but it did **not** shrink the dependency/CVE-scan surface: `lift-webkit_2.12-3.5.0.jar` (the jar that *contains* `net.liftweb.http`) is still on the classpath, pulled in transitively:

```
obp-api → lift-mapper → lift-db   → lift-webkit   (3.5.0)
obp-api → lift-mapper → lift-proto → lift-webkit   (duplicate path)
```

**Tested the cheap escape hatch and it does not work.** Adding an `<exclusion>` for `lift-webkit` on the `lift-mapper` dependency cleanly removes the jar from `dependency:tree`, but compilation then fails — **Lift Mapper's own public API is welded to lift-webkit types**, so the compiler needs them just to typecheck Mapper classes OBP already uses:

| Mapper symbol | lift-webkit type it requires | OBP site that fails to compile |
|---|---|---|
| `net.liftweb.mapper.MapperRules` | extends `net.liftweb.http.Factory` | `bootstrap/liftweb/Boot.scala:263` |
| `BaseMappedField.asJsExp` | returns `net.liftweb.http.js.JsExp` | `code/group/Group.scala:111` (every `MappedField` subclass) |
| `MappedPassword.asJsExp` | returns `net.liftweb.http.js.JsExp` | `code/model/dataAccess/AuthUser.scala:387` |

This is a compile-time coupling, not a reflective/runtime one — there is no surgical exclusion that survives. **`lift-webkit` cannot leave the classpath until `lift-mapper` itself leaves**, i.e. until the data-access migration below completes *and* the Schemifier/`ToSchemify` schema layer is also off Mapper (the Mapper class declarations are the last thing to go — see "What 'done' looks like" + the schema-layer follow-on).

**Implication for prioritisation.** This is a second, independent forcing function alongside Scala 3 (§"The forcing function isn't here yet"): dropping an old, sparsely-maintained web framework (`lift-webkit 3.5.0`) from the dependency manifest and from CVE-scanner output is only achievable via the full Mapper exit. Worth weighing if/when a security-driven dependency-reduction goal appears — but on its own it does not change the recommendation below, since `lift-webkit` carries no currently-flagged CVE (absent from `dependency-check-findings-2026-05-14.md`); the value is latent-surface and supply-chain hygiene, not an open finding.

---

# Option: full exit to Doobie

The rest of this document is the detailed playbook for one response to the staleness problem — replacing Mapper as the *data-access* layer with Doobie. It is the heaviest option on the menu; weigh it against "keep and maintain" (Options A–D) and the Conclusion before committing.

## Principle

Eliminate Lift Mapper as a data-access layer. All CRUD, queries, and reads/writes move to Doobie. Lift Mapper stays only for what is explicitly out of scope (see below) until a separate workstream removes it.

This is the data-access counterpart to `LIFT_HTTP4S_MIGRATION.md`. The two migrations are independent — an http4s endpoint can call Doobie or Mapper, and a Lift endpoint can call either — but the end state is **no `net.liftweb.mapper.*` import outside the schema/migration layer**.

API version numbers are unaffected: framework migrations happen in-place. A Mapper → Doobie swap inside `MappedFooProvider` does not justify a version bump unless the response shape changes.

---

## Scope

**In scope — migrate to Doobie**

- All CRUD: `findAll`, `find(By(...))`, `count(By(...))`, `bulkDelete_!!`, `delete_!`, `saveMe`, `create.foo(...).save`, etc.
- All raw-SQL queries currently using `DB.runQuery`, `DBUtil.runQuery`, `DB.use(...) { conn => ... }` for application logic.
- All `Future { tryo { Foo.findAll(...) } }` Provider methods.
- Bulk-load patterns that today do N+1 Mapper lookups (these become single JOINs in Doobie — see `DoobieUserQueries.scala` for the canonical example).
- Connector implementations that read/write Mapper entities directly (`LocalMappedConnector` and subclasses).

**Out of scope — stays on Lift**

- `Schemifier.schemify(...)` — table creation, column add/drop, index sync. Stays in `Boot.scala` and `MockedRabbitMqAdapter.scala`.
- `ToSchemify.models` — the canonical entity list driving Schemifier. Stays as-is.
- Per-table schema-mutation migrations in `code/api/util/migration/MigrationOf*.scala` that use `DB.use { conn => Schemifier.infoF }` to add columns / drop indexes. These are schema operations, not data access.
- `MappedUUID`, `MappedString`, `MappedLong`, and other Mapper field types referenced by entity definitions retained for Schemifier.

The Mapper case classes themselves (`class Foo extends LongKeyedMapper[Foo]`) stay until **both** (a) data access has fully moved to Doobie and (b) Schemifier is replaced. This migration only removes the **runtime use** of Mapper as a query/CRUD API — the class definitions remain as schema descriptors consumed by `Schemifier`.

---

## Current State (2026-05-18)

| Area | State |
|---|---|
| Doobie dependency | Present in `obp-api/pom.xml` (`doobie-core`, `doobie-hikari`) |
| Transactor | `code.api.util.DoobieUtil` — shares Lift's HikariCP pool and unifies with Lift's per-request transaction (`Transactor.fromConnection` + `Strategy.void`) |
| Doobie call sites | ~10 files: `DoobieMetricsQueries`, `DoobieInvestigationQueries`, `DoobieUserQueries`, `DoobieConsentQueries`, `DoobieChatMessageQueries`, `DoobieAccountAccessViewQueries`, `DoobieQueries`, `MetricBatchWriter`, `ConnectorMetricBatchWriter`, `StatusPage` |
| Mapper entities in `ToSchemify.models` | ~130 |
| `Mapped*Provider.scala` files (Mapper-backed providers) | ~99 |
| `extends LongKeyedMapper` declarations | ~24 source files, 27 unique classes |
| Files importing `net.liftweb.mapper` | ~250 |
| Files using `DB.use { conn => ... }` outside Schemifier-style migrations | ~10 |

Doobie is already the chosen target — this migration scales the existing pattern, it does not introduce a new technology.

---

## Target Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Application code (endpoints, connectors, services)        │
│                                                            │
│      ▼ calls Provider trait method                         │
│                                                            │
│  FooProvider trait (no change to signature)                │
│                                                            │
│      ▼ implementation                                      │
│                                                            │
│  DoobieFooProvider                                         │
│      uses DoobieFooQueries (ConnectionIO[A] definitions)   │
│      uses DoobieUtil.runQuery / runQueryAsync              │
│                                                            │
│      ▼ JDBC                                                │
│                                                            │
│  HikariCP pool (single, shared) ──── PostgreSQL/SQL Server │
└────────────────────────────────────────────────────────────┘

Schemifier (Lift Mapper) reads ToSchemify.models on boot and
ensures tables/columns/indexes exist. It never serves a runtime
request after boot completes.
```

**Two key invariants**

1. **Connection unification preserved.** A Doobie call from inside a Lift-served request (during the bridge phase) must use the same `Connection` Lift is already holding for that request — otherwise a write made through Mapper earlier in the request is invisible to the Doobie read that follows. `DoobieUtil.runQuery` already handles this via `liftCurrentConnection` peek. Do not bypass it.
2. **Provider traits do not change.** The `XxxProvider` trait in the domain layer is the seam. `MappedFooProvider` and `DoobieFooProvider` both implement the same trait. Swapping the binding is a one-line change in `RemotedataActors` / `ToSchemify`'s vendor wiring — no callers change. This means migration can happen one provider at a time without coordinated rewrites.

---

## Per-Entity Migration Playbook

For each Mapper-backed entity `Foo` with provider `MappedFooProvider`:

### Step 1 — Inventory the Provider trait

Locate the trait (usually `FooProvider` in the same package). List every method's signature. Note which methods return `Future[Box[List[A]]]` vs `Box[A]` vs `Future[A]` — Doobie returns must match exactly.

### Step 2 — Map column names

Mapper uses Scala field names; the DB columns are derived via Lift's naming convention (lowercased, no underscore for camelCase by default, but per-column overrides exist via `dbColumnName` / `MappedField` overrides). Get the actual column names by either:

- Reading the Mapper class and checking each field for `override def dbColumnName`.
- Running `psql \d <tablename>` against a populated dev DB (most reliable).

Record these in a comment block at the top of the new `DoobieFooQueries.scala` so future readers don't repeat the lookup.

### Step 3 — Write `DoobieFooQueries.scala`

Pattern (see `obp-api/src/main/scala/code/users/DoobieUserQueries.scala`):

```scala
package code.foo

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

object DoobieFooQueries {

  case class FooRow(
    fooId: String,
    name: Option[String],
    createdAt: java.sql.Timestamp
  )

  def findByFooId(fooId: String): ConnectionIO[Option[FooRow]] =
    sql"""SELECT foo_id, name, created_at
          FROM foo
          WHERE foo_id = $fooId""".query[FooRow].option

  def findAllByOwner(ownerId: String): ConnectionIO[List[FooRow]] =
    sql"""SELECT foo_id, name, created_at
          FROM foo
          WHERE owner_id = $ownerId
          ORDER BY created_at DESC""".query[FooRow].to[List]

  def insert(row: FooRow): ConnectionIO[Int] =
    sql"""INSERT INTO foo (foo_id, name, created_at)
          VALUES (${row.fooId}, ${row.name}, ${row.createdAt})""".update.run

  def deleteByFooId(fooId: String): ConnectionIO[Int] =
    sql"""DELETE FROM foo WHERE foo_id = $fooId""".update.run
}
```

Keep query objects pure: `ConnectionIO[A]` only, no `unsafeRunSync`, no `Future`. Composition (joins, transactions) happens by combining `ConnectionIO`s with `flatMap`. Execution is the caller's job.

### Step 4 — Write `DoobieFooProvider.scala`

```scala
package code.foo

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Failure, Full}
import scala.concurrent.Future

object DoobieFooProvider extends FooProvider {

  override def getFoo(fooId: String): Future[Box[Foo]] = Future {
    try Box(DoobieUtil.runQuery(DoobieFooQueries.findByFooId(fooId)).map(rowToFoo))
    catch { case t: Throwable => Failure(t.getMessage, Full(t), Empty) }
  }

  override def getAllFooForOwner(ownerId: String): Future[Box[List[Foo]]] = Future {
    try Full(DoobieUtil.runQuery(DoobieFooQueries.findAllByOwner(ownerId)).map(rowToFoo))
    catch { case t: Throwable => Failure(t.getMessage, Full(t), Empty) }
  }

  private def rowToFoo(r: DoobieFooQueries.FooRow): Foo = ???
}
```

Match the existing `MappedFooProvider`'s error shape exactly. If the old provider returned `tryo(...)` → `Empty` on null / `Failure` on exception, the Doobie provider must do the same. Tests that match on `Box` variants will catch you.

### Step 5 — Switch the binding

Find the line that picks the implementation. There are two patterns in the codebase:

- **`vend` constant in the provider trait companion object** — `object FooProvider { val foo: FooProvider = MappedFooProvider }`. Change the assignment.
- **Akka-remoted wiring** (`RemotedataActors`) — actor selects based on `props.RemoteDatabaseEnabled`. Change the local-side binding.

Leave `MappedFooProvider` in the codebase for one release as a fallback option — gate the switch on a prop if the change is risky: `getPropsAsBoolValue("provider.foo.doobie", true)`. Remove the prop and the Mapper provider in the next clean-up PR.

### Step 6 — Tests

A Doobie provider must pass the same provider-level tests as the Mapper one. The integration tests (server + DB) for endpoints calling this provider must continue to pass unchanged — that's the contract.

If the only existing coverage is integration tests, add a focused suite for the new provider before deleting the Mapper one. The Doobie-backed providers already in the tree have unit-test counterparts (`DoobieUserQueriesTest`, etc.) — copy the structure.

### Step 7 — Removal (final pass per entity)

Once the Doobie provider has been the default for one full release with no rollbacks:

- Delete `MappedFooProvider.scala`.
- Remove the prop gate.
- **Keep** the `Foo` Mapper class in its file — `ToSchemify.models` still references it. The class becomes a pure schema descriptor; no application code calls it.

---

## Cross-Cutting Concerns

### Transaction unification

`DoobieUtil.runQuery` peeks `DB.currentConnection` and, when a Lift request is in flight, runs the Doobie query on the same `Connection`. This is non-negotiable while the bridge serves any traffic: a request that does `MappedFoo.save` followed by a Doobie read of the same row would otherwise see a stale value (the Mapper write is still in Lift's per-request transaction; the Doobie read on a different pool connection wouldn't see it).

Migration order must respect this: a Provider that *writes* moves to Doobie at the same time as its *readers*, not before. Otherwise mid-request you have Doobie writes invisible to subsequent Mapper reads on a different connection.

Background tasks and schedulers do not have a Lift request context — `DoobieUtil` falls back to the shared pool. No special handling needed.

### `RequestScopeConnection` interaction

`code.api.util.http4s.RequestScopeConnection` already holds a single `Connection` for the lifetime of an http4s request and exposes it to `DB.use` calls made from `Future`s spawned during that request (see `RequestScopeConnection.scala` for the full lifecycle). Doobie picks the same connection via `DB.currentConnection` because `RequestScopeConnection` installs it in the `DynoVar`. No additional plumbing required.

### Boxes, Failures, Empties

Mapper providers return `Box[A]` ubiquitously (`Full`, `Empty`, `Failure`). Doobie returns `A`, `Option[A]`, `List[A]`. The conversion convention used in the existing Doobie providers:

| Doobie | Box equivalent |
|---|---|
| `query.option` → `Option[A]` | `Box(opt)` (`Full`/`Empty`) |
| `query.to[List]` → `List[A]` | `Full(list)` (empty list still `Full(Nil)`) |
| `query.unique` → `A`, throws if 0 or >1 | wrap in `try { Full(x) } catch { ... Failure }` |
| exception thrown by JDBC | `Failure(msg, Full(t), Empty)` |

Mirror the existing `MappedFooProvider`'s exact `Box` shapes per method — tests on `Box.isDefined`, `.openOrThrow`, pattern matches on `Failure(...)` will break otherwise.

### N+1 elimination

This is the primary *secondary* benefit of the migration. Mapper makes the obvious code path a separate query per row. Doobie makes a single JOIN the obvious code path. When migrating a provider that already does N+1, fold the dependent reads into the JOIN — `DoobieUserQueries.UserSearchRow` is the canonical example (single SELECT replaces what was 3 round-trips per user).

Do this opportunistically per entity; don't gate the migration on rewriting every query, but flag in the per-entity PR which round-trips were collapsed.

### Sort/filter parameter validation

User-supplied sort columns can never be string-spliced into SQL. The existing Doobie providers handle this with a per-endpoint `Map[String, String]` whitelist (see `DoobieUserQueries.SortableColumns`) and use `Fragment.const` only on whitelisted values. Apply the same pattern everywhere a Mapper `OrderBy(MyTable.someField, Descending)` is replaced — never construct an `ORDER BY` clause from raw request input.

### SQL Server compatibility

Some entities run on SQL Server (NVARCHAR / type -9). Doobie handles JDBC types correctly out of the box — this is part of the reason for the migration (`DBUtil.runQuery` had bugs here). When migrating, if the existing code had SQL-Server branching (`if (DBUtil.isSqlServer) ... else ...`), audit whether Doobie removes the need. Often it does; sometimes (`TOP` vs `LIMIT`) it doesn't and the branch stays.

---

## Migration Order (recommended)

Sort the ~130 entities by **blast radius × write-hotness**, smallest first. Suggested phasing:

### Phase 1 — Stand-alone read-mostly entities (low risk, fast feedback)

Entities whose providers have no callers outside their own package and are read-dominated. Good rehearsal for the team and exercises the playbook with low blast radius.

Candidates (cross-check current usage before starting):

- `WebUiProps`, `FeaturedApiCollection`, `EndpointTag`, `MigrationScriptLog`, `MappedSocialMedia`, `MappedFXRate`, `MappedCurrency`, `MappedETag`.

### Phase 2 — Provider-shaped entities with clean trait seams (medium risk)

Entities accessed exclusively through a `FooProvider` trait. The trait is the seam — swap the implementation without touching callers.

Candidates: `MappedCustomerAddress`, `MappedCustomerAttribute`, `MappedCustomerDependant`, `MappedUserAttribute`, `MappedAccountApplication`, `MappedProductAttribute`, `MappedKycCheck`/`Document`/`Media`/`Status`, `MappedMeeting`/`Invitee`, `MappedAccountWebhook`, `RoutingScheme`/`BankSupportedRoutingScheme`, `BankAttribute`, `MappedTransactionType`, `RateLimiting`, `EndpointMapping`, `MethodRouting`.

### Phase 3 — Core domain (high risk; needs feature flag + extra test coverage)

Entities at the centre of the system. Each one is a multi-PR effort: write Doobie queries, write Doobie provider, ship behind a prop, soak for one release, remove Mapper provider.

Candidates: `ResourceUser`, `AuthUser`, `AccountAccess`, `ViewDefinition`, `ViewPermission`, `MapperAccountHolders`, `MappedBank`, `MappedBankAccount`, `BankAccountRouting`, `MappedTransaction`, `MappedTransactionRequest`, `TransactionRequestAttribute`, `MappedCounterparty`/`Bespoke`/`Metadata`/`WhereTag`, `MappedCustomer`, `MappedUserCustomerLink`, `Consumer`, `MappedConsent`, `ConsentItem`, `ConsentRequest`, `MappedEntitlement`, `MappedEntitlementRequest`, `MappedScope`/`UserScope`, `DirectDebit`, `StandingOrder`.

### Phase 4 — Niche / connector-internal (do alongside connector migration)

Entities only touched by specific connectors or rarely-used flows. Best done as part of the related feature work, not as a standalone push.

Candidates: `BulkPayment`, `BulkBatchReference`, `PinReset`, `Nonce`, `Token`, `OpenIDConnectToken`, `MappedBadLoginAttempt`, `UserLocks`, `JobScheduler`, `MappedSigningBasket`/`Payment`/`Consent`, `MappedRegulatedEntity`, `RegulatedEntityAttribute`, `AbacRule`, `Mandate`/`Provision`/`SignatoryPanel`, `code.chat.ChatRoom`/`Participant`/`ChatMessage`/`Reaction`, `Group`, `Organisation`, `PayeeLookup`, `AccountAccessRequest`, `UserInvitation`, `UserAgreement`, `UserInitAction`, `ConnectorMethod`, `ConnectorTrace`, `MappedConnectorMetric`, `MappedMetric`, `MetricArchive`, `DynamicEntity`/`Data`/`Endpoint`/`ResourceDoc`/`MessageDoc`, `JsonSchemaValidation`, `AuthenticationTypeValidation`, `CounterpartyLimit`, `MappedExpectedChallengeAnswer`, `MappedTaxResidence`, `MappedUserAuthContext`/`Update`, `MappedConsentAuthContext`, `MappedUserRefreshes`, `MappedCustomerMessage`, `MappedCustomerIdMapping`, `MappedAccountAttribute`, `MappedTransactionAttribute`, `MappedCardAttribute`, `MappedPhysicalCard`, `CardAction`, `AtmAttribute`, `MappedAtm`, `MappedBranch`, `MappedProduct`, `ProductFee`, `ProductTag`, `MappedProductCollection`/`Item`, `BankAccountBalance`, `BankAccountNotificationWebhook`, `SystemAccountNotificationWebhook`, `DoubleEntryBookTransaction`, `TransactionRequestReasons`, `MappedTransactionRequestTypeCharge`, `MappedCrmEvent`, `AttributeDefinition`, `CustomerAccountLink`, `CustomerLink`, `TransactionIdMapping`, `AccountIdMapping`, `ApiCollection`/`Endpoint`, `ApiProduct`/`Attribute`, `MappedComment`, `MappedTag`, `MappedWhereTag`, `MappedTransactionImage`, `MappedNarrative`, `MappedBankAccountData`.

### Phase 5 — Connector internals (`LocalMappedConnector`)

`LocalMappedConnector` and its subclasses are the heaviest Mapper consumers. Each connector method is independently migratable. Recommend doing this as a *separate* workstream after Phase 3 lands, because:

- Connector traits are shared with remote connectors (Kafka, gRPC, REST) — the *signature* must not change, only the local-implementation body.
- Many connector methods are already-tested in `LocalMappedConnectorTest` — strong safety net.
- Volume: hundreds of methods. A per-method PR cadence is realistic.

---

## Risks and Gotchas

### Risk 1 — Silent column-name drift

Mapper derives column names from field names with non-obvious rules (mixed-case fields, `dbColumnName` overrides, table-prefix conventions). A Doobie query with the wrong column name compiles but throws at runtime. **Always verify against the live DB with `\d tablename`**, not against the Mapper class declaration.

### Risk 2 — Mixed Mapper + Doobie writes in the same request

If a request writes via Mapper, then reads via Doobie on a *different* connection, the read misses the write. `DoobieUtil` defends against this for Lift requests via `liftCurrentConnection`. But: only the *synchronous* `runQuery` does. `runQueryAsync` and `runQueryIO` always use the fallback pool — they cannot see in-flight Lift writes. Migrate the writer and the reader in the same PR.

### Risk 3 — `Box` shape regressions

Tests assert on `Failure.msg` strings, `Box.isDefined` after `Empty`/`Full` discrimination, and `openOrThrow` behaviour. The Doobie provider's `Box` shape must match the Mapper provider's exactly. When in doubt, mirror Mapper's behaviour: `tryo { ... }` → `try ... catch { Failure(t.getMessage, Full(t), Empty) }`.

### Risk 4 — Lift's `MappedField` side-effects on write

Mapper's `saveMe` invokes per-field validation callbacks, `beforeSave`/`afterSave` hooks, dirty-tracking, and `CreatedUpdated` automatic timestamps. None of this happens through Doobie. For each entity, audit the Mapper class for:

- `override def dbDisplay_?` (cosmetic, ignore)
- Trait composition: `with CreatedUpdated` → handle `created_at` / `updated_at` explicitly in Doobie inserts/updates
- `beforeSave` / `afterSave` overrides — must be re-implemented in the Doobie provider, often as `flatMap` chains
- `MappedField` with a `validate` override — must be re-implemented as a pre-insert check in the provider

### Risk 5 — Removed Mapper class breaking schema

`ToSchemify.models` references the *companion object* of each Mapper class. Deleting the class breaks Schemifier and therefore boot. Never delete the Mapper class as part of the data-access migration — only the **Provider** that wraps it. Schema removal is a separate Phase (eventually replacing Schemifier itself with Flyway or similar).

### Risk 6 — Connector trait surface

Many connector methods take Mapper case-class instances as arguments (`def saveTransaction(t: MappedTransaction): ...`). Migrating the *body* to Doobie is straightforward; migrating the *signature* away from `MappedTransaction` requires changing every caller and every remote-connector implementation. Don't conflate the two. Step 1: replace the implementation's body. Step 2 (separate PR, separate decision): introduce a non-Mapper case class as the trait parameter and convert at the boundary.

---

## Per-Entity Tracker

Add a row per entity as you go. Status: `mapper` (untouched) → `dual` (Doobie provider exists, prop-gated) → `doobie` (Doobie default, Mapper provider deleted) → `schema-only` (Mapper class is now only a Schemifier descriptor, no runtime use).

| Entity | Provider trait | Status | Last touched | Notes |
|---|---|---|---|---|
| MappedMetric | MetricsProvider | dual (partial) | — | `DoobieMetricsQueries` exists for hot read paths; writes still Mapper |
| MappedConnectorMetric | ConnectorMetricsProvider | dual (partial) | — | `ConnectorMetricBatchWriter` uses Doobie for batched writes |
| ResourceUser (search path only) | UsersProvider | dual (partial) | — | `DoobieUserQueries.getUsers` JOINs ResourceUser + AuthUser + MappedBadLoginAttempt |
| MappedConsent | Consents | dual (partial) | — | `DoobieConsentQueries` covers some lookups |
| ChatMessage | — | dual (partial) | — | `DoobieChatMessageQueries` |
| AccountAccess + ViewDefinition | — | dual (partial) | — | `DoobieAccountAccessViewQueries` (account-listing hot path) |
| _all other ~125 entities_ | various | mapper | — | not started |

Fill in as PRs land. Mirror the format of `LIFT_HTTP4S_MIGRATION.md`'s tracker.

---

## Alternative: Keep Mapper, Maintain It Ourselves

This whole migration assumes Mapper is going away. The opposite stance — *keep Mapper indefinitely, reach for Doobie only where it pays off* — is also defensible. If chosen, the question becomes "who maintains Mapper?" since Lift upstream is sparse.

Four shapes for self-maintenance, ordered cheapest to heaviest:

### Option A — Upstream patches

Submit fixes to `lift/framework`. Maintainers are responsive enough for discrete bugs with tests; you don't own a fork.

**Works when:** specific bugs, no urgency, change isn't OBP-specific.
**Doesn't work when:** you need a fix shipped this week, or the change is too OBP-specific (e.g. "we want a different transaction model").

Try this first for any Mapper bug before considering anything heavier.

### Option B — Vendor the source into OBP

Copy `net.liftweb.mapper.*` (plus its hard deps from `lift-db`, `lift-util`, `lift-common`, `lift-json`) into `code/vendor/mapper/`, rename the package, drop the Lift Mapper dependency. ~20–30k lines total, most of it stable.

**Cost:** ~2 weeks one-time port; low ongoing while nothing breaks; spikes when you need non-trivial changes.

**Hidden risk:** the vendored code looks like every other Scala file in the repo, and reviewers will start "improving" it. Mapper has subtle invariants (field-dirty-tracking, lazy column-name derivation, implicit conversions in the query DSL). A well-intentioned refactor breaks Schemifier in ways tests don't catch. Mitigate with a banner comment in every vendored file: *"Vendored from Lift X.Y.Z. Do not refactor without understanding the originals."*

**Works when:** you've decided Mapper is good enough for years and want to escape upstream's release cadence without taking on infrastructure.

### Option C — Fork the framework

Fork `lift/framework`, publish as `org.openbankproject:obp-mapper` to your own Maven repo. Strip the bits you don't use.

**Extra cost over Option B:** publish pipeline, version scheme, build infrastructure.
**Extra benefit:** the option to share with other OBP repos or position as a community successor to Lift Mapper — neither materialises automatically.

**Works when:** you genuinely intend the fork to be a community successor. Otherwise Option B gives you the same control with less infrastructure cost.

### Option D — In-house Mapper-compatible rewrite

Write just enough of a Mapper-shaped DSL to cover what OBP uses: field types, `By`/`ByList`/`OrderBy`, `findAll`/`find`/`count`/`saveMe`/`delete_!`, a Schemifier replacement. ~2–3k lines if disciplined.

**The trap:** behavioural compatibility with Schemifier's DDL output is the hard part — column-name derivation rules, index naming conventions, `CreatedUpdated` timestamp behaviour, validation hook ordering. Drift means your DDL diverges and existing prod databases think they need migrations they don't. "Passes tests on a fresh DB" takes a month; "drops cleanly into existing prod with zero schema drift" takes much longer.

**Works when:** you're already replacing Schemifier (i.e. doing the full Mapper exit anyway). Otherwise the DDL-compatibility constraint kills it.

### The question hiding underneath

"Can we maintain Mapper?" is downstream of "what's actually wrong with Mapper today?" Three possible answers:

1. **Specific bugs that bite us** → Option A (upstream), Option B (vendor) as fallback.
2. **Scala 3 migration.** Mapper's implicit-heavy DSL and `MappedField` machinery is non-trivial to port. Forking/vendoring delays this but doesn't solve it — whoever does the Scala 3 port does the same work regardless. If OBP commits to Scala 3 within a few years, the Mapper-exit path (full Doobie + Schemifier replacement) becomes cheaper than porting Mapper.
3. **Soft cost: nobody knows Lift.** Real, but owning the source doesn't change the learning curve. This is a docs / training problem, not a Mapper problem.

### Recommendation

If we decide *not* to do the full migration in this document:

1. **Keep Mapper.** It works, it's not the current bottleneck.
2. **Adopt an explicit upstream-first policy** for Mapper bugs we hit. Costs nothing, often gets fixes through.
3. **Reserve vendoring (Option B) as a documented fallback.** If upstream stops responding or refuses a fix we need, we have a pre-decided escape hatch — not a panic.
4. **Treat the Scala 3 question as the real strategic input.** Until OBP commits to a Scala 3 timeline, "maintain Mapper" is a low-cost holding pattern. Once it commits, revisit this whole document — the answer probably becomes "yes, do the full migration, because porting Mapper to Scala 3 costs more than leaving it behind."

Forking / vendoring is genuinely feasible (~2 weeks for vendoring, low ongoing cost). But it's a solution to *"Lift might disappear"* or *"we need a fix upstream won't take"*, neither of which is the current problem. The current problem is the absence of a long-term plan — and forking doesn't supply one.

---

## Other possibilities (narrower or orthogonal moves)

Options A–D and the full Doobie exit are the "all of Mapper" answers. Several smaller moves attack just one facet of the problem — usually the stale `lift-webkit` dependency or the schema layer — and several are *not* mutually exclusive with each other or with the bigger options.

### Option E — Minimal `lift-webkit` shim

The exclusion experiment at the top of this file proved that Mapper's *compile-time* API touches exactly two webkit families: `net.liftweb.http.Factory` (superclass of `MapperRules`) and `net.liftweb.http.js.JsExp` (return type of `asJsExp`). Instead of *removing* lift-webkit, exclude it and supply a tiny in-house package providing just those types — enough to satisfy the compiler and the classloader — while OBP never calls the methods that use them (it has no `CRUDify` / `toForm` / `SHtml` usage).

**The catch:** the real surface is the *transitive closure* of `Factory` + `JsExp`, not two classes. `Factory` is part of Lift's injector framework and `JsExp` sits atop the `JsExp`/`JsCmd` hierarchy; both drag in more types. You'd find the true set empirically — exclude, compile, add the next missing symbol, repeat to green — then run the **full** test suite to confirm nothing reflectively hits a stubbed method at runtime (`asJsExp`, MapperRules' JS hooks). If the closure stays small (≈a dozen interface-only types) this kills the dependency-surface problem for a few days' work without touching data access. If it balloons, fall back to Option B (vendor all of Mapper) or accept Option F.

**Works when:** the *only* goal is getting lift-webkit out of the manifest / CVE scanner, and the data-access migration isn't otherwise justified.

### Option F — Suppress and document (no code change)

Accept that lift-webkit is on the classpath but unreachable from OBP code, and just stop it being noise. Add a `dependency-check` suppression scoped to `lift-webkit` with a written justification ("transitive via lift-mapper; `net.liftweb.http` unused in OBP source since PR #2828; unreachable"), and/or a CVE allowlist entry if one is ever filed. Zero code change, zero risk; the residual is honest and recorded.

This is the right **baseline** until one of the other options is chosen — it is not mutually exclusive with any of them.

**Works when:** there's no security-driven mandate yet and you just want the scanner quiet and the situation on the record.

### Option G — Exit to a different ORM than Doobie

Doobie is the incumbent target only because OBP already uses it — it is not the only exit. If the data-access decision is ever reopened: **Slick** (functional-relational, mature, Scala 3-ready), **Quill** (compile-time SQL, macro-heavy), **ScalaSql** / **Magnum** (newer, lighter, Scala 3-native). The migration *shape* — Provider-trait seam, per-entity swap, Schemifier kept until last — is identical regardless of target; only Steps 3–4 of the playbook change. Listed so a future decision isn't anchored to Doobie purely by omission.

**Works when:** a fresh evaluation is genuinely on the table (e.g. the Scala 3 forcing function arrives) rather than Doobie being assumed.

### Option H — Replace only Schemifier, keep Mapper for queries

The two jobs Mapper does — the query/CRUD DSL and DDL generation (`Schemifier`) — are separable. Swapping **Schemifier** for Flyway / Liquibase (explicit SQL migrations) removes the one thing that makes the Mapper class declarations undeletable, and it's a *prerequisite* for the final "delete the Mapper classes" step of any full exit anyway. Doing it first, independently: (a) de-risks the eventual exit, (b) lets new tables use plain SQL DDL immediately — directly enabling the "stop adding new Mapper entities" lever in the Conclusion — and (c) touches not a single query.

It does **not**, by itself, drop lift-webkit (the Mapper query layer still pulls it in). But it's the highest-leverage decoupling move that stands completely alone.

**Works when:** you want to bend the curve and de-risk without committing to the full query migration.

### Option I — Fund or sponsor upstream Lift

The root cause is cadence, not code. Sponsoring a maintainer — directly, via a foundation, or GitHub Sponsors — to keep Lift releasing (ideally including a Scala 3 line) attacks the actual problem and benefits every Lift user, not just OBP. Cheaper than a fork *if it works*; entirely contingent on upstream's willingness and on OBP's appetite to fund OSS sustainability. Pairs naturally with Option A: patches keep specific fixes flowing, sponsorship keeps the project alive enough to merge them.

**Works when:** OBP has budget for OSS sustainability and views Lift as worth keeping alive industry-wide.

---

## What "done" looks like

- `grep -r "net.liftweb.mapper" obp-api/src/main/scala/code/ | grep -v "/Mapped[A-Z]\|/Mapper[A-Z]\|Schemifier"` returns nothing.
- All `Mapped*Provider.scala` files deleted.
- `ToSchemify.models` unchanged. `Schemifier.schemify(...)` in `Boot.scala` unchanged.
- Mapper case classes (`class Foo extends LongKeyedMapper[Foo]`) remain, but no runtime code calls `Foo.findAll`, `Foo.create`, `foo.save`, etc.
- `DoobieUtil.runQuery` is the only entry point for application-level DB access.
- The next workstream (schema management, out of scope here) can replace Schemifier — and at that point the Mapper class declarations themselves are deletable.

---

## Conclusion: Leaving Mapper Is Genuinely Hard

The plan above is sound, but the size is real. That's not because Mapper is special — it's what happens when *any* foundational tech sits under 130 entities and a decade of code. Hibernate, Slick, raw JDBC: same outcome. The cost-to-leave is dominated by the size of what's built on top, not by the qualities of the thing being left.

Three framings worth holding onto:

### 1. The "let's migrate" framing is the trap

Multi-year migrations with no user-facing benefit are the projects that stall at 60% and leave two systems running in parallel forever. Don't start a migration unless the forcing function is strong enough to finish it. Half-done is worse than not-started.

### 2. The forcing function isn't here yet

Scala 3 is the obvious eventual one — it's coming for everyone on Scala 2.13, probably in the 3–5 year horizon. When it arrives, it brings real budget, real urgency, and real organisational consent to do the work. Starting now without that urgency means burning effort that would be cheaper to spend later, when the migration becomes self-justifying instead of speculative.

### 3. The realistic stance is "design around the constraint"

Not "migrate" or "don't migrate" — *constrain*:

- **Treat Mapper as a constraint, not a problem.** It's the data layer. It will be the data layer for years. Stop apologising for it in docs and code comments.
- **Doobie where it pays off, not as a stealth migration.** The existing pattern — added where N+1 or JDBC bugs forced the issue — is correct. Resist adding Doobie to entities where it doesn't earn its keep. Every dual-system entity is overhead.
- **Stop adding new Mapper entities.** The one cheap policy lever that bends the curve without committing to anything. New entity → Doobie + a small DDL mechanism (Schemifier extension or sidecar SQL file). Existing entity → leave alone. In 3 years Mapper's share of the codebase has shrunk passively while normal feature work continued.
- **Park the full migration as a contingency plan.** This document, essentially. When the forcing function arrives and someone asks *"ok, what would this take?"* — the plan exists and you start from page 1, not page 0.

### Bottom line

You don't *solve* this. You inherit it, you live with it, and you wait for the moment when the cost of migrating becomes lower than the cost of staying. That moment will probably arrive. It isn't now.

This document's value isn't as a Q3 execution plan. It's as the artefact you reach for when the moment does arrive — pre-thought, pre-argued, ready to cost out.
