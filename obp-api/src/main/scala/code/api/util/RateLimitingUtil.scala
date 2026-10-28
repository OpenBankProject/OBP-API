/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import java.util.Date
import code.ratelimiting.{RateLimiting, RateLimitingDI}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import code.api.{APIFailureNewStyle, JedisMethod}
import code.api.Constant._
import code.api.cache.Redis
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.TooManyRequests
import code.api.util.RateLimitingJson.CallLimit
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty}
import redis.clients.jedis.Jedis

import scala.collection.immutable
import scala.collection.immutable.{List, Nil}


object RateLimitingPeriod extends Enumeration {
  type LimitCallPeriod = Value
  val PER_SECOND, PER_MINUTE, PER_HOUR, PER_DAY, PER_WEEK, PER_MONTH, PER_YEAR = Value

  def toSeconds(period: LimitCallPeriod): Long = {
    period match {
      case PER_SECOND => 1
      case PER_MINUTE => 60
      case PER_HOUR   => 60 * 60
      case PER_DAY    => 60 * 60 * 24
      case PER_WEEK   => 60 * 60 * 24 * 7
      case PER_MONTH  => 60 * 60 * 24 * 30
      case PER_YEAR   => 60 * 60 * 24 * 365
    }
  }

  def toString(period: LimitCallPeriod): String = {
    period match {
      case PER_SECOND => "PER_SECOND"
      case PER_MINUTE => "PER_MINUTE"
      case PER_HOUR   => "PER_HOUR"
      case PER_DAY    => "PER_DAY"
      case PER_WEEK   => "PER_WEEK"
      case PER_MONTH  => "PER_MONTH"
      case PER_YEAR   => "PER_YEAR"
    }
  }
  def humanReadable(period: LimitCallPeriod): String = {
    period match {
      case PER_SECOND => "per second"
      case PER_MINUTE => "per minute"
      case PER_HOUR   => "per hour"
      case PER_DAY    => "per day"
      case PER_WEEK   => "per week"
      case PER_MONTH  => "per month"
      case PER_YEAR   => "per year"
    }
  }
}

object RateLimitingJson {
  case class CallLimit(
                  consumer_id : String,
                  api_name : Option[String],
                  api_version : Option[String],
                  bank_id : Option[String],
                  per_second : Long,
                  per_minute : Long,
                  per_hour : Long,
                  per_day : Long,
                  per_week : Long,
                  per_month : Long
                )
}

object RateLimitingUtil extends MdcLoggable {
  import code.api.util.RateLimitingPeriod._

  /** State of a rate limiting counter from Redis */
  case class RateLimitCounterState(
    calls: Option[Long],       // Current counter value
    ttl: Option[Long],         // Time to live in seconds
    status: String             // ACTIVE, NO_COUNTER, EXPIRED, REDIS_UNAVAILABLE
  )

  def useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", true)

