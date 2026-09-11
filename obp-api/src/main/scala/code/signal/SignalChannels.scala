package code.signal

import code.api.cache.RedisMessaging
import code.api.util.CustomJsonFormats
import code.api.v6_0_0.{PostSignalMessageJsonV600, SignalChannelInfoJsonV600, SignalMessageJsonV600, SignalMessagePublishedJsonV600, SignalMessagesJsonV600}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.Extraction
import org.json4s.jvalue2extractable
import org.json4s.jvalue2monadic

import java.util.UUID.randomUUID
import scala.util.Try

/**
 * The signal-channel behaviour shared by the REST endpoints (Http4s600) and
 * the gRPC SignalChannelsService, so both transports build the same envelope,
 * apply the same privacy filter and list channels the same way. Validation
 * (channel name, size cap, dangerous characters) stays in the callers because
 * each transport reports failures differently.
 */
object SignalChannels {

  // Scala 3 requires an explicit type on an implicit definition.
  private implicit val formats: org.json4s.Formats = CustomJsonFormats.formats

  private def utcTimestampNow(): String = {
    val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    sdf.format(new java.util.Date())
  }

  /** Build the envelope, store it and notify live subscribers. */
  def publish(channelName: String, senderUserId: String, senderConsumerId: String, post: PostSignalMessageJsonV600): SignalMessagePublishedJsonV600 = {
    val messageId = randomUUID().toString
    val timestamp = utcTimestampNow()
    val envelope = SignalMessageJsonV600(
      message_id = messageId, channel_name = channelName,
      sender_consumer_id = senderConsumerId, sender_user_id = senderUserId,
      to_user_id = post.to_user_id, timestamp = timestamp,
      message_type = post.message_type.getOrElse(""),
      payload = post.payload)
    // The sequence is stamped inside Redis (atomically with the push); strip the placeholder.
    val msgStr = JsonAliases.compactRender(Extraction.decompose(envelope).removeField(_._1 == "sequence"))
    val (sequence, count) = RedisMessaging.publishMessage(channelName, msgStr)
    SignalMessagePublishedJsonV600(messageId, channelName, timestamp, count, sequence)
  }

  def parseMessage(raw: String): Option[SignalMessageJsonV600] =
    Try(JsonAliases.parse(raw).extract[SignalMessageJsonV600]).toOption

  /** Broadcasts are visible to everyone; a private message only to its sender and recipient. */
  def isVisibleTo(msg: SignalMessageJsonV600, userId: String): Boolean =
    msg.to_user_id.isEmpty || msg.to_user_id.contains(userId) || msg.sender_user_id == userId

  /**
   * Fetch a page of a channel and apply the privacy filter for `userId`.
   *
   * With `afterSequence` this is a cursor read (messages newer than that sequence), which is the
   * way to poll: offset paging drifts once the channel is trimmed to its newest N messages.
   * Without it, plain offset/limit paging over whatever the channel currently holds.
   * In both modes next_after_sequence is the sequence of the last raw message in the window,
   * visible or not, so a caller can always advance.
   */
  def fetch(channelName: String, offset: Int, limit: Int, afterSequence: Option[Long], userId: String): SignalMessagesJsonV600 =
    afterSequence match {
      case Some(after) =>
        val (raw, total, latest) = RedisMessaging.fetchMessagesAfter(channelName, after, limit)
        val nextAfter = raw.lastOption.map(RedisMessaging.sequenceOf).getOrElse(after)
        page(channelName, raw, total, hasMore = nextAfter < latest, latest, nextAfter, userId)
      case None =>
        val (raw, total) = RedisMessaging.fetchMessages(channelName, offset, limit)
        val latest = RedisMessaging.latestSequence(channelName)
        val nextAfter = raw.lastOption.map(RedisMessaging.sequenceOf).getOrElse(0L)
        page(channelName, raw, total, hasMore = (offset + limit) < total, latest, nextAfter, userId)
    }

  private def page(channelName: String, raw: List[String], total: Long, hasMore: Boolean,
                   latest: Long, nextAfter: Long, userId: String): SignalMessagesJsonV600 = {
    val visible = raw.flatMap(parseMessage).filter(isVisibleTo(_, userId))
    SignalMessagesJsonV600(channelName, visible, total, hasMore, latest, nextAfter, visibleCount(channelName, userId))
  }

  /** How many messages in the channel the caller may see. Counted over the whole channel, not
   *  the page, so it is comparable with total_count. One extra Redis read per fetch. */
  def visibleCount(channelName: String, userId: String): Long = {
    val (raw, _) = RedisMessaging.fetchMessages(channelName, 0, RedisMessaging.channelMaxMessages)
    raw.flatMap(parseMessage).count(isVisibleTo(_, userId)).toLong
  }

  /** Channels holding at least one broadcast message. Private-only channels are not listed. */
  def listBroadcastChannels(): List[SignalChannelInfoJsonV600] =
    RedisMessaging.listChannels().flatMap { name =>
      RedisMessaging.channelInfo(name).flatMap { case (count, ttl) =>
        val (messages, _) = RedisMessaging.fetchMessages(name, 0, count.toInt)
        val hasBroadcast = messages.exists(s => parseMessage(s).exists(_.to_user_id.isEmpty))
        if (hasBroadcast) Some(SignalChannelInfoJsonV600(name, count, ttl)) else None
      }
    }

  /** Every channel, private-only ones included. */
  def listAllChannels(): List[SignalChannelInfoJsonV600] =
    RedisMessaging.listChannels().flatMap { name =>
      RedisMessaging.channelInfo(name).map { case (count, ttl) => SignalChannelInfoJsonV600(name, count, ttl) }
    }
}
