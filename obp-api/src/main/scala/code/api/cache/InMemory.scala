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

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A): A = {
    logger.debug(s"InMemory.memoizeSyncWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    memoizeSync(ttl)(f)
  }

  def memoizeWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    logger.debug(s"InMemory.memoizeWithInMemory.underlyingGuavaCache size ${underlyingGuavaCache.size()}, current cache key is $cacheKey")
    memoize(ttl)(f)
  }
}