  /** Get system default rate limits from properties. Used when no RateLimiting records exist for a consumer.
    * @param consumerId The consumer ID
    * @return RateLimit with system property defaults (default to -1 if not set)
    */
  /** THE SINGLE SOURCE OF TRUTH for active rate limits.
    * This is the ONLY function that should be called to get active rate limits.
    * Used by BOTH enforcement (AfterApiAuth) and API reporting (Http4s600).
    *
    * @param consumerId The consumer ID
    * @param date The date to check active limits for
    * @return Future containing (aggregated CallLimit, List of rate_limiting_ids that contributed)
    */
  def getActiveRateLimitsWithIds(consumerId: String, date: Date): Future[(CallLimit, List[String])] = {
    def getActiveRateLimitings(consumerId: String): Future[List[RateLimiting]] = {
      useConsumerLimits match {
        case true => RateLimitingDI.rateLimiting.vend.getActiveCallLimitsByConsumerIdAtDate(consumerId, date)
        case false => Future(List.empty)
      }
    }

    def aggregateRateLimits(rateLimitRecords: List[RateLimiting]): CallLimit = {
      // Per period, over the consumer's active rows (see Glossary "Rate Limiting"):
      //   -1 values are ignored (unlimited rows contribute nothing)
      //   the remaining values (>= 0) are summed: overlapping rows add up, by design
      //   a sum of 0 means blocked: every call in that period is refused with 429. A single 0 row does
      //     not override positive rows; it only blocks when nothing else grants calls.
      //   nothing to sum (all -1) -> -1 unlimited. A row exists, so the rate_limiting_per_* props do not apply.
      // No rows at all is handled below: the rate_limiting_per_* props apply.
      def resolveLimit(values: List[Long]): Long = {
        val counted = values.filter(_ >= 0)
        if (counted.isEmpty) -1 else counted.sum
      }

      if (rateLimitRecords.nonEmpty) {
        RateLimitingJson.CallLimit(
          consumerId,
          rateLimitRecords.find(_.apiName.isDefined).flatMap(_.apiName),
          rateLimitRecords.find(_.apiVersion.isDefined).flatMap(_.apiVersion),
          rateLimitRecords.find(_.bankId.isDefined).flatMap(_.bankId),
          resolveLimit(rateLimitRecords.map(_.perSecondCallLimit)),
          resolveLimit(rateLimitRecords.map(_.perMinuteCallLimit)),
          resolveLimit(rateLimitRecords.map(_.perHourCallLimit)),
          resolveLimit(rateLimitRecords.map(_.perDayCallLimit)),
          resolveLimit(rateLimitRecords.map(_.perWeekCallLimit)),
          resolveLimit(rateLimitRecords.map(_.perMonthCallLimit))
        )
      } else {
        // No records found - return system defaults
        RateLimitingJson.CallLimit(
          consumerId,
          None,
          None,
          None,
          APIUtil.getPropsAsLongValue("rate_limiting_per_second", -1),
          APIUtil.getPropsAsLongValue("rate_limiting_per_minute", -1),
          APIUtil.getPropsAsLongValue("rate_limiting_per_hour", -1),
          APIUtil.getPropsAsLongValue("rate_limiting_per_day", -1),
          APIUtil.getPropsAsLongValue("rate_limiting_per_week", -1),
          APIUtil.getPropsAsLongValue("rate_limiting_per_month", -1)
        )
      }
    }

    for {
      rateLimitRecords <- getActiveRateLimitings(consumerId)
    } yield {
      val callLimit = aggregateRateLimits(rateLimitRecords)
      val ids = rateLimitRecords.map(_.rateLimitingId)
      (callLimit, ids)
    }
  }


  /**
   * Single source of truth for reading rate limit counter state from Redis.
   * All rate limiting functions should call this instead of accessing Redis directly.
   * 
   * @param consumerKey The consumer ID
   * @param period The time period (PER_SECOND, PER_MINUTE, etc.)
   * @return RateLimitCounterState with calls, ttl, and status
   */
  private def getCounterState(consumerKey: String, period: LimitCallPeriod): RateLimitCounterState = {
    val key = createUniqueKey(consumerKey, period)
    
    // Read TTL and value from Redis (2 operations)
    val ttlOpt: Option[Long] = Redis.use(JedisMethod.TTL, key).map(_.toLong)
    val valueOpt: Option[Long] = Redis.use(JedisMethod.GET, key).map(_.toLong)
    
    // Determine status based on Redis TTL response
    val status = ttlOpt match {
      case Some(ttl) if ttl > 0 => "ACTIVE"        // Counter running with time remaining
      case Some(-2) => "NO_COUNTER"                // Key does not exist, never been set
      case Some(ttl) if ttl <= 0 => "EXPIRED"      // Key expired (TTL=0) or no expiry (TTL=-1)
      case None => "REDIS_UNAVAILABLE"             // Redis connection failed
    }
    
    // Normalize calls value
    val calls = ttlOpt match {
      case Some(-2) => Some(0L)                    // Key doesn't exist -> 0 calls
      case Some(ttl) if ttl <= 0 => Some(0L)       // Expired or invalid -> 0 calls
      case Some(_) => valueOpt.orElse(Some(0L))    // Active key -> return value or 0
      case None => Some(0L)                        // Redis unavailable -> 0 calls
    }
    
    // Normalize TTL value
    val normalizedTtl = ttlOpt match {
      case Some(-2) => Some(0L)                    // Key doesn't exist -> 0 TTL
      case Some(ttl) if ttl <= 0 => Some(0L)       // Expired -> 0 TTL
      case Some(ttl) => Some(ttl)                  // Active -> actual TTL
      case None => Some(0L)                        // Redis unavailable -> 0 TTL
    }
    
    RateLimitCounterState(calls, normalizedTtl, status)
  }
  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = CALL_COUNTER_PREFIX + consumerKey + "_" + RateLimitingPeriod.toString(period)

