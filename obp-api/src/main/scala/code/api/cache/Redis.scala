package code.api.cache

import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import redis.clients.jedis.Jedis
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}
import scalacache.{Flags, ScalaCache}
import scalacache.redis.RedisCache
import scalacache.serialization.Codec
import net.liftweb.json
import net.liftweb.json.{Extraction, Formats, parse}

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
  implicit def formats: Formats = CustomJsonFormats.formats

  implicit def anyToByte[T](implicit m: Manifest[T]) = new Codec[T, Array[Byte]] {

    import com.twitter.chill.KryoInjection
    def serialize(value: T): Array[Byte] = {
      val jsonString = json.compactRender(Extraction.decompose(value))
      logger.debug("KryoInjection started")
      logger.trace(s"redis.anyToByte.serialize value is $jsonString")
      val bytes: Array[Byte] = KryoInjection(jsonString)
      logger.debug("KryoInjection finished")
      bytes
    }

    def deserialize(data: Array[Byte]): T = {
      import scala.util.{Failure, Success}
      val tryDecode: scala.util.Try[Any] = KryoInjection.invert(data)
      tryDecode match {
        case Success(v) => json.parse(v.toString).extract[T]
        case Failure(e) =>
          logger.error(e)
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
