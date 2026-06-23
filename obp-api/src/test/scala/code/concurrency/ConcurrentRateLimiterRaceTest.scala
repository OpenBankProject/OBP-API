package code.concurrency

import code.api.JedisMethod
import code.api.cache.Redis

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
      assume(redisUp, "Redis not reachable — skipping H4")
      Given("a rate-limit counter key with limit=5 and 20 concurrent callers")
      val key   = "__conc_h4_rl_" + UUID.randomUUID.toString.take(8)
      val limit = 5L
      val n     = 20
      // Seed nothing — first caller creates the key. Mirror RateLimitingUtil:
      //   check = underConsumerLimits: GET current count, allow if count+1 <= limit
      //   incr  = incrementConsumerCounters: INCR (or SET with ttl if key missing)
      val passed = new AtomicInteger(0)

      When(s"$n threads concurrently run [check limit then increment], replicating RateLimitingUtil")
      val results = runConcurrentWithBarrier(n) { _ =>
        // --- check phase (underConsumerLimits) ---
        val current = Redis.use(JedisMethod.GET, key).map(_.toLong).getOrElse(0L)
        val underLimit = current + 1 <= limit
        if (underLimit) {
          passed.incrementAndGet()
          // --- increment phase (incrementConsumerCounters) ---
          val ttlOpt = Redis.use(JedisMethod.TTL, key).map(_.toLong).getOrElse(-2L)
          if (ttlOpt == -2L) Redis.use(JedisMethod.SET, key, Some(3600), Some("1"))
          else Redis.use(JedisMethod.INCR, key)
        }
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
      assume(redisUp, "Redis not reachable — skipping M6")
      Given("an idempotency response key that receives two writes with different bodies")
      val key = "__conc_m6_rd_" + UUID.randomUUID.toString.take(8)
      val ttl = 60

      When("two responses are cached under the same key via the production primitive (Redis.use SET = setex)")
      // IdempotencyMiddleware.writeResponseKey caches via setex (Redis.use SET-with-ttl), which
      // UNCONDITIONALLY overwrites. So a second response for the same idempotency key clobbers the
      // first — a replay of the original request can then return the WRONG body.
      // The correct contract is first-write-wins: the first cached response is immutable for its TTL.
      Redis.use(JedisMethod.SET, key, Some(ttl), Some("first"))
      Redis.use(JedisMethod.SET, key, Some(ttl), Some("second"))

      Then("the stored response must still be the FIRST one written, not the overwrite")
      val stored = Redis.use(JedisMethod.GET, key).orNull
      withClue(
        s"stored=$stored: writeResponseKey uses `setex` (Redis.use SET-with-ttl), which overwrites — the " +
        s"second write clobbers the first cached idempotent response. Fix: atomic `SET key value EX ttl NX` " +
        s"(first-write-wins). Phase B adds Redis.setNxEx and retargets this test onto it — "
      ) {
        // RED today: setex overwrote → stored == "second". GREEN after Phase B: SET NX EX keeps "first".
        stored shouldBe "first"
      }
    }

    scenario("M7: idempotency lock must be acquired atomically with its TTL (SET NX EX, not setnx+expire)", ConcurrencyRace) {
      assume(redisUp, "Redis not reachable — skipping M7")
      Given("a lock key acquired the way IdempotencyMiddleware.tryAcquireLock does it")
      val key       = "__conc_m7_lock_" + UUID.randomUUID.toString.take(8)
      val lockTtl   = 60

      When("the lock is acquired via setnx then a separate expire (the non-atomic production sequence)")
      // Production: val acquired = j.setnx(key,"1")==1; if(acquired) j.expire(key, lockTtl)
      // If the process crashes between setnx and expire, the key lives forever with TTL=-1,
      // permanently blocking every future retry of that idempotency key.
      val jedis = Redis.jedisPool.getResource
      val ttlAfterSetnxOnly: Long =
        try {
          jedis.del(key)
          val acquired = jedis.setnx(key, "1") == 1L
          // SIMULATE the crash window: expire has NOT run yet.
          val ttl = jedis.ttl(key) // -1 == key exists with NO expiry → orphaned lock
          acquired // keep acquired referenced
          ttl
        } finally jedis.close()

      Then("immediately after acquiring, the lock key MUST already carry a positive TTL (atomic acquire)")
      withClue(
        s"ttlAfterSetnxOnly=$ttlAfterSetnxOnly: " +
        s"tryAcquireLock does setnx then a SEPARATE expire. Between the two the key has TTL=-1 (no " +
        s"expiry); a crash there orphans the lock forever and blocks all retries of that idempotency key. " +
        s"Fix: a single atomic SET key value EX 60 NX sets value and TTL in one command — there is no window. " +
        s"This test asserts the post-fix invariant: the TTL is set atomically with the value — "
      ) {
        // RED today: setnx-only leaves TTL = -1. GREEN after Phase B (atomic SET NX EX).
        ttlAfterSetnxOnly should be > 0L
      }
    }
  }
}
