package code.api.cache


import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Covers the Guava-backed half of the cache.
 *
 * Redis has RedisDeserializeMissTest and MethodRoutingCacheInvalidationTest; the in-memory backend
 * had nothing, despite six business call sites going through Caching.memoizeSyncWithImMemory -
 * resource docs, web UI props, dynamic resource docs, and two reflection lookups in APIUtil. So the
 * one thing a cache has to do, and the conditions under which it must not do it, were unasserted.
 *
 * The key-shape scenarios matter as much as the hit/miss ones. scalacache derives the cache key
 * from the enclosing method and its non-excluded arguments, which is why the caller-supplied string
 * ends up inside it, and why NewStyle's deleteKeysByPattern("*getMethodRoutings*") works at all.
 * Anything that changes how keys are derived breaks that pattern matching silently: the cache still
 * caches, the invalidation just stops finding anything. Pinning the shape here makes such a change
 * show up as a test failure rather than as a stale entry in production.
 */
class InMemoryCachingTest extends AnyFlatSpec with Matchers {

  private val ttl = 10.seconds

  /** A distinct key per scenario - the Guava cache is a shared object across this suite. */
  private def freshKey(name: String): String = s"InMemoryCachingTest-$name-${System.nanoTime()}"

  "memoizeSyncWithImMemory" should "compute once and serve the cached value afterwards" in {
    val key = freshKey("hit")
    var computations = 0
    def call(): String = Caching.memoizeSyncWithImMemory(Some(key))(ttl) {
      computations += 1
      s"value-$computations"
    }

    call() should equal("value-1")
    call() should equal("value-1")
    call() should equal("value-1")
    computations should equal(1)
  }

  it should "keep entries for different keys apart" in {
    val keyA = freshKey("distinct-a")
    val keyB = freshKey("distinct-b")
    def call(key: String, value: String): String =
      Caching.memoizeSyncWithImMemory(Some(key))(ttl)(value)

    call(keyA, "a") should equal("a")
    call(keyB, "b") should equal("b")
    // If the two shared a key, the second would have served the first one's value.
    call(keyA, "ignored") should equal("a")
    call(keyB, "ignored") should equal("b")
  }

  it should "not cache when the ttl is zero" in {
    val key = freshKey("zero-ttl")
    var computations = 0
    def call(): Int = Caching.memoizeSyncWithImMemory(Some(key))(Duration.Zero) {
      computations += 1
      computations
    }

    call(); call()
    computations should equal(2)
  }

  it should "not cache when no key is given" in {
    var computations = 0
    def call(): Int = Caching.memoizeSyncWithImMemory(None)(ttl) {
      computations += 1
      computations
    }

    call(); call()
    computations should equal(2)
  }

  it should "put the caller's key inside the derived cache key" in {
    // This is the contract deleteKeysByPattern relies on. It is asserted through countKeys, which
    // matches against the keys actually stored in the Guava cache.
    val key = freshKey("shape")
    Caching.memoizeSyncWithImMemory(Some(key))(ttl)("stored")

    InMemory.countKeys(s"*$key*") should equal(1)
    InMemory.countKeys(s"*$key-no-such-suffix*") should equal(0)
  }

  "memoizeWithImMemory" should "compute once for a Future-returning block" in {
    val key = freshKey("future-hit")
    var computations = 0
    def call(): String = Await.result(
      Caching.memoizeWithImMemory(Some(key))(ttl) {
        computations += 1
        scala.concurrent.Future.successful(s"value-$computations")
      },
      10.seconds
    )

    call() should equal("value-1")
    call() should equal("value-1")
    computations should equal(1)
  }
}
