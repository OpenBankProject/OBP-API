package code.util

import java.util.concurrent.{Callable, TimeUnit}

import com.google.common.cache.{ Cache => GuavaCache, CacheBuilder}

sealed trait Caching[K <: String, V <: AnyRef] {
  def get(k: K): Option[V]
  def set(k: K, v: V): Unit
  def getOrElseUpdate(k: K, f: () => V): V
}

class Cache[K <: String, V <: AnyRef](cache: GuavaCache[K,V]) extends Caching[K, V] {
  /**
    * Get an optional value from the cache
    *
    * @param k Cache key
    */
  def get(k: K): Option[V] = {
    val res = Option(cache.getIfPresent(k))
    res
  }

  /**
    * Get a value from the cache or call a function to set the cache value
    *
    * This can be used to make TTL caching easy to understand
    *
    * @param k key
    * @param f a function to call if the cache value is not present
    * @return
    */
  def getOrElseUpdate(k: K, f: () => V): V = {
    cache.get(k, new Callable[V] {
      def call(): V = f()
    })
  }

  /**
    * Insert an item into the cache
    *
    *
    * @param k key
    * @param v value
    */
  def set(k: K, v: V) {
    cache.put(k, v)
  }

  /**
    * Evicts an item from the cache
    *
    * @param k the key to evict
    */
  def remove(k: K) {
    cache.invalidate(k)
  }

  /**
    * Clear all items in the cache
    *
    */
  def clear() {
    cache.invalidateAll()
  }

  /**
    * Returns the size of the current cache
    *
    */
  def size: Long = {
    cache.size()
  }
}

object TTLCache {
  /**
    * Builds a TTL Cache store
    *
    * @param duration the TTL in seconds
    * @tparam V
    */
  def apply[V <: AnyRef](duration: Int) = {
    val ttlCache: GuavaCache[String, V] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(duration, TimeUnit.SECONDS)
        .build()
    new Cache(ttlCache)
  }
}

