package code.logcache

import code.api.cache.{Redis, RedisLogger}
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import io.grpc.stub.StreamObserver
import redis.clients.jedis.{Jedis, JedisPubSub, Pipeline}

import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap, CopyOnWriteArrayList, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._

/**
 * Redis pub/sub event bus for log cache streaming.
 *
 * Bridges Redis pub/sub channels (published from `RedisLogger.pushLog`)
 * to gRPC streaming clients.
 *
 * Channel naming:
 * - obp_log_cache:trace   — TRACE-level entries
 * - obp_log_cache:debug   — DEBUG-level entries
 * - obp_log_cache:info    — INFO-level entries
 * - obp_log_cache:warning — WARNING-level entries
 * - obp_log_cache:error   — ERROR-level entries
 * - obp_log_cache:all     — every entry (aggregate firehose)
 *
 * Backpressure: each subscribed observer sits behind a bounded queue with
 * drop-oldest semantics, plus a dedicated delivery thread. The shared Redis
 * subscriber thread only calls `queue.offer` — a slow gRPC client cannot
 * stall delivery to other subscribers or to the subscriber thread itself.
 */
object LogCacheEventBus extends MdcLoggable {

  private val CHANNEL_PREFIX = "obp_log_cache:"
  private val ENABLED = APIUtil.getPropsAsBoolValue("grpc.log_cache_stream.enabled", true)
  private val QUEUE_MAX_ENTRIES = APIUtil.getPropsAsIntValue("grpc.log_cache_stream.observer_queue_max_entries", 1000)

  def isEnabled: Boolean = ENABLED

  // channel suffix → observers subscribed to that channel
  private val observers = new ConcurrentHashMap[String, CopyOnWriteArrayList[BufferedObserver]]()
  // raw StreamObserver → buffered wrapper, for unsubscribe lookup
  private val rawToBuffered = new ConcurrentHashMap[StreamObserver[String], BufferedObserver]()

  @volatile private var subscriberThread: Thread = _
  @volatile private var subscriberJedis: Jedis = _
  @volatile private var pubSub: JedisPubSub = _
  @volatile private var running = false

  // --- Publish (called from RedisLogger.pushLog, within an existing pipeline) ---

  /**
   * Publish a log entry to its level channel and to the `all` channel, within
   * the caller's Redis pipeline. Cheap: adds two PUBLISH commands to the
   * pipeline the caller is already flushing. Skipped entirely when the
   * stream service is disabled.
   */
  def publishInPipeline(pipeline: Pipeline, level: RedisLogger.LogLevel.LogLevel, payload: String): Unit = {
    if (!ENABLED) return
    pipeline.publish(CHANNEL_PREFIX + level.toString.toLowerCase, payload)
    if (level != RedisLogger.LogLevel.ALL) {
      pipeline.publish(CHANNEL_PREFIX + "all", payload)
    }
  }

  // --- Subscribe/unsubscribe for gRPC StreamObservers ---

  def subscribe(level: RedisLogger.LogLevel.LogLevel, observer: StreamObserver[String]): Unit = {
    val key = level.toString.toLowerCase
    val buffered = new BufferedObserver(observer, key, QUEUE_MAX_ENTRIES)
    rawToBuffered.put(observer, buffered)
    observers.computeIfAbsent(key, _ => new CopyOnWriteArrayList[BufferedObserver]())
    observers.get(key).add(buffered)
    buffered.start()
    logger.info(s"LogCacheEventBus says: Observer subscribed to $key (total: ${observers.get(key).size})")
  }

  def unsubscribe(level: RedisLogger.LogLevel.LogLevel, observer: StreamObserver[String]): Unit = {
    val key = level.toString.toLowerCase
    val buffered = rawToBuffered.remove(observer)
    if (buffered != null) {
      buffered.stop()
      val list = observers.get(key)
      if (list != null) {
        list.remove(buffered)
        logger.info(s"LogCacheEventBus says: Observer unsubscribed from $key (remaining: ${list.size}, dropped during session: ${buffered.droppedCount})")
        if (list.isEmpty) observers.remove(key)
      }
    }
  }

  // --- Lifecycle ---

  def start(): Unit = {
    if (!ENABLED || running) return
    running = true

    pubSub = new JedisPubSub {
      override def onPMessage(pattern: String, channel: String, message: String): Unit = {
        val key = channel.stripPrefix(CHANNEL_PREFIX)
        val list = observers.get(key)
        if (list != null) {
          list.asScala.foreach(_.offer(message))
        }
      }
    }

    subscriberThread = new Thread(() => {
      try {
        subscriberJedis = new Jedis(Redis.url, Redis.port, Redis.timeout)
        if (Redis.password != null) subscriberJedis.auth(Redis.password)
        logger.info(s"LogCacheEventBus says: Redis subscriber started, pattern-subscribing to ${CHANNEL_PREFIX}*")
        subscriberJedis.psubscribe(pubSub, s"${CHANNEL_PREFIX}*")
      } catch {
        case e: Throwable if running =>
          logger.error(s"LogCacheEventBus says: Redis subscriber thread died: ${e.getMessage}")
        case _: Throwable => // shutting down
      }
    }, "log-cache-event-bus-subscriber")
    subscriberThread.setDaemon(true)
    subscriberThread.start()

    logger.info("LogCacheEventBus says: Started")
  }

  def stop(): Unit = {
    running = false
    try { if (pubSub != null) pubSub.punsubscribe() } catch { case _: Throwable => }
    try { if (subscriberJedis != null) subscriberJedis.close() } catch { case _: Throwable => }
    observers.values.asScala.foreach(_.asScala.foreach(_.stop()))
    observers.clear()
    rawToBuffered.clear()
    logger.info("LogCacheEventBus says: Stopped")
  }

  // --- Buffered delivery (per-subscriber backpressure) ---

  /**
   * Wraps a raw StreamObserver with a bounded queue and a dedicated delivery
   * thread. Redis subscriber thread calls offer(); delivery thread drains.
   * On queue-full: drop oldest. On delivery error: stop the thread and let
   * the gRPC cancel handler clean up the subscription.
   */
  private class BufferedObserver(
    inner: StreamObserver[String],
    channelKey: String,
    queueSize: Int
  ) extends MdcLoggable {
    private val queue = new ArrayBlockingQueue[String](queueSize)
    private val dropped = new AtomicLong(0)
    @volatile private var alive = true
    private var thread: Thread = _

    def droppedCount: Long = dropped.get()

    def offer(msg: String): Unit = {
      if (!queue.offer(msg)) {
        queue.poll() // drop oldest
        queue.offer(msg)
        val d = dropped.incrementAndGet()
        if (d == 1L || d % 1000L == 0L) {
          logger.warn(s"LogCacheEventBus says: Dropping messages on $channelKey (slow consumer); dropped so far: $d")
        }
      }
    }

    def start(): Unit = {
      thread = new Thread(() => {
        try {
          while (alive) {
            val msg = queue.poll(200L, TimeUnit.MILLISECONDS)
            if (msg != null) {
              try {
                inner.synchronized { inner.onNext(msg) }
              } catch {
                case e: Throwable =>
                  logger.info(s"LogCacheEventBus says: Delivery to observer on $channelKey failed, stopping delivery thread: ${e.getMessage}")
                  alive = false
              }
            }
          }
        } catch {
          case _: InterruptedException => // normal shutdown
        }
      }, s"log-cache-event-bus-delivery-$channelKey")
      thread.setDaemon(true)
      thread.start()
    }

    def stop(): Unit = {
      alive = false
      if (thread != null) thread.interrupt()
    }
  }
}
