package code.obp.grpc.logcache

import org.json4s._
import code.api.cache.RedisLogger
import code.api.util.APIUtil
import code.api.util.APIUtil.UserOnly
import code.logcache.LogCacheEventBus
import code.obp.grpc.chat.AuthInterceptor
import code.obp.grpc.logcache.api._
import code.util.Helper.MdcLoggable
import com.google.protobuf.timestamp.Timestamp
import io.grpc.Status
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JValue

/**
 * gRPC service implementation for log cache streaming.
 *
 * Auth: the shared `AuthInterceptor` validates the token at stream open and
 * puts the `User` in gRPC Context. Per-level entitlements are checked here
 * against the same `canGetSystemLogCache*` roles the REST endpoint uses.
 */
object LogCacheStreamServiceImpl extends LogCacheStreamServiceGrpc.LogCacheStreamService with MdcLoggable {

  private implicit val formats: org.json4s.DefaultFormats.type = json.DefaultFormats

  override def streamLogCacheEntries(
    request: StreamLogCacheRequest,
    responseObserver: StreamObserver[LogCacheEntry]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val internalLevel = levelToRedis(request.level) match {
      case Some(l) => l
      case None =>
        responseObserver.onError(Status.INVALID_ARGUMENT
          .withDescription(s"Unknown or unspecified log level: ${request.level}").asRuntimeException())
        return
    }

    val requiredRoles = RedisLogger.LogLevel.requiredRoles(internalLevel)
    val callContext = Option(AuthInterceptor.CALL_CONTEXT_KEY.get())
    val consumerId = APIUtil.getConsumerPrimaryKey(callContext)
    if (!APIUtil.handleAccessControlWithAuthMode("", user.userId, consumerId, requiredRoles, UserOnly)) {
      responseObserver.onError(Status.PERMISSION_DENIED
        .withDescription(s"Missing entitlement for log level $internalLevel").asRuntimeException())
      return
    }

    logger.info(s"LogCacheStreamServiceImpl says: User ${user.userId} subscribed to $internalLevel log cache stream")

    val bridge = new StreamObserver[String] {
      override def onNext(jsonPayload: String): Unit = {
        try {
          val jv = json.parse(jsonPayload)
          responseObserver.onNext(jsonToLogCacheEntry(jv))
        } catch {
          case e: Throwable =>
            logger.warn(s"LogCacheStreamServiceImpl says: Failed to parse log cache entry: ${e.getMessage}")
        }
      }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    LogCacheEventBus.subscribe(internalLevel, bridge)

    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          LogCacheEventBus.unsubscribe(internalLevel, bridge)
          logger.info(s"LogCacheStreamServiceImpl says: User ${user.userId} unsubscribed from $internalLevel log cache stream")
        })
      case _ =>
    }
  }

  // The Redis mapping used to live on a hand-written Int-constant LogLevel object;
  // LogLevel is scalapb-generated from log_cache.proto now (same wire numbers), so
  // the mapping lives here. LOG_LEVEL_UNSPECIFIED and Unrecognized map to None.
  private def levelToRedis(level: LogLevel): Option[RedisLogger.LogLevel.LogLevel] = level match {
    case LogLevel.TRACE   => Some(RedisLogger.LogLevel.TRACE)
    case LogLevel.DEBUG   => Some(RedisLogger.LogLevel.DEBUG)
    case LogLevel.INFO    => Some(RedisLogger.LogLevel.INFO)
    case LogLevel.WARNING => Some(RedisLogger.LogLevel.WARNING)
    case LogLevel.ERROR   => Some(RedisLogger.LogLevel.ERROR)
    case LogLevel.ALL     => Some(RedisLogger.LogLevel.ALL)
    case _                => None
  }

  private def levelFromRedis(level: RedisLogger.LogLevel.LogLevel): LogLevel = level match {
    case RedisLogger.LogLevel.TRACE   => LogLevel.TRACE
    case RedisLogger.LogLevel.DEBUG   => LogLevel.DEBUG
    case RedisLogger.LogLevel.INFO    => LogLevel.INFO
    case RedisLogger.LogLevel.WARNING => LogLevel.WARNING
    case RedisLogger.LogLevel.ERROR   => LogLevel.ERROR
    case RedisLogger.LogLevel.ALL     => LogLevel.ALL
  }

  private def jsonToLogCacheEntry(jv: JValue): LogCacheEntry = {
    val levelStr = (jv \ "level").extractOrElse[String]("")
    val levelInt = try {
      levelFromRedis(RedisLogger.LogLevel.valueOf(levelStr))
    } catch { case _: Throwable => LogLevel.LOG_LEVEL_UNSPECIFIED }
    val ts = (jv \ "ts").extractOrElse[Long](0L)
    val timestamp =
      if (ts > 0) Some(Timestamp(seconds = ts / 1000L, nanos = ((ts % 1000L) * 1000000L).toInt))
      else None
    LogCacheEntry(
      level = levelInt,
      message = (jv \ "message").extractOrElse[String](""),
      timestamp = timestamp,
      apiInstanceId = (jv \ "api_instance_id").extractOrElse[String]("")
    )
  }
}
