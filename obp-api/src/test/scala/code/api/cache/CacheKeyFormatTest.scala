package code.api.cache


import scala.concurrent.duration._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the memoize key format and TTL rounding to what scalacache 0.28 produced.
 *
 * The expected strings below are NOT derived from the implementation: the Redis ones were
 * sampled verbatim from a live instance running the scalacache-based build (redis-cli --scan),
 * before the in-house replacement landed. scalacache's memoization macro derived the key from
 * the enclosing wrapper method: full object + method name, the one non-@cacheKeyExclude'd
 * parameter list rendered with its argument, then one "()" per excluded list.
 *
 * Why the format is load-bearing rather than cosmetic:
 *  - rate-limit counters live inside this envelope (Constant.RATE_LIMIT_ACTIVE_PREFIX keys are
 *    the <cacheKey> part) - a silent format change would reset every consumer's counters and
 *    detach /management/cache/info's per-namespace key counts (S-2: rate limiting is a
 *    security control);
 *  - NewStyle.invalidateMethodRoutingCache deletes "*getMethodRoutings*" by pattern against
 *    the full stored key;
 *  - InMemoryCachingTest asserts countKeys("*<cacheKey>*") matches, i.e. the logical key must
 *    appear verbatim inside the stored key.
 *
 * Since develop added Redis.serializationNamespace, the stored key carries that prefix: two
 * builds whose encodings differ must not address each other's entries (see
 * CacheSerializationNamespaceTest). The prefix leaves all three properties above intact -- the
 * sampled envelope is still present verbatim, which is what the pattern deletes and the counter
 * lookups rely on -- so what is pinned here is that the envelope survives unchanged as the
 * SUFFIX, not that it is the whole key. Asserting equality against the bare sample would be
 * asserting the absence of the namespace.
 */
class CacheKeyFormatTest extends AnyFlatSpec with Matchers {

  "Redis memoize keys" should "match the live-sampled scalacache 0.28 format, sync variant" in {
    // Sampled live: a rate-limit counter entry.
    val key = Redis.redisMemoKey("memoizeSyncWithRedis", Some("obp_dev_rl_active_1_6f801b42-ed41-4856-8308-ddd2b853538a_2026-08-15-14"), 3)
    key should endWith(
      "code.api.cache.Redis.memoizeSyncWithRedis(Some(obp_dev_rl_active_1_6f801b42-ed41-4856-8308-ddd2b853538a_2026-08-15-14))()()()")
    key should startWith(Redis.serializationNamespace)
  }

  it should "match the live-sampled format for a MappedMetrics entry with a composite key" in {
    // Sampled live: the composite (class, method, args) cache keys pass through unescaped.
    val composite = "(code.metrics.MappedMetrics,getTopApisFuture,List(OBPLimit(50), OBPOffset(0)))"
    val key = Redis.redisMemoKey("memoizeSyncWithRedis", Some(composite), 3)
    key should endWith(s"code.api.cache.Redis.memoizeSyncWithRedis(Some($composite))()()()")
    key should startWith(Redis.serializationNamespace)
  }

  it should "render the Future variant with the same three excluded parameter lists" in {
    val key = Redis.redisMemoKey("memoizeWithRedis", Some("k"), 3)
    key should endWith("code.api.cache.Redis.memoizeWithRedis(Some(k))()()()")
    key should startWith(Redis.serializationNamespace)
  }

  "InMemory memoize keys" should "render sync with two excluded lists and Future with three" in {
    // The sync wrapper has parameter lists (cacheKey)(ttl)(f): one rendered, two excluded.
    InMemory.inMemoryMemoKey("memoizeSyncWithInMemory", Some("k"), 2) shouldBe
      "code.api.cache.InMemory.memoizeSyncWithInMemory(Some(k))()()"
    // The Future wrapper has (cacheKey)(ttl)(f)(m): one rendered, three excluded.
    InMemory.inMemoryMemoKey("memoizeWithInMemory", Some("k"), 3) shouldBe
      "code.api.cache.InMemory.memoizeWithInMemory(Some(k))()()()"
  }

