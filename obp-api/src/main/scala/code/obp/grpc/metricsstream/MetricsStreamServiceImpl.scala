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

package code.obp.grpc.metricsstream

import org.json4s._
import code.api.util.APIUtil
import code.api.util.APIUtil.UserOnly
import code.api.util.ApiRole.canReadMetrics
import code.metricsstream.MetricsEventBus
import code.obp.grpc.chat.AuthInterceptor
import code.obp.grpc.metricsstream.api._
import code.util.Helper.MdcLoggable
import io.grpc.Status
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JValue

/**
 * gRPC service implementation for metrics streaming.
 *
 * Auth: the shared `AuthInterceptor` validates the token at stream open and
 * puts the `User` in gRPC Context. The user must hold `canReadMetrics`
 * (same role the REST `/management/metrics` endpoint requires).
 *
 * Filters: applied server-side in the bridge observer — we still broadcast
 * via a single Redis channel, but only call `onNext` on matching events,
 * so slow filters don't waste gRPC wire bandwidth on unwanted events.
 */
object MetricsStreamServiceImpl extends MetricsStreamServiceGrpc.MetricsStreamService with MdcLoggable {

  private implicit val formats = json.DefaultFormats

  override def streamMetrics(
    request: StreamMetricsRequest,
    responseObserver: StreamObserver[MetricEvent]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val callContext = Option(AuthInterceptor.CALL_CONTEXT_KEY.get())
    val consumerId = APIUtil.getConsumerPrimaryKey(callContext)
    if (!APIUtil.handleAccessControlWithAuthMode("", user.userId, consumerId, List(canReadMetrics), UserOnly)) {
      responseObserver.onError(Status.PERMISSION_DENIED
        .withDescription("Missing entitlement canReadMetrics").asRuntimeException())
      return
    }

    logger.info(s"MetricsStreamServiceImpl says: User ${user.userId} subscribed to metrics stream (filters: $request)")

    val bridge = new StreamObserver[String] {
      override def onNext(jsonPayload: String): Unit = {
        try {
          val jv = json.parse(jsonPayload)
          if (matchesFilters(jv, request)) {
            responseObserver.onNext(jsonToMetricEvent(jv))
          }
        } catch {
          case e: Throwable =>
            logger.warn(s"MetricsStreamServiceImpl says: Failed to parse metric event: ${e.getMessage}")
        }
      }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    MetricsEventBus.subscribe(bridge)

    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          MetricsEventBus.unsubscribe(bridge)
          logger.info(s"MetricsStreamServiceImpl says: User ${user.userId} unsubscribed from metrics stream")
        })
      case _ =>
    }
  }

  /**
   * All filters AND together. Empty filter field = no restriction.
   * url_substring does a simple `contains`; everything else is an exact
   * match.
   */
  private def matchesFilters(jv: JValue, req: StreamMetricsRequest): Boolean = {
    def matchExact(filter: String, actual: String): Boolean =
      filter.isEmpty || filter == actual
    def matchSubstring(filter: String, actual: String): Boolean =
      filter.isEmpty || actual.contains(filter)

    matchExact(req.consumerId, (jv \ "consumer_id").extractOrElse[String]("")) &&
    matchExact(req.userId, (jv \ "user_id").extractOrElse[String]("")) &&
    matchExact(req.verb, (jv \ "verb").extractOrElse[String]("")) &&
    matchSubstring(req.urlSubstring, (jv \ "url").extractOrElse[String]("")) &&
    matchExact(req.implementedByPartialFunction, (jv \ "implemented_by_partial_function").extractOrElse[String]("")) &&
    matchExact(req.appName, (jv \ "app_name").extractOrElse[String]("")) &&
    matchExact(req.consentReferenceId, (jv \ "consent_reference_id").extractOrElse[String](""))
  }

  private def jsonToMetricEvent(jv: JValue): MetricEvent = {
    MetricEvent(
      url                          = (jv \ "url").extractOrElse[String](""),
      date                         = (jv \ "date").extractOrElse[String](""),
      duration                     = (jv \ "duration").extractOrElse[Long](0L),
      userId                       = (jv \ "user_id").extractOrElse[String](""),
      username                     = (jv \ "username").extractOrElse[String](""),
      appName                      = (jv \ "app_name").extractOrElse[String](""),
      developerEmail               = (jv \ "developer_email").extractOrElse[String](""),
      consumerId                   = (jv \ "consumer_id").extractOrElse[String](""),
      implementedByPartialFunction = (jv \ "implemented_by_partial_function").extractOrElse[String](""),
      implementedInVersion         = (jv \ "implemented_in_version").extractOrElse[String](""),
      verb                         = (jv \ "verb").extractOrElse[String](""),
      statusCode                   = (jv \ "status_code").extractOrElse[Int](0),
      correlationId                = (jv \ "correlation_id").extractOrElse[String](""),
      sourceIp                     = (jv \ "source_ip").extractOrElse[String](""),
      targetIp                     = (jv \ "target_ip").extractOrElse[String](""),
      apiInstanceId                = (jv \ "api_instance_id").extractOrElse[String](""),
      operationId                  = (jv \ "operation_id").extractOrElse[String](""),
      consentReferenceId           = (jv \ "consent_reference_id").extractOrElse[String]("")
    )
  }
}
