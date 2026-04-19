package code.api.util

import code.api.DirectLogin
import code.api.util.APIUtil.{ResourceDoc, buildOperationId, getCorrelationId, getPropsAsBoolValue, getPropsValue, hasAnOAuthHeader, hasDirectLoginHeader}
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.metrics.APIMetrics
import code.metricsstream.MetricsEventBus
import code.model.Consumer
import code.util.Helper.{MdcLoggable, ObpS}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.S
import net.liftweb.util.TimeHelpers.TimeSpan

import java.util.Date
import scala.collection.immutable
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.json.{Extraction, JValue, compactRender}
import net.liftweb.json.Serialization.write

object WriteMetricUtil extends MdcLoggable {

  implicit val formats = CustomJsonFormats.formats

  private val operationIds: immutable.Seq[String] =
    getPropsValue("metrics_store_response_body_for_operation_ids")
      .toList.map(_.split(",")).flatten

  def writeMetricForOperationId(operationId: Option[String]): Boolean = {
    operationIds.contains(operationId.getOrElse("None"))
  }

  def writeEndpointMetric(responseBody: Any, callContext: Option[CallContextLight]) = {
    callContext match {
      case Some(cc) =>
        if (getPropsAsBoolValue("write_metrics", false)) {
          val userId = cc.userId.orNull
          val userName = cc.userName.orNull

          val implementedByPartialFunction = cc.partialFunctionName

          val duration =
            (cc.startTime, cc.endTime) match {
              case (Some(s), Some(e)) => (e.getTime - s.getTime)
              case _ => -1
            }

          val responseBodyToWrite: String =
            if (writeMetricForOperationId(cc.operationId)) {
              Extraction.decompose(responseBody) match {
                case jValue: JValue =>
                  compactRender(jValue)
                case _ =>
                  responseBody.toString
              }
            } else {
              "Not enabled"
            }

          //execute saveMetric in future, as we do not need to know result of the operation
          Future {
            val consumerId = cc.consumerId.orNull
            val appName = cc.appName.orNull
            val developerEmail = cc.developerEmail.orNull

            val sourceIp = cc.requestHeaders.find(_.name.toLowerCase() == "x-forwarded-for").map(_.values.mkString(",")).getOrElse("")
            val targetIp = cc.requestHeaders.find(_.name.toLowerCase() == "x-forwarded-host").map(_.values.mkString(",")).getOrElse("")
            APIMetrics.apiMetrics.vend.saveMetric(
              userId,
              cc.url,
              cc.startTime.getOrElse(null),
              duration,
              userName,
              appName,
              developerEmail,
              consumerId,
              implementedByPartialFunction,
              cc.implementedInVersion,
              cc.verb,
              cc.httpCode,
              cc.correlationId,
              responseBodyToWrite,
              sourceIp,
              targetIp,
              code.api.Constant.ApiInstanceId
            )
            publishMetricEvent(userId, cc.url, cc.startTime.getOrElse(null), duration, userName, appName,
              developerEmail, consumerId, implementedByPartialFunction, cc.implementedInVersion, cc.verb,
              cc.httpCode, cc.correlationId, sourceIp, targetIp)
          }
        }
      case _ =>
        logger.error("CallContextLight is not defined. Metrics cannot be saved.")
    }
  }

