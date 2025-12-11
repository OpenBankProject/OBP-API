package code.api.util

import code.api.DirectLogin
import code.api.OAuthHandshake.{getConsumer, getUser}
import code.api.util.APIUtil.{ResourceDoc, buildOperationId, getCorrelationId, getPropsAsBoolValue, getPropsValue, hasAnOAuthHeader, hasDirectLoginHeader}
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.metrics.APIMetrics
import code.model.Consumer
import code.util.Helper.{MdcLoggable, ObpS}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.S
import net.liftweb.util.TimeHelpers.TimeSpan

import scala.collection.immutable
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.json.{Extraction, JValue, compactRender}

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
              cc.requestHeaders.find(_.name.toLowerCase() == "x-forwarded-for").map(_.values.mkString(",")).getOrElse(""),
              cc.requestHeaders.find(_.name.toLowerCase() == "x-forwarded-host").map(_.values.mkString(",")).getOrElse("")
            )
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
        if (hasAnOAuthHeader(authorization)) {
          getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } // Direct Login
        else if (getPropsAsBoolValue("allow_direct_login", true) && directLogin.isDefined) {
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
        if (hasAnOAuthHeader(authorization)) {
          getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } // Direct Login
        else if (getPropsAsBoolValue("allow_direct_login", true) && directLogin.isDefined) {
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
          reqHeaders.find(_.name.toLowerCase() == "x-forwarded-for").map(_.values.mkString(",")).getOrElse(""),
          reqHeaders.find(_.name.toLowerCase() == "x-forwarded-host").map(_.values.mkString(",")).getOrElse("")
        )
      }

    }
  }

}
