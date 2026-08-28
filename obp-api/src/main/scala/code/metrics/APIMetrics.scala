package code.metrics

import java.util.{Calendar, Date}
import code.api.util.{APIUtil, CallContext, OBPQueryParam}
import code.api.util.APIUtil.{HTTPParam, createQueriesByHttpParamsFuture}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object APIMetrics extends SimpleInjector {

  val apiMetrics = new Inject(() => buildOne) {}

  def buildOne: APIMetrics =
    APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) &&
      APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) match {
        case false => MappedMetrics
        case true => ElasticsearchMetrics
    }

  /**
   * Returns a Date which is at the start of the day of the date
   * of the metric. Useful for implementing getAllGroupedByDay
   * @param metric
   * @return
   */
  def getMetricDay(metric : APIMetric) : Date = {
    val cal = Calendar.getInstance()
    cal.setTime(metric.getDate())
    cal.set(Calendar.HOUR_OF_DAY,0)
    cal.set(Calendar.MINUTE,0)
    cal.set(Calendar.SECOND,0)
    cal.set(Calendar.MILLISECOND,0)
    cal.getTime
  }

  // Inject default from_date so metrics queries don't hit all rows since epoch.
  // Shared by every endpoint that reads metrics (GET /management/metrics,
  // GET /management/aggregate-metrics, GET /my/metrics, ...).
  def applyMetricsFromDateDefault(httpParams: List[HTTPParam]): List[HTTPParam] = {
    val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
    if (hasFromDate) httpParams
    else {
      val stableBoundary = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
      val defaultFromDate = new Date(System.currentTimeMillis() - ((stableBoundary - 1) * 1000L))
      HTTPParam("from_date", List(APIUtil.DateWithMsFormat.format(defaultFromDate))) :: httpParams
    }
  }

  // One shared fetch path for metrics-reading endpoints: builds OBPQueryParams
  // from the http params (with the from_date default applied) and runs the query.
  // lockedUserId pins the user filter server-side (for self-service endpoints
  // like GET /my/metrics); when set it overrides anything in httpParams.
  def getMetricsFromHttpParams(
      httpParams: List[HTTPParam],
      callContext: Option[CallContext],
      lockedUserId: Option[String] = None
  ): Future[(List[APIMetric], Option[CallContext])] = {
    val effectiveParams = lockedUserId match {
      case Some(userId) =>
        httpParams.filterNot(_.name == "user_id") :+ HTTPParam("user_id", List(userId))
      case None => httpParams
    }
    for {
      (obpQueryParams, cc) <- createQueriesByHttpParamsFuture(
        applyMetricsFromDateDefault(effectiveParams), callContext)
      metrics <- Future(apiMetrics.vend.getAllMetrics(obpQueryParams))
    } yield (metrics, cc)
  }

}

trait APIMetrics {

  def saveMetric(userId: String,
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
                 responseBody: String,
                 sourceIp: String,
                 targetIp: String,
                 apiInstanceId: String,
                 consentReferenceId: String,
                 certificateTrust: String,
                 certificateTrustDetail: String): Unit

  def saveMetricsArchive(primaryKey: Long,
                         userId: String,
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
                         responseBody: String,
                         sourceIp: String,
                         targetIp: String,
                         apiInstanceId: String,
                         consentReferenceId: String,
                         certificateTrust: String,
                         certificateTrustDetail: String
                        ): Boolean

//  //TODO: ordering of list? should this be by date? currently not enforced
//  def getAllGroupedByUrl() : Map[String, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByDay() : Map[Date, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByUserId() : Map[String, List[APIMetric]]

  def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric]

  /**
   * 
   * @param queryParams
   * @param isNewVersion from V510, we change the queryParams, use includeXxx instead of excludeXxxx, so add this flag 
   * @return
   */
  def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Future[Box[List[AggregateMetrics]]]
  
  def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]]
  
  def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]]

  def bulkDeleteMetrics(): Boolean

}

trait APIMetric {

  def getMetricId() : Long
  def getUrl() : String
  def getDate() : Date
  def getDuration(): Long
  def getUserId() : String
  def getUserName() : String
  def getAppName() : String
  def getDeveloperEmail() : String
  def getConsumerId() : String
  def getImplementedByPartialFunction() : String
  def getImplementedInVersion() : String
  def getVerb() : String
  def getHttpCode() : Int
  def getCorrelationId(): String
  def getResponseBody(): String
  def getSourceIp(): String
  def getTargetIp(): String
  def getApiInstanceId(): String
  def getConsentReferenceId(): String
  def getCertificateTrust(): String
  def getCertificateTrustDetail(): String

}

case class OBPUrlQueryParams(
  startDate: Date,
  endDate: Date,
  consumerId: String,
  userId: String,
  url: String,
  appName: String,
  implementedByPartialFunction: String,
  implementedInVersion: String,
  verb: String,
  anon: String,
  correlationId: String,
  duration: String,
  excludeAppNames: String,
  excludeUrlPattern: String,
  excludeImplementedByPartialfunctions: String
)

case class AggregateMetrics(
  totalCount: Int,
  avgResponseTime: Double,
  minResponseTime: Double,
  maxResponseTime: Double
)

case class TopApi(
  count: Int,
  ImplementedByPartialFunction: String,
  implementedInVersion: String
)


case class TopConsumer(
  count: Int,
  consumerId: String,
  appName: String,
  developerEmail: String
)