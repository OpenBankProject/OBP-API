package code.api.cache

import code.setup.RedisTestTarget
import org.scalatest.{FlatSpec, Matchers}
import scalacache.{CacheConfig, DefaultCacheKeyBuilder}

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
class CacheSerializationNamespaceTest extends FlatSpec with Matchers {

  /** A stand-in caller key; its value is arbitrary, only its stability across calls matters. */
  private val SampleCallerKey = "code.example.Provider.getAll(Some(bank))"

  /** This build's namespace, spelled out rather than derived, so a test asserting against it
   *  fails loudly if the derivation and the literal ever disagree. */
  private val CurrentNamespace = "obpser1-scala2.13"

  private def keyFor(namespace: String, callerKey: String): String =
    CacheConfig(cacheKeyBuilder = DefaultCacheKeyBuilder(keyPrefix = Some(namespace)))
      .cacheKeyBuilder.toCacheKey(Seq(callerKey))

  "the derived cache key" should "differ between two serialization namespaces" in {
    val a = keyFor("obpser1-scala2.12", SampleCallerKey)
    val b = keyFor(CurrentNamespace, SampleCallerKey)

    withClue(s"2.12 key <$a> and 2.13 key <$b> are the same, so a 2.13 instance would read the " +
             s"bytes a 2.12 instance wrote -- which is the defect this exists to prevent. ") {
      a should not equal b
    }
    a should include("2.12")
    b should include("2.13")
  }

  it should "also differ when only the manual counter is bumped" in {
    // The Scala version does not move for a dependency upgrade that changes the encoding --
    // chill 0.9.3 to 0.9.5 on its own would not have. The counter is the escape hatch for that,
    // and it is only an escape hatch if it actually changes the key.
    keyFor(CurrentNamespace, SampleCallerKey) should not equal keyFor("obpser2-scala2.13", SampleCallerKey)
  }

  it should "stay stable for one namespace, or nothing would ever be a cache hit" in {
    keyFor(CurrentNamespace, SampleCallerKey) shouldBe keyFor(CurrentNamespace, SampleCallerKey)
  }

  "the namespace this build uses" should "name the Scala binary version it was compiled against" in {
    // Derived, not asserted verbatim: the point is that it tracks the axis that actually moved.
    val expected = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    val probe = keyFor(s"obpser1-scala$expected", "x")
    probe should include(expected)
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
