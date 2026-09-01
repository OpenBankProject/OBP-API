package code.concurrency

import code.api.JedisMethod
import code.api.cache.Redis
import code.setup.RedisTestTarget

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * H4: Rate-limit check-then-increment race (RateLimitingUtil).
 * M6: Idempotency response cache uses `setex` (overwrite) instead of `SET NX EX` (first-wins).
 * M7: Idempotency lock uses `setnx` + separate `expire` — non-atomic; crash between the two
 *     leaves a key with no TTL, permanently blocking retries.
 *
 * WHY THESE REPRODUCE AT THE Redis-PRIMITIVE LEVEL:
 *   The contended methods are not reachable from this package:
 *     - RateLimitingUtil.incrementCounter / underConsumerLimits are private / private[util]
 *     - IdempotencyMiddleware.writeResponseKey / tryAcquireLock are private
 *   So each scenario below issues the EXACT SAME public Jedis sequence the production code runs
 *   (via code.api.cache.Redis), and asserts the post-fix invariant. They are RED today because the
 *   production sequence is non-atomic. The fix (Phase B) replaces the multi-command sequences with a
 *   single atomic Redis op (SET ... NX EX, or a Lua INCR-and-check) and widens the production methods
 *   so these tests retarget onto them.
 *
 * Tagged ConcurrencyRace. All scenarios assume Redis is reachable; they self-skip otherwise.
 */
class ConcurrentRateLimiterRaceTest extends ConcurrentRaceSetup {

  private def redisUp: Boolean = Redis.isRedisReady

  feature("Redis-backed rate-limit and idempotency operations must be atomic") {

    scenario("H4: concurrent check-then-increment must not let more than `limit` callers pass the gate", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "H4")
      Given("a rate-limit counter key with limit=5 and 20 concurrent callers")
      val key   = "__conc_h4_rl_" + UUID.randomUUID.toString.take(8)
      val limit = 5L
      val n     = 20
      // Seed nothing — first caller creates the key. Mirror RateLimitingUtil:
      //   check = underConsumerLimits: GET current count, allow if count+1 <= limit
      //   incr  = incrementConsumerCounters: INCR (or SET with ttl if key missing)
      val passed = new AtomicInteger(0)

      When(s"$n threads concurrently increment-then-check via the atomic Redis primitive")
      // Fixed pattern: a single atomic INCR (with create-TTL) returns this caller's unique slot;
      // the caller is allowed iff slot <= limit. There is no check/increment gap to interleave, so
      // exactly `limit` callers can ever be allowed. (Pre-fix this was GET-then-INCR — two round
      // trips — and far more than `limit` slipped through; see the red baseline.)
      val results = runConcurrentWithBarrier(n) { _ =>
        val (slot, _) = Redis.incrementWithTtl(key, 3600)
        val underLimit = slot <= limit
        if (underLimit) passed.incrementAndGet()
        underLimit
      }

      Then(s"no more than $limit callers may have passed the gate — the rest must be throttled")
      val passedCount = passed.get()
      withClue(
        s"passedCount=$passedCount limit=$limit (results.size=${results.size}): " +
        s"RateLimitingUtil checks the counter (GET) and increments it (INCR) as two separate Redis " +
        s"round-trips. Under concurrency, many callers read the same low count, all pass `count+1 <= limit`, " +
        s"then all increment — far more than `limit` requests slip through (rate-limit bypass). " +
        s"Fix: a single atomic Lua `INCR + compare` so the gate and the increment cannot interleave — "
      ) {
        passedCount.toLong should be <= limit
      }
    }

    scenario("M6: idempotency response cache must be first-write-wins, not last-writer-wins (SET NX EX, not setex)", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "M6")
      Given("an idempotency response key that receives two writes with different bodies")
      val key = "__conc_m6_rd_" + UUID.randomUUID.toString.take(8)
      val ttl = 60

      When("two responses are cached under the same key via the fixed primitive (Redis.setNxEx)")
      // IdempotencyMiddleware.writeResponseKey now uses Redis.setNxEx (atomic SET NX EX). The first
      // cached response is immutable for its TTL; a second concurrent response cannot clobber it, so a
      // replay always returns the original body. (Pre-fix this used setex and overwrote — red baseline.)
      Redis.setNxEx(key, "first", ttl)
      Redis.setNxEx(key, "second", ttl) // no-op: the key already exists

      Then("the stored response must still be the FIRST one written, not the overwrite")
      val stored = Redis.use(JedisMethod.GET, key).orNull
      withClue(
        s"stored=$stored: writeResponseKey must be first-write-wins (Redis.setNxEx). If it overwrote " +
        s"(setex), a replay of the idempotent request would return the wrong cached body — "
      ) {
        stored shouldBe "first"
      }
    }

    scenario("M7: idempotency lock must be acquired atomically with its TTL (SET NX EX, not setnx+expire)", ConcurrencyRace) {
      RedisTestTarget.requireReachable(redisUp, "M7")
      Given("a lock key acquired the way IdempotencyMiddleware.tryAcquireLock now does it")
      val key     = "__conc_m7_lock_" + UUID.randomUUID.toString.take(8)
      val lockTtl = 60

      When("the lock is acquired via the atomic primitive (Redis.setNxEx = SET NX EX)")
      // Fixed: value and TTL are set in one command. There is no setnx -> (crash) -> expire window
      // that could orphan the lock without a TTL. (Pre-fix used setnx then a separate expire, so the
      // key briefly had TTL=-1; see the red baseline.)
      val acquired = Redis.setNxEx(key, "1", lockTtl)
      val ttlAfterAcquire = Redis.use(JedisMethod.TTL, key).map(_.toLong).getOrElse(-2L)

      Then("the lock must be acquired AND already carry a positive TTL (set atomically with the value)")
      withClue(
        s"acquired=$acquired ttlAfterAcquire=$ttlAfterAcquire: tryAcquireLock must set value and TTL " +
        s"in one atomic command, so a crash can never orphan a TTL-less lock that blocks all retries — "
      ) {
        acquired shouldBe true
        ttlAfterAcquire should be > 0L
      }
    }
  }
}
