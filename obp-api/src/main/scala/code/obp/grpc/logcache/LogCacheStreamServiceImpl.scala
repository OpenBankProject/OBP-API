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

  private implicit val formats = json.DefaultFormats

  override def streamLogCacheEntries(
    request: StreamLogCacheRequest,
    responseObserver: StreamObserver[LogCacheEntry]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val internalLevel = LogLevel.toRedis(request.level) match {
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

  private def jsonToLogCacheEntry(jv: JValue): LogCacheEntry = {
    val levelStr = (jv \ "level").extractOrElse[String]("")
    val levelInt = try {
      LogLevel.fromRedis(RedisLogger.LogLevel.valueOf(levelStr))
    } catch { case _: Throwable => LogLevel.UNSPECIFIED }
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
