package code.api.util

import java.security.MessageDigest

import code.api.Constant.CALL_COUNTER_PREFIX
import code.util.Helper.MdcLoggable

/** Pre-credential-check rate limiter for authentication endpoints.
 *
 *  Distinct from [[RateLimitingUtil]] (which fires post-auth, keyed by consumer_id).
 *  This limiter fires BEFORE the password check, keyed by source IP and (provider, username),
 *  to defend against brute-force, credential-stuffing, and lockout-DoS attacks.
 *
 *  Three counters checked per call:
 *   - per-IP per minute   — burst defence
 *   - per-IP per hour     — sustained-spray defence
 *   - per-(provider, username) per minute — single-account targeting and DoS
 *
 *  Modes:
 *   - `enabled = false` (default): skipped entirely, no Redis calls
 *   - `enabled = true && mode = "shadow"` (default when enabled): trips are logged, request allowed
 *   - `enabled = true && mode = "enforce"`: trips return Left(Exceeded), caller renders 429
 *
 *  Fail-open: if Redis is unavailable, [[RateLimitingUtil.incrementCounter]] returns (-1, -1)
 *  for that counter and we skip its check. Auth is never blocked by Redis outage.
 */
object AuthRateLimiter extends MdcLoggable {
  import RateLimitingPeriod._

  /** A counter that exceeded its limit. Carries the data the caller needs to render a 429. */
  case class Exceeded(counter: String, current: Long, limit: Long, retryAfterSeconds: Long)

  private def enabled: Boolean         = APIUtil.getPropsAsBoolValue("auth.rate_limit.enabled", false)
  private def mode: String             = APIUtil.getPropsValue("auth.rate_limit.mode", "shadow")
  private def perIpPerMinute: Long     = APIUtil.getPropsAsLongValue("auth.rate_limit.per_ip.per_minute", 10L)
  private def perIpPerHour: Long       = APIUtil.getPropsAsLongValue("auth.rate_limit.per_ip.per_hour", 100L)
  private def perUserPerMinute: Long   = APIUtil.getPropsAsLongValue("auth.rate_limit.per_user.per_minute", 6L)

  def check(ip: String, provider: String, username: String): Either[Exceeded, Unit] = {
    if (!enabled) return Right(())

    val safeIp       = if (ip == null || ip.isEmpty) "unknown" else ip
    val safeProvider = if (provider == null || provider.isEmpty) "local" else provider
    val safeUser     = if (username == null) "" else username
    val userHash     = sha256Hex(safeUser).take(12)

    val counters: List[(String, String, LimitCallPeriod, Long)] = List(
      ("ip_per_minute",   buildKey(s"ip_$safeIp",                       PER_MINUTE), PER_MINUTE, perIpPerMinute),
      ("user_per_minute", buildKey(s"user_${safeProvider}_$userHash",   PER_MINUTE), PER_MINUTE, perUserPerMinute),
      ("ip_per_hour",     buildKey(s"ip_$safeIp",                       PER_HOUR),   PER_HOUR,   perIpPerHour)
    )

    val trips = counters.flatMap { case (name, key, period, limit) =>
      val (ttl, current) = RateLimitingUtil.incrementCounter(key, period)
      // current == -1 signals Redis-unavailable; fail open by skipping this counter.
      // limit <= 0 signals "disabled"; skip.
      if (current > 0 && limit > 0 && current > limit) {
        Some(Exceeded(name, current, limit, ttl))
      } else {
        None
      }
    }

    trips.headOption match {
      case None =>
        Right(())
      case Some(exceeded) if mode == "enforce" =>
        logger.warn(authRateLimitLog("trip", exceeded, safeIp, safeProvider, userHash))
        Left(exceeded)
      case Some(exceeded) =>
        // shadow mode: log what would have happened, allow the request through
        logger.warn(authRateLimitLog("shadow_trip", exceeded, safeIp, safeProvider, userHash))
        Right(())
    }
  }

  private def buildKey(scope: String, period: LimitCallPeriod): String =
    s"${CALL_COUNTER_PREFIX}auth_${scope}_${RateLimitingPeriod.toString(period)}"

  private def authRateLimitLog(event: String, e: Exceeded, ip: String, provider: String, userHash: String): String =
    s"event=auth_rate_limit_$event counter=${e.counter} ip=$ip provider=$provider username_hash=$userHash current=${e.current} limit=${e.limit} retry_after_s=${e.retryAfterSeconds}"

  private def sha256Hex(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(s.getBytes("UTF-8")).map(b => f"$b%02x").mkString
  }
}
