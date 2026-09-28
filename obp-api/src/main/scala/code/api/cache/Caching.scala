/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.cache

import code.api.Constant._
import code.api.JedisMethod
import code.api.cache.Redis.use
import code.util.Helper.MdcLoggable

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
object Caching extends MdcLoggable {

  // ===== Telemetry =====

  // A key built by CacheKeyFromArguments renders as "(Owner,method,arguments...)". Its first two
  // fields name the cached code, from a list the code fixes; the arguments do not.
  private val MacroKeyShape = """^\(([A-Za-z_][\w.$]*),([A-Za-z_][\w$]*),""".r.unanchored

  /**
   * The Telemetry label for a cache key: "Owner.method" for a key built by CacheKeyFromArguments,
   * and "other" for a key a caller wrote itself, which may contain an identifier (a consumer id,
   * a date) and so must never become a tag value.
   */
  private[cache] def telemetryLabel(cacheKey: Option[String]): String = cacheKey match {
    case Some(MacroKeyShape(owner, method)) => s"$owner.$method"
    case _ => "other"
  }

  /** Counts one memoised call as a hit, or as a miss when the cached function had to run. */
  private def recordMemoizeGet(provider: String, cacheKey: Option[String], computed: Boolean): Unit =
    code.telemetry.Telemetry.counter("obp.api.memoize.gets",
      "provider" -> provider, "cache" -> telemetryLabel(cacheKey), "result" -> (if (computed) "miss" else "hit"))
      .increment()

  private def recordedSync[A](provider: String, cacheKey: Option[String])(memoize: (=> A) => A)(f: => A): A = {
    var computed = false
    val result = memoize { computed = true; f }
    recordMemoizeGet(provider, cacheKey, computed)
    result
  }

  private def recordedAsync[A](provider: String, cacheKey: Option[String])(memoize: (=> Future[A]) => Future[A])(f: => Future[A]): Future[A] = {
    val computed = new java.util.concurrent.atomic.AtomicBoolean(false)
    val result = memoize { computed.set(true); f }
    result.onComplete(_ => recordMemoizeGet(provider, cacheKey, computed.get))(scala.concurrent.ExecutionContext.parasitic)
    result
  }

