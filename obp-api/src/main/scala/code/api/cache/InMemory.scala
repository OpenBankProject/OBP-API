package code.api.cache

import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import com.openbankproject.commons.ExecutionContext.Implicits.global

object InMemory extends MdcLoggable {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(100000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A): A = {
    logger.trace(s"InMemory.memoizeSyncWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    memoizeSync(ttl)(f)
  }

  def memoizeWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    logger.trace(s"InMemory.memoizeWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    memoize(ttl)(f)
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
      import scala.collection.JavaConverters._
      allKeys.asScala.count(key => regex.pattern.matcher(key).matches())
    } catch {
      case e: Throwable =>
        logger.error(s"Error counting in-memory cache keys for pattern $pattern: ${e.getMessage}")
        0
    }
  }
}
