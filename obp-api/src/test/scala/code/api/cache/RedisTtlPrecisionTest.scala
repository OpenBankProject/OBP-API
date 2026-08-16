package code.api.cache

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
 * A sub-second TTL must expire when it says it does.
 *
 * scalacache's Redis backend stored entries with millisecond precision, so a 300ms TTL was a
 * 300ms TTL. The in-house memoize layer that replaced it originally wrote with SETEX, whose
 * unit is whole seconds, and clamped with max(1, ttl.toSeconds) - which silently turned every
 * sub-second TTL into one second.
 *
 * No current call site passes one: connector.cache.ttl.seconds.* values are whole seconds
 * multiplied to millis, and a zero TTL short-circuits in Caching before reaching Redis. So
 * this is a latent difference rather than a live bug - but the entire claim made for that
 * replacement is that key, value and TTL semantics are unchanged, and "unchanged except for
 * TTLs under a second" is a different and much weaker claim.
 */
class RedisTtlPrecisionTest extends AnyFlatSpec with Matchers {

  "a sub-second TTL" should "expire within its own window, not be rounded up to a second" in {
    val key = Some(s"ttl-precision-${java.util.UUID.randomUUID()}")
    var computed = 0

    def call(): Int = Redis.memoizeSyncWithRedis(key)(300.milliseconds) {
      computed += 1
      computed
    }

    call() should be(1)
    call() should be(1) // still inside the window: served from cache, source block not re-run

    Thread.sleep(600) // past 300ms, and past the point where a 1s rounding would still hold

    call() should be(2) // expired, so the source block runs again
    computed should be(2)
  }
}
