package code.api.cache

import code.api.util.APIUtil
import net.liftweb.common.Full

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import com.softwaremill.macmemo.{Cache, MemoCacheBuilder, MemoizeParams}

import scala.reflect.runtime.universe._
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

  /**
   * the default MemoCacheBuilder for annotation OBPMemoize
   *
   * e.g:
   *{{{
   *   import Caching._
   *
   *     @OBPMemoize(ttl = 2 hours, maxSize = 111)
   *     def hello(name: String, age: Int): Future[String] = ???
   *}}}
   */
  implicit object OBPCacheBuilder extends  MemoCacheBuilder {
    override def build[V : TypeTag : Manifest](bucketId: String, params: MemoizeParams): Cache[V] = new Cache[V] {
      val ttl = params.expiresAfterMillis
      var isFuture = implicitly[TypeTag[V]].tpe <:< typeOf[Future[_]]
      var fixedReturnType = false

      override def get(key: List[Any], compute: => V): V = {
        val cacheKey = bucketId + "_" + key.mkString("_")
        if(isFuture) {
          val result = memoizeWithProvider(Some(cacheKey))(ttl)(compute.asInstanceOf[Future[Any]])
          result.asInstanceOf[V]
        } else if(implicitly[TypeTag[V]].tpe =:= typeOf[Any] && !fixedReturnType) {
          val result = compute
          isFuture = result.isInstanceOf[Future[_]]
          fixedReturnType = true
          this.get(key, result)
        } else {
          val result = memoizeSyncWithProvider(Some(cacheKey))(ttl)(compute)
          if(result.isInstanceOf[Future[_]]) {
            isFuture = true
          }
          result
        }
      }
    }
  }

}
