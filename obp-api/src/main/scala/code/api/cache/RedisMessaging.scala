package code.api.cache

import code.api.Constant
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import redis.clients.jedis.Jedis

import scala.collection.JavaConverters._

object RedisMessaging extends MdcLoggable {

  val channelTtlSeconds: Int = APIUtil.getPropsAsIntValue("messaging.channel.ttl.seconds", 3600)
  val channelMaxMessages: Int = APIUtil.getPropsAsIntValue("messaging.channel.max.messages", 1000)

  private def keyPrefix: String = s"${Constant.getGlobalCacheNamespacePrefix}signal_channel_"

  private def channelKey(channelName: String): String = s"${keyPrefix}${channelName}"

  def validateChannelName(name: String): Boolean = {
    name.nonEmpty &&
      name.length <= 128 &&
      name.matches("^[a-zA-Z0-9._\\-]+$")
  }

  /**
   * Publish a message to a channel.
   * Uses RPUSH so messages are ordered oldest-first (index 0 = oldest).
   * LTRIM caps the list at max messages. EXPIRE refreshes the TTL.
   *
   * @return the length of the list after push
   */
  def publishMessage(channelName: String, messageJson: String): Long = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val key = channelKey(channelName)

      val length = jedis.rpush(key, messageJson)
      // Cap the list: keep only the last N messages
      jedis.ltrim(key, -channelMaxMessages.toLong, -1)
      // Refresh TTL on every publish
      jedis.expire(key, channelTtlSeconds)
      // Pub/sub notification for live gRPC subscribers — fire-and-forget, no persistence
      jedis.publish(s"obp_signal:$channelName", messageJson)
      length
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.publishMessage error for channel $channelName: ${e.getMessage}")
        throw new RuntimeException(e)
    } finally {
      jedisConnection.foreach(_.close())
    }
  }

  /**
   * Fetch messages from a channel with offset/limit pagination.
   * Messages are ordered oldest-first (index 0 = oldest).
   *
   * @return (list of message JSON strings, total count in channel)
   */
  def fetchMessages(channelName: String, offset: Int, limit: Int): (List[String], Long) = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val key = channelKey(channelName)

      val totalCount = jedis.llen(key)
      val messages = jedis.lrange(key, offset.toLong, (offset + limit - 1).toLong)
      (messages.asScala.toList, totalCount)
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.fetchMessages error for channel $channelName: ${e.getMessage}")
        throw new RuntimeException(e)
    } finally {
      jedisConnection.foreach(_.close())
    }
  }

  /**
   * List all active channel names by scanning for the key prefix.
   *
   * @return list of channel names (prefix stripped)
   */
  def listChannels(): List[String] = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val pattern = s"${keyPrefix}*"
      val keys = jedis.keys(pattern).asScala.toList
      keys.map(_.stripPrefix(keyPrefix))
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.listChannels error: ${e.getMessage}")
        List.empty
    } finally {
      jedisConnection.foreach(_.close())
    }
  }

  /**
   * Delete a channel.
   *
   * @return true if the key was deleted
   */
  def deleteChannel(channelName: String): Boolean = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val key = channelKey(channelName)
      jedis.del(key) > 0
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.deleteChannel error for channel $channelName: ${e.getMessage}")
        false
    } finally {
      jedisConnection.foreach(_.close())
    }
  }

  /**
   * Get channel info: message count and remaining TTL.
   *
   * @return Some((messageCount, ttlSeconds)) or None if channel doesn't exist
   */
  def channelInfo(channelName: String): Option[(Long, Long)] = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val key = channelKey(channelName)

      val exists = jedis.exists(key)
      if (exists) {
        val count = jedis.llen(key)
        val ttl = jedis.ttl(key)
        Some((count, ttl))
      } else {
        None
      }
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.channelInfo error for channel $channelName: ${e.getMessage}")
        None
    } finally {
      jedisConnection.foreach(_.close())
    }
  }
}
