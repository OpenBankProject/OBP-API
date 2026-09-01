package code.api.cache

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}

import code.setup.RedisTestTarget

import scala.concurrent.duration._

/**
 * Exercises the two cache behaviours the MethodRouting cache relies on, end-to-end
 * against a real Redis (each scenario is cancelled via assume() when no Redis is
 * reachable, so the suite is safe in environments without one):
 *
 * 1. Pattern invalidation: NewStyle.invalidateMethodRoutingCache() issues
 *    Redis.deleteKeysByPattern("*getMethodRoutings*"). The memoize key embeds the
 *    cacheKey argument verbatim, so seeding a key whose cacheKey contains the literal
 *    "getMethodRoutings" and pattern-deleting it must force the next read to recompute.
 *
 * 2. Self-healing on corrupt entries: overwriting a memoized key's bytes with garbage
 *    must NOT surface a sentinel/ClassCastException on the next read — the codec throws,
 *    scalacache treats the read as a miss, recomputes, and repopulates the key.
 */
class MethodRoutingCacheInvalidationTest extends FlatSpec with Matchers {

  private def memoize[A](cacheKey: String, ttl: Duration)(f: => A)(implicit m: Manifest[A]): A =
    Caching.memoizeSyncWithProvider(Some(cacheKey))(ttl)(f)

  "deleteKeysByPattern(*getMethodRoutings*)" should "invalidate memoized entries so the next read recomputes" in {
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the MethodRouting cache checks")
    val marker = s"inv-${UUID.randomUUID().toString}"
    val cacheKey = s"(MethodRoutingCacheInvalidationTest,getMethodRoutings,$marker)"
    var computations = 0
    def compute: List[String] = { computations += 1; List(s"value-$computations") }

    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-1")
    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-1")
    computations shouldBe 1

    val deleted = Redis.deleteKeysByPattern(s"*$marker*")
    deleted should be >= 1

    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-2")
    computations shouldBe 2
  }

  "a corrupted cache entry" should "behave as a miss: recompute once and repopulate with valid bytes" in {
    RedisTestTarget.requireReachable(Redis.isRedisReady, "the MethodRouting cache checks")
    val marker = s"poison-${UUID.randomUUID().toString}"
    val cacheKey = s"(MethodRoutingCacheInvalidationTest,getMethodRoutings,$marker)"
    var computations = 0
    def compute: List[String] = { computations += 1; List(s"value-$computations") }

    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-1")
    computations shouldBe 1

    val keys = Redis.scanKeys(s"*$marker*")
    keys should not be empty
    val jedis = Redis.jedisPool.getResource
    try keys.foreach(k => jedis.set(k.getBytes("UTF-8"), Array[Byte](0x7f, -1, 3, 9, 42, 0, 0x11)))
    finally jedis.close()

    // Corrupt read -> codec throws -> scalacache miss -> exactly one recompute, no sentinel/CCE.
    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-2")
    computations shouldBe 2

    // The key was repopulated with valid bytes: the next read is a HIT again.
    memoize(cacheKey, 30.seconds)(compute) shouldBe List("value-2")
    computations shouldBe 2

    Redis.deleteKeysByPattern(s"*$marker*")
  }
}
