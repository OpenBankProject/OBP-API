package code.api.cache

import code.util.Helper.MdcLoggable
import com.google.common.cache.{CacheBuilder, Cache => GuavaUnderlying}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import com.openbankproject.commons.ExecutionContext.Implicits.global

object InMemory extends MdcLoggable {

  /** What scalacache's GuavaCache stored per key: the value plus its own expiry stamp.
    * Guava only bounds the size; the TTL check happens at read time, exactly as before. */
  private[cache] final case class Entry(value: Any, expiresAtMillis: Option[Long]) {
    def isExpired: Boolean = expiresAtMillis.exists(_ <= System.currentTimeMillis())
  }

  // Same single shared instance as the scalacache era: the store is untyped (Entry holds Any)
  // and narrowed per call site - the cast is erased, and a given key always holds the type its
  // own call site wrote. JSONFactory6.0.0 reads .size() off this directly.
  val underlyingGuavaCache: GuavaUnderlying[String, Entry] =
    CacheBuilder.newBuilder().maximumSize(100000L).build[String, Entry]()

  // scalacache's memoization macro derived the stored key from the enclosing wrapper method:
  // full name, the one non-excluded parameter list rendered with its argument, then one "()"
  // per excluded list. Byte-compatible so countKeys("*<cacheKey>*") patterns (pinned by
  // InMemoryCachingTest) and any operator tooling keep matching. CacheKeyFormatTest pins it.
  private[cache] def inMemoryMemoKey(wrapperMethod: String, cacheKey: Option[String], excludedParamLists: Int): String =
    s"code.api.cache.InMemory.$wrapperMethod($cacheKey)" + ("()" * excludedParamLists)

  private def entryFor(value: Any, ttl: Duration): Entry =
    Entry(value, if (ttl.isFinite) Some(System.currentTimeMillis() + ttl.toMillis) else None)

  private def lookup[A](key: String): Option[A] =
    Option(underlyingGuavaCache.getIfPresent(key)) match {
      case Some(e) if e.isExpired =>
        underlyingGuavaCache.invalidate(key)
        None
      case Some(e) => Some(e.value.asInstanceOf[A])
      case None    => None
    }

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => A): A = {
    logger.trace(s"InMemory.memoizeSyncWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    val key = inMemoryMemoKey("memoizeSyncWithInMemory", cacheKey, 2)
    lookup[A](key) match {
      case Some(v) => v
      case None =>
        val v = f
        underlyingGuavaCache.put(key, entryFor(v, ttl))
        v
    }
  }

  def memoizeWithInMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    logger.trace(s"InMemory.memoizeWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    val key = inMemoryMemoKey("memoizeWithInMemory", cacheKey, 3)
    lookup[A](key) match {
      case Some(v) => Future.successful(v)
      case None    => f.map { v => underlyingGuavaCache.put(key, entryFor(v, ttl)); v }
    }
  }

  /**
   * Count keys matching a pattern in the in-memory cache
   * @param pattern Pattern to match (supports * wildcard)
   * @return Number of matching keys
   */
  def countKeys(pattern: String): Int = {
    try {
      val regex = pattern.replace("*", ".*").r
      val allKeys = underlyingGuavaCache.asMap().keySet()
      import scala.jdk.CollectionConverters._
      allKeys.asScala.count(key => regex.pattern.matcher(key).matches())
    } catch {
      case e: Throwable =>
        logger.error(s"Error counting in-memory cache keys for pattern $pattern: ${e.getMessage}")
        0
    }
  }
}
