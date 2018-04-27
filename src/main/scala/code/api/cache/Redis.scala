package code.api.cache

import code.util.Helper.MdcLoggable
import scalacache._
import scalacache.memoization.{cacheKeyExclude, memoizeSync}
import scalacache.redis._
import scalacache.serialization.Codec

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Redis extends MdcLoggable {

  implicit val scalaCache = ScalaCache(RedisCache("127.0.0.1", 6379))
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

  def memoizeSyncWithRedis[A](unique: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A)(implicit @cacheKeyExclude m: Manifest[A]): A = {
    memoizeSync(ttl)(f)(scalaCache, flags, anyToByte)
  }

}
