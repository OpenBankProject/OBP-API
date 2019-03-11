package code.api.cache

import code.api.util.APIUtil
import net.liftweb.common.Full

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Caching {

  def memoizeSyncWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        APIUtil.getPropsValue("guava.cache") match {
          case Full(value) if value.toLowerCase == "redis" =>
            Redis.memoizeSyncWithRedis(cacheKey)(ttl)(f)
          case Full(value) if value.toLowerCase == "in-memory" =>
            InMemory.memoizeSyncWithInMemory(cacheKey)(ttl)(f)
          case _ =>
            InMemory.memoizeSyncWithInMemory(cacheKey)(ttl)(f)
        }
      case _  => // Just forwarding a call
        f
    }

  }

  def memoizeWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        APIUtil.getPropsValue("guava.cache") match {
          case Full(value) if value.toLowerCase == "redis" =>
            Redis.memoizeWithRedis(cacheKey)(ttl)(f)
          case Full(value) if value.toLowerCase == "in-memory" =>
            InMemory.memoizeWithInMemory(cacheKey)(ttl)(f)
          case _ =>
            InMemory.memoizeWithInMemory(cacheKey)(ttl)(f)
        }
      case _  => // Just forwarding a call
        f
    }

  }
}
