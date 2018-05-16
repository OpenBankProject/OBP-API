package code.api.cache

import com.google.common.cache.CacheBuilder
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object InMemory {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A): A = {
    memoizeSync(ttl)(f)
  }

  def memoizeWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    memoize(ttl)(f)
  }
}
