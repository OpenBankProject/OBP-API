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
    * Used by BOTH enforcement (AfterApiAuth) and API reporting (APIMethods600).
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
      def sumLimits(values: List[Long]): Long = {
        val positiveValues = values.filter(_ > 0)
        if (positiveValues.isEmpty) -1 else positiveValues.sum
      }

      if (rateLimitRecords.nonEmpty) {
        RateLimitingJson.CallLimit(
          consumerId,
          rateLimitRecords.find(_.apiName.isDefined).flatMap(_.apiName),
          rateLimitRecords.find(_.apiVersion.isDefined).flatMap(_.apiVersion),
          rateLimitRecords.find(_.bankId.isDefined).flatMap(_.bankId),
          sumLimits(rateLimitRecords.map(_.perSecondCallLimit)),
          sumLimits(rateLimitRecords.map(_.perMinuteCallLimit)),
          sumLimits(rateLimitRecords.map(_.perHourCallLimit)),
          sumLimits(rateLimitRecords.map(_.perDayCallLimit)),
          sumLimits(rateLimitRecords.map(_.perWeekCallLimit)),
          sumLimits(rateLimitRecords.map(_.perMonthCallLimit))
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
  private def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {

    if (useConsumerLimits) {
      (limit) match {
        case l if l > 0 => // Limit is set, check against Redis counter
          val state = getCounterState(consumerKey, period)
          state.status match {
            case "ACTIVE" =>
              // Counter is active, check if we're under limit
              // +1 means we count the current call as well. We increment later i.e after successful call.
              state.calls.getOrElse(0L) + 1 <= limit
            case "NO_COUNTER" | "EXPIRED" =>
              // No counter or expired -> allow (first call or period expired)
              true
            case "REDIS_UNAVAILABLE" =>
              // Redis unavailable -> fail open (allow request)
              logger.warn(s"Redis unavailable when checking rate limit for consumer $consumerKey, period $period - allowing request")
              true
            case _ =>
              // Unknown status -> fail open (allow request)
              logger.warn(s"Unknown status '${state.status}' when checking rate limit for consumer $consumerKey, period $period - allowing request")
              true
          }
        case _ =>
          // Rate Limiting for a Consumer <= 0 implies successful result
          // Or any other unhandled case implies successful result
          true
      }
    } else {
      true // Rate Limiting disabled implies successful result
    }
  }

  /**
   * Increment API call counter for a consumer after successful rate limit check.
   * Called after the request passes all rate limit checks to update the counters.
   * 
   * @param consumerKey The consumer ID or IP address
   * @param period The time period (PER_SECOND, PER_MINUTE, etc.)
   * @param limit The rate limit value (-1 means disabled)
   * @return (TTL in seconds, current counter value) or (-1, -1) on error/disabled
   */
  private def incrementConsumerCounters(consumerKey: String, period: LimitCallPeriod, limit: Long): (Long, Long) = {
    if (useConsumerLimits) {
      val key = createUniqueKey(consumerKey, period)
      (limit) match {
        case -1 => // Limit is disabled for this period
          Redis.use(JedisMethod.DELETE, key)
          (-1, -1)
        case _ => // Limit is enabled, increment counter
          val ttlOpt = Redis.use(JedisMethod.TTL, key).map(_.toInt)
          ttlOpt match {
            case Some(-2) => // Key does not exist, create it
              val seconds = RateLimitingPeriod.toSeconds(period).toInt
              Redis.use(JedisMethod.SET, key, Some(seconds), Some("1"))
              (seconds, 1)
            case Some(ttl) if ttl > 0 => // Key exists with TTL, increment it
              val cnt = Redis.use(JedisMethod.INCR, key).map(_.toInt).getOrElse(1)
              (ttl, cnt)
            case Some(ttl) if ttl <= 0 => // Key expired or has no expiry (shouldn't happen)
              logger.warn(s"Unexpected TTL state ($ttl) for consumer $consumerKey, period $period - recreating counter")
              val seconds = RateLimitingPeriod.toSeconds(period).toInt
              Redis.use(JedisMethod.SET, key, Some(seconds), Some("1"))
              (seconds, 1)
            case None => // Redis unavailable
              logger.error(s"Redis unavailable when incrementing counter for consumer $consumerKey, period $period")
              (-1, -1)
          }
      }
    } else {
      (-1, -1)
    }
  }

  /**
   * Get remaining TTL (time to live) for a rate limit counter.
   * Used to populate X-Rate-Limit-Reset header when rate limit is exceeded.
   * 
   * NOTE: This function could be further optimized by eliminating it entirely.
   * We already call getCounterState() in underConsumerLimits(), so we could 
   * cache/reuse that TTL value instead of making another Redis call here.
   * 
   * @param consumerKey The consumer ID or IP address
   * @param period The time period
   * @return Seconds until counter resets, or 0 if no counter exists
   */
  private def ttl(consumerKey: String, period: LimitCallPeriod): Long = {
    val state = getCounterState(consumerKey, period)
    state.ttl.getOrElse(0L)
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
    * - Atomic operations ensure thread-safe counter increments
    *
    * RATE LIMIT HEADERS:
    * - Sets X-Rate-Limit-Limit: maximum allowed requests for the period
    * - Sets X-Rate-Limit-Reset: seconds until the limit resets (TTL)
    * - Sets X-Rate-Limit-Remaining: requests remaining in current period
    *
    * ERROR HANDLING:
    * - Redis connectivity issues default to allowing the request (fail-open)
    * - Rate limiting can be globally disabled via "use_consumer_limits" property
    * - Malformed or missing limits default to unlimited access
    *
    * @param userAndCallContext Tuple containing (Box[User], Option[CallContext]) from authentication
    * @return Same tuple structure, either with updated rate limit headers or rate limit exceeded error
    */
  def underCallLimits(userAndCallContext: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    // Configuration and helper functions
    def perHourLimitAnonymous = APIUtil.getPropsAsIntValue("user_consumer_limit_anonymous_access", 1000)
    def composeMsgAuthorizedAccess(period: LimitCallPeriod, limit: Long): String = TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for this Consumer."
    def composeMsgAnonymousAccess(period: LimitCallPeriod, limit: Long): String = TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for anonymous access."

    // Helper function to set rate limit headers in successful responses
    def setXRateLimits(c: CallLimit, z: (Long, Long), period: LimitCallPeriod): Option[CallContext] = {
      val limit = period match {
        case PER_SECOND => c.per_second
        case PER_MINUTE => c.per_minute
        case PER_HOUR   => c.per_hour
        case PER_DAY    => c.per_day
        case PER_WEEK   => c.per_week
        case PER_MONTH  => c.per_month
        case PER_YEAR   => -1
      }
      userAndCallContext._2.map(_.copy(xRateLimitLimit = limit))
        .map(_.copy(xRateLimitReset = z._1))
        .map(_.copy(xRateLimitRemaining = limit - z._2))
    }
    // Helper function to set rate limit headers for anonymous access
    def setXRateLimitsAnonymous(id: String, z: (Long, Long), period: LimitCallPeriod): Option[CallContext] = {
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(xRateLimitLimit = limit))
        .map(_.copy(xRateLimitReset = z._1))
        .map(_.copy(xRateLimitRemaining = limit - z._2))
    }

    // Helper function to create rate limit exceeded response with remaining TTL for authorized users
    def exceededRateLimit(c: CallLimit, period: LimitCallPeriod): Option[CallContextLight] = {
      val remain = ttl(c.consumer_id, period)
      val limit = period match {
        case PER_SECOND => c.per_second
        case PER_MINUTE => c.per_minute
        case PER_HOUR   => c.per_hour
        case PER_DAY    => c.per_day
        case PER_WEEK   => c.per_week
        case PER_MONTH  => c.per_month
        case PER_YEAR   => -1
      }
      userAndCallContext._2.map(_.copy(xRateLimitLimit = limit))
        .map(_.copy(xRateLimitReset = remain))
        .map(_.copy(xRateLimitRemaining = 0)).map(_.toLight)
    }

    // Helper function to create rate limit exceeded response for anonymous users
    def exceededRateLimitAnonymous(id: String, period: LimitCallPeriod): Option[CallContextLight] = {
      val remain = ttl(id, period)
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(xRateLimitLimit = limit))
        .map(_.copy(xRateLimitReset = remain))
        .map(_.copy(xRateLimitRemaining = 0)).map(_.toLight)
    }

    // Main logic: check if we have a CallContext and determine access type
    userAndCallContext._2 match {
      case Some(cc) =>
        cc.rateLimiting match {
          case Some(rl) => // AUTHORIZED ACCESS - consumer has valid credentials and rate limits
            // Create rate limiting key for Redis storage using consumer_id
            val rateLimitingKey = rl.consumer_id
            // Check if current request would exceed any of the 6 rate limits
            val checkLimits = List(
              underConsumerLimits(rateLimitingKey, PER_SECOND, rl.per_second),
              underConsumerLimits(rateLimitingKey, PER_MINUTE, rl.per_minute),
              underConsumerLimits(rateLimitingKey, PER_HOUR, rl.per_hour),
              underConsumerLimits(rateLimitingKey, PER_DAY, rl.per_day),
              underConsumerLimits(rateLimitingKey, PER_WEEK, rl.per_week),
              underConsumerLimits(rateLimitingKey, PER_MONTH, rl.per_month)
            )
            // Return 429 error for first exceeded limit (shorter periods take precedence)
            checkLimits match {
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x1 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_SECOND, rl.per_second), 429, exceededRateLimit(rl, PER_SECOND))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x2 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_MINUTE, rl.per_minute), 429, exceededRateLimit(rl, PER_MINUTE))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x3 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_HOUR, rl.per_hour), 429, exceededRateLimit(rl, PER_HOUR))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x4 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_DAY, rl.per_day), 429, exceededRateLimit(rl, PER_DAY))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x5 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_WEEK, rl.per_week), 429, exceededRateLimit(rl, PER_WEEK))), userAndCallContext._2)
              case x1 :: x2 :: x3 :: x4 :: x5 :: x6 :: Nil if x6 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAuthorizedAccess(PER_MONTH, rl.per_month), 429, exceededRateLimit(rl, PER_MONTH))), userAndCallContext._2)
              case _ =>
                // All limits passed - increment counters and set rate limit headers
                val incrementCounters = List (
                  incrementConsumerCounters(rateLimitingKey, PER_SECOND, rl.per_second),
                  incrementConsumerCounters(rateLimitingKey, PER_MINUTE, rl.per_minute),
                  incrementConsumerCounters(rateLimitingKey, PER_HOUR, rl.per_hour),
                  incrementConsumerCounters(rateLimitingKey, PER_DAY, rl.per_day),
                  incrementConsumerCounters(rateLimitingKey, PER_WEEK, rl.per_week),
                  incrementConsumerCounters(rateLimitingKey, PER_MONTH, rl.per_month)
                )
                // Set rate limit headers based on the most restrictive active period
                incrementCounters match {
                  case first :: _ :: _ :: _ :: _ :: _ :: Nil if first._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, first, PER_SECOND))
                  case _ :: second :: _ :: _ :: _ :: _ :: Nil if second._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, second, PER_MINUTE))
                  case _ :: _ :: third :: _ :: _ :: _ :: Nil if third._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, third, PER_HOUR))
                  case _ :: _ :: _ :: fourth :: _ :: _ :: Nil if fourth._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, fourth, PER_DAY))
                  case _ :: _ :: _ :: _ :: fifth :: _ :: Nil if fifth._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, fifth, PER_WEEK))
                  case _ :: _ :: _ :: _ :: _ :: sixth :: Nil if sixth._1 > 0 =>
                    (userAndCallContext._1, setXRateLimits(rl, sixth, PER_MONTH))
                  case _  =>
                    (userAndCallContext._1, userAndCallContext._2)
                }
            }
          case None => // ANONYMOUS ACCESS - no consumer credentials, use IP-based limiting
            // Use client IP address as rate limiting key for anonymous access
            val consumerId = cc.ipAddress
            // Anonymous access only has per-hour limits to prevent abuse
            val checkLimits = List(
              underConsumerLimits(consumerId, PER_HOUR, perHourLimitAnonymous)
            )
            checkLimits match {
              case x1 :: Nil if !x1 =>
                // Return 429 error if anonymous hourly limit exceeded
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAnonymousAccess(PER_HOUR, perHourLimitAnonymous), 429, exceededRateLimitAnonymous(consumerId, PER_HOUR))), userAndCallContext._2)
              case _ =>
                // Limit not exceeded - increment counter and set headers
                val incrementCounters = List (
                  incrementConsumerCounters(consumerId, PER_HOUR, perHourLimitAnonymous)
                )
                incrementCounters match {
                  case x1 :: Nil if x1._1 > 0 =>
                    (userAndCallContext._1, setXRateLimitsAnonymous(consumerId, x1, PER_HOUR))
                  case _  =>
                    (userAndCallContext._1, userAndCallContext._2)
                }
            }
        }
      case _ => // No CallContext available - pass through without rate limiting
        (userAndCallContext._1, userAndCallContext._2)
    }
  }


}