  def writeEndpointMetric(date: TimeSpan, duration: Long, rd: Option[ResourceDoc]) = {
    val authorization = S.request.map(_.header("Authorization")).flatten
    val directLogin: Box[String] = S.request.map(_.header("DirectLogin")).flatten
    if (getPropsAsBoolValue("write_metrics", false)) {
      val user =
        if (getPropsAsBoolValue("allow_direct_login", true) && directLogin.isDefined) {
          DirectLogin.getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } // Direct Login Deprecated
        else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(authorization)) {
          DirectLogin.getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else {
          Empty
        }

      val consumer =
        if (getPropsAsBoolValue("allow_direct_login", true) && directLogin.isDefined) {
          DirectLogin.getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } // Direct Login Deprecated
        else if (getPropsAsBoolValue("allow_direct_login", true) && hasDirectLoginHeader(authorization)) {
          DirectLogin.getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } else {
          Empty
        }

      // TODO This should use Elastic Search not an RDBMS
      val u: User = user.orNull
      val userId = if (u != null) u.userId else "null"
      val userName = if (u != null) u.name else "null"

      val c: Consumer = consumer.orNull
      //The consumerId, not key
      val consumerId = if (c != null) c.consumerId.get else "null"
      var appName = if (u != null) c.name.toString() else "null"
      var developerEmail = if (u != null) c.developerEmail.toString() else "null"
      val implementedByPartialFunction = rd match {
        case Some(r) => r.partialFunctionName
        case _ => ""
      }
      //name of version where the call is implemented) -- S.request.get.view
      val implementedInVersion = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).view
      //(GET, POST etc.) --S.request.get.requestType.method
      val verb = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).requestType.method
      val url = ObpS.uriAndQueryString.getOrElse("")
      val correlationId = getCorrelationId()
      val reqHeaders = S.request.openOrThrowException(attemptedToOpenAnEmptyBox).request.headers

      val sourceIp = reqHeaders.find(_.name.toLowerCase() == "x-forwarded-for").map(_.values.mkString(",")).getOrElse("")
      val targetIp = reqHeaders.find(_.name.toLowerCase() == "x-forwarded-host").map(_.values.mkString(",")).getOrElse("")

      //execute saveMetric in future, as we do not need to know result of operation
      Future {
        APIMetrics.apiMetrics.vend.saveMetric(
          userId,
          url,
          date,
          duration: Long,
          userName,
          appName,
          developerEmail,
          consumerId,
          implementedByPartialFunction,
          implementedInVersion,
          verb,
          None,
          correlationId,
          "Not enabled for old style endpoints",
          sourceIp,
          targetIp,
          code.api.Constant.ApiInstanceId
        )
        publishMetricEvent(userId, url, date, duration, userName, appName, developerEmail, consumerId,
          implementedByPartialFunction, implementedInVersion, verb, None, correlationId, sourceIp, targetIp)
      }

    }
  }

  private val metricFormats = net.liftweb.json.DefaultFormats

  /**
   * Publish a metric event to the gRPC pub/sub channel. No-op when the
   * stream service is disabled. Safe to call from the metric-write Future
   * — exceptions are swallowed so the REST path is never affected.
   */
  private def publishMetricEvent(userId: String,
                                 url: String,
                                 date: Date,
                                 duration: Long,
                                 userName: String,
                                 appName: String,
                                 developerEmail: String,
                                 consumerId: String,
                                 implementedByPartialFunction: String,
                                 implementedInVersion: String,
                                 verb: String,
                                 httpCode: Option[Int],
                                 correlationId: String,
                                 sourceIp: String,
                                 targetIp: String): Unit = {
    if (!MetricsEventBus.isEnabled) return
    try {
      implicit val fmts = metricFormats
      val dateMs: Long = if (date != null) date.getTime else 0L
      val payload = write(Map(
        "url"                             -> Option(url).getOrElse(""),
        "date_ms"                         -> dateMs,
        "duration_ms"                     -> duration,
        "user_id"                         -> Option(userId).getOrElse(""),
        "user_name"                       -> Option(userName).getOrElse(""),
        "app_name"                        -> Option(appName).getOrElse(""),
        "developer_email"                 -> Option(developerEmail).getOrElse(""),
        "consumer_id"                     -> Option(consumerId).getOrElse(""),
        "implemented_by_partial_function" -> Option(implementedByPartialFunction).getOrElse(""),
        "implemented_in_version"          -> Option(implementedInVersion).getOrElse(""),
        "verb"                            -> Option(verb).getOrElse(""),
        "http_code"                       -> httpCode.getOrElse(0),
        "correlation_id"                  -> Option(correlationId).getOrElse(""),
        "source_ip"                       -> Option(sourceIp).getOrElse(""),
        "target_ip"                       -> Option(targetIp).getOrElse(""),
        "api_instance_id"                 -> code.api.Constant.ApiInstanceId
      ))
      MetricsEventBus.publish(payload)
    } catch {
      case e: Throwable =>
        logger.warn(s"WriteMetricUtil says: publishMetricEvent failed: ${e.getMessage}")
    }
  }

}
