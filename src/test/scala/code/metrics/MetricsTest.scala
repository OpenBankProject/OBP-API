package code.metrics

import java.text.SimpleDateFormat

import code.api.test.ServerSetup
import net.liftweb.mongodb._

class MetricsTest extends ServerSetup with WipeMetrics {

  val testUrl1 = "http://example.com/foo"
  val testUrl2 = "http://example.com/bar"

  val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  val day1 = dateFormatter.parse("2015-01-12T01:00:00")
  val day2 = dateFormatter.parse("2015-01-13T14:00:00")

  val startOfDayFormat = new SimpleDateFormat("yyyy-MM-dd")
  val startOfDay1 = startOfDayFormat.parse("2015-01-12")
  val startOfDay2 = startOfDayFormat.parse("2015-01-13")

  val metrics = APIMetrics.apiMetrics.vend

  override def beforeEach(): Unit = {
    super.beforeEach()
    wipeAllExistingMetrics()
  }

  feature("API Metrics") {

    scenario("We save a new API metric") {
      metrics.saveMetric(testUrl1, day1)

      val byUrl = metrics.getAllGroupedByUrl()

      byUrl.keys.size should equal(1)
      val metricsForUrl = byUrl(testUrl1)

      metricsForUrl.size should equal(1)

      val metric = metricsForUrl(0)
      metric.getDate() should equal(day1)
      metric.getUrl() should equal(testUrl1)
    }

    scenario("Group all metrics by url") {
      metrics.saveMetric(testUrl1, day1)
      metrics.saveMetric(testUrl1, day1)
      metrics.saveMetric(testUrl1, day2)
      metrics.saveMetric(testUrl2, day2)

      val byUrl = metrics.getAllGroupedByUrl()
      byUrl.keySet should equal(Set(testUrl1, testUrl2))

      val url1Metrics = byUrl(testUrl1)
      url1Metrics.size should equal(3)
      url1Metrics.count(_.getUrl() == testUrl1) should equal(3)
      url1Metrics.count(_.getDate() == day1) should equal(2)
      url1Metrics.count(_.getDate() == day2) should equal(1)

      val url2Metrics = byUrl(testUrl2)
      url2Metrics.size should equal(1)
      url2Metrics.count(_.getUrl() == testUrl2) should equal(1)
      url2Metrics.count(_.getDate() == day2) should equal(1)
    }

    scenario("Group all metrics by day") {
      metrics.saveMetric(testUrl1, day1)
      metrics.saveMetric(testUrl1, day1)
      metrics.saveMetric(testUrl1, day2)
      metrics.saveMetric(testUrl2, day2)

      val byDay = metrics.getAllGroupedByDay()
      byDay.keySet should equal(Set(startOfDay1, startOfDay2))

      val day1Metrics = byDay(startOfDay1)
      day1Metrics.size should equal(2)
      day1Metrics.count(_.getDate() == day1) should equal(2)
      day1Metrics.count(_.getUrl() == testUrl1) should equal(2)

      val day2Metrics = byDay(startOfDay2)
      day2Metrics.size should equal(2)
      day2Metrics.count(_.getDate() == day2) should equal(2)
      day2Metrics.count(_.getUrl() == testUrl1) should equal(1)
      day2Metrics.count(_.getUrl() == testUrl2) should equal(1)
    }

  }

}

/**
 * This trait provides a method to wipe all existing metrics
 *
 * If the APIMetrics implementation changes, this trait will need to change as well
 */
trait WipeMetrics {
  def wipeAllExistingMetrics() = {
    //just drops all mongodb databases
    MongoDB.getDb(DefaultMongoIdentifier).foreach(_.dropDatabase())
  }
}