  /** Pure Redis INCR with create-if-missing for a fully-formed key.
   *  No gates, no key formatting — call sites pass the final key and supply their own enable flags.
   *  Returns (ttl_seconds, current_count); (-1, -1) when Redis is unreachable. */
  private[util] def incrementCounter(key: String, period: LimitCallPeriod): (Long, Long) = {
    val seconds = RateLimitingPeriod.toSeconds(period).toInt
    try {
      // Atomic INCR + create-TTL in one Lua call. Replaces the former TTL-read-then-SET/INCR sequence,
      // which could lose increments and race the expiry under concurrency. The script also self-heals
      // a key stuck without an expiry and returns the TTL atomically with the count — one round trip,
      // no separate TTL read that could observe a different window.
      val (cnt, ttl) = Redis.incrementWithTtl(key, seconds)
      (ttl, cnt)
    } catch {
      case e: Throwable =>
        logger.error(s"Redis unavailable when incrementing counter for key $key, period $period", e)
        (-1, -1)
    }
  }

  /** The periods of the consumer quota, shortest first: the order the limits are checked in, so the
   *  429 names the shortest exhausted period, and the order the X-Rate-Limit-* headers prefer. */
  private val consumerPeriods: List[LimitCallPeriod] = List(PER_SECOND, PER_MINUTE, PER_HOUR, PER_DAY, PER_WEEK, PER_MONTH)

  private def limitOf(c: CallLimit, period: LimitCallPeriod): Long = period match {
    case PER_SECOND => c.per_second
    case PER_MINUTE => c.per_minute
    case PER_HOUR   => c.per_hour
    case PER_DAY    => c.per_day
    case PER_WEEK   => c.per_week
    case PER_MONTH  => c.per_month
    case PER_YEAR   => -1
  }

  /** What one call did to the quota counters. */
  private[util] sealed trait QuotaOutcome
  /** `period` refused the call (its limit is 0, or its counter is full). Nothing was counted.
   *  `resetSeconds` is the counter's remaining TTL, 0 when it has none. */
  private[util] final case class Refused(period: LimitCallPeriod, limit: Long, resetSeconds: Long) extends QuotaOutcome
  /** Every period passed and the call was counted in all of them, unlimited ones included:
   *  (period, limit, (ttl, count after this call)) in [[consumerPeriods]] order. */
  private[util] final case class Counted(perPeriod: List[(LimitCallPeriod, Long, (Long, Long))]) extends QuotaOutcome

  /**
   * Check the call against every period's limit and, when all pass, count it in every period, in
   * ONE Redis round trip (Redis.quotaCheckAndCount, a Lua script). The script runs atomically, so
   * two concurrent calls cannot both read a counter one below its limit and both pass: at most
   * `limit` calls are served per period.
   *
   * Counted whatever a period's limit is, -1 (unlimited) included: a limit of -1 is not checked, so
   * a counter that is always kept changes nothing about what is refused, and gives visibility of
   * the consumer's activity through the call-counter endpoints even when no limit applies. A limit
   * of 0 refuses before counting, so nothing is ever served or counted under it.
   *
   * @param consumerKey The consumer ID (or the client IP for anonymous access)
   * @param periodsWithLimits the periods to check and count, shortest first, each with its limit
   * @return None when nothing was checked or counted: `use_consumer_limits=false` (an instance
   *         without Redis is left alone) or Redis unreachable (fail open: the call is allowed and
   *         no X-Rate-Limit-* headers are set)
   */
  private def checkAndCount(consumerKey: String, periodsWithLimits: List[(LimitCallPeriod, Long)]): Option[QuotaOutcome] =
    if (!useConsumerLimits) None
    else try {
      Redis.quotaCheckAndCount(
        keys = periodsWithLimits.map { case (period, _) => createUniqueKey(consumerKey, period) },
        limits = periodsWithLimits.map(_._2),
        periodSeconds = periodsWithLimits.map { case (period, _) => RateLimitingPeriod.toSeconds(period).toInt }
      ) match {
        case Redis.QuotaRefused(index, _, ttl) =>
          val (period, limit) = periodsWithLimits(index)
          Some(Refused(period, limit, Math.max(0L, ttl))) // TTL -1/-2 (no expiry / no key) reads as 0, as getCounterState does
        case Redis.QuotaCounted(countsAndTtls) =>
          Some(Counted(periodsWithLimits.zip(countsAndTtls).map { case ((period, limit), (count, ttl)) => (period, limit, (ttl, count)) }))
      }
    } catch {
      case e: Throwable =>
        logger.error(s"Redis unavailable when checking the rate limit for $consumerKey - allowing request", e)
        None
    }



