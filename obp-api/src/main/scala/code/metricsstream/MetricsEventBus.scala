package code.metricsstream

import code.api.cache.Redis
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import io.grpc.stub.StreamObserver
import redis.clients.jedis.{Jedis, JedisPubSub}

import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap, CopyOnWriteArrayList, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._

/**
 * Redis pub/sub event bus for metrics streaming.
 *
 * Bridges the Redis pub/sub channel (published from
 * `WriteMetricUtil.writeEndpointMetric` after each REST call's metric is
 * saved) to gRPC streaming clients.
 *
 * Channel naming:
 * - obp_metrics:all — every metric event (single channel; server-side
 *   filtering happens in `MetricsStreamServiceImpl`).
 *
 * Why single-channel: unlike log cache which has a natural low-cardinality
 * dimension (level), metrics filters are high-cardinality (consumer_id,
 * user_id, url). Pre-computing a Redis channel per filter combination
 * would explode. Broadcast + filter in the service is simpler and still
 * avoids shipping unwanted events over gRPC.
 *
 * Backpressure: each subscribed observer sits behind a bounded queue with
 * drop-oldest semantics, plus a dedicated delivery thread. The shared Redis
 * subscriber thread only calls `queue.offer` — a slow gRPC client cannot
 * stall delivery to other subscribers or to the subscriber thread itself.
 */
object MetricsEventBus extends MdcLoggable {

  private val CHANNEL_PREFIX = "obp_metrics:"
  private val ALL_CHANNEL = CHANNEL_PREFIX + "all"
  private val ENABLED = APIUtil.getPropsAsBoolValue("grpc.metrics_stream.enabled", true)
  private val QUEUE_MAX_ENTRIES = APIUtil.getPropsAsIntValue("grpc.metrics_stream.observer_queue_max_entries", 1000)

  def isEnabled: Boolean = ENABLED

  private val observers = new CopyOnWriteArrayList[BufferedObserver]()
  private val rawToBuffered = new ConcurrentHashMap[StreamObserver[String], BufferedObserver]()

  @volatile private var subscriberThread: Thread = _
  @volatile private var subscriberJedis: Jedis = _
  @volatile private var pubSub: JedisPubSub = _
  @volatile private var running = false

  // --- Publish (called from WriteMetricUtil after saveMetric) ---

  /**
   * Borrow a connection from the pool, publish, release. No-op when disabled.
   * Fire-and-forget semantics: callers should not rely on delivery.
   */
  def publish(payload: String): Unit = {
    if (!ENABLED) return
    var jedis: Jedis = null
    try {
      jedis = Redis.jedisPool.getResource
      jedis.publish(ALL_CHANNEL, payload)
    } catch {
      case e: Throwable =>
        logger.warn(s"MetricsEventBus says: Failed to publish metric: ${e.getMessage}")
    } finally {
      if (jedis != null) jedis.close()
    }
  }

  // --- Subscribe/unsubscribe for gRPC StreamObservers ---

  def subscribe(observer: StreamObserver[String]): Unit = {
    val buffered = new BufferedObserver(observer, QUEUE_MAX_ENTRIES)
    rawToBuffered.put(observer, buffered)
    observers.add(buffered)
    buffered.start()
    logger.info(s"MetricsEventBus says: Observer subscribed (total: ${observers.size})")
  }

  def unsubscribe(observer: StreamObserver[String]): Unit = {
    val buffered = rawToBuffered.remove(observer)
    if (buffered != null) {
      buffered.stop()
      observers.remove(buffered)
      logger.info(s"MetricsEventBus says: Observer unsubscribed (remaining: ${observers.size}, dropped during session: ${buffered.droppedCount})")
    }
  }

  // --- Lifecycle ---

  def start(): Unit = {
    if (!ENABLED || running) return
    running = true

    pubSub = new JedisPubSub {
      override def onMessage(channel: String, message: String): Unit = {
        observers.asScala.foreach(_.offer(message))
      }
    }

    subscriberThread = new Thread(() => {
      try {
        subscriberJedis = new Jedis(Redis.url, Redis.port, Redis.timeout)
        if (Redis.password != null) subscriberJedis.auth(Redis.password)
        logger.info(s"MetricsEventBus says: Redis subscriber started, subscribing to $ALL_CHANNEL")
        subscriberJedis.subscribe(pubSub, ALL_CHANNEL)
      } catch {
        case e: Throwable if running =>
          logger.error(s"MetricsEventBus says: Redis subscriber thread died: ${e.getMessage}")
        case _: Throwable => // shutting down
      }
    }, "metrics-event-bus-subscriber")
    subscriberThread.setDaemon(true)
    subscriberThread.start()

    logger.info("MetricsEventBus says: Started")
  }

  def stop(): Unit = {
    running = false
    try { if (pubSub != null) pubSub.unsubscribe() } catch { case _: Throwable => }
    try { if (subscriberJedis != null) subscriberJedis.close() } catch { case _: Throwable => }
    observers.asScala.foreach(_.stop())
    observers.clear()
    rawToBuffered.clear()
    logger.info("MetricsEventBus says: Stopped")
  }

  // --- Buffered delivery (per-subscriber backpressure) ---

  private class BufferedObserver(
    inner: StreamObserver[String],
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
          logger.warn(s"MetricsEventBus says: Dropping messages (slow consumer); dropped so far: $d")
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
                  logger.info(s"MetricsEventBus says: Delivery to observer failed, stopping delivery thread: ${e.getMessage}")
                  alive = false
              }
            }
          }
        } catch {
          case _: InterruptedException => // normal shutdown
        }
      }, "metrics-event-bus-delivery")
      thread.setDaemon(true)
      thread.start()
    }

    def stop(): Unit = {
      alive = false
      if (thread != null) thread.interrupt()
    }
  }
}
