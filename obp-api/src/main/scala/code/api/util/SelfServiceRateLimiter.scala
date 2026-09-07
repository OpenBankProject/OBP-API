package code.api.util

import code.api.Constant.CALL_COUNTER_PREFIX
import code.api.util.ErrorMessages.{RateLimitFutureWarning, TooManyRequestsSelfService}
import code.api.util.RateLimitingPeriod._
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full

/** Rate limiter for the self-service endpoints: the calls a client can make before it holds
 *  any credential, or with credentials it obtained for free (sign-up, password reset,
 *  consent requests, consumer registration, lookups, signal channel creation).
 *
 *  One of three limiters, each with its own 429 code:
 *   - [[RateLimitingUtil]]  post-auth, keyed by Consumer, the commercial quota      -> OBP-10018
 *   - this limiter          pre-auth, keyed by client IP, abuse of the free tier    -> OBP-10060
 *   - [[AuthRateLimiter]]   inside the credential check, keyed by IP and account   -> OBP-10061
 *  Login attempts are NOT a self-service scope: AuthRateLimiter owns every credential check
 *  (DirectLogin via AuthUser.getResourceUserId, DAuth, GatewayLogin, SIWE), so counting them
 *  here too would count each attempt twice. Every self-service endpoint belongs to a
 *  named *scope*; each scope has per-key limits (per minute, per hour, per day, keyed by the
 *  client IP address on REST and gRPC alike, with a Consumer fallback over gRPC when no peer
 *  address is available) and an optional global per-hour cap that acts
 *  as a circuit breaker against a spray from many addresses.
 *
 *  Props (all optional; built-in defaults apply):
 *   - `self_service.rate_limit.enabled` (default true)
 *   - `self_service.rate_limit.mode` = shadow (default) | enforce
 *   - `self_service.rate_limit.per_ip.per_minute|per_hour|per_day` generic per-key limits
 *   - `self_service.rate_limit.<scope>.per_ip.per_minute|per_hour|per_day` per-scope overrides
 *   - `self_service.rate_limit.<scope>.global.per_hour` per-scope global cap (-1 = off)
 *   - `self_service.rate_limit.enforce_announced_from` free text appended to the warning
 *  A limit of -1 disables that window; 0 blocks every call in that window.
 *
 *  Shadow mode: trips are logged and reported to the caller through the
 *  `X-Rate-Limit-Warning` header (see [[warningMessage]]), but the request is allowed.
 *  Enforce mode: trips produce [[Blocked]] and the caller renders 429.
 *
 *  Counting is attempt-based (every request increments) and fail-open: if Redis is
 *  unavailable, [[RateLimitingUtil.incrementCounter]] returns (-1, -1) and the window is
 *  skipped, so a Redis outage never blocks a self-service call.
 */
object SelfServiceRateLimiter extends MdcLoggable {

  val PropsPrefix = "self_service.rate_limit"

  val ModeShadow = "shadow"
  val ModeEnforce = "enforce"

  /** Built-in limits for one scope. */
  final case class ScopeDefaults(perMinute: Long, perHour: Long, perDay: Long, globalPerHour: Long)

  /** Used for any scope that has no entry in [[scopeDefaults]]. */
  val genericDefaults: ScopeDefaults = ScopeDefaults(perMinute = 10, perHour = 60, perDay = 200, globalPerHour = -1)

  /** Sensible defaults per scope. A real person or a well-behaved agent never gets near these
   *  from one address; they are meant to be hit only by scripts. */
  val scopeDefaults: Map[String, ScopeDefaults] = Map(
    "signup"                -> ScopeDefaults(perMinute = 3,  perHour = 5,   perDay = 10,  globalPerHour = 500),
    "password_reset"        -> ScopeDefaults(perMinute = 3,  perHour = 5,   perDay = 10,  globalPerHour = 500),
    "consent_request"       -> ScopeDefaults(perMinute = 10, perHour = 30,  perDay = 100, globalPerHour = -1),
    "consumer_registration" -> ScopeDefaults(perMinute = 5,  perHour = 10,  perDay = 20,  globalPerHour = 500),
    "lookup"                -> ScopeDefaults(perMinute = 20, perHour = 60,  perDay = 200, globalPerHour = -1),
    "signal_channel_create" -> ScopeDefaults(perMinute = 5,  perHour = 20,  perDay = 50,  globalPerHour = -1)
  )

