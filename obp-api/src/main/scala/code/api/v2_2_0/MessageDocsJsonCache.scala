package code.api.v2_2_0

import java.util.concurrent.Callable

import com.google.common.cache.{Cache, CacheBuilder}

/**
 * In-process cache for the fully built `GET /message-docs/CONNECTOR` response.
 *
 * Building the response runs Scala runtime reflection over every message doc's example
 * inbound and outbound message, which is expensive in CPU and grows the reflection
 * universe. The result only depends on the connector's `messageDocs`, which a connector
 * fills once while its singleton is initialised and never changes afterwards, so it is
 * safe to keep per connector name for the life of the process.
 *
 * Contract:
 *  - Key: the connector name, and only after it has been resolved to a real connector.
 *    Unknown names fail before reaching the cache, so request input cannot grow it;
 *    `MaxEntries` is a second, hard bound.
 *  - Single flight: concurrent cold requests for one connector run the generator once.
 *  - A generator failure is not cached; the next request retries.
 *  - The cached value is an immutable case class tree and is shared between requests.
 *  - Invalidation: `invalidateAll()`. Nothing in production mutates a connector's
 *    message docs after start-up, so nothing calls it there.
 */
object MessageDocsJsonCache {
  private val MaxEntries = 64L

  private val cache: Cache[String, JSONFactory220.MessageDocsJson] =
    CacheBuilder.newBuilder().maximumSize(MaxEntries).build[String, JSONFactory220.MessageDocsJson]()

  def getOrCompute(connectorName: String)(generate: => JSONFactory220.MessageDocsJson): JSONFactory220.MessageDocsJson =
    try cache.get(connectorName, new Callable[JSONFactory220.MessageDocsJson] { def call() = generate })
    catch {
      // Surface the generator's own exception, not Guava's wrapper.
      case e: java.util.concurrent.ExecutionException if e.getCause != null => throw e.getCause
      case e: com.google.common.util.concurrent.UncheckedExecutionException if e.getCause != null => throw e.getCause
    }

  def invalidateAll(): Unit = cache.invalidateAll()

  def size: Long = cache.size()
}