  "the stored key" should "contain the logical cache key verbatim, so *key* patterns keep matching" in {
    val logical = "rate-limiting-CONSUMER42-PER_HOUR"
    Redis.redisMemoKey("memoizeSyncWithRedis", Some(logical), 3) should include(logical)
    InMemory.inMemoryMemoKey("memoizeSyncWithInMemory", Some(logical), 2) should include(logical)
  }
}


import scala.jdk.CollectionConverters._

/**
 * The exact string scalacache derives for a cache key, pinned.
 *
 * PR #2890 moves scalacache 0.9.3 -> 0.28.0, which is a rewrite rather than an upgrade: the
 * backends were restructured, `memoize` became `memoizeF`, `ttl: Duration` became `Some(ttl)`,
 * and the Guava store's value type changed. Key derivation lives inside that rewrite.
 *
 * Why the existing coverage is not enough. InMemoryCachingTest asserts:
 *
 *     InMemory.countKeys(s"*$key*") should equal(1)
 *
 * which proves the caller's own string survives INTO the derived key -- a substring check. It
 * passes for any prefix, any separator, any argument rendering, as long as the caller's string
 * is in there somewhere. That is not enough for the thing that actually depends on this format:
 *
 *     NewStyle.scala:3306   Redis.deleteKeysByPattern("*getMethodRoutings*")
 *     Caching.scala:121     Redis.deleteKeysByPattern(s"${RATE_LIMIT_ACTIVE_PREFIX}${id}_*")
 *
 * Invalidation is pattern matching over the whole key. If the derivation grows a prefix, changes
 * a separator, or renders the enclosing method differently, the cache keeps caching and the
 * invalidation quietly stops matching anything -- `deleteKeysByPattern` returns 0 and swallows
 * it, so nothing anywhere reports a problem. Stale MethodRoutings then serve for a full TTL.
 *
 * So this asserts the FULL derived key, read back out of the store, not a substring of it.
 * The value is written down rather than computed, because a check that derives its expectation
 * the same way the code does cannot fail.
 *
 * If this test breaks after a scalacache change, the fix is NOT to update the expected string
 * until it passes. It is to check every deleteKeysByPattern call site against the new format
 * first -- this test failing is that review being demanded, which is its whole purpose.
 */
class CacheKeyDerivationTest extends AnyFlatSpec with Matchers {

  private val ttl = 60.seconds

  private def storedKeys: Set[String] =
    InMemory.underlyingGuavaCache.asMap().keySet().asScala.toSet

  private def freshMarker(tag: String): String =
    s"CacheKeyFormatTest-$tag-${java.util.UUID.randomUUID().toString.take(8)}"

  /**
   * The derivation, recorded from the scalacache on this branch.
   *
   * Discovered by writing this test with the naive expectation (the bare caller key) and reading
   * what came back. The wrapper is scalacache's MethodCallToStringConverter: the enclosing
   * method's fully-qualified name, then each parameter list rendered in order -- so the caller's
   * key arrives inside `Some(...)`, and the two @cacheKeyExclude lists render as empty `()`.
   *
   * That wrapper is exactly what a substring assertion cannot see, and exactly what an
   * invalidation glob has to survive.
   */
  private def derivedKey(callerKey: String): String =
    s"code.api.cache.InMemory.memoizeSyncWithInMemory(Some($callerKey))()()"

  "the derived cache key" should "be exactly the recorded derivation of the caller's key" in {
    val marker = freshMarker("exact")
    val before = storedKeys
    Caching.memoizeSyncWithImMemory(Some(marker))(ttl)("stored")
    val added = storedKeys -- before

    withClue(s"one memoize call should add exactly one key; added=${added.mkString(", ")} ") {
      added.size shouldBe 1
    }

    // THE assertion. Not `contains`, not a regex -- the whole string.
    //
    // Recorded from the scalacache on this branch. Any change to it means every
    // deleteKeysByPattern pattern in the codebase has to be re-read against the new shape
    // before this line is updated.
    withClue(s"the derived key is '${added.head}' but was recorded as '${derivedKey(marker)}'. " +
             s"Before changing the expectation, check NewStyle.scala:3306's " +
             s"\"*getMethodRoutings*\" and Caching.scala:121/132's rate-limit patterns still " +
             s"match the new shape -- deleteKeysByPattern returns 0 and swallows a miss, so a " +
             s"broken pattern is silent. ") {
      added.head shouldBe derivedKey(marker)
    }
  }

