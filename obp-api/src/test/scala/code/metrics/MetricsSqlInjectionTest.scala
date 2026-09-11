package code.metrics

import code.api.util.APIUtil
import code.api.util.APIUtil.getCorrelationId
import code.api.util.{OBPAppName, OBPFromDate, OBPToDate}
import code.api.cache.Redis
import code.consumer.Consumers
import code.setup.ServerSetup
import net.liftweb.util.Helpers.randomString

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A metrics filter value is data, not SQL.
 *
 * getAllAggregateMetricsBox and getTopConsumersFuture build their WHERE clause by splicing the
 * filter values in through `sqlFriendly` - `s"'$value'"`, with no escaping - and hand the finished
 * string to DBUtil.runQuery, which prepareStatement's it with no bound parameters. So a value like
 * `' OR '1'='1` closes the quote and turns `appname = '...'` into an always-true disjunction, and
 * the filter that was supposed to narrow the result stops narrowing it. The aggregate is the clean
 * oracle: it counts rows, and a filter that names an app which does not exist must count zero - if
 * the injection nullifies the filter, it counts every row instead.
 *
 * Same reflected value reaches these from `GET /management/aggregate-metrics` (role
 * canReadAggregateMetrics) and `GET /management/metrics/top-consumers` (role canReadMetrics), through
 * getHttpRequestUrlParam, which URL-decodes and applies no character filter. The sibling
 * getTopApisFuture was already routed to the parameter-binding DoobieMetricsQueries; these two were
 * left behind.
 */
class MetricsSqlInjectionTest extends ServerSetup with WipeMetrics {

  private val dateFormatter = APIUtil.DateWithSecondsFormat
  private val day = dateFormatter.parse("2015-01-12T01:00:00Z")
  private val from = OBPFromDate(dateFormatter.parse("2010-01-01T00:00:00Z"))
  private val to = OBPToDate(dateFormatter.parse("2030-01-01T00:00:00Z"))

  private val realApp = "legit-app"
  // Closes the quote sqlFriendly opens and makes the disjunction always true.
  private val injection = "no-such-app' OR '1'='1"

  private val metrics = APIMetrics.apiMetrics.vend

  /**
   * Drop this query's own cache entry before reading through it.
   *
   * Both methods memoize on the query-parameter list alone, and a fromDate this old lands in the
   * "stable" cache whose TTL is 24 hours - so the answer a vulnerable build cached is handed
   * straight back to a fixed one, and the fix looks like it did nothing. The key does not change
   * when the code does, which is exactly what makes this a trap rather than a nuisance.
   *
   * Exact key, not a wildcard: the local runner shares one Redis across four parallel shards, and a
   * pattern delete would evict another shard's live entries mid-run (same reasoning as
   * CacheKeyGoldenTest.afterClearing).
   */
  private def uncached[A](method: String, params: List[Any], extra: String = "")(f: => A): A = {
    val cacheKey = ("code.metrics.MappedMetrics", method, List(params).mkString("_") + extra)
    Redis.deleteKeysByPattern(
      s"code.api.cache.Redis.memoizeSyncWithRedis(Some(${cacheKey.toString()}))()()()")
    f
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    wipeAllExistingMetrics()
    // Three rows, all for realApp - none for the injected name. verb is "GET" throughout.
    for (_ <- 1 to 3) {
      metrics.saveMetric("uid", "http://example.com/x", day, 5L, "uname", realApp,
        "dev@example.com", "cid", "getBanks", "1.0", "GET", None, getCorrelationId(),
        "body", "1.2.3.4", "1.2.3.4", "inst", null, null, null, null)
    }
    MetricBatchWriter.flush()
    // top-consumers joins metric.appname = consumer.name, so without a matching consumer the join
    // filters everything out and the verb filter is unobservable. Give realApp a consumer (a unique
    // key each run keeps beforeEach idempotent without needing to delete it).
    Consumers.consumers.vend.createConsumer(
      key = Some(randomString(40).toLowerCase), secret = Some(randomString(40).toLowerCase),
      isActive = Some(true), name = Some(realApp), appType = None,
      description = Some("sqli fixture"), developerEmail = Some("dev@example.com"),
      redirectURL = None, createdByUserId = None, None, None, None)
  }

  Feature("metrics filters bind their values instead of splicing them") {

    Scenario("aggregate metrics: an app_name that names no app counts zero, injection or not") {
      val benignParams = List(from, to, OBPAppName("still-no-such-app"))
      val benign = uncached("getAllAggregateMetricsBox", List(benignParams, false))(Await.result(
        metrics.getAllAggregateMetricsFuture(benignParams, false),
        20.seconds).openOrThrowException("aggregate query failed"))
      withClue("a plain non-matching app_name must count zero, proving the filter is applied: ") {
        benign.head.totalCount should equal(0)
      }

      val injectedParams = List(from, to, OBPAppName(injection))
      val injected = uncached("getAllAggregateMetricsBox", List(injectedParams, false))(Await.result(
        metrics.getAllAggregateMetricsFuture(injectedParams, false),
        20.seconds).openOrThrowException("aggregate query failed"))
      withClue(s"'$injection' must be matched as a literal app name (matching nothing), not " +
        "spliced into SQL where it nullifies the filter and counts every row: ") {
        injected.head.totalCount should equal(0)
      }
    }

    Scenario("top consumers: an app_name filter narrows, and an injected app_name cannot widen it back") {
      // Positive control: the real app_name returns realApp's consumer. Without this the Nil
      // assertions below would pass even if the join never matched, making the test vacuous.
      val controlParams = List(from, to, OBPAppName(realApp))
      val control = uncached("getTopConsumersFuture", controlParams)(Await.result(
        metrics.getTopConsumersFuture(controlParams),
        20.seconds).openOrThrowException("top-consumers query failed"))
      withClue("the real app_name must return the seeded consumer, proving the join and data are live: ") {
        control.map(_.appName) should contain(realApp)
      }

      val benignTcParams = List(from, to, OBPAppName("still-no-such-app"))
      val benign = uncached("getTopConsumersFuture", benignTcParams)(Await.result(
        metrics.getTopConsumersFuture(benignTcParams),
        20.seconds).openOrThrowException("top-consumers query failed"))
      withClue("a non-matching app_name must return no consumers, proving the filter narrows: ") {
        benign should equal(Nil)
      }

      // On the spliced query `appname = 'no-such-app' OR '1'='1'` is always true, so the app_name
      // filter is nullified and realApp's consumer comes back through the join; bound, the whole
      // string is one literal app name that matches nothing.
      val injectedTcParams = List(from, to, OBPAppName(injection))
      val injected = uncached("getTopConsumersFuture", injectedTcParams)(Await.result(
        metrics.getTopConsumersFuture(injectedTcParams),
        20.seconds).openOrThrowException("top-consumers query failed"))
      withClue(s"the injected app_name '$injection' must be matched literally (matching nothing), not spliced: ") {
        injected should equal(Nil)
      }
    }
  }
}
