# The Lift Mapper blocker for the Scala 3 flip

The Scala 3 flip of `obp-api` stops at one thing: Scala 3 cannot compile a class that extends
Lift's `KeyedMapper` hierarchy, which is roughly 140 entity classes. The compiler does not
report a type error in our code; it fails an internal consistency check:

```
assertion failure for net.liftweb.mapper.Mapper[...] & OwnerType <:< net.liftweb.mapper.Mapper[...], frozen = true
```

Identical on 3.3.8 and 3.7.2. This file records what the failure is and — more usefully — what it
is *not*, so that nobody re-runs these experiments.

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

## The next experiment, and what it would settle

**Cross-build `lift-persistence` itself for Scala 3 and compile one entity against the `_3`
artifact.** OBP owns that fork, so this is a change we can make. If it compiles, the blocker
dissolves and no OBP source has to change. If it does not, the errors are the real cost estimate.
Either way it is a bounded assessment — clone, set `scalaVersion`, compile, count — and it should
be run before anyone commits to a larger direction.

Note what this does to the migration plan's architecture. The plan has lift-mapper staying
`_2.13` forever and being consumed via `for3Use2_13`. If the pickling format is what breaks this,
that specific decision is what has to change, and the four routes previously on the table
(patch the fork's declarations / carve the entity layer into a 2.13 module / do the Doobie
migration first / report upstream) are all more expensive than trying the cross-build first.

## Reproducing

Against the OBP compile classpath, with any Scala 3 compiler:

```sh
scalac -classpath "$OBP_CLASSPATH" v6.scala
```

where `v6.scala` is the three-line v6 row above. The crash is immediate; no OBP source is needed.
