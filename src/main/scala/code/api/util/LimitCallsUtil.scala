package code.api.util

import code.api.util.LimitCallPeriod.LimitCallPeriod
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props
import redis.clients.jedis.Jedis


object LimitCallPeriod extends Enumeration {
  type LimitCallPeriod = Value
  val PER_MINUTE, PER_HOUR, PER_DAY, PER_WEEK, PER_MONTH, PER_YEAR = Value

  def toSeconds(period: LimitCallPeriod): Long = {
    period match {
      case PER_MINUTE => 60
      case PER_HOUR   => 60 * 60
      case PER_DAY    => 60 * 60 * 24
      case PER_WEEK   => 60 * 60 * 24 * 7
      case PER_MONTH  => 60 * 60 * 24 * 7 * 30
      case PER_YEAR   => 60 * 60 * 24 * 7 * 365
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

object LimitCallsUtil extends MdcLoggable {

  val useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", false)

  lazy val jedis = Props.mode match {
    case Props.RunModes.Test  =>
      import redis.clients.jedis.Jedis
      import ai.grakn.redismock.RedisServer
      val server = RedisServer.newRedisServer // bind to a random port
      server.start()
      new Jedis(server.getHost, server.getBindPort)
    case _ =>
      val port = APIUtil.getPropsAsIntValue("redis_port", 6379)
      val url = APIUtil.getPropsValue("redis_address", "127.0.0.1")
      new Jedis(url, port)
  }

  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = consumerKey + LimitCallPeriod.toString(period)

  def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {
    if (useConsumerLimits) {
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
    } else {
      true // Rate Limiting disabled implies successful result
    }
  }

  def incrementConsumerCounters(consumerKey: String, period: LimitCallPeriod, limit: Long): (Long, Long) = {
    if (useConsumerLimits) {
      if (jedis.isConnected() == false) jedis.connect()
      (jedis.isConnected(), limit) match {
        case (false, _)  => // Redis is NOT available
          logger.warn("Redis is NOT available")
          (-1, -1)
        case (true, -1)  => // Limit is not set for the period
          (-1, -1)
        case _ => // Redis is available and limit is set
          val key = createUniqueKey(consumerKey, period)
          val ttl =  jedis.ttl(key).toInt
          ttl match {
            case -2 => // if the Key does not exists, -2 is returned
              val seconds =  LimitCallPeriod.toSeconds(period).toInt
              jedis.setex(key, seconds, "1")
              (seconds, 1)
            case _ => // otherwise increment the counter
              val cnt = jedis.incr(key)
              (ttl, cnt)
          }
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


}