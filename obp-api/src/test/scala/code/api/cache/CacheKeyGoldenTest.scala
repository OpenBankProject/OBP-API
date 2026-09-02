package code.api.cache

import code.model.dataAccess.{AuthUser, ResourceUser}
import code.setup.ServerSetup

/**
 * Golden A/B guard for the CacheKeyFromArguments explicitization (a cross-user cache
 * leak is the failure mode: a memoize key that loses an argument dimension serves one
 * caller's entry to every other caller for the whole TTL).
 *
 * These tests drive REAL cached methods end to end and then look the produced key up in
 * the REAL Redis. The expected strings encode the macro-era format - (classFullName,
 * methodName, args.mkString("_")) wrapped in the memoize envelope - and were captured
 * while the com.tesobe macro still generated the keys, so the same suite passing before
 * and after the explicitization proves the hand-written keys are byte-identical, argument
 * dimensions included.
 *
 * Two sites intentionally NO LONGER match the macro-era format, and neither is covered by a
 * scenario here: NewStyle.getEndpointMappings and LocalMappedConnectorInternal
 * .getCurrentFxRateCached have dropped callContext from their key. The macro included it
 * because neither declared @CacheKeyOmit, but CallContext renders per-request state, so those
 * keys were unique per request - never a hit, one leaked Redis entry per call. Losing that
 * "dimension" cannot leak across callers the way the failure mode above describes: it was
 * request identity, not an argument the result depends on. CacheKeyCallContextTest guards the
 * invariant going forward.
 */
class CacheKeyGoldenTest extends ServerSetup {

  // The envelope is spelled out rather than derived, which is the point of a golden test: it
  // fails if the rewrite changes the shape. The namespace is the one part taken from the code,
  // because it is deliberately build-dependent (Redis.serializationNamespace isolates builds
  // whose encodings differ) - pinning its current value here would pin the Scala version.
  private def expectedRedisKey(cacheKey: String): String =
    Redis.serializationNamespace +
      s"code.api.cache.Redis.memoizeSyncWithRedis(Some($cacheKey))()()()"

  /**
   * Delete the EXACT expected key before exercising the method, so the assertion can only be
   * satisfied by a key this build has just written. Without it a leftover entry from an earlier
   * build would satisfy `contain` even if the current build now writes a different key - which
   * is precisely the regression this suite exists to catch.
   *
   * Exact key, not a wildcard: the local runner shares one Redis across four parallel shards,
   * and a pattern delete would evict another shard's live entries mid-run. Dropping this single
   * read-through entry only costs the next reader a recompute.
   */
  private def afterClearing[A](expectedKey: String)(f: => A): A = {
    Redis.deleteKeysByPattern(expectedKey)
    f
  }

  Feature("memoize keys survive the macro-to-explicit rewrite byte-identically") {

    Scenario("AuthUser.updateComputedLocale keys by (sessionId, computedLocale) - the session dimension") {
      val session = s"golden-${java.util.UUID.randomUUID().toString}"
      val expected = expectedRedisKey(s"(code.model.dataAccess.AuthUser,updateComputedLocale,${session}_en_GB)")
      afterClearing(expected)(AuthUser.updateComputedLocale(session, "en_GB"))
      Redis.scanKeys(s"*$session*") should contain(expected)
    }

    Scenario("ResourceUser.getDistinctProviders keys with an empty argument segment") {
      val expected = expectedRedisKey("(code.model.dataAccess.ResourceUser,getDistinctProviders,)")
      afterClearing(expected)(ResourceUser.getDistinctProviders)
      Redis.scanKeys("*getDistinctProviders*") should contain(expected)
    }

    Scenario("MappedMetrics.getAllAggregateMetricsBox keys by its full query-parameter list") {
      import code.api.util.{OBPFromDate, OBPLimit, OBPOffset, OBPToDate}
      val marker = 7654321 // an offset value unlikely to collide with other suites' keys
      val from = new java.util.Date(0L)
      val to = new java.util.Date(86400000L)
      val params = List(OBPLimit(1), OBPOffset(marker), OBPFromDate(from), OBPToDate(to))
      // The argument segment is List(...).mkString of the SAME runtime values, so the
      // Date.toString rendering is interpolated rather than hardcoded; the structure
      // (class, method, args-joined-by-underscore) matches the live-sampled entries.
      val expected = expectedRedisKey(s"(code.metrics.MappedMetrics,getAllAggregateMetricsBox,List(OBPLimit(1), OBPOffset($marker), OBPFromDate($from), OBPToDate($to))_true)")
      afterClearing(expected)(code.metrics.MappedMetrics.getAllAggregateMetricsBox(params, true))
      Redis.scanKeys(s"*$marker*") should contain(expected)
    }
  }
}
