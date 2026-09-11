package code.api.cache

import code.setup.RedisTestTarget
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Two OBP-API versions must not read each other's cached bytes.
 *
 * The defect this pins is not hypothetical and is not a decode failure. An empty `List`, written
 * by chill 0.9.3 on Scala 2.12, decodes under chill 0.9.5 on 2.13 into a
 * `scala.collection.immutable.Queue`. The decode SUCCEEDS; the call site, whose signature says
 * `List`, is where it dies:
 *
 *     class scala.collection.immutable.Queue cannot be cast to
 *     class scala.collection.immutable.List
 *
 * Measured on `GET /management/dynamic-message-docs` and `GET /management/connector-methods`:
 * 200 on 2.12, 500 on 2.13 reading the entry 2.12 wrote, and correct in either version alone.
 * The 500 lasts the whole TTL, because a read that throws does not evict the key. A rolling
 * upgrade, or any upgrade against a warm Redis, produces exactly this.
 *
 * `Redis.serializationNamespace` prefixes every memoized key with the Scala binary version and a
 * manually bumpable counter, so entries from another build are not addressable at all and expire
 * on their own.
 *
 * ── What is asserted ──
 *
 * The PROPERTY, not the string. Asserting the current prefix would pin `obpser1-scala2.13`, which
 * says nothing about whether isolation holds and turns every legitimate bump into a test edit.
 * What has to stay true is that two different namespaces cannot see each other's entries, and
 * that one namespace still sees its own -- an isolation that isolated everything, including a
 * version from itself, would "pass" while disabling the cache entirely.
 *
 * These run against a real Redis. RedisTestTarget cancels them when none is reachable, and
 * OBP_TEST_REDIS_REQUIRED=true turns that cancel into a failure so CI cannot lose the check
 * silently.
 */
class CacheSerializationNamespaceTest extends AnyFlatSpec with Matchers {

  /** A stand-in caller key; its value is arbitrary, only its stability across calls matters. */
  private val SampleCallerKey = "code.example.Provider.getAll(Some(bank))"

  /** This build's own namespace, taken from the code under test rather than spelled out: the
   *  literal it used to carry was a 2.13 string, which this branch (Scala 3) would have made
   *  permanently stale. The derivation itself is asserted separately, below. */
  private val CurrentNamespace = Redis.serializationNamespace

  private def keyFor(namespace: String, callerKey: String): String =
    Redis.composeMemoKey(namespace, callerKey)

  "the derived cache key" should "differ between two serialization namespaces" in {
    val a = keyFor("obpser1-scala2.12", SampleCallerKey)
    val b = keyFor(CurrentNamespace, SampleCallerKey)

    withClue(s"2.12 key <$a> and 2.13 key <$b> are the same, so a 2.13 instance would read the " +
             s"bytes a 2.12 instance wrote -- which is the defect this exists to prevent. ") {
      a should not equal b
    }
    a should include("2.12")
    b should include(CurrentNamespace)
  }

  it should "also differ when only the manual counter is bumped" in {
    // The Scala version does not move for a dependency upgrade that changes the encoding --
    // chill 0.9.3 to 0.9.5 on its own would not have. The counter is the escape hatch for that,
    // and it is only an escape hatch if it actually changes the key.
    val bumped = CurrentNamespace.replaceFirst("^obpser1", "obpser2")
    withClue(s"the counter bump produced the same namespace <$bumped>, so it is not an escape hatch ") {
      bumped should not equal CurrentNamespace
    }
    keyFor(CurrentNamespace, SampleCallerKey) should not equal keyFor(bumped, SampleCallerKey)
  }

  it should "stay stable for one namespace, or nothing would ever be a cache hit" in {
    keyFor(CurrentNamespace, SampleCallerKey) shouldBe keyFor(CurrentNamespace, SampleCallerKey)
  }

  "the namespace this build uses" should "separate a Scala 3 build from a Scala 2.13 one" in {
    // The regression: `scala.util.Properties.versionNumberString` reads the STANDARD LIBRARY,
    // and Scala 3 compiles against the 2.13 one - so it answers "2.13" on a Scala 3 build and
    // this branch produced byte-identical keys to develop. Deploying it against a Redis warmed
    // by the 2.13 build is exactly the collision the namespace exists to prevent, and it was
    // the one case it could not see.
    //
    // The probe here is `scala.runtime.LazyVals$`, NOT the `scala.runtime.Scala3RunTime` the
    // implementation uses: a test that repeated the production probe would agree with it however
    // wrong both were. Both classes ship only in scala3-library.
    val runningOnScala3 =
      try { Class.forName("scala.runtime.LazyVals$"); true } catch { case _: Throwable => false }

    val libraryBinary = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    val whatAScala213BuildProduces = s"obpser1-scala$libraryBinary"

    if (runningOnScala3) {
      withClue(s"this is a Scala 3 build, but its namespace <$CurrentNamespace> is the one a " +
               s"Scala 2.13 build produces - the two would read each other's Kryo entries ") {
        CurrentNamespace should not equal whatAScala213BuildProduces
      }
      // Not `include("3")`: "obpser1-scala2.13" contains a '3' too, so that assertion could
      // never have failed. The compiler generation has to be named as such.
      CurrentNamespace should include("scala3")
      keyFor(CurrentNamespace, SampleCallerKey) should not equal
        keyFor(whatAScala213BuildProduces, SampleCallerKey)
    } else {
      // On a 2.13 build the spelling must stay exactly what develop writes, or moving between
      // develop and this branch would cold-start the cache for no reason.
      CurrentNamespace shouldBe whatAScala213BuildProduces
    }
  }

  it should "name the Scala library version it was compiled against" in {
    // Derived, not asserted verbatim: the point is that it tracks the axis that actually moved.
    // The library version is present on either build - on its own for a 2.13 one, and after the
    // compiler generation for a Scala 3 one ("3-lib2.13"), since the encoding depends on both.
    val expected = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    CurrentNamespace should include(expected)
    keyFor(CurrentNamespace, "x") should include(expected)
  }

  "a real Redis" should "not return an entry written under a different namespace" in {
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the cross-namespace isolation check")

    val caller = s"code.example.Probe.roundTrip(${System.nanoTime()})"
    val oldKey = keyFor("obpser1-scalaOLD", caller)
    val newKey = keyFor("obpser1-scalaNEW", caller)

    import code.api.JedisMethod
    try {
      Redis.use(JedisMethod.SET, oldKey, Some(60), Some("written-by-the-other-version"))

      withClue("the new namespace found the old namespace's entry -- the prefix is not isolating ") {
        Redis.use(JedisMethod.GET, newKey, None, None) shouldBe None
      }
      withClue("the old namespace could not read back its OWN entry, so this test proved nothing " +
               "about isolation -- it would pass with the cache switched off entirely ") {
        Redis.use(JedisMethod.GET, oldKey, None, None) shouldBe Some("written-by-the-other-version")
      }
    } finally {
      Redis.use(JedisMethod.DELETE, oldKey, None, None)
      Redis.use(JedisMethod.DELETE, newKey, None, None)
    }
  }
}
