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

  val url = APIUtil.getPropsValue("guava.cache.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("guava.cache.port", 6379)
  val jedis = new Jedis(url, port)

  private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) = consumerKey + LimitCallPeriod.toString(period)

  def underConsumerLimits(consumerKey: String, period: LimitCallPeriod, limit: Long): Boolean = {
    limit match {
      case l if l > 0 =>
        val key = createUniqueKey(consumerKey, period)
        val exists = jedis.exists(key)
        exists match {
          case java.lang.Boolean.TRUE =>
            jedis.get(key).toLong <= limit
          case java.lang.Boolean.FALSE =>
            true
        }
      case _ =>
        true
    }
  }

  def incrementConsumerCounters(consumerKey: String, period: LimitCallPeriod): Long = {
    val key = createUniqueKey(consumerKey, period)
    val ttl = jedis.ttl(key).toInt
    ttl match {
      case -2 => // if the Key does not exists, -2 is returned
        jedis.setex(key, LimitCallPeriod.toSeconds(period).toInt, "1")
        ttl
      case _ =>
        jedis.incr(key)
        ttl
    }
  }

}