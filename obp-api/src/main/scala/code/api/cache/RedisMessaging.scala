package code.api.cache

import code.api.Constant
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import redis.clients.jedis.Jedis

import scala.jdk.CollectionConverters._

object RedisMessaging extends MdcLoggable {

  val channelTtlSeconds: Int = APIUtil.getPropsAsIntValue("messaging.channel.ttl.seconds", 3600)
  val channelMaxMessages: Int = APIUtil.getPropsAsIntValue("messaging.channel.max.messages", 1000)

  private def keyPrefix: String = s"${Constant.getGlobalCacheNamespacePrefix}signal_channel_"

  private def channelKey(channelName: String): String = s"${keyPrefix}${channelName}"

  // Deliberately NOT under keyPrefix: listChannels() globs keyPrefix* and must not see these.
  private def sequenceKey(channelName: String): String =
    s"${Constant.getGlobalCacheNamespacePrefix}signal_seq_${channelName}"

  private val SequencePrefix = """^\{"sequence":(\d+),""".r

  /**
   * The sequence stamped on a stored envelope. 0 for envelopes stored before sequences
   * existed, which sort as older than everything and are skipped by cursor reads.
   */
  def sequenceOf(storedJson: String): Long =
    Option(storedJson).flatMap(SequencePrefix.findFirstMatchIn(_)).map(_.group(1).toLong).getOrElse(0L)

  /**
   * Publish as one atomic Lua script so the sequence, the push, the trim, the TTL refresh and the
   * pub/sub fan-out cannot interleave with another publisher:
   *
   *  - sequence = Redis server time in microseconds, forced strictly greater than the channel's
   *    previous sequence. Time-based rather than a counter so a cursor stays valid across a channel
   *    expiring and being recreated (a counter would restart at 1 and strand old cursors).
   *  - the sequence is spliced into the JSON as its first field; the envelope itself never carries
   *    one, so there is exactly one "sequence" key in what is stored and fanned out.
   *  - string.format('%d') everywhere a number becomes text: Lua 5.1 prints large numbers as
   *    1.7e+15 otherwise.
   */
  private val publishScript: String =
    """local listKey, seqKey = KEYS[1], KEYS[2]
      |local msg, maxMessages, ttl, pubsubChannel = ARGV[1], tonumber(ARGV[2]), tonumber(ARGV[3]), ARGV[4]
      |local t = redis.call('TIME')
      |local seq = tonumber(t[1]) * 1000000 + tonumber(t[2])
      |local last = tonumber(redis.call('GET', seqKey) or '0')
      |if seq <= last then seq = last + 1 end
      |local seqStr = string.format('%d', seq)
      |redis.call('SET', seqKey, seqStr, 'EX', ttl)
      |local stamped = '{"sequence":' .. seqStr .. ',' .. string.sub(msg, 2)
      |redis.call('RPUSH', listKey, stamped)
      |redis.call('LTRIM', listKey, -maxMessages, -1)
      |redis.call('EXPIRE', listKey, ttl)
      |redis.call('PUBLISH', pubsubChannel, stamped)
      |return {seqStr, redis.call('LLEN', listKey)}""".stripMargin

  def validateChannelName(name: String): Boolean = {
    name.nonEmpty &&
      name.length <= 128 &&
      name.matches("^[a-zA-Z0-9._\\-]+$")
  }

  /**
   * Publish a message to a channel: stamp a sequence, RPUSH (oldest first), LTRIM to the newest
   * `maxMessages`, refresh the TTL and PUBLISH for live gRPC subscribers — atomically, see
   * publishScript. `messageJson` must be a JSON object without a "sequence" field.
   *
   * @param maxMessages overridable so a test can prove cursor reads survive trimming
   * @return (sequence stamped on the message, length of the list after push)
   */
  def publishMessage(channelName: String, messageJson: String, maxMessages: Int = channelMaxMessages): (Long, Long) = {
    require(messageJson.startsWith("{"), "signal envelope must be a JSON object")
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val result = jedis.eval(
        publishScript,
        java.util.Arrays.asList(channelKey(channelName), sequenceKey(channelName)),
        java.util.Arrays.asList(messageJson, maxMessages.toString, channelTtlSeconds.toString,
          code.signal.SignalEventBus.redisChannel(channelName))
      ).asInstanceOf[java.util.List[AnyRef]]
      (result.get(0).toString.toLong, result.get(1).asInstanceOf[java.lang.Long].longValue())
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

  /** Sequence of the newest message in a channel, 0 when the channel is empty or missing. */
  def latestSequence(channelName: String): Long = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      sequenceOf(jedisConnection.get.lindex(channelKey(channelName), -1))
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.latestSequence error for channel $channelName: ${e.getMessage}")
        throw new RuntimeException(e)
    } finally {
      jedisConnection.foreach(_.close())
    }
  }

  /**
   * Cursor read: up to `limit` messages whose sequence is greater than `afterSequence`, oldest
   * first. Unlike offset paging this is unaffected by LTRIM moving list indexes, because the
   * position is found by binary search over the (strictly increasing) stamped sequences —
   * at most log2(channelMaxMessages) LINDEX calls.
   *
   * @return (messages, total count in channel, latest sequence in channel)
   */
  def fetchMessagesAfter(channelName: String, afterSequence: Long, limit: Int): (List[String], Long, Long) = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(Redis.jedisPool.getResource())
      val jedis = jedisConnection.get
      val key = channelKey(channelName)
      val total = jedis.llen(key)
      if (total == 0L) (Nil, 0L, 0L)
      else {
        val latest = sequenceOf(jedis.lindex(key, -1))
        var lo = 0L
        var hi = total
        while (lo < hi) {
          val mid = (lo + hi) / 2
          if (sequenceOf(jedis.lindex(key, mid)) <= afterSequence) lo = mid + 1 else hi = mid
        }
        val messages = if (lo >= total) Nil else jedis.lrange(key, lo, lo + limit - 1).asScala.toList
        (messages, total, latest)
      }
    } catch {
      case e: Throwable =>
        logger.error(s"RedisMessaging.fetchMessagesAfter error for channel $channelName: ${e.getMessage}")
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
      jedis.del(sequenceKey(channelName))
      jedis.del(channelKey(channelName)) > 0
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
