package code.metrics

import code.api.Constant._
import code.api.JedisMethod
import code.api.cache.Redis
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

/**
 * Redis-based per-hour counters for connector outbound/inbound message tracking.
 *
 * Follows the same pattern as RateLimitingUtil.incrementConsumerCounters:
 * - Check TTL of key
 * - If key doesn't exist (TTL = -2): SET key 1 with TTL = 3600 seconds (1 hour)
 * - If key exists (TTL > 0): INCR key
 * - Counters automatically expire after 1 hour, giving a rolling per-hour window
 *
 * Gated by prop: write_connector_metrics_redis (default: false)
 */
object ConnectorCountsRedis extends MdcLoggable {

  private val PER_HOUR_TTL = 3600 // 1 hour in seconds

  def isEnabled: Boolean = APIUtil.getPropsAsBoolValue("write_connector_metrics_redis", false)

  private def outboundKey(connectorName: String, methodName: String): String =
    s"${CONNECTOR_OUTBOUND_PREFIX}${connectorName}_${methodName}_PER_HOUR"

  private def inboundKey(connectorName: String, methodName: String, success: Boolean): String = {
    val suffix = if (success) "_success" else "_failure"
    s"${CONNECTOR_INBOUND_PREFIX}${connectorName}_${methodName}${suffix}_PER_HOUR"
  }

  /**
   * Increment the per-hour outbound counter for a connector method call.
   * Called before invoking the connector method.
   */
  def incrementOutbound(connectorName: String, methodName: String): Unit = {
    if (!isEnabled) return
    try {
      incrementCounter(outboundKey(connectorName, methodName))
    } catch {
      case e: Throwable =>
        logger.warn(s"Failed to increment outbound counter for $connectorName.$methodName: ${e.getMessage}")
    }
  }

  /**
   * Increment the per-hour inbound counter for a connector method response.
   * Called after receiving the connector method result.
   */
  def incrementInbound(connectorName: String, methodName: String, success: Boolean): Unit = {
    if (!isEnabled) return
    try {
      incrementCounter(inboundKey(connectorName, methodName, success))
    } catch {
      case e: Throwable =>
        logger.warn(s"Failed to increment inbound counter for $connectorName.$methodName (success=$success): ${e.getMessage}")
    }
  }

  /**
   * Read the current hour's outbound counter value.
   */
  def getOutboundCount(connectorName: String, methodName: String): Long = {
    try {
      Redis.use(JedisMethod.GET, outboundKey(connectorName, methodName)).map(_.toLong).getOrElse(0L)
    } catch {
      case _: Throwable => 0L
    }
  }

  /**
   * Read the current hour's inbound counter value (success or failure).
   */
  def getInboundCount(connectorName: String, methodName: String, success: Boolean): Long = {
    try {
      Redis.use(JedisMethod.GET, inboundKey(connectorName, methodName, success)).map(_.toLong).getOrElse(0L)
    } catch {
      case _: Throwable => 0L
    }
  }

  /**
   * Scan all per-hour outbound/inbound keys for a summary view.
   * Returns a list of ConnectorCount entries with current counts and TTL.
   */
  def getAllCounts(): List[ConnectorCount] = {
    try {
      val outboundPrefix = CONNECTOR_OUTBOUND_PREFIX
      val inboundPrefix = CONNECTOR_INBOUND_PREFIX

      // Scan for outbound keys
      val outboundPattern = s"${outboundPrefix}*_PER_HOUR"
      val outboundKeys = Redis.scanKeys(outboundPattern)

      // Build a map of connectorName_methodName -> outbound count
      val outboundMap: Map[String, Long] = outboundKeys.flatMap { key =>
        extractConnectorMethod(key, outboundPrefix, "_PER_HOUR").map { cm =>
          val count = Redis.use(JedisMethod.GET, key).map(_.toLong).getOrElse(0L)
          cm -> count
        }
      }.toMap

      // Scan for inbound keys
      val inboundPattern = s"${inboundPrefix}*_PER_HOUR"
      val inboundKeys = Redis.scanKeys(inboundPattern)

      // Build maps for success and failure inbound counts
      val inboundSuccessMap = scala.collection.mutable.Map[String, Long]()
      val inboundFailureMap = scala.collection.mutable.Map[String, Long]()

      inboundKeys.foreach { key =>
        val count = Redis.use(JedisMethod.GET, key).map(_.toLong).getOrElse(0L)
        if (key.contains("_success_PER_HOUR")) {
          extractConnectorMethod(key, inboundPrefix, "_success_PER_HOUR").foreach { cm =>
            inboundSuccessMap(cm) = count
          }
        } else if (key.contains("_failure_PER_HOUR")) {
          extractConnectorMethod(key, inboundPrefix, "_failure_PER_HOUR").foreach { cm =>
            inboundFailureMap(cm) = count
          }
        }
      }

      // Combine all connector_method keys
      val allKeys = (outboundMap.keySet ++ inboundSuccessMap.keySet ++ inboundFailureMap.keySet).toList

      allKeys.flatMap { cm =>
        val parts = cm.split("_", 2)
        if (parts.length == 2) {
          val connectorName = parts(0)
          val methodName = parts(1)
          val ttl = Redis.use(JedisMethod.TTL, outboundKey(connectorName, methodName)).map(_.toLong).getOrElse(0L)
          Some(ConnectorCount(
            connector_name = connectorName,
            method_name = methodName,
            per_hour_outbound_count = outboundMap.getOrElse(cm, 0L),
            per_hour_inbound_success_count = inboundSuccessMap.getOrElse(cm, 0L),
            per_hour_inbound_failure_count = inboundFailureMap.getOrElse(cm, 0L),
            ttl_seconds = ttl
          ))
        } else None
      }
    } catch {
      case e: Throwable =>
        logger.warn(s"Failed to get all connector counts: ${e.getMessage}")
        List.empty
    }
  }

  /**
   * Core counter increment logic following the RateLimitingUtil pattern.
   */
  private def incrementCounter(key: String): Unit = {
    val ttlOpt = Redis.use(JedisMethod.TTL, key).map(_.toInt)
    ttlOpt match {
      case Some(-2) => // Key does not exist, create it
        Redis.use(JedisMethod.SET, key, Some(PER_HOUR_TTL), Some("1"))
      case Some(ttl) if ttl > 0 => // Key exists with TTL, increment it
        Redis.use(JedisMethod.INCR, key)
      case Some(ttl) if ttl <= 0 => // Expired or no expiry (shouldn't happen)
        Redis.use(JedisMethod.SET, key, Some(PER_HOUR_TTL), Some("1"))
      case None => // Redis unavailable
        logger.warn(s"Redis unavailable when incrementing connector counter: $key")
    }
  }

  /**
   * Extract connectorName_methodName from a Redis key by stripping prefix and suffix.
   */
  private def extractConnectorMethod(key: String, prefix: String, suffix: String): Option[String] = {
    if (key.startsWith(prefix) && key.endsWith(suffix)) {
      Some(key.stripPrefix(prefix).stripSuffix(suffix))
    } else {
      None
    }
  }
}

case class ConnectorCount(
  connector_name: String,
  method_name: String,
  per_hour_outbound_count: Long,
  per_hour_inbound_success_count: Long,
  per_hour_inbound_failure_count: Long,
  ttl_seconds: Long
)
