package code.api.util

import code.util.Helper.MdcLoggable
import redis.clients.jedis.Jedis

object LimitCallsUtil extends MdcLoggable {

  val url = APIUtil.getPropsValue("guava.cache.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("guava.cache.port", 6379)
  val jedis = new Jedis(url, port)

  def underConsumerLimits(consumerKey: String, limit: Long): Boolean = {
    val exists = jedis.exists(consumerKey)
    exists match {
      case java.lang.Boolean.TRUE =>
        jedis.get(consumerKey).toLong <= limit
      case java.lang.Boolean.FALSE =>
        true
    }
}

  def incrementConsumerCounters(consumerKey: String, seconds: Int): Long = {
    val exists = jedis.exists(consumerKey)
    exists match {
      case java.lang.Boolean.TRUE =>
        jedis.incr(consumerKey)
      case java.lang.Boolean.FALSE =>
        jedis.setex(consumerKey, seconds, "1")
        jedis.get(consumerKey).toLong
    }
  }

}