package code.api.cache

import code.api.Constant._
import code.api.JedisMethod
import code.api.cache.Redis.use
import code.util.Helper.MdcLoggable

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
object Caching extends MdcLoggable {

  def memoizeSyncWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        Redis.memoizeSyncWithRedis(cacheKey)(ttl)(f)
      case _  => // Just forwarding a call
        f
    }

  }

  def memoizeWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        Redis.memoizeWithRedis(cacheKey)(ttl)(f)
      case _  => // Just forwarding a call
        f
    }

  }
  
  def memoizeSyncWithImMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        InMemory.memoizeSyncWithInMemory(cacheKey)(ttl)(f)
      case _  => // Just forwarding a call
        f
    }

  }

  def memoizeWithImMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        InMemory.memoizeWithInMemory(cacheKey)(ttl)(f)
      case _  => // Just forwarding a call
        f
    }
  }

  // Resource-doc / swagger caches. These go through the same fail-safe wrappers as the product
  // caches below: the cache is an optimisation, and an unreachable Redis must degrade to a miss
  // (recompute and serve) rather than turn every /resource-docs, /swagger and message-docs request
  // into a 500. These are the documents API Explorer and Portal load on startup, so a Redis blip
  // used to take the whole surface down.
  def getDynamicResourceDocCache(key: String): Option[String] =
    tryGet(DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL)

  def setDynamicResourceDocCache(key: String, value: String): Unit =
    trySet(DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL, value)

  def getStaticResourceDocCache(key: String): Option[String] =
    tryGet(STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL)

  def setStaticResourceDocCache(key: String, value: String): Unit =
    trySet(STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL, value)

  def getAllResourceDocCache(key: String): Option[String] =
    tryGet(ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL)

  def setAllResourceDocCache(key: String, value: String): Unit =
    trySet(ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL, value)

  def getStaticSwaggerDocCache(key: String): Option[String] =
    tryGet(STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL)

  def setStaticSwaggerDocCache(key: String, value: String): Unit =
    trySet(STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL, value)

  // Fail-safe wrappers around Redis.use. If Redis is unreachable (dev without a
  // running Redis, transient failure, etc.) we treat it as a miss and recompute instead of failing
  // the whole request.
  private def tryGet(prefix: String, key: String, ttlSeconds: Int): Option[String] =
    try use(JedisMethod.GET, (prefix + key).intern(), Some(ttlSeconds))
    catch { case e: Throwable => logger.debug(s"Cache GET failed for $prefix$key: ${e.getMessage}"); None }

  private def trySet(prefix: String, key: String, ttlSeconds: Int, value: String): Unit =
    try { use(JedisMethod.SET, (prefix + key).intern(), Some(ttlSeconds), Some(value)); () }
    catch { case e: Throwable => logger.debug(s"Cache SET failed for $prefix$key: ${e.getMessage}") }

  def getFinancialProductsCache(key: String, ttlSeconds: Int): Option[String] =
    tryGet(FINANCIAL_PRODUCTS_PREFIX, key, ttlSeconds)

  def setFinancialProductsCache(key: String, value: String, ttlSeconds: Int): Unit =
    trySet(FINANCIAL_PRODUCTS_PREFIX, key, ttlSeconds, value)

  def getApiProductsCache(key: String, ttlSeconds: Int): Option[String] =
    tryGet(API_PRODUCTS_PREFIX, key, ttlSeconds)

  def setApiProductsCache(key: String, value: String, ttlSeconds: Int): Unit =
    trySet(API_PRODUCTS_PREFIX, key, ttlSeconds, value)

  /**
   * Invalidate all rate limit cache entries for a specific consumer.
   * Uses pattern matching to delete all cache keys with prefix: rl_active_{consumerId}_*
   *
   * @param consumerId The consumer ID whose rate limit cache should be invalidated
   * @return Number of cache keys deleted
   */
  def invalidateRateLimitCache(consumerId: String): Int = {
    val pattern = s"${RATE_LIMIT_ACTIVE_PREFIX}${consumerId}_*"
    Redis.deleteKeysByPattern(pattern)
  }

  /**
   * Invalidate ALL rate limit cache entries for ALL consumers.
   * Use with caution - this clears the entire rate limiting cache namespace.
   *
   * @return Number of cache keys deleted
   */
  def invalidateAllRateLimitCache(): Int = {
    val pattern = s"${RATE_LIMIT_ACTIVE_PREFIX}*"
    Redis.deleteKeysByPattern(pattern)
  }

  
}
