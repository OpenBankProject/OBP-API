package code.metrics

import java.util.{Calendar, Date}

import code.api.util.APIUtil
import code.bankconnectors.OBPQueryParam
import code.remotedata.RemotedataMetrics
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object APIMetrics extends SimpleInjector {

  val apiMetrics = new Inject(buildOne _) {}

  def buildOne: APIMetrics =
    APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) &&
      APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) match {
        // case false => MappedMetrics
        case false =>
          APIUtil.getPropsAsBoolValue("use_akka", false) match {
            case false  => MappedMetrics
            case true => RemotedataMetrics     // We will use Akka as a middleware
          }
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
                 correlationId: String): Unit

//  //TODO: ordering of list? should this be by date? currently not enforced
//  def getAllGroupedByUrl() : Map[String, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByDay() : Map[Date, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByUserId() : Map[String, List[APIMetric]]

  def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric]
  
  def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AggregateMetrics]]]
  
  def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]]
  
  def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]]

  def bulkDeleteMetrics(): Boolean

}

class RemotedataMetricsCaseClasses {
  case class saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, correlationId: String)
//  case class getAllGroupedByUrl()
//  case class getAllGroupedByDay()
//  case class getAllGroupedByUserId()
  case class getAllMetrics(queryParams: List[OBPQueryParam])
  case class getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam])
  case class getTopApisFuture(queryParams: List[OBPQueryParam])
  case class getTopConsumersFuture(queryParams: List[OBPQueryParam])
  case class bulkDeleteMetrics()
}

object RemotedataMetricsCaseClasses extends RemotedataMetricsCaseClasses

trait APIMetric {

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
  def getCorrelationId(): String

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