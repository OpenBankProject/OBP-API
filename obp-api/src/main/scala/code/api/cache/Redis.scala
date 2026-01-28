package code.api.cache

import code.api.JedisMethod
import code.api.util.APIUtil
import code.api.Constant
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import scalacache.memoization.{cacheKeyExclude, memoize, memoizeSync}
import scalacache.{Flags, ScalaCache}
import scalacache.redis.RedisCache
import scalacache.serialization.Codec
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import java.net.URI
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.io.FileInputStream
import java.security.KeyStore
import com.typesafe.config.{Config, ConfigFactory}
import net.liftweb.common.Full

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Redis extends MdcLoggable {

  val url = APIUtil.getPropsValue("cache.redis.url", "127.0.0.1")
  val port = APIUtil.getPropsAsIntValue("cache.redis.port", 6379)
  val timeout = 4000
  val password: String = APIUtil.getPropsValue("cache.redis.password") match {
    case Full(password) if password.trim.nonEmpty => password
    case _ => null
  }
  val useSsl = APIUtil.getPropsAsBoolValue("redis.use.ssl", false)

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

  val jedisPool =
    if (useSsl) {
      // SSL connection: Use SSLContext with JedisPool
      val sslContext = configureSslContext()
      new JedisPool(poolConfig, url, port, timeout, password, true, sslContext.getSocketFactory, null, null)
    } else {
      // Non-SSL connection
      new JedisPool(poolConfig, url, port, timeout, password)
    }

  // Redis startup health check
  private def performStartupHealthCheck(): Unit = {
    try {
      val namespacePrefix = Constant.getGlobalCacheNamespacePrefix
      logger.info(s"Redis startup health check: connecting to $url:$port")
      logger.info(s"Global cache namespace prefix: '$namespacePrefix'")

      val testKey = s"${namespacePrefix}obp_startup_test"
      val testValue = s"OBP started at ${new java.util.Date()}"

      // Write test key with 1 hour TTL
      use(JedisMethod.SET, testKey, Some(3600), Some(testValue))

      // Read it back
      val readResult = use(JedisMethod.GET, testKey, None, None)

      if (readResult.contains(testValue)) {
        logger.info(s"Redis health check PASSED - connected to $url:$port")
        logger.info(s"   Pool: max=${poolConfig.getMaxTotal}, idle=${poolConfig.getMaxIdle}")
        logger.info(s"   Test key: $testKey")
      } else {
        logger.warn(s"WARNING: Redis health check FAILED - could not read back test key")
      }
    } catch {
      case e: Throwable =>
        logger.error(s"ERROR: Redis health check FAILED - ${e.getMessage}")
        logger.error(s"   Redis may be unavailable at $url:$port")
    }

  }

  // Run health check on startup
  performStartupHealthCheck()

  def jedisPoolDestroy: Unit = jedisPool.destroy()

  def isRedisReady: Boolean = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(jedisPool.getResource)
      val pong = jedisConnection.get.ping() // sends PING command
      pong == "PONG"
    } catch {
      case e: Throwable =>
        logger.error(s"Redis is not ready: ${e.getMessage}")
        false
    } finally {
      jedisConnection.foreach(_.close())
    }
  }


  private def configureSslContext(): SSLContext = {

    // Load the CA certificate
    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val trustStorePassword = APIUtil.getPropsValue("truststore.password.redis")
      .getOrElse(APIUtil.initPasswd).toCharArray
    val truststorePath = APIUtil.getPropsValue("truststore.path.redis").getOrElse("")
    val trustStoreStream = new FileInputStream(truststorePath)
    trustStore.load(trustStoreStream, trustStorePassword)
    trustStoreStream.close()

    // Load the client certificate and private key
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val keyStorePassword = APIUtil.getPropsValue("keystore.password.redis")
      .getOrElse(APIUtil.initPasswd).toCharArray
    val keystorePath = APIUtil.getPropsValue("keystore.path.redis").getOrElse("")
    val keyStoreStream = new FileInputStream(keystorePath)
    keyStore.load(keyStoreStream, keyStorePassword)
    keyStoreStream.close()

    // Initialize KeyManager and TrustManager
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, keyStorePassword)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)

    // Configure and return the SSLContext
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)
    sslContext
  }

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

  /**
   * Delete all Redis keys matching a pattern using KEYS command
   * @param pattern Redis key pattern (e.g., "rl_active_CONSUMER123_*")
   * @return Number of keys deleted
   */
  def deleteKeysByPattern(pattern: String): Int = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(jedisPool.getResource())
      val jedis = jedisConnection.get

      // Use keys command for pattern matching (acceptable for rate limiting cache which has limited keys)
      // In production with millions of keys, consider using SCAN instead
      val keys = jedis.keys(pattern)

      val deletedCount = if (!keys.isEmpty) {
        val keysArray = keys.toArray(new Array[String](keys.size()))
        jedis.del(keysArray: _*).toInt
      } else {
        0
      }

      logger.info(s"Deleted $deletedCount Redis keys matching pattern: $pattern")
      deletedCount
    } catch {
      case e: Throwable =>
        logger.error(s"Error deleting keys by pattern: $pattern", e)
        0
    } finally {
      if (jedisConnection.isDefined && jedisConnection.get != null)
        jedisConnection.map(_.close())
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


  /**
   * Scan Redis keys matching a pattern using KEYS command
   * Note: In production with large datasets, consider using SCAN instead
   *
   * @param pattern Redis pattern (e.g., "rl_counter_*", "rd_*")
   * @return List of matching keys
   */
  def scanKeys(pattern: String): List[String] = {
    var jedisConnection: Option[Jedis] = None
    try {
      jedisConnection = Some(jedisPool.getResource())
      val jedis = jedisConnection.get

      import scala.collection.JavaConverters._
      val keys = jedis.keys(pattern)
      keys.asScala.toList

    } catch {
      case e: Throwable =>
        logger.error(s"Error scanning Redis keys with pattern $pattern: ${e.getMessage}")
        List.empty
    } finally {
      if (jedisConnection.isDefined && jedisConnection.get != null)
        jedisConnection.foreach(_.close())
    }
  }

  /**
   * Count keys matching a pattern
   *
   * @param pattern Redis pattern (e.g., "rl_counter_*")
   * @return Number of matching keys
   */
  def countKeys(pattern: String): Int = {
    scanKeys(pattern).size
  }

  /**
   * Get a sample key matching a pattern (first found)
   *
   * @param pattern Redis pattern
   * @return Option of a sample key
   */
  def getSampleKey(pattern: String): Option[String] = {
    scanKeys(pattern).headOption
  }
}
