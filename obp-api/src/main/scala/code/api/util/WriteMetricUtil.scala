package code.api.util

import org.json4s._
import code.api.util.APIUtil.{buildOperationId, getCorrelationId, getPropsAsBoolValue, getPropsValue}
import code.metrics.APIMetrics
import code.metricsstream.MetricsEventBus
import code.util.Helper.MdcLoggable

import java.util.Date
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.control.NonFatal
import com.openbankproject.commons.ExecutionContext.Implicits.global
import org.json4s.{Extraction, JValue}
import com.openbankproject.commons.util.JsonAliases.compactRender
import org.json4s.native.Serialization.write

object WriteMetricUtil extends MdcLoggable {

  implicit val formats: org.json4s.Formats = CustomJsonFormats.formats

  private val operationIds: immutable.Seq[String] =
    getPropsValue("metrics_store_response_body_for_operation_ids")
      .toList.map(_.split(",")).flatten

  def writeMetricForOperationId(operationId: Option[String]): Boolean = {
    operationIds.contains(operationId.getOrElse("None"))
  }

  def writeEndpointMetric(responseBody: Any, callContext: Option[CallContextLight]): Unit = callContext match {
    case Some(cc) if code.metrics.MetricsProps.writeMetrics =>
      persistAndPublishMetric(responseBody, cc)
    case Some(_) =>
      // metrics disabled — nothing to do
    case None =>
      logger.error("CallContextLight is not defined. Metrics cannot be saved.")
  }

  private case class MetricFields(userId: String,
                                   userName: String,
                                   appName: String,
                                   developerEmail: String,
                                   consumerId: String,
                                   implementedByPartialFunction: String,
                                   duration: Long,
                                   responseBodyToWrite: String,
                                   sourceIp: String,
                                   targetIp: String,
                                   authType: String)

  private def persistAndPublishMetric(responseBody: Any, cc: CallContextLight): Unit = {
    val fields = MetricFields(
      userId = cc.userId.orNull,
      userName = cc.userName.orNull,
      appName = cc.appName.orNull,
      developerEmail = cc.developerEmail.orNull,
      consumerId = cc.consumerId.orNull,
      implementedByPartialFunction = cc.partialFunctionName,
      duration = callDuration(cc),
      responseBodyToWrite = responseBodyForMetric(responseBody, cc),
      sourceIp = requestHeaderValue(cc, "x-forwarded-for"),
      targetIp = requestHeaderValue(cc, "x-forwarded-host"),
      authType = deriveAuthType(cc)
    )

    // enqueue synchronously so flush() in tests reliably drains this metric before assertions
    saveMetricSafely(cc, fields)

    // gRPC publish is potentially blocking — keep it async
    Future {
      import fields._
      publishMetricEvent(userId, cc.url, cc.startTime.getOrElse(null), duration, userName, appName,
        developerEmail, consumerId, implementedByPartialFunction, cc.implementedInVersion, cc.verb,
        cc.httpCode, cc.correlationId, sourceIp, targetIp, cc.operationId.getOrElse(""),
        cc.consentReferenceId.orNull, cc.certificateTrust.orNull, cc.certificateTrustDetail.orNull)
    }
  }

  /**
   * Authentication SCHEME of the call — never the credential itself. "Consent" wins
   * outright: when a consent authenticated the call, the Authorization header (if any)
   * was not what authorized it. The rest is read off the Authorization header shape,
   * with the gateway payload / direct-login params as fallbacks for flows that
   * populate those without a header.
   */
  private[util] def deriveAuthType(cc: CallContextLight): String = {
    if (cc.consentReferenceId.isDefined) "Consent"
    else cc.authReqHeaderField.map(_.trim) match {
      case Some(h) if h.startsWith("DirectLogin") => "DirectLogin"
      case Some(h) if h.startsWith("Bearer") => "OAuth2"
      case Some(h) if h.startsWith("GatewayLogin") => "GatewayLogin"
      case Some(h) if h.startsWith("DAuth") => "DAuth"
      case Some(h) if h.startsWith("OAuth") => "OAuth1"
      case Some(_) => "Other"
      case None =>
        if (cc.gatewayLoginRequestPayload.isDefined) "GatewayLogin"
        else if (cc.directLoginToken != null && cc.directLoginToken.nonEmpty) "DirectLogin"
        else if (cc.userId.isDefined) "Other"
        else "Anonymous"
    }
  }

