package code.obp.grpc.logcache.api

import code.api.cache.RedisLogger

/**
 * Constants matching the proto LogLevel enum. The wire field is an int32
 * varint; these are the values clients will see.
 *
 * See `log_cache.proto` for the canonical definition. Kept in a separate
 * file rather than a scalapb-generated enum class to minimise hand-written
 * boilerplate.
 */
object LogLevel {
  val UNSPECIFIED: Int = 0
  val TRACE: Int = 1
  val DEBUG: Int = 2
  val INFO: Int = 3
  val WARNING: Int = 4
  val ERROR: Int = 5
  val ALL: Int = 6

  def fromRedis(level: RedisLogger.LogLevel.LogLevel): Int = level match {
    case RedisLogger.LogLevel.TRACE   => TRACE
    case RedisLogger.LogLevel.DEBUG   => DEBUG
    case RedisLogger.LogLevel.INFO    => INFO
    case RedisLogger.LogLevel.WARNING => WARNING
    case RedisLogger.LogLevel.ERROR   => ERROR
    case RedisLogger.LogLevel.ALL     => ALL
  }

  def toRedis(level: Int): Option[RedisLogger.LogLevel.LogLevel] = level match {
    case TRACE   => Some(RedisLogger.LogLevel.TRACE)
    case DEBUG   => Some(RedisLogger.LogLevel.DEBUG)
    case INFO    => Some(RedisLogger.LogLevel.INFO)
    case WARNING => Some(RedisLogger.LogLevel.WARNING)
    case ERROR   => Some(RedisLogger.LogLevel.ERROR)
    case ALL     => Some(RedisLogger.LogLevel.ALL)
    case _       => None
  }
}
