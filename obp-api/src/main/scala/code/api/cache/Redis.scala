package code.api.cache

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import redis.clients.jedis.Jedis
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}
import scalacache.{Flags, ScalaCache}
import scalacache.redis.RedisCache
import scalacache.serialization.Codec

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Redis extends MdcLoggable {

  val url = APIUtil.getPropsValue("cache.redis.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("cache.redis.port", 6379)

  lazy val jedis = new Jedis(url, port)

  def isRedisAvailable() = {
    try {
      val uuid = APIUtil.generateUUID()
      jedis.connect()
      jedis.set(uuid, "10")
      jedis.exists(uuid) == true
    } catch {
      case e: Throwable =>
        logger.warn("------------| Redis.isRedisAvailable |------------")
        logger.warn(e)
        false
    }
  }

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