  private def callDuration(cc: CallContextLight): Long =
    (cc.startTime, cc.endTime) match {
      case (Some(s), Some(e)) => e.getTime - s.getTime
      case _ => -1
    }

  private def responseBodyForMetric(responseBody: Any, cc: CallContextLight): String =
    if (!writeMetricForOperationId(cc.operationId)) {
      "Not enabled"
    } else {
      Extraction.decompose(responseBody) match {
        case jValue: JValue => compactRender(jValue)
        case _ => responseBody.toString
      }
    }

  private def requestHeaderValue(cc: CallContextLight, headerName: String): String =
    cc.requestHeaders.find(_.name.toLowerCase() == headerName).map(_.values.mkString(",")).getOrElse("")

  private def saveMetricSafely(cc: CallContextLight, fields: MetricFields): Unit = {
    import fields._
    try {
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
        code.api.Constant.ApiInstanceId,
        cc.consentReferenceId.orNull,
        cc.certificateTrust.orNull,
        cc.certificateTrustDetail.orNull,
        authType
      )
    } catch {
      case NonFatal(e) =>
        logger.warn(s"WriteMetricUtil says: saveMetric failed: ${e.getMessage}")
    }
  }


  private val metricFormats = org.json4s.DefaultFormats

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
                                 targetIp: String,
                                 operationId: String,
                                 consentReferenceId: String,
                                 certificateTrust: String,
                                 certificateTrustDetail: String): Unit = {
    if (!MetricsEventBus.isEnabled) return
    try {
      implicit val fmts = metricFormats
      // Use Lift's date format (same one REST v6.0.0 uses when serializing
      // MetricJsonV600.date) so the stream string matches byte-for-byte.
      val dateStr = if (date != null) metricFormats.dateFormat.format(date) else ""
      val payload = write(Map(
        "url"                             -> Option(url).getOrElse(""),
        "date"                            -> dateStr,
        "duration"                        -> duration,
        "user_id"                         -> Option(userId).getOrElse(""),
        "username"                        -> Option(userName).getOrElse(""),
        "app_name"                        -> Option(appName).getOrElse(""),
        "developer_email"                 -> Option(developerEmail).getOrElse(""),
        "consumer_id"                     -> Option(consumerId).getOrElse(""),
        "implemented_by_partial_function" -> Option(implementedByPartialFunction).getOrElse(""),
        "implemented_in_version"          -> Option(implementedInVersion).getOrElse(""),
        "verb"                            -> Option(verb).getOrElse(""),
        "status_code"                     -> httpCode.getOrElse(0),
        "correlation_id"                  -> Option(correlationId).getOrElse(""),
        "source_ip"                       -> Option(sourceIp).getOrElse(""),
        "target_ip"                       -> Option(targetIp).getOrElse(""),
        "api_instance_id"                 -> code.api.Constant.ApiInstanceId,
        "operation_id"                    -> Option(operationId).getOrElse(""),
        "consent_reference_id"            -> Option(consentReferenceId).getOrElse(""),
        "certificate_trust"               -> Option(certificateTrust).getOrElse(""),
        "certificate_trust_detail"        -> Option(certificateTrustDetail).getOrElse("")
      ))
      MetricsEventBus.publish(payload)
    } catch {
      case e: Throwable =>
        logger.warn(s"WriteMetricUtil says: publishMetricEvent failed: ${e.getMessage}")
    }
  }

}
