package code.chat

import org.json4s._
import code.api.cache.Redis
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import io.grpc.stub.StreamObserver
import com.openbankproject.commons.util.json
import org.json4s.{Extraction, JsonAST}
import org.json4s.native.Serialization.write
import redis.clients.jedis.{Jedis, JedisPubSub}

import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import scala.collection.JavaConverters._

/**
 * Redis pub/sub event bus for chat real-time streaming.
 *
 * Bridges REST write operations (create/update/delete message, typing, etc.)
 * to gRPC streaming clients via Redis pub/sub.
 *
 * Channel naming:
 * - obp_chat:message:{chatRoomId}  — new/updated/deleted messages
 * - obp_chat:typing:{chatRoomId}   — typing indicators
 * - obp_chat:presence:{chatRoomId} — online/offline status
 * - obp_chat:unread:{userId}       — unread count updates
 */
object ChatEventBus extends MdcLoggable {

  implicit val formats = json.DefaultFormats

  private val CHANNEL_PREFIX = "obp_chat:"

  // Local observer registry: channel key → list of StreamObservers
  private val observers = new ConcurrentHashMap[String, CopyOnWriteArrayList[StreamObserver[String]]]()

  // Dedicated subscriber connection and thread
  @volatile private var subscriberThread: Thread = _
  @volatile private var subscriberJedis: Jedis = _
  @volatile private var pubSub: JedisPubSub = _
  @volatile private var running = false

  // --- Publish methods ---

  def publishMessage(chatRoomId: String, payload: String): Unit = {
    publish(s"message:$chatRoomId", payload)
  }

  def publishTyping(chatRoomId: String, payload: String): Unit = {
    publish(s"typing:$chatRoomId", payload)
  }

  def publishPresence(chatRoomId: String, payload: String): Unit = {
    publish(s"presence:$chatRoomId", payload)
  }

  def publishUnread(userId: String, payload: String): Unit = {
    publish(s"unread:$userId", payload)
  }

  private def publish(channelSuffix: String, payload: String): Unit = {
    val channel = CHANNEL_PREFIX + channelSuffix
    var jedis: Jedis = null
    try {
      jedis = Redis.jedisPool.getResource
      jedis.publish(channel, payload)
    } catch {
      case e: Throwable =>
        logger.error(s"ChatEventBus says: Failed to publish to $channel: ${e.getMessage}")
    } finally {
      if (jedis != null) jedis.close()
    }
  }

  // --- Subscribe/unsubscribe for local StreamObservers ---

  def subscribe(channelSuffix: String, observer: StreamObserver[String]): Unit = {
    val key = channelSuffix
    observers.computeIfAbsent(key, _ => new CopyOnWriteArrayList[StreamObserver[String]]())
    observers.get(key).add(observer)
    logger.info(s"ChatEventBus says: Observer subscribed to $key (total: ${observers.get(key).size})")
  }

  def unsubscribe(channelSuffix: String, observer: StreamObserver[String]): Unit = {
    val list = observers.get(channelSuffix)
    if (list != null) {
      list.remove(observer)
      logger.info(s"ChatEventBus says: Observer unsubscribed from $channelSuffix (remaining: ${list.size})")
      if (list.isEmpty) {
        observers.remove(channelSuffix)
      }
    }
  }

  // --- Lifecycle ---

  def start(): Unit = {
    if (running) return
    running = true

    pubSub = new JedisPubSub {
      override def onPMessage(pattern: String, channel: String, message: String): Unit = {
        // channel is e.g. "obp_chat:message:room-123"
        // strip prefix to get "message:room-123"
        val key = channel.stripPrefix(CHANNEL_PREFIX)
        val list = observers.get(key)
        if (list != null) {
          list.asScala.foreach { observer =>
            try {
              observer.synchronized {
                observer.onNext(message)
              }
            } catch {
              case e: Throwable =>
                logger.warn(s"ChatEventBus says: Failed to deliver to observer on $key, removing: ${e.getMessage}")
                list.remove(observer)
            }
          }
        }
      }
    }

    subscriberThread = new Thread(() => {
      try {
        // Dedicated connection for the subscriber (not from the pool)
        subscriberJedis = new Jedis(Redis.url, Redis.port, Redis.timeout)
        if (Redis.password != null) subscriberJedis.auth(Redis.password)
        logger.info(s"ChatEventBus says: Redis subscriber started, pattern-subscribing to ${CHANNEL_PREFIX}*")
        subscriberJedis.psubscribe(pubSub, s"${CHANNEL_PREFIX}*")
      } catch {
        case e: Throwable if running =>
          logger.error(s"ChatEventBus says: Redis subscriber thread died: ${e.getMessage}")
        case _: Throwable => // shutting down, ignore
      }
    }, "chat-event-bus-subscriber")
    subscriberThread.setDaemon(true)
    subscriberThread.start()

    logger.info("ChatEventBus says: Started")
  }

  def stop(): Unit = {
    running = false
    try {
      if (pubSub != null) pubSub.punsubscribe()
    } catch {
      case _: Throwable => // ignore
    }
    try {
      if (subscriberJedis != null) subscriberJedis.close()
    } catch {
      case _: Throwable => // ignore
    }
    observers.clear()
    logger.info("ChatEventBus says: Stopped")
  }
}