  it should "keep the pattern MethodRouting invalidation depends on matchable" in {
    // The real one. NewStyle.invalidateMethodRoutingCache issues
    // deleteKeysByPattern("*getMethodRoutings*"), so a key derived from a caller string
    // containing "getMethodRoutings" must be matched by that glob.
    val marker = s"(CacheKeyFormatTest,getMethodRoutings,${java.util.UUID.randomUUID().toString.take(8)})"
    val before = storedKeys
    Caching.memoizeSyncWithImMemory(Some(marker))(ttl)("routings")
    val added = (storedKeys -- before).head

    val glob = "*getMethodRoutings*"
    val regex = glob.replace("*", ".*")
    withClue(s"derived key '$added' is not matched by the invalidation pattern '$glob'. " +
             s"NewStyle.invalidateMethodRoutingCache would delete nothing and report nothing. ") {
      added.matches(regex) shouldBe true
    }
    InMemory.countKeys(glob) should be >= 1
  }

  it should "give different callers different keys" in {
    // A derivation that collapsed distinct callers onto one key would serve one caller's value
    // to another -- and every substring assertion in the suite would still pass.
    val a = freshMarker("distinct-a")
    val b = freshMarker("distinct-b")
    val before = storedKeys
    Caching.memoizeSyncWithImMemory(Some(a))(ttl)("value-a")
    Caching.memoizeSyncWithImMemory(Some(b))(ttl)("value-b")
    val added = storedKeys -- before

    added.size shouldBe 2
    Caching.memoizeSyncWithImMemory(Some(a))(ttl)("recomputed-a") shouldBe "value-a"
    Caching.memoizeSyncWithImMemory(Some(b))(ttl)("recomputed-b") shouldBe "value-b"
  }

  it should "not let one caller's key be a prefix-collision of another's" in {
    // `deleteKeysByPattern` globs. If a key were rendered such that one caller's string is a
    // prefix of another's WITHOUT a delimiter, invalidating the first would take out the second.
    val short = freshMarker("collide")
    val long  = s"${short}-extended"
    val before = storedKeys
    Caching.memoizeSyncWithImMemory(Some(short))(ttl)("short-value")
    Caching.memoizeSyncWithImMemory(Some(long))(ttl)("long-value")
    val added = storedKeys -- before

    added.size shouldBe 2
    withClue(s"keys: ${added.mkString(", ")} -- an exact-match glob on the shorter key must not " +
             s"also match the longer one. ") {
      added.count(_ == derivedKey(short)) shouldBe 1
      added.count(_ == derivedKey(long)) shouldBe 1
    }

    // The collision the wrapper actually prevents: because the caller key is ENCLOSED rather
    // than concatenated, the `)` that follows it is a hard delimiter -- the shorter key's full
    // derivation is not a prefix of the longer one's, so nothing anchored on it can reach the
    // longer entry.
    //
    // Asserted by set membership rather than through countKeys, deliberately. countKeys builds
    // its matcher as `pattern.replace("*", ".*").r` (InMemory.scala:49), so every other regex
    // metacharacter in the pattern is live -- and a derived key is full of them: `(`, `)` and
    // `.` all appear in `...memoizeSyncWithInMemory(Some(x))()()`. Passing a whole derived key
    // to countKeys therefore asks a question about regex syntax, not about key collision.
    // (That is a real sharp edge in a helper whose callers pass user-shaped strings, but it
    // belongs in its own finding rather than being asserted sideways from here.)
    withClue(s"the shorter key's derivation must not be a prefix of the longer one's. ") {
      derivedKey(long).startsWith(derivedKey(short)) shouldBe false
    }
  }
}
