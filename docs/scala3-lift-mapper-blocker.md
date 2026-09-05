# The Lift Mapper blocker for the Scala 3 flip

> **Decided (2026-08-16): Doobie first. The Scala 3 flip waits for it.**
>
> Of the routes weighed below, the one taken is to remove Lift Mapper rather than to work around
> it — no 2.13 entity module, no patch to the fork. The flip is not abandoned, it is sequenced
> after the persistence migration, because that migration deletes the blocker instead of
> containing it.
>
> That work already exists and is underway in the `OBP-API-I` working copy on branch
> `lift-mapper-remove` (ATMs is the first table fully off Lift; there is also a
> `feature/doobie-flyway-phase1` remote). This document's job from here is to stop anyone
> re-litigating the blocker: the four disproved routes below are disproved, and the flip becomes
> possible when entities no longer extend `KeyedMapper`.
>
> Everything this branch delivered — scalatest 3.2.20, the scalacache removal, the dynamic
> compiler seam, the `-Xsource:3` debt, the avro CVE actually leaving the runtime classpath — is
> independent of that sequencing and ships on its own.

The Scala 3 flip of `obp-api` stops at one thing: Scala 3 cannot compile a class that extends
Lift's `KeyedMapper` hierarchy, which is roughly 140 entity classes. The compiler does not
report a type error in our code; it fails an internal consistency check:

```
assertion failure for net.liftweb.mapper.Mapper[...] & OwnerType <:< net.liftweb.mapper.Mapper[...], frozen = true
```

Identical on **3.3.8 (LTS), 3.7.2 and 3.8.4** — the latest release at the time of writing, six
versions past the first one tried. Three compiler generations reject it the same way, with the
same assertion text, so **"wait for a newer compiler" is not a route** and should not be offered
as one.

This file records what the failure is and — more usefully — what it is *not*, so that nobody
re-runs these experiments.

## What was ruled out

Each row is a compile of a few lines against the real `lift-persistence_2.13` jar on the OBP
classpath. "OK" means the file compiled clean.

| # | Source | Result |
|---|---|---|
| v7 | `class T extends Mapper[T]` | **OK** |
| v6 | `class T extends LongKeyedMapper[T] with IdPK` | CRASH |
| v5 | same as v6 but without `IdPK` (hand-written `primaryKeyField`) | CRASH |
| v3 | `object` does not extend the entity class (`class TMeta extends T ...; object TMeta extends TMeta`) | CRASH |
| v8 | `object M extends LongKeyedMetaMapper[Nothing]` — no entity class at all | CRASH |

Conclusions, in order of how much work each one saves:

* **Plain `Mapper[A]` is fine.** The failure is confined to the *keyed* part of the hierarchy —
  `KeyedMapper` / `KeyedMetaMapper`. `javap` shows why that part is different: it is F-bounded,
  `KeyedMapper<KeyType, OwnerType extends KeyedMapper<KeyType, OwnerType>> extends Mapper<OwnerType>`,
  and `Mapper[A]` carries a `self: A =>` self-type. `Mapper[...] & OwnerType` in the assertion text
  is that self-type intersected with the F-bounded parameter.
* **`IdPK` is not implicated** (v5), so the singleton-typed `primaryKeyField` is not the trigger.
* **The `object X extends class X` idiom is not the trigger** (v3). This one matters most in
  practice: it means *rewriting how the 140 entity classes are spelled cannot fix this*. An
  entity-side refactor is not a route, and should not be attempted.
* **An entity class is not even required** (v8). One meta object alone is enough.

## The part that suggests a route

The same idiom, modelled in dependency-free Scala 3 source — self-type, F-bound, companion meta
object — compiles cleanly on the same compiler:

```scala
trait MyMapper[A] { self: A => def meta: MyMeta[A] }
trait MyKeyed[K, A <: MyKeyed[K, A]] extends MyMapper[A] { self: A => }
trait MyMeta[A]
class Row extends MyKeyed[Long, Row] { def meta = Meta }
object Meta extends MyMeta[Row]
```

So the shape is legal Scala 3. What differs in the failing case is that Lift arrives as
**2.13-pickled classfiles**, which Scala 3 reads through its Scala 2 unpickler, rather than as
TASTy.

That is a lead, not a proof — the model above is five lines and Lift's real hierarchy is not, so
it does not establish that the only relevant difference is the pickling format. It is recorded
because it points at a cheap, decisive next experiment.

## The cross-build, measured