  /** One counter window after this request was counted. */
  final case class Window(name: String, period: LimitCallPeriod, limit: Long, current: Long, resetSeconds: Long) {
    def remaining: Long = math.max(0L, limit - current)
    /** limit > 0 and the count is over it. A limit of 0 blocks every call (current is always >= 1). */
    def exceeded: Boolean = limit >= 0 && current > limit
    def describe: String = s"$limit ${RateLimitingPeriod.humanReadable(period)}"
  }

  sealed trait Outcome {
    def scope: String
    def windows: List[Window]
    /** The window the caller is closest to exhausting: fewest remaining calls, then soonest reset.
     *  This is what the X-Rate-Limit-* headers describe. */
    def tightest: Option[Window] =
      windows.filter(_.limit >= 0).sortBy(w => (w.remaining, w.resetSeconds)).headOption
    def exceededWindow: Option[Window] = None
  }
  /** Limiter disabled, or Redis unavailable for every window: nothing was counted. */
  final case class Skipped(scope: String) extends Outcome { val windows: List[Window] = Nil }
  final case class Allowed(scope: String, windows: List[Window]) extends Outcome
  /** A limit was exceeded but the mode is shadow: the request proceeds with a warning. */
  final case class Warned(scope: String, windows: List[Window], exceeded: Window) extends Outcome {
    override def exceededWindow: Option[Window] = Some(exceeded)
  }
  /** A limit was exceeded and the mode is enforce: the caller must respond 429. */
  final case class Blocked(scope: String, windows: List[Window], exceeded: Window) extends Outcome {
    override def exceededWindow: Option[Window] = Some(exceeded)
  }

  def enabled: Boolean = APIUtil.getPropsAsBoolValue(s"$PropsPrefix.enabled", true)

  def mode: String = APIUtil.getPropsValue(s"$PropsPrefix.mode", ModeShadow).trim.toLowerCase match {
    case ModeEnforce => ModeEnforce
    case _           => ModeShadow
  }

  def isEnforcing: Boolean = mode == ModeEnforce

  /** Optional operator text naming when enforcement is planned, e.g. "2026-10-01". Only
   *  appended to the warning when set; nothing about timing is claimed otherwise. */
  def enforceAnnouncedFrom: Option[String] =
    APIUtil.getPropsValue(s"$PropsPrefix.enforce_announced_from").toOption.map(_.trim).filter(_.nonEmpty)

  /** Resolution order for a per-key limit: explicit scope prop, explicit generic prop,
   *  built-in scope default, built-in generic default. */
  def perKeyLimit(scope: String, dimension: String): Long = {
    val builtIn = scopeDefaults.getOrElse(scope, genericDefaults)
    val builtInValue = dimension match {
      case "per_minute" => builtIn.perMinute
      case "per_hour"   => builtIn.perHour
      case "per_day"    => builtIn.perDay
      case _            => -1L
    }
    APIUtil.getPropsAsLongValue(s"$PropsPrefix.$scope.per_ip.$dimension") match {
      case Full(v) => v
      case _ => APIUtil.getPropsAsLongValue(s"$PropsPrefix.per_ip.$dimension") match {
        case Full(v) => v
        case _       => builtInValue
      }
    }
  }

  def globalPerHourLimit(scope: String): Long =
    APIUtil.getPropsAsLongValue(s"$PropsPrefix.$scope.global.per_hour") match {
      case Full(v) => v
      case _       => scopeDefaults.getOrElse(scope, genericDefaults).globalPerHour
    }

