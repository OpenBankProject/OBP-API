package code.api.util

import code.api.util.RateLimitPeriod.LimitCallPeriod
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props
import redis.clients.jedis.Jedis

import scala.collection.immutable


object RateLimitPeriod extends Enumeration {
  type LimitCallPeriod = Value
  val PER_MINUTE, PER_HOUR, PER_DAY, PER_WEEK, PER_MONTH, PER_YEAR = Value

  def toSeconds(period: LimitCallPeriod): Long = {
    period match {
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
      case PER_MINUTE => "per minute"
      case PER_HOUR   => "per hour"
      case PER_DAY    => "per day"
      case PER_WEEK   => "per week"
      case PER_MONTH  => "per month"
      case PER_YEAR   => "per year"
    }
  }
}

object RateLimitUtil extends MdcLoggable {

  val useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", false)

  lazy val jedis = Props.mode match {
    case Props.RunModes.Test  =>
      import ai.grakn.redismock.RedisServer
      import redis.clients.jedis.Jedis
      val server = RedisServer.newRedisServer // bind to a random port
      server.start()
      new Jedis(server.getHost, server.getBindPort)
    case _ =>
      val port = APIUtil.getPropsAsIntValue("redis_port", 6379)
      val url = APIUtil.getPropsValue("redis_address", "127.0.0.1")
      new Jedis(url, port)
  }

  def isRedisAvailable() = {
    try {
      val uuid = APIUtil.generateUUID()
      jedis.connect()
      jedis.set(uuid, "10")
      jedis.exists(uuid) == true
    } catch {
      case e : Throwable => false
    }
  }

  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = consumerKey + RateLimitPeriod.toString(period)

  def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {
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

  def incrementConsumerCounters(consumerKey: String, period: LimitCallPeriod, limit: Long): (Long, Long) = {
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
                val seconds =  RateLimitPeriod.toSeconds(period).toInt
                jedis.setex(key, seconds, "1")
                (seconds, 1)
              case _ => // otherwise increment the counter
                val cnt = jedis.incr(key)
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

  def ttl(consumerKey: String, period: LimitCallPeriod): Long = {
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
      getInfo(consumerKey, RateLimitPeriod.PER_MINUTE) ::
      getInfo(consumerKey, RateLimitPeriod.PER_HOUR) ::
      getInfo(consumerKey, RateLimitPeriod.PER_DAY) ::
      getInfo(consumerKey, RateLimitPeriod.PER_WEEK) ::
      getInfo(consumerKey, RateLimitPeriod.PER_MONTH) ::
        Nil
    } else {
      Nil
    }
  }


}