package code.api.util

import code.api.JedisMethod
import code.api.cache.Redis
import code.setup.RedisTestTarget
import org.scalatest.{FlatSpec, Matchers}

/**
 * `NewStyle.function.invalidateEndpointMappingCache` guards against the write-then-invalidate
 * race that comes with the cache key fix in `getEndpointMappingsCached`: while CallContext was
 * part of the memoize key nothing could ever hit, so a stale entry was unreachable by
 * construction. Now that the cache genuinely hits, a reader that fetched the pre-write value a
 * moment earlier can still complete its own cache write AFTER the immediate delete finishes,
 * silently reintroducing the stale entry for the rest of `endpointMapping.cache.ttl.seconds` --
 * nothing else would clear it before the next write.
 *
 * Racing two real threads against real DB/Redis latency would make this test flaky by
 * construction (the window it is trying to hit is exactly the thing that is nondeterministic).
 * Planting a key that matches the invalidation glob directly stands in for the straggler write
 * instead -- the same deterministic-simulation technique IdempotencyMiddlewareTest uses for its
 * own concurrency scenarios -- and this asserts the mechanism that is supposed to catch it: a
 * second, delayed delete.
 */
class EndpointMappingCacheInvalidationTest extends FlatSpec with Matchers {

  private def redis(): Unit =
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the endpoint-mapping cache invalidation race guard")

  "invalidateEndpointMappingCache" should "clear a straggler entry that lands after the immediate delete" in {
    redis()
    // Any key matching the same glob the real memoized entry would (*getEndpointMappings*)
    // stands in for the straggler -- the exact scalacache-derived key shape is not what this
    // guards, only that a second sweep eventually clears whatever landed in the gap.
    val stragglerKey = "test_ns:code.api.util.NewStyle.function.getEndpointMappingsCached(Some(straggler))()"
    Redis.use(JedisMethod.SET, stragglerKey, None, Some("[]"))
    withClue("test setup failed to plant the straggler key: ") {
      Redis.use(JedisMethod.GET, stragglerKey, None, None) shouldBe Some("[]")
    }

    NewStyle.function.invalidateEndpointMappingCache()

    withClue("immediately after the call the straggler should already be gone once, but that " +
             "alone does not prove there is a SECOND delete -- see below") {
      Redis.use(JedisMethod.GET, stragglerKey, None, None) shouldBe None
    }

    // Simulate the race: the straggler write lands in the gap between the immediate delete and
    // the scheduled one.
    Redis.use(JedisMethod.SET, stragglerKey, None, Some("[]"))

    Thread.sleep(NewStyle.function.endpointMappingCacheInvalidationDelay.toMillis + 300)

    withClue("the delayed second invalidation must still clear a straggler that landed after " +
             "the first delete, or a concurrent read racing the write can leave a stale " +
             "endpoint-mapping list cached for the rest of the TTL: ") {
      Redis.use(JedisMethod.GET, stragglerKey, None, None) shouldBe None
    }
  }
}
