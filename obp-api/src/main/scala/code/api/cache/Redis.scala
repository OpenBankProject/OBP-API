package code.api.cache

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import scalacache._
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}
import scalacache.redis._
import scalacache.serialization.Codec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Redis extends MdcLoggable {

  val url = APIUtil.getPropsValue("guava.cache.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("guava.cache.port", 6379)

  implicit val scalaCache = ScalaCache(RedisCache(url, port))
  implicit val flags = Flags(readsEnabled = true, writesEnabled = true)

  implicit def anyToByte[T](implicit m: Manifest[T]) = new Codec[T, Array[Byte]] {

    import com.twitter.chill.KryoInjection

    def serialize(value: T): Array[Byte] = {
      logger.debug("KryoInjection started")
      val bytes: Array[Byte] = KryoInjection(value)
      logger.debug("KryoInjection finished")
      bytes
    }

    def deserialize(data: Array[Byte]): T = {
      import scala.util.{Failure, Success}
      val tryDecode: scala.util.Try[Any] = KryoInjection.invert(data)
      tryDecode match {
        case Success(v) => v.asInstanceOf[T]
        case Failure(e) =>
          println(e)
          "NONE".asInstanceOf[T]
      }
    }
  }

  def memoizeSyncWithRedis[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A)(implicit @cacheKeyExclude m: Manifest[A]): A = {
    memoizeSync(ttl)(f)
  }

  def memoizeWithRedis[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    memoize(ttl)(f)
  }

}