  /** Count this request against `scope` for `key` and report the outcome.
   *
   *  @param scope   the endpoint class, e.g. "signup"
   *  @param key     what the per-key windows are keyed on; normally the client IP address
   *  @param keyKind label for logs and keys, "ip" (default) or "consumer"
   */
  def check(scope: String, key: String, keyKind: String = "ip"): Outcome = {
    if (!enabled) return Skipped(scope)

    val safeScope = sanitise(scope)
    val safeKey   = if (key == null || key.trim.isEmpty || key.equalsIgnoreCase("unknown")) "" else sanitise(key.trim)

    val perKeyWindows: List[(String, LimitCallPeriod, Long)] =
      if (safeKey.isEmpty) Nil // no usable key: only the global window can be checked
      else List(
        (s"${keyKind}_per_minute", PER_MINUTE, perKeyLimit(safeScope, "per_minute")),
        (s"${keyKind}_per_hour",   PER_HOUR,   perKeyLimit(safeScope, "per_hour")),
        (s"${keyKind}_per_day",    PER_DAY,    perKeyLimit(safeScope, "per_day"))
      )
    val globalWindow: List[(String, LimitCallPeriod, Long)] =
      List(("global_per_hour", PER_HOUR, globalPerHourLimit(safeScope)))

    val windows: List[Window] = (perKeyWindows ++ globalWindow).flatMap { case (name, period, limit) =>
      if (limit < 0) None // -1: this window is switched off, do not touch Redis
      else {
        val redisKey =
          if (name.startsWith("global")) buildKey(safeScope, "global", period)
          else buildKey(safeScope, s"${keyKind}_$safeKey", period)
        val (ttl, current) = RateLimitingUtil.incrementCounter(redisKey, period)
        // current == -1 signals Redis unavailable: fail open by dropping this window.
        if (current < 0) None else Some(Window(name, period, limit, current, ttl))
      }
    }

    if (windows.isEmpty) return Skipped(safeScope)

    // Report the shortest exceeded window first, matching the consumer limiter's precedence.
    windows.find(_.exceeded) match {
      case None => Allowed(safeScope, windows)
      case Some(exceeded) if isEnforcing =>
        logger.warn(logLine("trip", safeScope, keyKind, safeKey, exceeded))
        Blocked(safeScope, windows, exceeded)
      case Some(exceeded) =>
        // Shadow: the first request over the limit in a window is warn, the rest debug, so a
        // burst produces one line per window rather than one per request.
        val line = logLine("shadow_trip", safeScope, keyKind, safeKey, exceeded)
        if (exceeded.current == exceeded.limit + 1) logger.warn(line) else logger.debug(line)
        Warned(safeScope, windows, exceeded)
    }
  }

  /** The `X-Rate-Limit-Warning` text for a shadow trip:
   *  "OBP-10059: Could conflict with a Future Rate Limit: This request might exceed the rate
   *  limit for signup (5 per hour) in the future." plus ", from <date>" when the operator has
   *  announced one. */
  def warningMessage(scope: String, exceeded: Window): String = {
    val base = RateLimitFutureWarning
      .replace("SCOPE", scope)
      .replace("LIMIT", exceeded.describe)
    enforceAnnouncedFrom match {
      case Some(from) => base.stripSuffix(".") + s", from $from."
      case None       => base
    }
  }

  /** The 429 body text for an enforced trip. OBP-10060, so a client can tell this limiter from the
   *  Consumer quota (OBP-10018) and the authentication limiter (OBP-10061). */
  def blockedMessage(scope: String, exceeded: Window): String =
    s"$TooManyRequestsSelfService The rate limit for $scope is ${exceeded.describe}. Try again in ${exceeded.resetSeconds} seconds."

  private def buildKey(scope: String, subject: String, period: LimitCallPeriod): String =
    s"${CALL_COUNTER_PREFIX}self_service_${scope}_${subject}_${RateLimitingPeriod.toString(period)}"

  /** Keep Redis keys and log lines free of separators and whitespace. */
  private def sanitise(s: String): String = s.replaceAll("[^A-Za-z0-9._:\\-]", "_")

  private def logLine(event: String, scope: String, keyKind: String, key: String, w: Window): String =
    s"event=self_service_rate_limit_$event scope=$scope key_kind=$keyKind key=${if (key.isEmpty) "none" else key} " +
      s"counter=${w.name} current=${w.current} limit=${w.limit} retry_after_s=${w.resetSeconds}"
}
