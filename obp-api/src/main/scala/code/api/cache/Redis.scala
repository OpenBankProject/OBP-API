package code.api.cache

import code.api.JedisMethod
import code.api.util.APIUtil
import code.api.Constant
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
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

  // ---------------------------------------------------------------------------------------
  // Memoize layer. scalacache used to provide this; it is dead upstream (no Scala 3 release
  // since a 2021 milestone), so the same contract is implemented directly on the pool above.
  // Three things are deliberately byte-compatible with what scalacache 0.28 produced, because
  // live Redis entries and external tooling depend on them - CacheKeyFormatTest pins all three:
  //
  //   1. The stored KEY. scalacache's memoization macro derived it from the enclosing wrapper
  //      method: "code.api.cache.Redis.<method>(Some(<cacheKey>))" followed by one "()" per
  //      @cacheKeyExclude'd parameter list (sampled from a live instance). Rate-limit counters
  //      (S-2: rate limiting is a security control) and NewStyle's "*getMethodRoutings*"
  //      pattern invalidation both address keys inside this envelope, so a format change would
  //      silently detach them.
  //   2. The VALUE bytes: chill/Kryo, unchanged.
  //   3. The failure semantics: any cache-layer error - unreachable Redis, corrupt bytes, a
  //      class-shape change across a redeploy - is a MISS: recompute from the source block,
  //      try to rewrite the key, self-heal on the next call. Never a sentinel value (the
  //      pre-0.28 codec's "NONE".asInstanceOf[T] bug), never an exception on the request
  //      path. RedisDeserializeMissTest pins the decode half.
  //
  //   TTL: psetex(max(1, ttl.toMillis)) keeps scalacache's millisecond precision, so a
  //   sub-second TTL expires when it says it does - see cachePut below for why setex was
  //   wrong here. A non-finite ttl stores without expiry, as scalacache's ttl=None did.

  /** True on a Scala 3 build. Deliberately a class probe rather than a version string: see
   *  serializationNamespace. Any failure to load is read as "not Scala 3", which is the safe
   *  direction - the 2.13 spelling is what develop already uses. */
  private[cache] val isScala3Runtime: Boolean =
    try {
      Class.forName("scala.runtime.Scala3RunTime", false, getClass.getClassLoader)
      true
    } catch {
      case _: Throwable => false
    }

  /**
   * The serialization identity these cached bytes were produced under.
   *
   * Ported from develop, which added it on scalacache's CacheConfig; this branch replaced
   * scalacache (no Scala 3 release since a 2021 milestone) with the memoize layer above, so the
   * prefix is applied here in the key builder instead. The reason is unchanged and measured:
   * cache entries are Kryo-encoded, and what Kryo produces depends on the Scala library and chill
   * build that encoded it. Across 2.12 -> 2.13 an EMPTY `List` written by chill 0.9.3 decodes
   * under 0.9.5 into a `scala.collection.immutable.Queue`. That decode SUCCEEDS; it fails only at
   * the call site, whose signature says `List` -
   *
   *     class scala.collection.immutable.Queue cannot be cast to
   *     class scala.collection.immutable.List
   *
   * - so the caller gets a 500 rather than a miss, for the whole TTL, because a failed read does
   * not evict. Reproduced on `GET /management/dynamic-message-docs` and
   * `GET /management/connector-methods`. Namespacing the key is the fix rather than casting at
   * each call site: entries written by another version simply stop being addressable and age out.
   *
   * This matters more on this branch than on develop, not less: the compiler moves to Scala 3
   * here. `versionNumberString` alone does NOT see that move - Scala 3 compiles against the 2.13
   * standard library, so it reports "2.13" for a Scala 3 build too, and this branch would have
   * shared develop's namespace on exactly the upgrade the namespace exists to protect. Hence the
   * probe: `scala.runtime.Scala3RunTime` ships in scala3-library and does not exist in
   * scala-library 2.13, so its presence is the compiler generation, which
   * `versionNumberString` cannot report. Both halves are kept, because the encoding depends on
   * both the compiler that produced the classes and the library they were compiled against.
   *
   * `obp.cache.serialization.version` covers the case the Scala version does not - a dependency
   * upgrade that changes the encoding on its own, which is what chill 0.9.3 to 0.9.5 would have
   * been. Bump it then; the cost is one cold cache.
   */
  private[cache] val serializationNamespace: String = {
    val libraryBinary = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    // A 2.13 build keeps the spelling develop produces, so only the Scala 3 side moves.
    val scalaBinary = if (isScala3Runtime) s"3-lib$libraryBinary" else libraryBinary
    val manual = APIUtil.getPropsValue("obp.cache.serialization.version", "1")
    s"obpser$manual-scala$scalaBinary"
  }

  /** The namespace and the caller key are joined here and nowhere else, so a test can exercise
   *  the real composition with a namespace of its own rather than re-implementing it. */
  private[cache] def composeMemoKey(namespace: String, callerKey: String): String = namespace + callerKey

  private[cache] def redisMemoKey(wrapperMethod: String, cacheKey: Option[String], excludedParamLists: Int): String =
    composeMemoKey(
      serializationNamespace,
      s"code.api.cache.Redis.$wrapperMethod($cacheKey)" + ("()" * excludedParamLists))

  import com.twitter.chill.KryoInjection

  private[cache] def encode(value: Any): Array[Byte] = KryoInjection(value)

  private[cache] def decode[A](bytes: Array[Byte]): Option[A] =
    KryoInjection.invert(bytes) match {
      case scala.util.Success(v) => Some(v.asInstanceOf[A])
      case scala.util.Failure(e) =>
        logger.error("Redis cache decoding failed; treating as a cache miss and recomputing.", e)
        None
    }

  private val utf8 = java.nio.charset.StandardCharsets.UTF_8

  private def cacheGet[A](key: String): Option[A] =
    try {
      Option(withJedis(_.get(key.getBytes(utf8)))).flatMap(decode[A])
    } catch {
      case scala.util.control.NonFatal(e) =>
        logger.warn(s"Redis cache read failed; treating as a miss: ${e.getMessage}")
        None
    }

  private def cachePut(key: String, value: Any, ttl: Duration): Unit =
    try {
      val keyBytes = key.getBytes(utf8)
      // psetex, not setex: its unit is milliseconds, so a sub-second TTL expires when it says
      // it does. setex takes whole seconds, and rounding up to a one-second floor would make
      // every TTL below a second longer than asked for - a behaviour change from scalacache,
      // which stored with millisecond precision. No current caller passes a sub-second TTL
      // (the connector.cache.ttl.seconds.* props are whole seconds, and a zero TTL never
      // reaches here - Caching forwards it uncached), so this is about not leaving a trap for
      // the caller who does. RedisTtlPrecisionTest pins it.
      if (ttl.isFinite) withJedis(_.psetex(keyBytes, math.max(1L, ttl.toMillis), encode(value)))
      else withJedis(_.set(keyBytes, encode(value)))
      ()
    } catch {
      case scala.util.control.NonFatal(e) =>
        logger.warn(s"Redis cache write failed; result served uncached: ${e.getMessage}")
    }

  def memoizeSyncWithRedis[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    val key = redisMemoKey("memoizeSyncWithRedis", cacheKey, 3)
    cacheGet[A](key) match {
      case Some(v) => v
      case None =>
        val v = f
        cachePut(key, v, ttl)
        v
    }
  }

  def memoizeWithRedis[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    val key = redisMemoKey("memoizeWithRedis", cacheKey, 3)
    // The read runs on the pool's thread, not the caller's, matching scalacache's Future mode;
    // a failed read is a miss (cacheGet already swallows), and only a miss evaluates f.
    Future(cacheGet[A](key)).flatMap {
      case Some(v) => Future.successful(v)
      case None    => f.map { v => cachePut(key, v, ttl); v }
    }
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
