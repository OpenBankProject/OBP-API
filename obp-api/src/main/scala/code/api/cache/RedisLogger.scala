package code.api.cache

import code.api.util.ApiRole._
import code.api.util.{APIUtil, ApiRole}

import net.liftweb.common.{Box, Empty, Failure => LiftFailure, Full, Logger}
import redis.clients.jedis.{Jedis, Pipeline}

import java.util.concurrent.{Executors, ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Redis queue configuration per log level.
 */
case class RedisLogConfig(
                           queueName: String,
                           maxEntries: Int
                         )

/**
 * Simple Redis FIFO log writer.
 */
object RedisLogger {

  private val logger = Logger(RedisLogger.getClass)

  // Performance and reliability improvements
  private val redisLoggingEnabled = APIUtil.getPropsAsBoolValue("redis_logging_enabled", false)
  private val batchSize = APIUtil.getPropsAsIntValue("redis_logging_batch_size", 100)
  private val flushIntervalMs = APIUtil.getPropsAsIntValue("redis_logging_flush_interval_ms", 1000)
  private val maxRetries = APIUtil.getPropsAsIntValue("redis_logging_max_retries", 3)
  private val circuitBreakerThreshold = APIUtil.getPropsAsIntValue("redis_logging_circuit_breaker_threshold", 10)

  // Circuit breaker state
  private val consecutiveFailures = new AtomicLong(0)
  private val circuitBreakerOpen = new AtomicBoolean(false)
  private var lastFailureTime = 0L

  // Async executor for Redis operations
  private val redisExecutor: ScheduledThreadPoolExecutor = Executors.newScheduledThreadPool(
    APIUtil.getPropsAsIntValue("redis_logging_thread_pool_size", 2)
  ).asInstanceOf[ScheduledThreadPoolExecutor]
  private implicit val redisExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(redisExecutor)

  // Batch logging support
  private val logBuffer = new java.util.concurrent.ConcurrentLinkedQueue[LogEntry]()

  case class LogEntry(level: LogLevel.LogLevel, message: String, timestamp: Long = System.currentTimeMillis())

  // Start background flusher
  startBackgroundFlusher()

  /**
   * Redis-backed logging utilities for OBP.
   */
  object LogLevel extends Enumeration {
    type LogLevel = Value
    val TRACE, DEBUG, INFO, WARNING, ERROR, ALL = Value

    /** Parse a string into LogLevel, throw if unknown */
    def valueOf(str: String): LogLevel = str.toUpperCase match {
      case "TRACE"   => TRACE
      case "DEBUG"   => DEBUG
      case "INFO"    => INFO
      case "WARN" | "WARNING" => WARNING
      case "ERROR"   => ERROR
      case "ALL"     => ALL
      case other     => throw new IllegalArgumentException(s"Invalid log level: $other")
    }

    /** Map a LogLevel to its required entitlements */
    def requiredRoles(level: LogLevel): List[ApiRole] = level match {
      case TRACE   => List(canGetTraceLevelLogsAtAllBanks, canGetAllLevelLogsAtAllBanks)
      case DEBUG   => List(canGetDebugLevelLogsAtAllBanks, canGetAllLevelLogsAtAllBanks)
      case INFO    => List(canGetInfoLevelLogsAtAllBanks, canGetAllLevelLogsAtAllBanks)
      case WARNING => List(canGetWarningLevelLogsAtAllBanks, canGetAllLevelLogsAtAllBanks)
      case ERROR   => List(canGetErrorLevelLogsAtAllBanks, canGetAllLevelLogsAtAllBanks)
      case ALL     => List(canGetAllLevelLogsAtAllBanks)
    }
  }



  // Define FIFO queues, sizes configurable via props
    val configs: Map[LogLevel.Value, RedisLogConfig] = Map(
      LogLevel.TRACE   -> RedisLogConfig("obp_trace_logs",   APIUtil.getPropsAsIntValue("redis_logging_trace_queue_max_entries",   1000)),
      LogLevel.DEBUG   -> RedisLogConfig("obp_debug_logs",   APIUtil.getPropsAsIntValue("redis_logging_debug_queue_max_entries",   1000)),
      LogLevel.INFO    -> RedisLogConfig("obp_info_logs",    APIUtil.getPropsAsIntValue("redis_logging_info_queue_max_entries",    1000)),
      LogLevel.WARNING -> RedisLogConfig("obp_warning_logs", APIUtil.getPropsAsIntValue("redis_logging_warning_queue_max_entries", 1000)),
      LogLevel.ERROR   -> RedisLogConfig("obp_error_logs",   APIUtil.getPropsAsIntValue("redis_logging_error_queue_max_entries",   1000)),
      LogLevel.ALL     -> RedisLogConfig("obp_all_logs",     APIUtil.getPropsAsIntValue("redis_logging_all_queue_max_entries",     1000))
    )

  /**
   * Synchronous log (blocking until Redis writes are done).
   */
  def logSync(level: LogLevel.LogLevel, message: String): Try[Unit] = {
    if (!redisLoggingEnabled || circuitBreakerOpen.get()) {
      return Success(()) // Skip if disabled or circuit breaker is open
    }

    var attempt = 0
    var lastException: Throwable = null

    while (attempt < maxRetries) {
      try {
        withPipeline { pipeline =>
          // log to requested level
          configs.get(level).foreach(cfg => pushLog(pipeline, cfg, message))
          // also log to ALL
          configs.get(LogLevel.ALL).foreach(cfg => pushLog(pipeline, cfg, s"[$level] $message"))
          pipeline.sync()
        }

        // Reset circuit breaker on success
        consecutiveFailures.set(0)
        circuitBreakerOpen.set(false)
        return Success(())

      } catch {
        case e: Exception =>
          lastException = e
          attempt += 1

          if (attempt < maxRetries) {
            Thread.sleep(100 * attempt) // Exponential backoff
          }
      }
    }

    // Handle circuit breaker
    val failures = consecutiveFailures.incrementAndGet()
    if (failures >= circuitBreakerThreshold) {
      circuitBreakerOpen.set(true)
      lastFailureTime = System.currentTimeMillis()
      logger.warn(s"Redis logging circuit breaker opened after $failures consecutive failures")
    }

    Failure(lastException)
  }

  /**
   * Asynchronous log with batching support (fire-and-forget).
   * Returns a Future[Unit], failures are handled gracefully.
   */
  def logAsync(level: LogLevel.LogLevel, message: String): Future[Unit] = {
    if (!redisLoggingEnabled) {
      return Future.successful(())
    }

    // Add to batch buffer for better performance
    logBuffer.offer(LogEntry(level, message))

    // If buffer is full, flush immediately
    if (logBuffer.size() >= batchSize) {
      Future {
        flushLogBuffer()
      }(redisExecutionContext).recover {
        case e => logger.debug(s"RedisLogger batch flush failed: ${e.getMessage}")
      }
    } else {
      Future.successful(())
    }
  }

  /**
   * Immediate async log without batching for critical messages.
   */
  def logAsyncImmediate(level: LogLevel.LogLevel, message: String): Future[Unit] = {
    Future {
      logSync(level, message) match {
        case Success(_) => // ok
        case Failure(e) => logger.debug(s"RedisLogger immediate async failed: ${e.getMessage}")
      }
    }(redisExecutionContext)
  }

  private def withPipeline(block: Pipeline => Unit): Unit = {
    Option(Redis.jedisPool).foreach { pool =>
      val jedis = pool.getResource()
      try {
        val pipeline: Pipeline = jedis.pipelined()
        block(pipeline)
      } catch {
        case e: Exception =>
          logger.debug(s"Redis pipeline operation failed: ${e.getMessage}")
          throw e
      } finally {
        if (jedis != null) {
          jedis.close()
        }
      }
    }
  }

  private def flushLogBuffer(): Unit = {
    if (logBuffer.isEmpty || circuitBreakerOpen.get()) {
      return
    }

    val entriesToFlush = new java.util.ArrayList[LogEntry]()
    var entry = logBuffer.poll()
    while (entry != null && entriesToFlush.size() < batchSize) {
      entriesToFlush.add(entry)
      entry = logBuffer.poll()
    }

    if (!entriesToFlush.isEmpty) {
      try {
        withPipeline { pipeline =>
          entriesToFlush.asScala.foreach { logEntry =>
            configs.get(logEntry.level).foreach(cfg => pushLog(pipeline, cfg, logEntry.message))
            configs.get(LogLevel.ALL).foreach(cfg => pushLog(pipeline, cfg, s"[${logEntry.level}] ${logEntry.message}"))
          }
          pipeline.sync()
        }

        // Reset circuit breaker on success
        consecutiveFailures.set(0)
        circuitBreakerOpen.set(false)

      } catch {
        case e: Exception =>
          val failures = consecutiveFailures.incrementAndGet()
          if (failures >= circuitBreakerThreshold) {
            circuitBreakerOpen.set(true)
            lastFailureTime = System.currentTimeMillis()
            logger.warn(s"Redis logging circuit breaker opened after batch flush failure")
          }
          logger.debug(s"Redis batch flush failed: ${e.getMessage}")
      }
    }
  }

  private def startBackgroundFlusher(): Unit = {
    val flusher = new Runnable {
      override def run(): Unit = {
        try {
          // Check if circuit breaker should be reset (after 60 seconds)
          if (circuitBreakerOpen.get() && System.currentTimeMillis() - lastFailureTime > 60000) {
            circuitBreakerOpen.set(false)
            consecutiveFailures.set(0)
            logger.info("Redis logging circuit breaker reset")
          }

          flushLogBuffer()
        } catch {
          case e: Exception =>
            logger.debug(s"Background log flusher failed: ${e.getMessage}")
        }
      }
    }

    redisExecutor.scheduleAtFixedRate(
      flusher,
      flushIntervalMs,
      flushIntervalMs,
      TimeUnit.MILLISECONDS
    )
  }

  private def pushLog(pipeline: Pipeline, cfg: RedisLogConfig, msg: String): Unit = {
    if (cfg.maxEntries > 0) {
      pipeline.lpush(cfg.queueName, msg)
      pipeline.ltrim(cfg.queueName, 0, cfg.maxEntries - 1)
    }
  }

  case class LogTailEntry(level: String, message: String)
  case class LogTail(entries: List[LogTailEntry])

  private val LogPattern = """\[(\w+)\]\s+(.*)""".r

  /**
   * Read latest messages from Redis FIFO queue.
   */
  def getLogTail(level: LogLevel.LogLevel): LogTail = {
    Option(Redis.jedisPool).map { pool =>
      val jedis = pool.getResource()
      try {
        val cfg = configs(level)
        val rawLogs = jedis.lrange(cfg.queueName, 0, -1).asScala.toList

        val entries: List[LogTailEntry] = level match {
          case LogLevel.ALL =>
            rawLogs.collect {
              case LogPattern(lvl, msg) => LogTailEntry(lvl, msg)
            }
          case other =>
            rawLogs.map(msg => LogTailEntry(other.toString, msg))
        }

        LogTail(entries)
      } finally {
        jedis.close()
      }
    }.getOrElse(LogTail(Nil))
  }

  /**
   * Get Redis logging statistics
   */
  def getStats: Map[String, Any] = Map(
    "redisLoggingEnabled" -> redisLoggingEnabled,
    "circuitBreakerOpen" -> circuitBreakerOpen.get(),
    "consecutiveFailures" -> consecutiveFailures.get(),
    "bufferSize" -> logBuffer.size(),
    "batchSize" -> batchSize,
    "flushIntervalMs" -> flushIntervalMs,
    "threadPoolActiveCount" -> redisExecutor.getActiveCount,
    "threadPoolQueueSize" -> redisExecutor.getQueue.size()
  )

  /**
   * Shutdown the Redis logger gracefully
   */
  def shutdown(): Unit = {
    logger.info("Shutting down Redis logger...")

    // Flush remaining logs
    flushLogBuffer()

    // Shutdown executor
    redisExecutor.shutdown()
    try {
      if (!redisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        redisExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        redisExecutor.shutdownNow()
    }

    logger.info("Redis logger shutdown complete")
  }
}
