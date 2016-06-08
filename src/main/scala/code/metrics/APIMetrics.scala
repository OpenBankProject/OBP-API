package code.metrics

import net.liftweb.util.{Props, SimpleInjector}
import java.util.{Calendar, Date}

object APIMetrics extends SimpleInjector {

  val apiMetrics = new Inject(buildOne _) {}

  def buildOne: APIMetrics = ElasticsearchMetrics
    Props.getBool("allow_elasticsearch", false) match {
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

}

trait APIMetrics {

  def saveMetric(userId: String, url : String, date : Date) : Unit

  def saveMetric(url : String, date : Date) : Unit ={
    //TODO: update all places calling old function before removing this
    saveMetric ("TODO: userId", url, date)
  }

  //TODO: ordering of list? should this be by date? currently not enforced
  def getAllGroupedByUrl() : Map[String, List[APIMetric]]

  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
  def getAllGroupedByDay() : Map[Date, List[APIMetric]]

  //TODO: ordering of list? should this be alphabetically by url? currently not enforced
  def getAllGroupedByUserId() : Map[String, List[APIMetric]]

}

trait APIMetric {

  def getUrl() : String
  def getDate() : Date
  def getUserId() : String
}
