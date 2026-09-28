package code.api.v2_2_0

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicLong

import code.api.cache.Caching
import com.google.common.cache.{Cache, CacheBuilder, CacheStats}
import com.openbankproject.commons.util.JsonAliases.{compactRender, parse}
import net.liftweb.common.Loggable
import org.json4s.JValue

/**
 * Two-level cache for the fully built `GET /message-docs/CONNECTOR` response.
 *
 * Building the response runs Scala runtime reflection over every message doc's example
 * inbound and outbound message, which is expensive in CPU and grows the reflection
 * universe. The result only depends on the connector's `messageDocs`, which a connector
 * fills once while its singleton is initialised and never changes afterwards.
 *
 * Levels, checked in this order:
 *  1. In-process: a bounded Guava cache holding the immutable JValue. It keeps working when
 *     Redis is down, so an unreachable Redis can never send every request back into
 *     reflection.
 *  2. Shared: Redis, in the `message_docs` cache namespace (`Caching.getMessageDocsCache`,
 *     same TTL as the resource docs, same fail-safe behaviour: an unreachable Redis is a miss,
 *     never an error). It lets replicas and restarts reuse one instance's work.
 *
 * Contract:
 *  - Key: the connector name, and only after it has been resolved to a real connector.
 *    Unknown names fail before reaching the cache, so request input cannot grow it;
 *    `MaxEntries` is a second, hard bound on the in-process level.
 *  - Single flight: concurrent cold requests for one connector run the loader once, and
 *    therefore touch Redis and the generator once.
 *  - A failure is never cached; the next request retries.
 *  - Redis is written only after a successful generation. An unparsable Redis value is
 *    treated as a miss and regenerated.
 *  - Invalidation: bumping the `message_docs` cache namespace (for example from the cache page
 *    in API Manager) reaches both levels on every instance: the Redis keys carry the namespace
 *    version, and so do the in-process keys, which read it at most once a second
 *    (`Constant.recentCacheNamespaceVersion`). The Redis level also expires by
 *    `staticResourceDocsObp.cache.ttl.seconds`. `invalidateAll()` clears this instance's
 *    in-process level only (tests).
 */
object MessageDocsJsonCache extends Loggable {
  private val MaxEntries = 64L

  /** The shared level. Abstracted so tests can count reads and writes without a Redis. */
  trait SharedStore {
    def get(key: String): Option[String]
    def set(key: String, value: String): Unit
  }

  object RedisStore extends SharedStore {
    def get(key: String): Option[String] = Caching.getMessageDocsCache(key)
    def set(key: String, value: String): Unit = Caching.setMessageDocsCache(key, value)
  }

  private def sharedKey(connectorName: String) = s"message-docs-v2.2.0-$connectorName"

  private val cache: Cache[String, JValue] = code.telemetry.Telemetry.monitorCache(
    CacheBuilder.newBuilder().maximumSize(MaxEntries).recordStats().build[String, JValue](), "message_docs")

  // Counters for tests and monitoring. They only ever go up; compare before and after values.
  private val generatorCallsCounter = new AtomicLong(0)
  private val sharedGetsCounter = new AtomicLong(0)
  private val sharedHitsCounter = new AtomicLong(0)
  private val sharedSetsCounter = new AtomicLong(0)

  // The in-process key carries the namespace version, so a bump makes old entries unreachable here
  // too; they then age out of the bounded cache.
  private def localKey(connectorName: String): String =
    s"${code.api.Constant.recentCacheNamespaceVersion(code.api.Constant.MESSAGE_DOCS_NAMESPACE)}|$connectorName"

  def getOrCompute(connectorName: String, store: SharedStore = RedisStore)(generate: => JValue): JValue =
    try cache.get(localKey(connectorName), new Callable[JValue] {
      def call(): JValue = {
        val key = sharedKey(connectorName)
        sharedGetsCounter.incrementAndGet()
        val fromShared = store.get(key).flatMap { s =>
          try Some(parse(s))
          catch { case e: Exception => logger.warn(s"Ignoring unparsable shared message-docs entry $key: ${e.getMessage}"); None }
        }
        if (fromShared.isDefined) sharedHitsCounter.incrementAndGet()
        fromShared.getOrElse {
          // Serve the round-tripped form even on the instance that generated it. Otherwise this
          // instance would return the JValue it built while every other replica (and this one
          // after a restart) returns parse(compactRender(...)), and number formatting could differ
          // between them.
          generatorCallsCounter.incrementAndGet()
          val rendered = compactRender(generate)
          sharedSetsCounter.incrementAndGet()
          store.set(key, rendered)
          parse(rendered)
        }
      }
    })
    catch {
      // Surface the loader's own exception, not Guava's wrapper.
      case e: java.util.concurrent.ExecutionException if e.getCause != null => throw e.getCause
      case e: com.google.common.util.concurrent.UncheckedExecutionException if e.getCause != null => throw e.getCause
    }

  def invalidateAll(): Unit = cache.invalidateAll()

  def size: Long = cache.size()

  /** In-process level hits and misses (a miss goes on to the shared level). */
  def stats: CacheStats = cache.stats()

  /** Times the response was actually built, i.e. both levels missed. */
  def generatorCalls: Long = generatorCallsCounter.get()

  /** Shared level reads, reads that found a usable entry, and writes. */
  def sharedGets: Long = sharedGetsCounter.get()
  def sharedHits: Long = sharedHitsCounter.get()
  def sharedSets: Long = sharedSetsCounter.get()
}
