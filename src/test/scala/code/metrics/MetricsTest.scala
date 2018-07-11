package code.metrics

import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil.getCorrelationId
import code.bankconnectors.OBPLimit
import code.setup.ServerSetup


/*
TODO. We need to test concurrent / multiple inserts.
 */


class MetricsTest extends ServerSetup with WipeMetrics {

  val testUrl1 = "http://example.com/foo"
  val testUrl2 = "http://example.com/bar"
  val testUserId ="UserId"
  val testUserName = "userName"
  val testAppName = "appName"
  val testDeveloperEmail = "developerEmail"
  val testConsumerId = "consumerId" 
  val testImplementedByPartialFunction = "implementedByPartialFunction" 
  val testVersion = "i.i.v"
  val testVerb = "verb"

  val dateFormatter = APIUtil.DateWithSecondsFormat
  val day1 = dateFormatter.parse("2015-01-12T01:00:00Z")
  val day2 = dateFormatter.parse("2015-01-13T14:00:00Z")

  val startOfDayFormat = APIUtil.DateWithDayFormat
  val startOfDay1 = startOfDayFormat.parse("2015-01-12")
  val startOfDay2 = startOfDayFormat.parse("2015-01-13")

  val metrics = APIMetrics.apiMetrics.vend

  val limit = 100

  override def beforeEach(): Unit = {
    super.beforeEach()
    wipeAllExistingMetrics()
  }

  //java dates are weird doing Date == subclassOfDate can evaluate to false even if they
  //represent the same point in time
  def dateEqual(date1 : Date, date2 : Date) : Boolean = {
    date1.compareTo(date2) == 0
  }

  def shouldBeEqual(date1 : Date, date2 : Date): Unit = {
    date1.compareTo(date2) should equal(0)
  }

  feature("API Metrics") {

    scenario("We save a new API metric") {
      metrics.saveMetric(testUserId,testUrl1, day1, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())

      val byUrl = metrics.getAllMetrics(List(OBPLimit(limit))).groupBy(_.getUrl())

      byUrl.keys.size should equal(1)
      val metricsForUrl = byUrl(testUrl1)

      metricsForUrl.size should equal(1)

      val metric = metricsForUrl(0)
      shouldBeEqual(metric.getDate, day1)
      metric.getUrl() should equal(testUrl1)
    }

    scenario("Group all metrics by url") {
      metrics.saveMetric(testUserId, testUrl1, day1, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb,getCorrelationId())
      metrics.saveMetric(testUserId, testUrl1, day1, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())
      metrics.saveMetric(testUserId, testUrl1, day2, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())
      metrics.saveMetric(testUserId, testUrl2, day2, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())

      val byUrl = metrics.getAllMetrics(List(OBPLimit(limit))).groupBy(_.getUrl())
      byUrl.keySet should equal(Set(testUrl1, testUrl2))

      val url1Metrics = byUrl(testUrl1)
      url1Metrics.size should equal(3)
      url1Metrics.count(_.getUrl() == testUrl1) should equal(3)
      url1Metrics.count(m => dateEqual(m.getDate(), day1)) should equal(2)
      url1Metrics.count(m => dateEqual(m.getDate(), day2)) should equal(1)

      val url2Metrics = byUrl(testUrl2)
      url2Metrics.size should equal(1)
      url2Metrics.count(_.getUrl() == testUrl2) should equal(1)
      url2Metrics.count(m => dateEqual(m.getDate(), day2)) should equal(1)
    }

    scenario("Group all metrics by day") {
      metrics.saveMetric(testUserId, testUrl1, day1, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())
      metrics.saveMetric(testUserId, testUrl1, day1, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())
      metrics.saveMetric(testUserId, testUrl1, day2, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())
      metrics.saveMetric(testUserId, testUrl2, day2, -1L, testUserName, testAppName,
                         testDeveloperEmail, testConsumerId, testImplementedByPartialFunction,
                         testVersion, testVerb, getCorrelationId())

      val byDay = metrics.getAllMetrics(List(OBPLimit(limit))).groupBy(APIMetrics.getMetricDay)
      byDay.keySet should equal(Set(startOfDay1, startOfDay2))

      val day1Metrics = byDay(startOfDay1)
      day1Metrics.size should equal(2)
      day1Metrics.count(m => dateEqual(m.getDate(), day1)) should equal(2)
      day1Metrics.count(_.getUrl() == testUrl1) should equal(2)

      val day2Metrics = byDay(startOfDay2)
      day2Metrics.size should equal(2)
      day2Metrics.count(m => dateEqual(m.getDate(), day2)) should equal(2)
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
    APIMetrics.apiMetrics.vend.bulkDeleteMetrics()
  }
}