That experiment has now been run: the fork's 63 main sources were compiled with Scala 3.3.8
against the same dependency set, in a scratch clone (no repository was modified).

**It does not crash.** Compiling Lift's own sources produces ordinary migration errors, not the
`assertion failure` — so there is no dotty bug to report and nothing to wait for upstream. The
work is a normal Scala 3 migration of a legacy library.

| | errors |
|---|---|
| plain Scala 3 | 162 |
| `-source:3.0-migration` | 95 |

The 67 that migration mode absorbs are procedure syntax (`def f() { ... }`, 37 sites) and related
Scala-2-only syntax. What remains splits into one mechanical pile and one real design question:

* **18 `TypeTag` errors — mechanical, and already resolved.** Lift's fields carry scala-reflect
  `TypeTag`s (`MappedInt.scala:237`, `MappedEnum`'s implicit parameter), which Scala 3 does not
  have. This looked like an API-level design change, but the tag is only ever *stored* — it feeds
  `SourceFieldMetadataRep` and is never introspected, and OBP-API references neither `.manifest`
  nor `SourceInfo` at all (it served a lift-webkit-era feature that is gone). Swapping `TypeTag`
  → `ClassTag` and `typeTag` → `classTag` took the count **95 → 79** and cleared 16 of the 18.

  Note this is the **same root cause as the plan's F-1 risk item** (79 `No TypeTag` errors in
  `SwaggerJSONFactory`) — one problem in two places. F-1 may be similarly mechanical; it has not
  been checked.

* **42 cyclic errors — NOT mechanical.** `-explain-cyclic` reports *"required to type the right
  hand side of method `apply` since no explicit type was given"*, which reads like "add a result
  type". **That was tested and it is wrong**: annotating all four `object By` overloads with
  explicit `QueryParam[O]` result types changed nothing — 79 errors and 42 cyclic before and
  after, the same two lines still reported.

  What the 42 actually share: **33 are `primaryKeyField` accesses through an F-bounded keyed
  type**, and the remaining **9 are the keyed trait declarations themselves** —

  ```scala
  trait KeyedMetaMapper[Type, A <: KeyedMapper[Type, A]] extends MetaMapper[A] with KeyedMapper[Type, A]
  trait LongKeyedMetaMapper[A <: LongKeyedMapper[A]] extends KeyedMetaMapper[Long, A] { self: A => }
  ```

  A trait that is simultaneously the *meta* and the *keyed mapper* of its own F-bounded parameter,
  under a self-type. That is the same construct as the assertion failure — so the crash when
  consuming the 2.13 artifact and the cyclic errors when compiling from source are **one problem
  wearing two faces**, not two problems, and cross-building does not sidestep it.

* ~35 assorted not-found / type errors, not yet triaged.

So the cost is measured rather than guessed, and one of the two piles turned out to be free. But
the conclusion of the previous section has to be corrected: cross-building is **not** an escape
from the blocker. It converts an unhandled assertion into 42 legible diagnostics, which is worth
having — the failure is now describable — but the underlying construct is what Scala 3 rejects
either way.

## Consuming entities from Scala 3 works — only defining them fails

Every crash above comes from *defining* a class that extends the keyed hierarchy. Using an
already-compiled one is a different question, and it was tested separately: a Scala 3 source file
compiled against obp-api's own 2.13 `target/classes`, reading the meta object, calling a query
method and touching a field —

```scala
import code.model.dataAccess.ResourceUser
object UseEntity {
  def count(): Long = ResourceUser.count
  def emailOf(u: ResourceUser): String = u.email.get
}
```

— compiles cleanly: exit 0, zero errors, zero assertions, classfiles produced.

**This makes "keep the entity layer on 2.13" a technically viable route**, not a hypothesis. The
Scala 3 side can call into the entities; it just cannot declare them.

One caveat, which is a warning rather than an error and must not be read as a clean bill:

```
An existential type that came from a Scala-2 classfile for trait MetaMapper
cannot be mapped accurately to a Scala-3 equivalent.
original type: T forSome type T   reduces to: T   type used instead: Any
This choice can cause follow-on type errors or hide type errors.
```

So `MetaMapper`-typed values degrade to `Any` across the boundary. That sounds like a design
constraint on where the module boundary goes, so the real surface was measured rather than left
as a worry.

