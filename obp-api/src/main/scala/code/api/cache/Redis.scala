package code.api.cache

import code.api.JedisMethod
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
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

  final val poolConfig = new JedisPoolConfig()
  poolConfig.setMaxTotal(128)
  poolConfig.setMaxIdle(128)
  poolConfig.setMinIdle(16)
  poolConfig.setTestOnBorrow(true)
  poolConfig.setTestOnReturn(true)
  poolConfig.setTestWhileIdle(true)
  poolConfig.setMinEvictableIdleTimeMillis(30*60*1000)
  poolConfig.setTimeBetweenEvictionRunsMillis(30*60*1000)
  poolConfig.setNumTestsPerEvictionRun(3)
  poolConfig.setBlockWhenExhausted(true)

  def jedisPoolDestroy: Unit = jedisPool.destroy()
  val jedisPool = new JedisPool(poolConfig,url, port, 4000)

  /**
   * this is the help method, which can be used to auto close all the jedisConnection
   * 
   * @param method can only be "get" or "set" 
   * @param key the cache key
   * @param ttlSeconds the ttl is option. 
   *            if ttl == None, this means value will be cached forver 
   *            if ttl == Some(0), this means turn off the cache, do not use cache at all
   *            if ttl == Some(Int), this mean the cache will be only cached for ttl seconds
   * @param value the cache value.
   *              
   * @return
   */
  def use(method:JedisMethod.Value, key:String, ttlSeconds: Option[Int] = None, value:Option[String] = None) : Option[String] = {
    
    //we will get the connection from jedisPool later, and will always close it in the finally clause.
    var jedisConnection = None:Option[Jedis]
    
    if(ttlSeconds.equals(Some(0))){ // set ttl = 0, we will totally turn off the cache
      None
    }else{
      try {
        jedisConnection = Some(jedisPool.getResource())
        
        val redisResult = if (method ==JedisMethod.EXISTS) {
          jedisConnection.head.exists(key).toString
        }else if (method == JedisMethod.FLUSHDB) {
          jedisConnection.head.flushDB.toString
        }else if (method == JedisMethod.INCR) {
          jedisConnection.head.incr(key).toString
        }else if (method == JedisMethod.TTL) {
          jedisConnection.head.ttl(key).toString
        }else if (method == JedisMethod.DELETE) {
          jedisConnection.head.del(key).toString
        }else if (method ==JedisMethod.GET) {
          jedisConnection.head.get(key)
        } else if(method ==JedisMethod.SET && value.isDefined){
          if (ttlSeconds.isDefined) {//if set ttl, call `setex` method to set the expired seconds.
            jedisConnection.head.setex(key, ttlSeconds.get, value.get).toString
          } else {//if do not set ttl, call `set` method, the cache will be forever. 
            jedisConnection.head.set(key, value.get).toString
          }
        } else {// the use()method parameters need to be set properly, it missing value in set, then will throw the exception. 
          throw new RuntimeException("Please check the Redis.use parameters, if the method == set, the value can not be None !!!")
        }
        //change the null to Option 
        APIUtil.stringOrNone(redisResult)
      } catch {
        case e: Throwable =>
          throw new RuntimeException(e)
      } finally {
        if (jedisConnection.isDefined && jedisConnection.get != null)
          jedisConnection.map(_.close())
      }
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
