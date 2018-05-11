package code.api.cache

import code.api.util.APIUtil
import net.liftweb.common.Full

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Caching {

  def memoizeSyncWithProvider[A](unique: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (unique, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        APIUtil.getPropsValue("guava.cache") match {
          case Full(value) if value.toLowerCase == "redis" =>
            Redis.memoizeSyncWithRedis(unique)(ttl)(f)
          case Full(value) if value.toLowerCase == "in-memory" =>
            InMemory.memoizeSyncWithInMemory(unique)(ttl)(f)
          case _ =>
            InMemory.memoizeSyncWithInMemory(unique)(ttl)(f)
        }
      case _  => // Just forwarding a call
        f
    }

  }

}
