package code.api.util

import code.api.util.LimitCallPeriod.LimitCallPeriod
import code.util.Helper.MdcLoggable
import redis.clients.jedis.Jedis


object LimitCallPeriod extends Enumeration {
  type LimitCallPeriod = Value
  val MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY = Value

  def toSeconds(period: LimitCallPeriod): Long = {
    period match {
      case MINUTELY => 60
      case HOURLY   => 60 * 60
      case DAILY    => 60 * 60 * 24
      case WEEKLY   => 60 * 60 * 24 * 7
      case MONTHLY  => 60 * 60 * 24 * 7 * 30
      case YEARLY   => 60 * 60 * 24 * 7 * 365
    }
  }

  def toString(period: LimitCallPeriod): String = {
    period match {
      case MINUTELY => "MINUTELY"
      case HOURLY   => "HOURLY"
      case DAILY    => "DAILY"
      case WEEKLY   => "WEEKLY"
      case MONTHLY  => "MONTHLY"
      case YEARLY   => "YEARLY"
    }
  }
}

object LimitCallsUtil extends MdcLoggable {

  val url = APIUtil.getPropsValue("redis_address", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("redis_port", 6379)
  val useConsumerLimits = APIUtil.getPropsAsBoolValue("use_consumer_limits", false)
  lazy val jedis = new Jedis(url, port)

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
            case java.lang.Boolean.FALSE =>
              true
          }
        case _ =>
          true
      }
    } else {
      true
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


}