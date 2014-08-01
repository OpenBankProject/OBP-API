package code.metrics

import net.liftweb.util.SimpleInjector
import java.util.{Calendar, Date}

object APIMetrics extends SimpleInjector {

  val apiMetrics = new Inject(buildOne _) {}

  def buildOne: APIMetrics = MongoAPIMetric

}

trait APIMetrics {

  //TODO: inject date
  def saveMetric(url : String, date : Date) : Unit

  def getAllGroupedByUrl() : Map[String, List[APIMetric]]

  def getAllGroupedByDay() : Map[Date, List[APIMetric]]

}

trait APIMetric {

  def getUrl() : String
  def getDate() : Date
}
