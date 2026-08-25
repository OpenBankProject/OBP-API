package code.api.cache

import code.api.JedisMethod
import code.api.util.APIUtil
import code.api.Constant
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import scalacache.memoization.{cacheKeyExclude, memoizeF, memoizeSync}
import scalacache.{Cache, CacheConfig, DefaultCacheKeyBuilder, Flags}
import scalacache.redis.RedisCache
import scalacache.serialization.{Codec, FailedToDecode}
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

  // Lazy so the keystore/truststore files are only read when redis.use.ssl is on, and only once
  // even though both jedisPool and every subscriber connection need the socket factory.
  private lazy val sslContext: SSLContext = configureSslContext()

  val jedisPool =
    if (useSsl) {
      // SSL connection: Use SSLContext with JedisPool
      new JedisPool(poolConfig, url, port, timeout, password, true, sslContext.getSocketFactory, null, null)
    } else {
      // Non-SSL connection
      new JedisPool(poolConfig, url, port, timeout, password)
    }

  /**
   * Build a dedicated, non-pooled connection for a pub/sub subscriber.
   *
   * `subscribe`/`psubscribe` occupy their connection for the whole life of the subscription,
   * so subscribers cannot lease from jedisPool. They must still apply the same password and
   * TLS settings the pool uses: the plain `Jedis(url, port, timeout)` constructor is
   * unencrypted, so building one directly silently ignores redis.use.ssl.
   */
  def newSubscriberConnection(): Jedis = {
    val jedis =
      if (useSsl) new Jedis(url, port, timeout, true, sslContext.getSocketFactory, null, null)
      else new Jedis(url, port, timeout)
    if (password != null) jedis.auth(password)
    jedis
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
        } else if (method == JedisMethod.SCAN) {
          import scala.jdk.CollectionConverters._
          jedisConnection.head.keys(key).asScala.mkString(",")
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

  /** Loan-pattern helper: lease a Jedis connection from the pool, run f, always close. */
  private def withJedis[A](f: Jedis => A): A = {
    val jedis = jedisPool.getResource()
    try f(jedis)
    catch { case e: Throwable => throw new RuntimeException(e) }
    finally jedis.close()
  }

  /**
   * Atomic `SET key value EX ttlSeconds NX` (Jedis 2.9.0 five-arg overload). Sets the key with a TTL
   * only if it does not already exist, in a single command. Returns true iff this call set the key.
   *
   * Use for first-write-wins caching (idempotency response cache) and lock acquisition: there is no
   * window between "set value" and "set TTL" (unlike setnx + expire), so a crash can never leave a
   * key without an expiry, and a second writer can never clobber the first.
   */
  def setNxEx(key: String, value: String, ttlSeconds: Int): Boolean = withJedis { jedis =>
    jedis.set(key, value, "NX", "EX", ttlSeconds) == "OK"
  }

  /**
   * Counter script, executed atomically server-side:
   *   - INCR the key; on first creation (count == 1) set its expiry.
   *   - Self-heal: if the key exists WITHOUT an expiry (legacy writer, PERSIST, or an RDB restore
   *     that dropped expiries), recreate it as a fresh window (count = 1, new TTL) instead of
   *     incrementing a counter that never resets — the pre-Lua implementation had this recovery
   *     branch, and without it the key's consumer would be rate-limited forever.
   *   - Return {count, ttl} together so the caller needs no second round trip, and the pair is
   *     consistent (a separate TTL read could observe a different window).
   */
  private val incrementWithTtlScript =
    """local c = redis.call('INCR', KEYS[1])
      |if c == 1 then
      |  redis.call('EXPIRE', KEYS[1], ARGV[1])
      |elseif redis.call('TTL', KEYS[1]) < 0 then
      |  redis.call('SET', KEYS[1], 1, 'EX', ARGV[1])
      |  c = 1
      |end
      |return {c, redis.call('TTL', KEYS[1])}""".stripMargin

  /**
   * Atomic increment-with-create-TTL via a single Lua script (see incrementWithTtlScript).
   * Returns (post-increment count, remaining TTL seconds). Because everything runs atomically
   * server-side, concurrent callers cannot lose increments or race the TTL set, and an
   * increment-then-compare rate-limit check cannot be bypassed by interleaving.
   */
  def incrementWithTtl(key: String, ttlSeconds: Int): (Long, Long) = withJedis { jedis =>
    val result = jedis.eval(
      incrementWithTtlScript,
      java.util.Collections.singletonList(key),
      java.util.Collections.singletonList(ttlSeconds.toString)
    ).asInstanceOf[java.util.List[java.lang.Long]]
    (result.get(0).longValue(), result.get(1).longValue())
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

  // Reuse the pool built above so the memoize-backed cache shares the same authenticated,
  // optionally SSL-configured connection. The RedisCache(url, port) overload builds its own
  // JedisPool internally with no password and no SSL, so with `requirepass` enabled it fails
  // with NOAUTH while the jedisPool-based paths keep working.
  implicit val flags = Flags(readsEnabled = true, writesEnabled = true)

  // scalacache 0.28 types its Cache by the value type, while these wrappers are generic in A. One
  // instance still serves them all: RedisCache carries no per-type state, its value type is erased,
  // and the codec below ignores the Manifest it takes, so every A would get an identical wrapper -
  // building one per call only put two allocations in front of every cache read on the request
  // path. RedisCache is a thin wrapper over the pool built above and opens nothing of its own, so
  // the pool, its authentication and its SSL configuration stay shared.
  /**
   * The serialization identity these cached bytes were produced under.
   *
   * Cache entries are Kryo-encoded, and what Kryo produces depends on the Scala library and the
   * chill build that encoded it. Two OBP-API versions compiled against different ones therefore
   * write mutually unreadable bytes into the same keys -- and "unreadable" is the optimistic
   * case. Measured across the 2.12 -> 2.13 migration: an EMPTY `List`, written by chill 0.9.3,
   * decodes under 0.9.5 into a `scala.collection.immutable.Queue`. That decode SUCCEEDS. It is
   * only at the call site, whose signature says `List`, that it fails --
   *
   *     class scala.collection.immutable.Queue cannot be cast to
   *     class scala.collection.immutable.List
   *
   * -- so the caller gets a 500 rather than a cache miss, and gets it for the whole TTL, because
   * a failed read does not evict the entry. Reproduced on `GET /management/dynamic-message-docs`
   * and `GET /management/connector-methods`: 200 on 2.12, 500 on 2.13 reading 2.12's entry, and
   * fine in either version on its own. That is a rolling upgrade, or any upgrade against a warm
   * Redis.
   *
   * The migration note anticipated the risk and described the consequence as a cold cache. For
   * values that fail to decode that is exactly right. This handles the ones that do not fail.
   *
   * Namespacing the key is the fix rather than casting defensively at each call site: there are
   * eight `List`-returning memoized methods today, the same drift can hit any other type, and no
   * amount of care at the call sites can make bytes already in Redis readable. Entries written by
   * another version simply stop being addressable and age out on their own TTL.
   *
   * The Scala binary version is the axis that moved here and is the one derived automatically.
   * `obp.cache.serialization.version` is for the case it does not cover -- a dependency upgrade
   * that changes the encoding without changing the Scala version, which is what chill 0.9.3 to
   * 0.9.5 would have been on its own. Bump it in that situation; the cost is one cold cache.
   */
  private val serializationNamespace: String = {
    val scalaBinary = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    val manual = APIUtil.getPropsValue("obp.cache.serialization.version", "1")
    s"obpser$manual-scala$scalaBinary"
  }

  // Prefixing happens here, in the key builder, rather than at the call sites: scalacache derives
  // the rest of the key from the enclosing method and its arguments, and every caller goes through
  // it. `memoizeSync` and `memoizeF` both read this same implicit config.
  implicit val cacheConfig: CacheConfig =
    CacheConfig(cacheKeyBuilder = DefaultCacheKeyBuilder(keyPrefix = Some(serializationNamespace)))

  private val sharedCache: Cache[Any] = RedisCache[Any](jedisPool)
  private def cacheFor[A]: Cache[A] = sharedCache.asInstanceOf[Cache[A]]

  implicit def anyToByte[T](implicit m: Manifest[T]): Codec[T] = new Codec[T] {

    import com.twitter.chill.KryoInjection

    def encode(value: T): Array[Byte] = {
      logger.debug("KryoInjection started")
      val bytes: Array[Byte] = KryoInjection(value)
      logger.debug("KryoInjection finished")
      bytes
    }

    def decode(data: Array[Byte]): Codec.DecodingResult[T] = {
      import scala.util.{Failure, Success}
      KryoInjection.invert(data) match {
        case Success(v) => Right(v.asInstanceOf[T])
        case Failure(e) =>
          // Decoding failed: corrupt bytes, a class-shape change across a redeploy, Kryo
          // registration drift. Never answer with a sentinel value cast to T - scalacache would
          // treat that as a HIT and hand e.g. a String to a caller expecting List[MethodRoutingT],
          // throwing ClassCastException for the whole TTL.
          //
          // Reporting the failure is what makes the cache self-heal, though the mechanism moved in
          // 0.28: the codec returns Left instead of throwing, RedisCacheBase.doGet raises it, and
          // AbstractCache._caching - the path memoize takes - wraps the read in handleNonFatal and
          // substitutes None. So a failed decode is still a miss: the source block runs and the key
          // is rewritten with a valid serialisation. RedisDeserializeMissTest pins this.
          logger.error("Redis cache decoding failed; treating as a cache miss and recomputing.", e)
          Left(FailedToDecode(e))
      }
    }
  }

  def memoizeSyncWithRedis[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => A)(implicit @cacheKeyExclude m: Manifest[A]): A = {
    import scalacache.modes.sync._
    implicit val cache: Cache[A] = cacheFor[A]
    memoizeSync(Some(ttl))(f)
  }

  def memoizeWithRedis[A](cacheKey: Option[String])(@cacheKeyExclude ttl: Duration)(@cacheKeyExclude f: => Future[A])(implicit @cacheKeyExclude m: Manifest[A]): Future[A] = {
    import scalacache.modes.scalaFuture._
    implicit val cache: Cache[A] = cacheFor[A]
    memoizeF(Some(ttl))(f)
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

      import scala.jdk.CollectionConverters._
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
