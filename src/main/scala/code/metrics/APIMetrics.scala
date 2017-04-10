package code.metrics

import java.util.{Calendar, Date}

import code.bankconnectors.OBPQueryParam
import code.remotedata.RemotedataMetrics
import net.liftweb.util.{Props, SimpleInjector}

object APIMetrics extends SimpleInjector {

  val apiMetrics = new Inject(buildOne _) {}

  def buildOne: APIMetrics =
    Props.getBool("allow_elasticsearch", false) &&
      Props.getBool("allow_elasticsearch_metrics", false) match {
        // case false => MappedMetrics
        case false => RemotedataMetrics
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

  def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String): Unit

  def saveMetric(url : String, date : Date, duration: Long) : Unit ={
    //TODO: update all places calling old function before removing this
    saveMetric("TODO: userId", url, date, duration, "TODO: userName", "TODO: appName", "TODO: developerEmail","TODO: consumerId" ,"TODO: implementedByPartialFunction" ,"TODO: implementedInVersion" ,"TODO: implementedInVersion" )
  }

//  //TODO: ordering of list? should this be by date? currently not enforced
//  def getAllGroupedByUrl() : Map[String, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByDay() : Map[Date, List[APIMetric]]
//
//  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
//  def getAllGroupedByUserId() : Map[String, List[APIMetric]]

  def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric]

  def bulkDeleteMetrics(): Boolean

}

class RemotedataMetricsCaseClasses {
  case class saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String)
//  case class getAllGroupedByUrl()
//  case class getAllGroupedByDay()
//  case class getAllGroupedByUserId()
  case class getAllMetrics(queryParams: List[OBPQueryParam])
  case class bulkDeleteMetrics()
}

object RemotedataMetricsCaseClasses extends RemotedataMetricsCaseClasses

trait APIMetric {

  def getUrl() : String
  def getDate() : Date
  def getDuration(): Long
  def getUserId() : String
  def getUserName() : String
  def getAppName : String
  def getDeveloperEmail() : String
  def getConsumerId() : String
  def getImplementedByPartialFunction() : String
  def getImplementedInVersion() : String
  def getVerb() : String

}
