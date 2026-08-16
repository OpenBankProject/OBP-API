package code.api.cache

import code.util.Helper.MdcLoggable
import com.google.common.cache.{CacheBuilder, Cache => GuavaUnderlying}
import scalacache.{Cache, Entry}
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoizeF, memoizeSync}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import com.openbankproject.commons.ExecutionContext.Implicits.global

object InMemory extends MdcLoggable {

  // scalacache 0.28 types its Cache by the value type, while these wrappers are generic in A and
  // a single Guava instance has to serve every one of them. The underlying store is declared at
  // Entry[Any] and narrowed per call: the cast is erased at run time, and a given key always holds
  // the type its own call site wrote, which is the same assumption the untyped ScalaCache made.
  val underlyingGuavaCache: GuavaUnderlying[String, Entry[Any]] =
    CacheBuilder.newBuilder().maximumSize(100000L).build[String, Entry[Any]]()

  // Built once, for the same reason as Redis's: the wrapper holds no per-type state and the cast is
  // erased, so one instance serves every A instead of one allocation per cache read.
  private val sharedCache: Cache[Any] = GuavaCache(underlyingGuavaCache)
  private def cacheFor[A]: Cache[A] = sharedCache.asInstanceOf[Cache[A]]

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A): A = {
    logger.trace(s"InMemory.memoizeSyncWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    import scalacache.modes.sync._
    implicit val cache: Cache[A] = cacheFor[A]
    memoizeSync(Some(ttl))(f)
  }

  def memoizeWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    logger.trace(s"InMemory.memoizeWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    import scalacache.modes.scalaFuture._
    implicit val cache: Cache[A] = cacheFor[A]
    memoizeF(Some(ttl))(f)
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