  def consumerRateLimitState(consumerKey: String): immutable.Seq[((Option[Long], Option[Long], String), LimitCallPeriod)] = {
    def getCallCounterForPeriod(consumerKey: String, period: LimitCallPeriod): ((Option[Long], Option[Long], String), LimitCallPeriod) = {
      val state = getCounterState(consumerKey, period)
      ((state.calls, state.ttl, state.status), period)
    }

    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_SECOND) ::
    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_MINUTE) ::
    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_HOUR) ::
    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_DAY) ::
    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_WEEK) ::
    getCallCounterForPeriod(consumerKey, RateLimitingPeriod.PER_MONTH) ::
      Nil
  }

  /**
    * Rate limiting guard that enforces API call limits for both authorized and anonymous access.
    *
    * This is the main rate limiting enforcement function that controls access to OBP API endpoints.
    * It operates in two modes depending on whether the caller is authenticated or anonymous.
    *
    * AUTHORIZED ACCESS (with valid consumer credentials):
    * - Enforces limits across 6 time periods: per second, minute, hour, day, week, and month
    * - Uses consumer_id as the rate limiting key (simplified for current implementation)
    * - Note: api_name, api_version, and bank_id may be added to the key in future versions
    * - Limits are defined in CallLimit configuration for each consumer
    * - Stores counters in Redis with TTL matching the time period
    * - Returns 429 status with appropriate error message when any limit is exceeded
    * - Lower period limits take precedence in error messages (e.g., per-second over per-minute)
    *
    * ANONYMOUS ACCESS (no consumer credentials):
    * - Only enforces per-hour limits (configurable via "user_consumer_limit_anonymous_access", default: 1000)
    * - Uses client IP address as the rate limiting key
    * - Designed to prevent abuse while allowing reasonable anonymous usage
    *
    * REDIS STORAGE MECHANISM:
    * - Keys format: {consumer_id}_{PERIOD} (e.g., "consumer123_PER_MINUTE")
    * - Values: current call count within the time window
    * - TTL: automatically expires keys when time period ends
    * - One Lua script per request (Redis.quotaCheckAndCount) checks every period and, when all
    *   pass, counts the call in every period: one round trip, atomic, so concurrent calls cannot
    *   pass a check together and overshoot the limit. A refusal comes back with the counter's TTL,
    *   so the 429 headers need no further read.
    *
    * RATE LIMIT HEADERS:
    * - Sets X-Rate-Limit-Limit: maximum allowed requests for the period
    * - Sets X-Rate-Limit-Reset: seconds until the limit resets (TTL)
    * - Sets X-Rate-Limit-Remaining: requests remaining in current period
    *
    * ERROR HANDLING:
    * - Redis connectivity issues default to allowing the request (fail-open)
    * - Rate limiting can be globally disabled via "use_consumer_limits" property
    * - A limit of 0 blocks the period (429 on every call), -1 means unlimited, no records means the props defaults apply
    *
    * @param userAndCallContext Tuple containing (Box[User], Option[CallContext]) from authentication
    * @return Same tuple structure, either with updated rate limit headers or rate limit exceeded error
    */
  def underCallLimits(userAndCallContext: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    // Configuration and helper functions
    def perHourLimitAnonymous = APIUtil.getPropsAsIntValue("user_consumer_limit_anonymous_access", 1000)
    def composeMsgBlocked(period: LimitCallPeriod, consumerId: String): String = TooManyRequests + s" This Consumer is blocked: its active rate limit ${RateLimitingPeriod.humanReadable(period)} is 0 (consumer_id: $consumerId)."
    def composeMsgAuthorizedAccess(period: LimitCallPeriod, limit: Long, consumerId: String): String =
      if (limit == 0) composeMsgBlocked(period, consumerId)
      else TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for this Consumer (consumer_id: $consumerId)."
    def composeMsgAnonymousAccess(period: LimitCallPeriod, limit: Long): String =
      if (limit == 0) TooManyRequests + s" Anonymous access is blocked: the anonymous rate limit ${RateLimitingPeriod.humanReadable(period)} is 0."
      else TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for anonymous access."

    // The X-Rate-Limit-* headers of a served call: the limit, the counter's TTL and what is left of the limit
    def setXRateLimits(limit: Long, ttlAndCount: (Long, Long)): Option[CallContext] =
      userAndCallContext._2.map(_.copy(xRateLimitLimit = limit))
        .map(_.copy(xRateLimitReset = ttlAndCount._1))
        .map(_.copy(xRateLimitRemaining = Math.max(0, limit - ttlAndCount._2)))

    // The X-Rate-Limit-* headers of a refused call: the exhausted limit, its reset and nothing remaining
    def exceededRateLimit(refused: Refused): Option[CallContextLight] =
      userAndCallContext._2.map(_.copy(xRateLimitLimit = refused.limit))
        .map(_.copy(xRateLimitReset = refused.resetSeconds))
        .map(_.copy(xRateLimitRemaining = 0)).map(_.toLight)

    def refuse(message: String, refused: Refused): (Box[User], Option[CallContext]) =
      (fullBoxOrException(Empty ~> APIFailureNewStyle(message, 429, exceededRateLimit(refused))), userAndCallContext._2)

    // Main logic: check if we have a CallContext and determine access type
    userAndCallContext._2 match {
      case Some(cc) =>
        cc.rateLimiting match {
          case Some(rl) => // AUTHORIZED ACCESS - consumer has valid credentials and rate limits
            // One round trip checks the 6 limits, shortest period first, and counts the call when all pass
            checkAndCount(rl.consumer_id, consumerPeriods.map(period => (period, limitOf(rl, period)))) match {
              case Some(refused: Refused) =>
                // 429 for the first exceeded limit (shorter periods take precedence)
                refuse(composeMsgAuthorizedAccess(refused.period, refused.limit, rl.consumer_id), refused)
              case Some(Counted(perPeriod)) =>
                // All limits passed and every period counted. Set the X-Rate-Limit-* headers from the
                // shortest period that HAS a limit. The counter alone cannot pick that period: every
                // period is counted, unlimited ones included, so a live per-second counter says nothing
                // about whether a per-second limit exists. With no limited period the CallContext keeps
                // its defaults and the headers read -1.
                perPeriod.collectFirst { case (_, limit, ttlAndCount) if limit > 0 && ttlAndCount._1 > 0 => (limit, ttlAndCount) } match {
                  case Some((limit, ttlAndCount)) => (userAndCallContext._1, setXRateLimits(limit, ttlAndCount))
                  case None                       => (userAndCallContext._1, userAndCallContext._2)
                }
              case None => // limits off, or Redis unreachable: served, not counted, no headers
                (userAndCallContext._1, userAndCallContext._2)
            }
          case None => // ANONYMOUS ACCESS - no consumer credentials, use IP-based limiting
            // Use client IP address as rate limiting key for anonymous access
            val consumerId = cc.ipAddress
            // Anonymous access only has per-hour limits to prevent abuse
            val limit = perHourLimitAnonymous.toLong
            checkAndCount(consumerId, List((PER_HOUR, limit))) match {
              case Some(refused: Refused) =>
                // Return 429 error if anonymous hourly limit exceeded
                refuse(composeMsgAnonymousAccess(PER_HOUR, limit), refused)
              case Some(Counted((_, _, ttlAndCount) :: _)) if ttlAndCount._1 > 0 =>
                // Limit not exceeded - counted, set headers
                (userAndCallContext._1, setXRateLimits(limit, ttlAndCount))
              case _ =>
                (userAndCallContext._1, userAndCallContext._2)
            }
        }
      case _ => // No CallContext available - pass through without rate limiting
        (userAndCallContext._1, userAndCallContext._2)
    }
  }


}
