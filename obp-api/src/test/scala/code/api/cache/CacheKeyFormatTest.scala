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
 */
class CacheKeyFormatTest extends AnyFlatSpec with Matchers {

  "Redis memoize keys" should "match the live-sampled scalacache 0.28 format, sync variant" in {
    // Sampled live: a rate-limit counter entry.
    Redis.redisMemoKey("memoizeSyncWithRedis", Some("obp_dev_rl_active_1_6f801b42-ed41-4856-8308-ddd2b853538a_2026-08-15-14"), 3) shouldBe
      "code.api.cache.Redis.memoizeSyncWithRedis(Some(obp_dev_rl_active_1_6f801b42-ed41-4856-8308-ddd2b853538a_2026-08-15-14))()()()"
  }

  it should "match the live-sampled format for a MappedMetrics entry with a composite key" in {
    // Sampled live: the composite (class, method, args) cache keys pass through unescaped.
    val composite = "(code.metrics.MappedMetrics,getTopApisFuture,List(OBPLimit(50), OBPOffset(0)))"
    Redis.redisMemoKey("memoizeSyncWithRedis", Some(composite), 3) shouldBe
      s"code.api.cache.Redis.memoizeSyncWithRedis(Some($composite))()()()"
  }

  it should "render the Future variant with the same three excluded parameter lists" in {
    Redis.redisMemoKey("memoizeWithRedis", Some("k"), 3) shouldBe
      "code.api.cache.Redis.memoizeWithRedis(Some(k))()()()"
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