**The surface is one line.** Of 163 `MetaMapper` mentions in main sources, 156 are `def
getSingleton` and nearly all the rest are `object X extends X with LongKeyedMetaMapper[X]` — both
of which live *inside* entity files and would stay in the 2.13 module, never crossing anything.
Filtering those leaves three genuine cross-boundary sites:

| site | type | affected? |
|---|---|---|
| `Boot.scala:928` `val models: List[MetaMapper[_]]` | generic, existential | **yes** |
| `Migration.scala:859,984` `tableExists`/`makeBackUpOfTable(table: BaseMetaMapper)` | `BaseMetaMapper` is **non-generic** | no |
| `AttributeQueryTrait` / `NewAttributeQueryTrait` `self: BaseMetaMapper =>` | mixed into meta objects, stays 2.13 | no |

Only the generic `MetaMapper[_]` carries the `T forSome` existential; `BaseMetaMapper` has no type
parameter and nothing to degrade.

It is tempting to conclude the affected site does not need the generic type: `schemify` takes
`Seq[BaseMetaMapper]` (confirmed by `javap`), so `List[BaseMetaMapper]` would remove the
existential. **That is wrong, and checking every use is what showed it.**

`ToSchemify.models` has six consumers, not one. Two call `schemify`; the other four are test
helpers that call `_.bulkDelete_!!()`:

```
obp-api/src/test/scala/code/setup/ServerSetup.scala:145
obp-api/src/test/scala/code/setup/LocalMappedConnectorTestSetup.scala:215
obp-api/src/test/scala/code/setup/TestConnectorSetupWithStandardPermissions.scala:159
obp-api/src/test/scala/code/api/v2_1_0/SandboxDataLoadingTest.scala:106
```

`javap` shows `BaseMetaMapper` declares seven schema members — `beforeSchemifier`,
`afterSchemifier`, `dbTableName`, `_dbTableNameLC`, `mappedFields`, `dbAddTable`, `dbIndexes` —
and **no `bulkDelete_!!`**. That method is on `MetaMapper`. So narrowing the annotation would not
be behaviour-preserving; it would fail to compile those four files.

The honest cost, then: this site genuinely needs the generic type, and under a 2.13-entity-layer
split those four test helpers sit on the Scala 3 side calling a method on a value whose type has
degraded to `Any`. That is a real boundary problem affecting real code, not a one-line annotation.

No change is made here — this is preparation for a route nobody has chosen. And this measured the
declared surface plus one entity's compile; the whole entity layer still needs measuring before
the route is committed to.

### What is NOT reducible (correcting the line above)

An earlier revision of this file said the 9 declaration-site errors make "a well-formed upstream
report … already small enough to file as-is". That was over-stated, and testing it is what showed
so. There are two distinct symptoms and only one of them has a small repro:

* **The assertion failure — reducible.** Three lines against the published `_2.13` jar (the v6 row
  above). Fileable as-is.
* **The cyclic errors — not reducible so far.** Four synthetic Scala 3 models were built, each
  adding more of the real shape, and **all four compile clean**:

  | model | shape | result |
  |---|---|---|
  | m1 | self-type + F-bound + meta object | OK |
  | k1 | + meta trait extending the mapper trait | OK |
  | k2 | + `getSingleton` making the trait pair mutually recursive | OK |
  | k3 | + concrete entity class and meta object | OK |

  Two candidate fixes were also tried directly on the fork's own sources and **neither moved the
  count** (79 errors / 42 cyclic before and after): simplifying `KeyedMetaMapper`'s redundant
  self-type `self: A with MetaMapper[A] with KeyedMapper[Type, A] =>` down to `self: A =>`, and
  adding explicit result types to the `object By` overloads.

So the cyclic form needs more of the real hierarchy than has been modelled, and the trigger is
still unidentified. An upstream report today would have to point at the whole fork rather than a
small case, which makes it much weaker. Anyone continuing this should keep bisecting the real
sources rather than building up from synthetic models — that direction has been tried and did not
reach it.

Note what this does to the migration plan's architecture. The plan has lift-mapper staying
`_2.13` forever and being consumed via `for3Use2_13`. That specific decision is what the evidence
now argues against: consuming the 2.13 artifact is what produces the uncompilable assertion, while
building the same source as `_3` produces a finite, ordinary error list.

## Reproducing

Against the OBP compile classpath, with any Scala 3 compiler:

```sh
scalac -classpath "$OBP_CLASSPATH" v6.scala
```

where `v6.scala` is the three-line v6 row above. The crash is immediate; no OBP source is needed.
