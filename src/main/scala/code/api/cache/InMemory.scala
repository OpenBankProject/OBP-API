package code.api.cache

import com.google.common.cache.CacheBuilder
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoizeSync}

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object InMemory {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  def memoizeSyncWithInMemory[A](unique: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A): A = {
    memoizeSync(ttl)(f)
  }

}
