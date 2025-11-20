package code.api.util

import code.api.{APIFailureNewStyle, JedisMethod}
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
  
  def useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", false)

  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = consumerKey + "_" + RateLimitingPeriod.toString(period)

  private def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {
    if (useConsumerLimits) {
      try {
        (limit) match {
          case l if l > 0 => // Redis is available and limit is set
            val key = createUniqueKey(consumerKey, period)
            val exists = Redis.use(JedisMethod.EXISTS,key).map(_.toBoolean).get
            exists match {
              case true =>
                val underLimit = Redis.use(JedisMethod.GET,key).get.toLong + 1 <= limit // +1 means we count the current call as well. We increment later i.e after successful call.
                underLimit
              case false => // In case that key does not exist we return successful result
                true
            }
          case _ =>
            // Rate Limiting for a Consumer <= 0 implies successful result
            // Or any other unhandled case implies successful result
            true
        }
      } catch {
        case e : Throwable =>
          logger.error(s"Redis issue: $e")
          true
      }
    } else {
      true // Rate Limiting disabled implies successful result
    }
  }

  private def incrementConsumerCounters(consumerKey: String, period: LimitCallPeriod, limit: Long): (Long, Long) = {
    if (useConsumerLimits) {
      try {
        (limit) match {
          case -1 => // Limit is not set for the period
            val key = createUniqueKey(consumerKey, period)
            Redis.use(JedisMethod.DELETE, key)
            (-1, -1)
          case _ => // Redis is available and limit is set
            val key = createUniqueKey(consumerKey, period)
            val ttl =  Redis.use(JedisMethod.TTL, key).get.toInt
            ttl match {
              case -2 => // if the Key does not exists, -2 is returned
                val seconds =  RateLimitingPeriod.toSeconds(period).toInt
                Redis.use(JedisMethod.SET,key, Some(seconds), Some("1"))
                (seconds, 1)
              case _ => // otherwise increment the counter
                val cnt = Redis.use(JedisMethod.INCR,key).get.toInt
                (ttl, cnt)
            }
        }
      } catch {
        case e : Throwable =>
          logger.error(s"Redis issue: $e")
          (-1, -1)
      }
    } else {
      (-1, -1)
    }
  }

  private def ttl(consumerKey: String, period: LimitCallPeriod): Long = {
    val key = createUniqueKey(consumerKey, period)
    val ttl = Redis.use(JedisMethod.TTL, key).get.toInt
    ttl match {
      case -2 => // if the Key does not exists, -2 is returned
        0
      case _ => // otherwise increment the counter
        ttl
    }
  }



  def consumerRateLimitState(consumerKey: String): immutable.Seq[((Option[Long], Option[Long]), LimitCallPeriod)] = {

    def getInfo(consumerKey: String, period: LimitCallPeriod): ((Option[Long], Option[Long]), LimitCallPeriod) = {
      val key = createUniqueKey(consumerKey, period)

      // get TTL
      val ttlOpt: Option[Long] = Redis.use(JedisMethod.TTL, key).map(_.toLong)

      // get value (assuming string storage)
      val valueOpt: Option[Long] = Redis.use(JedisMethod.GET, key).map(_.toLong)

      ((valueOpt, ttlOpt), period)
    }

    getInfo(consumerKey, RateLimitingPeriod.PER_SECOND) ::
    getInfo(consumerKey, RateLimitingPeriod.PER_MINUTE) ::
    getInfo(consumerKey, RateLimitingPeriod.PER_HOUR) ::
    getInfo(consumerKey, RateLimitingPeriod.PER_DAY) ::
    getInfo(consumerKey, RateLimitingPeriod.PER_WEEK) ::
    getInfo(consumerKey, RateLimitingPeriod.PER_MONTH) ::
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