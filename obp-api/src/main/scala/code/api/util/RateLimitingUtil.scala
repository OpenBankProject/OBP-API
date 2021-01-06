package code.api.util

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.TooManyRequests
import code.api.util.RateLimitingJson.CallLimit
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Props
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
  
  val useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", false)
  val inMemoryMode = APIUtil.getPropsAsBoolValue("use_consumer_limits_in_memory_mode", false)

  lazy val jedis = Props.mode match {
    case Props.RunModes.Test  =>
      startMockedRedis(mode="Test")
    case _ =>
      if(inMemoryMode == true) {
        startMockedRedis(mode="In-Memory")
      } else {
        val port = APIUtil.getPropsAsIntValue("redis_port", 6379)
        val url = APIUtil.getPropsValue("redis_address", "127.0.0.1")
        new Jedis(url, port)
      }
  }

  private def startMockedRedis(mode: String): Jedis = {
    import ai.grakn.redismock.RedisServer
    import redis.clients.jedis.Jedis
    val server = RedisServer.newRedisServer // bind to a random port
    server.start()
    logger.info(msg = "-------------| Mocked Redis instance has been run in " + mode + " mode") 
    logger.info(msg = "-------------| at host: " + server.getHost)
    logger.info(msg = "-------------| at port: " + server.getBindPort)
    new Jedis(server.getHost, server.getBindPort)
  }

  def isRedisAvailable() = {
    try {
      val uuid = APIUtil.generateUUID()
      jedis.connect()
      jedis.set(uuid, "10")
      jedis.exists(uuid) == true
    } catch {
      case e : Throwable =>
        logger.warn("------------| RateLimitUtil.isRedisAvailable |------------")
        logger.warn(e)
        false
    }
  }

  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = consumerKey + RateLimitingPeriod.toString(period)

  private def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {
    if (useConsumerLimits) {
      try {
        if (jedis.isConnected() == false) jedis.connect()
        (limit, jedis.isConnected()) match {
          case (_, false)  => // Redis is NOT available
            logger.warn("Redis is NOT available")
            true
          case (l, true) if l > 0 => // Redis is available and limit is set
            val key = createUniqueKey(consumerKey, period)
            val exists = jedis.exists(key)
            exists match {
              case java.lang.Boolean.TRUE =>
                val underLimit = jedis.get(key).toLong + 1 <= limit // +1 means we count the current call as well. We increment later i.e after successful call.
                underLimit
              case java.lang.Boolean.FALSE => // In case that key does not exist we return successful result
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
        if (jedis.isConnected() == false) jedis.connect()
        (jedis.isConnected(), limit) match {
          case (false, _)  => // Redis is NOT available
            logger.warn("Redis is NOT available")
            (-1, -1)
          case (true, -1)  => // Limit is not set for the period
            val key = createUniqueKey(consumerKey, period)
            jedis.del(key) // Delete the key in accordance to SQL database state. I.e. limit = -1 => delete the key from Redis.
            (-1, -1)
          case _ => // Redis is available and limit is set
            val key = createUniqueKey(consumerKey, period)
            val ttl =  jedis.ttl(key).toInt
            ttl match {
              case -2 => // if the Key does not exists, -2 is returned
                val seconds =  RateLimitingPeriod.toSeconds(period).toInt
                jedis.setex(key, seconds, "1")
                (seconds, 1)
              case _ => // otherwise increment the counter
                // TODO redis-mock has a bug "INCR clears TTL" 
                inMemoryMode match {
                  case true =>
                    val cnt: Long = jedis.get(key).toLong + 1
                    jedis.setex(key, ttl, String.valueOf(cnt))
                    (ttl, cnt)
                  case false =>
                    val cnt = jedis.incr(key)
                    (ttl, cnt)
                }
                
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
    val ttl =  jedis.ttl(key).toInt
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
      val ttl =  jedis.ttl(key).toLong
      ttl match {
        case -2 =>
          ((None, None), period)
        case _ =>
          ((Some(jedis.get(key).toLong), Some(ttl)), period)
      }
    }

    if(isRedisAvailable()) {
      getInfo(consumerKey, RateLimitingPeriod.PER_SECOND) ::
      getInfo(consumerKey, RateLimitingPeriod.PER_MINUTE) ::
      getInfo(consumerKey, RateLimitingPeriod.PER_HOUR) ::
      getInfo(consumerKey, RateLimitingPeriod.PER_DAY) ::
      getInfo(consumerKey, RateLimitingPeriod.PER_WEEK) ::
      getInfo(consumerKey, RateLimitingPeriod.PER_MONTH) ::
        Nil
    } else {
      Nil
    }
  }

  /**
    * This function checks rate limiting for a Consumer.
    * It will check rate limiting per minute, hour, day, week and month.
    * In case any of the above is hit an error is thrown.
    * In case two or more limits are hit rate limit with lower period has precedence regarding the error message.
    * @param userAndCallContext is a Tuple (Box[User], Option[CallContext]) provided from getUserAndSessionContextFuture function
    * @return a Tuple (Box[User], Option[CallContext]) enriched with rate limiting header or an error.
    */
  def underCallLimits(userAndCallContext: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    val perHourLimitAnonymous = APIUtil.getPropsAsIntValue("user_consumer_limit_anonymous_access", 1000)
    def composeMsgAuthorizedAccess(period: LimitCallPeriod, limit: Long): String = TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for this Consumer."
    def composeMsgAnonymousAccess(period: LimitCallPeriod, limit: Long): String = TooManyRequests + s" We only allow $limit requests ${RateLimitingPeriod.humanReadable(period)} for anonymous access."

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
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = z._1))
        .map(_.copy(`X-Rate-Limit-Remaining` = limit - z._2))
    }
    def setXRateLimitsAnonymous(id: String, z: (Long, Long), period: LimitCallPeriod): Option[CallContext] = {
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = z._1))
        .map(_.copy(`X-Rate-Limit-Remaining` = limit - z._2))
    }

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
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = remain))
        .map(_.copy(`X-Rate-Limit-Remaining` = 0)).map(_.toLight)
    }

    def exceededRateLimitAnonymous(id: String, period: LimitCallPeriod): Option[CallContextLight] = {
      val remain = ttl(id, period)
      val limit = period match {
        case PER_HOUR   => perHourLimitAnonymous
        case _   => -1
      }
      userAndCallContext._2.map(_.copy(`X-Rate-Limit-Limit` = limit))
        .map(_.copy(`X-Rate-Limit-Reset` = remain))
        .map(_.copy(`X-Rate-Limit-Remaining` = 0)).map(_.toLight)
    }

    userAndCallContext._2 match {
      case Some(cc) =>
        cc.rateLimiting match {
          case Some(rl) => // Authorized access
            val rateLimitingKey = 
              rl.consumer_id + 
              rl.api_name.getOrElse("") + 
              rl.api_version.getOrElse("") + 
              rl.bank_id.getOrElse("")
            val checkLimits = List(
              underConsumerLimits(rateLimitingKey, PER_SECOND, rl.per_second),
              underConsumerLimits(rateLimitingKey, PER_MINUTE, rl.per_minute),
              underConsumerLimits(rateLimitingKey, PER_HOUR, rl.per_hour),
              underConsumerLimits(rateLimitingKey, PER_DAY, rl.per_day),
              underConsumerLimits(rateLimitingKey, PER_WEEK, rl.per_week),
              underConsumerLimits(rateLimitingKey, PER_MONTH, rl.per_month)
            )
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
                val incrementCounters = List (
                  incrementConsumerCounters(rateLimitingKey, PER_SECOND, rl.per_second),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(rateLimitingKey, PER_MINUTE, rl.per_minute),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(rateLimitingKey, PER_HOUR, rl.per_hour),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(rateLimitingKey, PER_DAY, rl.per_day),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(rateLimitingKey, PER_WEEK, rl.per_week),  // Responses other than the 429 status code MUST be stored by a cache.
                  incrementConsumerCounters(rateLimitingKey, PER_MONTH, rl.per_month)  // Responses other than the 429 status code MUST be stored by a cache.
                )
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
          case None => // Anonymous access
            val consumerId = cc.ipAddress
            val checkLimits = List(
              underConsumerLimits(consumerId, PER_HOUR, perHourLimitAnonymous)
            )
            checkLimits match {
              case x1 :: Nil if x1 == false =>
                (fullBoxOrException(Empty ~> APIFailureNewStyle(composeMsgAnonymousAccess(PER_HOUR, perHourLimitAnonymous), 429, exceededRateLimitAnonymous(consumerId, PER_HOUR))), userAndCallContext._2)
              case _ =>
                val incrementCounters = List (
                  incrementConsumerCounters(consumerId, PER_HOUR, perHourLimitAnonymous),  // Responses other than the 429 status code MUST be stored by a cache.
                )
                incrementCounters match {
                  case x1 :: Nil if x1._1 > 0 =>
                    (userAndCallContext._1, setXRateLimitsAnonymous(consumerId, x1, PER_HOUR))
                  case _  =>
                    (userAndCallContext._1, userAndCallContext._2)
                }
            }
        }
      case _ => (userAndCallContext._1, userAndCallContext._2)
    }
  }


}