  def memoizeSyncWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        recordedSync[A]("redis", cacheKey)(g => Redis.memoizeSyncWithRedis(cacheKey)(ttl)(g))(f)
      case _  => // Just forwarding a call
        f
    }

  }

  def memoizeWithProvider[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        recordedAsync[A]("redis", cacheKey)(g => Redis.memoizeWithRedis(cacheKey)(ttl)(g))(f)
      case _  => // Just forwarding a call
        f
    }

  }
  
  def memoizeSyncWithImMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        recordedSync[A]("in_memory", cacheKey)(g => InMemory.memoizeSyncWithInMemory(cacheKey)(ttl)(g))(f)
      case _  => // Just forwarding a call
        f
    }

  }

  def memoizeWithImMemory[A](cacheKey: Option[String])(ttl: Duration)(f: => Future[A])(implicit m: Manifest[A]): Future[A] = {
    (cacheKey, ttl) match {
      case (_, t) if t == Duration.Zero  => // Just forwarding a call
        f
      case (Some(_), _) => // Caching a call
        recordedAsync[A]("in_memory", cacheKey)(g => InMemory.memoizeWithInMemory(cacheKey)(ttl)(g))(f)
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
    tryGet("dynamic_resource_docs", DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL)

  def setDynamicResourceDocCache(key: String, value: String): Unit =
    trySet("dynamic_resource_docs", DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL, value)

  def getStaticResourceDocCache(key: String): Option[String] =
    tryGet("static_resource_docs", STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL)

  def setStaticResourceDocCache(key: String, value: String): Unit =
    trySet("static_resource_docs", STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL, value)

  def getAllResourceDocCache(key: String): Option[String] =
    tryGet("all_resource_docs", ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL)

  def setAllResourceDocCache(key: String, value: String): Unit =
    trySet("all_resource_docs", ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, key, GET_DYNAMIC_RESOURCE_DOCS_TTL, value)

  // Also holds the connector JSON Schemas served by v6.0.0 message-docs/CONNECTOR/json-schema.
  def getStaticSwaggerDocCache(key: String): Option[String] =
    tryGet("static_swagger", STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL)

  def setStaticSwaggerDocCache(key: String, value: String): Unit =
    trySet("static_swagger", STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, key, GET_STATIC_RESOURCE_DOCS_TTL, value)

  // Fail-safe wrappers around Redis.use. If Redis is unreachable (dev without a
  // running Redis, transient failure, etc.) we treat it as a miss and recompute instead of failing
  // the whole request.
  //
  // Each read and write is counted for Telemetry under `cacheName`, a fixed name chosen at the call
  // site (the key prefixes carry the instance and version namespace and are not meant for people).
  // A read that failed because Redis was unreachable counts as "error", not "miss", so a Redis
  // outage does not look like a cold cache.
  private def tryGet(cacheName: String, prefix: String, key: String, ttlSeconds: Int): Option[String] = {
    val outcome: Either[Throwable, Option[String]] =
      try Right(use(JedisMethod.GET, (prefix + key).intern(), Some(ttlSeconds)))
      catch { case e: Throwable => logger.debug(s"Cache GET failed for $prefix$key: ${e.getMessage}"); Left(e) }
    val result = outcome match {
      case Right(Some(_)) => "hit"
      case Right(None) => "miss"
      case Left(_) => "error"
    }
    code.telemetry.Telemetry.counter("obp.api.redis_cache.gets", "cache" -> cacheName, "result" -> result).increment()
    outcome.toOption.flatten
  }

  private def trySet(cacheName: String, prefix: String, key: String, ttlSeconds: Int, value: String): Unit = {
    val result =
      try { use(JedisMethod.SET, (prefix + key).intern(), Some(ttlSeconds), Some(value)); "success" }
      catch { case e: Throwable => logger.debug(s"Cache SET failed for $prefix$key: ${e.getMessage}"); "error" }
    code.telemetry.Telemetry.counter("obp.api.redis_cache.sets", "cache" -> cacheName, "result" -> result).increment()
  }

  def getFinancialProductsCache(key: String, ttlSeconds: Int): Option[String] =
    tryGet("financial_products", FINANCIAL_PRODUCTS_PREFIX, key, ttlSeconds)

  def setFinancialProductsCache(key: String, value: String, ttlSeconds: Int): Unit =
    trySet("financial_products", FINANCIAL_PRODUCTS_PREFIX, key, ttlSeconds, value)

  def getApiProductsCache(key: String, ttlSeconds: Int): Option[String] =
    tryGet("api_products", API_PRODUCTS_PREFIX, key, ttlSeconds)

  def setApiProductsCache(key: String, value: String, ttlSeconds: Int): Unit =
    trySet("api_products", API_PRODUCTS_PREFIX, key, ttlSeconds, value)

  /**
   * Invalidate all rate limit cache entries for a specific consumer.
   * Uses pattern matching to delete all cache keys with prefix: rl_active_{consumerId}_*
   *
   * @param consumerId The consumer ID whose rate limit cache should be invalidated
   * @return Number of cache keys deleted
   */
  def invalidateRateLimitCache(consumerId: String): Int = {
    // scalacache stores the entry as
    //   <serialization namespace>:code.api.cache.Redis.memoizeSyncWithRedis(Some(<our cache key>))()()
    // so the glob must be unanchored at the front, as "*getMethodRoutings*" is. Without the
    // leading "*" this deleted nothing (silently) and a new or changed rate limit only took
    // effect when the hour cache expired. Pinned by CacheKeyFormatTest.
    val pattern = s"*${RATE_LIMIT_ACTIVE_PREFIX}${consumerId}_*"
    Redis.deleteKeysByPattern(pattern)
  }

  /**
   * Invalidate ALL rate limit cache entries for ALL consumers.
   * Use with caution - this clears the entire rate limiting cache namespace.
   *
   * @return Number of cache keys deleted
   */
  def invalidateAllRateLimitCache(): Int = {
    val pattern = s"*${RATE_LIMIT_ACTIVE_PREFIX}*"
    Redis.deleteKeysByPattern(pattern)
  }

  
}
