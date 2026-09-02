package code.metrics

import java.sql.{PreparedStatement, Timestamp}
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import code.api.cache.Caching
import code.api.util.APIUtil.generateUUID
import code.api.util._
import code.model.MappedConsumersProvider
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable
import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.duration._

object MappedMetrics extends APIMetrics with MdcLoggable{

  /**
   * Smart Caching Strategy for Metrics:
   * 
   * Metrics data becomes stable/immutable after a certain time period (default: 10 minutes).
   * We leverage this to use different cache TTLs based on the age of the data being queried.
   * 
   * This smart caching applies to:
   * - getAllMetrics (GET /management/metrics)
   * - getAllAggregateMetrics (GET /management/aggregate-metrics)
   * - getTopApis (GET /management/metrics/top-apis)
   * - getTopConsumers (GET /management/metrics/top-consumers)
   * 
   * Configuration:
   * - MappedMetrics.cache.ttl.seconds.getAllMetrics: Short TTL for queries including recent data (default: 7 seconds)
   * - MappedMetrics.cache.ttl.seconds.getStableMetrics: Long TTL for queries with only stable/old data (default: 86400 seconds = 24 hours)
   * - MappedMetrics.stable.boundary.seconds: Age threshold after which metrics are stable (default: 600 seconds = 10 minutes)
   * 
   * Deprecated (no longer used - smart caching now applies):
   * - MappedMetrics.cache.ttl.seconds.getAllAggregateMetrics (now uses smart caching)
   * - MappedMetrics.cache.ttl.seconds.getTopApis (now uses smart caching)
   * - MappedMetrics.cache.ttl.seconds.getTopConsumers (now uses smart caching)
   * 
   * Examples:
   * - Query with from_date=2025-01-01 (> 10 mins ago): Uses 24 hour cache (stable data)
   * - Query with from_date=5 mins ago: Uses 7 second cache (regular)
   * - Query with no from_date: Uses 7 second cache (regular, safe default)
   * 
   * This dramatically reduces database load for historical/reporting queries while keeping recent data fresh.
   */
  val cachedAllMetrics = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getAllMetrics", "7").toInt
  val cachedStableMetrics = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt
  val stableBoundarySeconds = APIUtil.getPropsValue(s"MappedMetrics.stable.boundary.seconds", "600").toInt
  val cachedAllAggregateMetrics = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getAllAggregateMetrics", "7").toInt
  val cachedTopApis = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getTopApis", "3600").toInt
  val cachedTopConsumers = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getTopConsumers", "3600").toInt

  /**
   * Determines the appropriate cache TTL based on the query's date range.
   * 
   * Strategy:
   * - If fromDate exists and is older than the stable boundary → use long TTL (stable cache)
   * - If no fromDate but toDate exists and is older than stable boundary → use long TTL (stable cache)
   * - Otherwise (no dates, or any date in recent zone) → use short TTL (regular cache)
   * 
   * Rationale:
   * Metrics older than the stable boundary (e.g., 10 minutes) never change, so they can be
   * cached for much longer. This significantly reduces database load for historical queries
   * (reports, analytics, etc.) while keeping recent data fresh.
   * 
   * Examples:
   * - from_date=2024-01-01 → stable cache (old data)
   * - to_date=2024-01-01, no from_date → stable cache (only old data)
   * - from_date=5 mins ago → regular cache (recent data)
   * - no date filters → regular cache (typically "latest N metrics")
   * 
   * @param queryParams The query parameters including potential OBPFromDate and OBPToDate
   * @return Cache TTL in seconds - either cachedStableMetrics or cachedAllMetrics
   */
  private def determineMetricsCacheTTL(queryParams: List[OBPQueryParam]): Int = {
    val now = new Date()
    val stableBoundary = new Date(now.getTime - (stableBoundarySeconds * 1000L))
    
    val fromDate = queryParams.collectFirst { case OBPFromDate(d) => d }
    val toDate = queryParams.collectFirst { case OBPToDate(d) => d }
    
    // Determine if we should use stable cache based on date parameters
    val useStableCache = (fromDate, toDate) match {
      // Case 1: fromDate exists and is before stable boundary (most common for historical queries)
      case (Some(from), _) if from.before(stableBoundary) =>
        logger.debug(s"Using stable metrics cache (TTL=${cachedStableMetrics}s): fromDate=$from is before stableBoundary=$stableBoundary")
        true
      
      // Case 2: No fromDate, but toDate exists and is before stable boundary (e.g., "all data up to Jan 2024")
      case (None, Some(to)) if to.before(stableBoundary) =>
        logger.debug(s"Using stable metrics cache (TTL=${cachedStableMetrics}s): toDate=$to is before stableBoundary=$stableBoundary (no fromDate)")
        true
      
      // Case 3: No dates, or dates include recent data → use regular cache
      case _ =>
        logger.debug(s"Using regular metrics cache (TTL=${cachedAllMetrics}s): fromDate=$fromDate, toDate=$toDate, stableBoundary=$stableBoundary")
        false
    }
    
    if (useStableCache) cachedStableMetrics else cachedAllMetrics
  }

  override def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, httpCode: Option[Int], correlationId: String,
                          responseBody: String, sourceIp: String, targetIp: String, apiInstanceId: String, consentReferenceId: String,
                          certificateTrust: String, certificateTrustDetail: String,
                          authType: String): Unit = {
    // A correlation id is expected on every metric. Rows without one cannot be moved
    // to the archive later (its correlationId column requires a UUID), so flag it at
    // write time where the source of the missing id can actually be traced.
    if (correlationId == null || correlationId.trim.isEmpty) {
      logger.warn(s"saveMetric: writing a Metric row with an empty correlation id (url=$url, verb=$verb, consumerId=$consumerId, implementedInVersion=$implementedInVersion). This row will not be archivable.")
    }
    MetricBatchWriter.enqueue(
      MetricBatchWriter.MetricRow(
        userId = userId,
        url = url,
        date = date,
        duration = duration,
        userName = userName,
        appName = appName,
        developerEmail = developerEmail,
        consumerId = consumerId,
        implementedByPartialFunction = implementedByPartialFunction,
        implementedInVersion = implementedInVersion,
        verb = verb,
        httpCode = httpCode.getOrElse(0),
        correlationId = correlationId,
        responseBody = responseBody,
        sourceIp = sourceIp,
        targetIp = targetIp,
        apiInstanceId = apiInstanceId,
        consentReferenceId = consentReferenceId,
        certificateTrust = certificateTrust,
        certificateTrustDetail = certificateTrustDetail,
        authType = authType
      )
    )
  }
  override def saveMetricsArchive(primaryKey: Long, userId: String,
                                  url: String, date: Date, duration: Long, userName: String,
                                  appName: String, developerEmail: String, consumerId: String,
                                  implementedByPartialFunction: String, implementedInVersion: String,
                                  verb: String, httpCode: Option[Int], correlationId: String,
                                  responseBody: String, sourceIp: String, targetIp: String,
                                  apiInstanceId: String, consentReferenceId: String,
                                  certificateTrust: String, certificateTrustDetail: String,
                                  authType: String): Boolean = {
    // Dedup by the source metric's primary key stored in `metricId`, NOT by the archive's own
    // auto-increment `id`. The two are unrelated id-spaces; matching on `id` overwrites an
    // unrelated archived row once the archive's id sequence grows into the live metric id range.
    //
    // A failed write comes back as false rather than as an exception, as Lift's save did: the
    // caller uses that to skip the source-row delete and mark the run as failed instead of
    // silently stalling.
    val saved = MetricArchive.upsertByMetricId(primaryKey, userId, url, date, duration, userName,
      appName, developerEmail, consumerId, implementedByPartialFunction, implementedInVersion,
      verb, httpCode, correlationId, responseBody, sourceIp, targetIp, apiInstanceId,
      consentReferenceId, certificateTrust, certificateTrustDetail, authType)
    if (!saved) {
      logger.error(s"saveMetricsArchive: failed to persist MetricArchive row for metricId=$primaryKey (url=$url, date=$date)")
    }
    saved
  }


//  override def getAllGroupedByUserId(): Map[String, List[APIMetric]] = {
//    //TODO: do this all at the db level using an actual group by query
//    MappedMetric.findAll.groupBy(_.getUserId)
//  }
//
//  override def getAllGroupedByDay(): Map[Date, List[APIMetric]] = {
//    //TODO: do this all at the db level using an actual group by query
//    MappedMetric.findAll.groupBy(APIMetrics.getMetricDay)
//  }
//
//  override def getAllGroupedByUrl(): Map[String, List[APIMetric]] = {
//    //TODO: do this all at the db level using an actual group by query
//    MappedMetric.findAll.groupBy(_.getUrl())
//  }

  //TODO, maybe move to `APIUtil.scala`
  private def getQueryParams(queryParams: List[OBPQueryParam]): MetricQuery =
    MetricQuery.fromQueryParams(queryParams)

  // TODO Cache this as long as fromDate and toDate are in the past (before now)
  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    val cacheKey = ("code.metrics.MappedMetrics", "getAllMetrics", List(queryParams).mkString("_"))
      val cacheTTL = determineMetricsCacheTTL(queryParams)
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
        MappedMetric.findAll(getQueryParams(queryParams))
    }
  }
  
  
  
    /**
      * Example of a Tuple response
      * (List(count, avg, min, max),List(List(7503, 70.3398640543782487, 0, 9039)))
      * First value of the Tuple is a List of field names returned by SQL query.
      * Second value of the Tuple is a List of rows of the result returned by SQL query. Please note it's only one row.
      */
      
  private def extendPrepareStement(startLine: Int, stmt:PreparedStatement, excludeFiledValues : Set[String]) = {
    for(i <- 0 until  excludeFiledValues.size) yield {
      stmt.setString(startLine+i, excludeFiledValues.toList(i))
    }
  }
  

  // Smart caching applied - uses determineMetricsCacheTTL based on query date range
  /**
   * The filter set a metrics read applies, taken from the request's query parameters.
   *
   * One extractor for all three reads. They used to carry a copy each of the same twenty
   * `queryParams.collect` lines and the same MetricsQueryFilters construction, differing only in
   * which fields they bothered to fill - three near-identical blocks that had to be kept in step by
   * hand, and that a filter added to one would silently miss in the others.
   *
   * The include* fields are read by buildFilterConditions only when isNewVersion is true, so passing
   * them from a caller that runs the exclude* branch is inert; they are filled unconditionally
   * rather than per-caller for that reason.
   *
   * withCorrelationId is a parameter and not simply always-on because it is a real behavioural
   * difference, not an oversight: the aggregate query has always filtered on correlation id, and
   * top-consumers has not - its old SQL extracted the value into a local and then never referenced
   * it. Defaulting it on here would quietly add a filter to top-consumers.
   */
  private def filtersFrom(
    queryParams: List[OBPQueryParam],
    withCorrelationId: Boolean
  ): MetricsQueryFilters =
    MetricsQueryFilters(
      consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption,
      userId = queryParams.collect { case OBPUserId(value) => value }.headOption,
      url = queryParams.collect { case OBPUrl(value) => value }.headOption,
      appName = queryParams.collect { case OBPAppName(value) => value }.headOption,
      implementedByPartialFunction =
        queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption,
      implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption,
      verb = queryParams.collect { case OBPVerb(value) => value }.headOption,
      anon = queryParams.collect { case OBPAnon(value) => value }.headOption,
      correlationId =
        if (withCorrelationId) queryParams.collect { case OBPCorrelationId(value) => value }.headOption
        else None,
      httpStatusCode = queryParams.collect { case OBPHttpStatusCode(value) => value }.headOption,
      excludeAppNames = queryParams.collect { case OBPExcludeAppNames(value) => value }.headOption,
      includeAppNames = queryParams.collect { case OBPIncludeAppNames(value) => value }.headOption,
      excludeUrlPatterns = queryParams.collect { case OBPExcludeUrlPatterns(value) => value }.headOption,
      includeUrlPatterns = queryParams.collect { case OBPIncludeUrlPatterns(value) => value }.headOption,
      excludeImplementedByPartialFunctions =
        queryParams.collect { case OBPExcludeImplementedByPartialFunctions(value) => value }.headOption,
      includeImplementedByPartialFunctions =
        queryParams.collect { case OBPIncludeImplementedByPartialFunctions(value) => value }.headOption
    )

  def getAllAggregateMetricsBox(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Box[List[AggregateMetrics]] = {
    logger.info(s"getAllAggregateMetricsBox called with ${queryParams.length} query params, isNewVersion=$isNewVersion")
    val cacheKey = ("code.metrics.MappedMetrics", "getAllAggregateMetricsBox", List(queryParams, isNewVersion).mkString("_"))
    val cacheTTL = determineMetricsCacheTTL(queryParams)
    logger.debug(s"getAllAggregateMetricsBox cache key: $cacheKey, TTL: $cacheTTL seconds")
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
      logger.info(s"getAllAggregateMetricsBox - CACHE MISS - Executing database query for aggregate metrics")
      val startTime = System.currentTimeMillis()
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption

      // Bind the filter values instead of splicing them into the SQL string, which is what
      // sqlFriendly did: a value like `' OR '1'='1` closed the quote and turned `appname = '...'`
      // into an always-true disjunction over the whole table. See MetricsSqlInjectionTest.
      val filters = filtersFrom(queryParams, withCorrelationId = true)
      val result = DoobieMetricsQueries.getAggregateMetrics(fromDate.get, toDate.get, filters, isNewVersion)
      val elapsedTime = System.currentTimeMillis() - startTime
      logger.info(s"getAllAggregateMetricsBox - Query completed in ${elapsedTime}ms")
      tryo(result)
    }
  }
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Future[Box[List[AggregateMetrics]]] = Future{
    getAllAggregateMetricsBox(queryParams: List[OBPQueryParam], isNewVersion)
  }
  
  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.deleteAll()
    true
  }

  // Smart caching applied - uses determineMetricsCacheTTL based on query date range
  // Uses Doobie for type-safe database queries with proper JDBC type handling (including SQL Server NVARCHAR)
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] = Future{
  val cacheKey = ("code.metrics.MappedMetrics", "getTopApisFuture", List(queryParams).mkString("_"))
  val cacheTTL = determineMetricsCacheTTL(queryParams)
  Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
    {
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(10)
      val filters = filtersFrom(queryParams, withCorrelationId = true)

      val result: Box[List[TopApi]] = tryo {
        logger.debug(s"getTopApisFuture using Doobie with filters: $filters, limit: $limit")
        val topApis = DoobieMetricsQueries.getTopApis(fromDate.get, toDate.get, limit, filters)
        logger.debug(s"getTopApisFuture returned ${topApis.length} rows")
        if (topApis.nonEmpty) {
          logger.debug(s"getTopApisFuture first row sample: ${topApis.head}")
        }
        topApis
      }
      result
    }}
  }

  // Smart caching applied - uses determineMetricsCacheTTL based on query date range
  // Groups by metric.consumerid — see DoobieMetricsQueries.buildTopConsumersByConsumerIdQuery.
  override def getTopConsumersByConsumerIdFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = Future{
  // Key built by hand, like every other cache site in this file: CacheKeyFromArguments is a macro
  // whose rendering depends on the enclosing signature, and binding its result to a val silently
  // empties the argument segment (every caller then shares one entry).
  val cacheKey = ("code.metrics.MappedMetrics", "getTopConsumersByConsumerIdFuture", List(queryParams).mkString("_"))
  val cacheTTL = determineMetricsCacheTTL(queryParams)
  Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
    {
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption
      val userId = queryParams.collect { case OBPUserId(value) => value }.headOption
      val url = queryParams.collect { case OBPUrl(value) => value }.headOption
      val appName = queryParams.collect { case OBPAppName(value) => value }.headOption
      val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption
      val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption
      val verb = queryParams.collect { case OBPVerb(value) => value }.headOption
      val anon = queryParams.collect { case OBPAnon(value) => value }.headOption
      val correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption
      val httpStatusCode = queryParams.collect { case OBPHttpStatusCode(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(50)

      val filters = MetricsQueryFilters(
        consumerId = consumerId,
        userId = userId,
        url = url,
        appName = appName,
        implementedByPartialFunction = implementedByPartialFunction,
        implementedInVersion = implementedInVersion,
        verb = verb,
        anon = anon,
        correlationId = correlationId,
        httpStatusCode = httpStatusCode,
        excludeAppNames = None,
        excludeUrlPatterns = None,
        excludeImplementedByPartialFunctions = None
      )

      val result: Box[List[TopConsumer]] = tryo {
        logger.debug(s"getTopConsumersByConsumerIdFuture using Doobie with filters: $filters, limit: $limit")
        DoobieMetricsQueries.getTopConsumersByConsumerId(fromDate.get, toDate.get, limit, filters)
      }
      result
    }}
  }

  // Smart caching applied - uses determineMetricsCacheTTL based on query date range
  // Groups by the on-behalf-of-resolved user — see DoobieMetricsQueries.buildTopUsersQuery.
  override def getTopUsersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopUser]]] = Future{
  // Key built by hand, like every other cache site in this file: CacheKeyFromArguments is a macro
  // whose rendering depends on the enclosing signature, and binding its result to a val silently
  // empties the argument segment (every caller then shares one entry).
  val cacheKey = ("code.metrics.MappedMetrics", "getTopUsersFuture", List(queryParams).mkString("_"))
  val cacheTTL = determineMetricsCacheTTL(queryParams)
  Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
    {
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption
      val userId = queryParams.collect { case OBPUserId(value) => value }.headOption
      val url = queryParams.collect { case OBPUrl(value) => value }.headOption
      val appName = queryParams.collect { case OBPAppName(value) => value }.headOption
      val excludeAppNames: Option[List[String]] = queryParams.collect { case OBPExcludeAppNames(value) => value }.headOption
      val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption
      val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption
      val verb = queryParams.collect { case OBPVerb(value) => value }.headOption
      val anon = queryParams.collect { case OBPAnon(value) => value }.headOption
      val correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption
      val httpStatusCode = queryParams.collect { case OBPHttpStatusCode(value) => value }.headOption
      val excludeUrlPatterns = queryParams.collect { case OBPExcludeUrlPatterns(value) => value }.headOption
      val excludeImplementedByPartialFunctions = queryParams.collect { case OBPExcludeImplementedByPartialFunctions(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(50)

      val filters = MetricsQueryFilters(
        consumerId = consumerId,
        userId = userId,
        url = url,
        appName = appName,
        implementedByPartialFunction = implementedByPartialFunction,
        implementedInVersion = implementedInVersion,
        verb = verb,
        anon = anon,
        correlationId = correlationId,
        httpStatusCode = httpStatusCode,
        excludeAppNames = excludeAppNames,
        excludeUrlPatterns = excludeUrlPatterns,
        excludeImplementedByPartialFunctions = excludeImplementedByPartialFunctions
      )

      val result: Box[List[TopUser]] = tryo {
        logger.debug(s"getTopUsersFuture using Doobie with filters: $filters, limit: $limit")
        val topUsers = DoobieMetricsQueries.getTopUsers(fromDate.get, toDate.get, limit, filters)
        logger.debug(s"getTopUsersFuture returned " + topUsers.length + " rows")
        topUsers
      }
      result
    }}
  }

  // Smart caching applied - uses determineMetricsCacheTTL based on query date range
  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = Future {
  val cacheKey = ("code.metrics.MappedMetrics", "getTopConsumersFuture", List(queryParams).mkString("_"))
  val cacheTTL = determineMetricsCacheTTL(queryParams)
  Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cacheTTL.seconds){
  
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(500)

      // withCorrelationId = false: the SQL this replaced extracted a correlation id and never used
      // it, so filtering on it here would be a new behaviour, not a restored one.
      val filters = filtersFrom(queryParams, withCorrelationId = false)
      val result = DoobieMetricsQueries.getTopConsumers(fromDate.get, toDate.get, limit, filters)
      tryo(result)
    }
  }

}

/**
 * The filters, paging and ordering a metrics read carries.
 *
 * Kept as a value rather than as SQL because it also goes into the cache key for the read, so two
 * requests asking for different pages, ranges or filters cannot share a cached answer.
 */
case class MetricQuery(
  limit: Option[Int],
  offset: Option[Int],
  fromDate: Option[Date],
  toDate: Option[Date],
  orderBy: Option[(String, Boolean)],
  consumerId: Option[String],
  bankId: Option[String],
  userId: Option[String],
  // The server-locked user set behind GET /my/metrics (the caller plus the agent users their
  // consents minted). It is a separate field from `userId` because it must not be expressible
  // through a request parameter: APIMetrics.getMetricsFromHttpParams strips any caller-supplied
  // user_id before setting it.
  userIds: Option[List[String]],
  url: Option[String],
  appName: Option[String],
  implementedInVersion: Option[String],
  implementedByPartialFunction: Option[String],
  verb: Option[String],
  correlationId: Option[String],
  durationGreaterThan: Option[Long],
  httpStatusCode: Option[Int],
  consentReferenceId: Option[String],
  certificateTrust: Option[String],
  anon: Option[Boolean],
  excludeAppNames: Option[List[String]]
)

object MetricQuery {

  /** The column an OBPOrdering field name selects; anything unrecognised falls back to date. */
  private val orderableColumns: Map[String, String] = Map(
    "user_id" -> "userid",
    "username" -> "username",
    "user_name" -> "username",
    "developer_email" -> "developeremail",
    "app_name" -> "appname",
    "url" -> "url",
    "date" -> "date_c",
    "consumer_id" -> "consumerid",
    "verb" -> "verb",
    "implemented_in_version" -> "implementedinversion",
    "implemented_by_partial_function" -> "implementedbypartialfunction",
    "correlation_id" -> "correlationid",
    "duration" -> "duration",
    "http_status_code" -> "httpcode")

  def columnFor(field: Option[String]): Option[String] = field.flatMap(orderableColumns.get)

  def fromQueryParams(queryParams: List[OBPQueryParam]): MetricQuery =
    MetricQuery(
      limit = queryParams.collect { case OBPLimit(value) => value }.headOption,
      offset = queryParams.collect { case OBPOffset(value) => value }.headOption,
      fromDate = queryParams.collect { case OBPFromDate(date) => date }.headOption,
      toDate = queryParams.collect { case OBPToDate(date) => date }.headOption,
      // An unrecognised sort field falls back to date descending regardless of the direction
      // asked for, which is what the Mapper translation did.
      orderBy = queryParams.collect {
        case OBPOrdering(field, direction) =>
          columnFor(field) match {
            case Some(column) => (column, direction == OBPAscending)
            case None => ("date_c", false)
          }
      }.headOption,
      consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption,
      bankId = queryParams.collect { case OBPBankId(value) => value }.headOption,
      userId = queryParams.collect { case OBPUserId(value) => value }.headOption,
      userIds = queryParams.collect { case OBPUserIds(values) => values }.headOption,
      url = queryParams.collect { case OBPUrl(value) => value }.headOption,
      appName = queryParams.collect { case OBPAppName(value) => value }.headOption,
      implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption,
      implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption,
      verb = queryParams.collect { case OBPVerb(value) => value }.headOption,
      correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption,
      durationGreaterThan = queryParams.collect { case OBPDuration(value) => value.toLong }.headOption,
      httpStatusCode = queryParams.collect { case OBPHttpStatusCode(value) => value }.headOption,
      consentReferenceId = queryParams.collect { case OBPConsentReferenceId(value) => value }.headOption,
      certificateTrust = queryParams.collect { case OBPCertificateTrust(value) => value }.headOption,
      anon = queryParams.collect { case OBPAnon(value) => value }.headOption,
      excludeAppNames = queryParams.collect { case OBPExcludeAppNames(values) => values }.headOption)
}

/** One request served, as the metrics API reads it back. */
case class MappedMetric(
  metricPrimaryKey: Long,
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
  httpCode: Int,
  correlationId: String,
  responseBody: String,
  sourceIp: String,
  targetIp: String,
  apiInstanceId: String,
  consentReferenceId: String,
  certificateTrust: String,
  certificateTrustDetail: String,
  authType: String
) extends APIMetric {
  override def getMetricId(): Long = metricPrimaryKey
  override def getUrl(): String = url
  override def getDate(): Date = date
  override def getDuration(): Long = duration
  override def getUserId(): String = userId
  override def getUserName(): String = userName
  override def getAppName(): String = appName
  override def getDeveloperEmail(): String = developerEmail
  override def getConsumerId(): String = consumerId
  override def getImplementedByPartialFunction(): String = implementedByPartialFunction
  override def getImplementedInVersion(): String = implementedInVersion
  override def getVerb(): String = verb
  override def getHttpCode(): Int = httpCode
  override def getCorrelationId(): String = correlationId
  override def getResponseBody(): String = responseBody
  override def getSourceIp(): String = sourceIp
  override def getTargetIp(): String = targetIp
  override def getApiInstanceId(): String = apiInstanceId
  override def getConsentReferenceId(): String = consentReferenceId
  override def getCertificateTrust(): String = certificateTrust
  override def getCertificateTrustDetail(): String = certificateTrustDetail
  override def getAuthType(): String = authType
}

object MappedMetric extends MetricStore[MappedMetric] {

  // The entity overrode its table name: the live metrics table is `metric`.
  override protected val tableName: String = "metric"

  /**
   * Whether an X-Request-ID has already been used to create something.
   *
   * Berlin Group requires a request id to be unique per creating call, and this is what enforces
   * it: a POST that returned 201 under the same correlation id means the caller is replaying.
   */
  def existsCreatedWithCorrelationId(correlationId: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM metric
            WHERE correlationid = ${Option(correlationId)} AND verb = 'POST' AND httpcode = 201"""
        .query[Long].unique) > 0

  override protected def dateOf(row: MappedMetric): Date = row.date

  override protected def build(id: Long, row: MetricColumns): MappedMetric =
    MappedMetric(id, row.userId, row.url, row.date, row.duration, row.userName, row.appName,
      row.developerEmail, row.consumerId, row.implementedByPartialFunction,
      row.implementedInVersion, row.verb, row.httpCode, row.correlationId, row.responseBody,
      row.sourceIp, row.targetIp, row.apiInstanceId, row.consentReferenceId, row.certificateTrust,
      row.certificateTrustDetail, row.authType)
}

/**
 * A metric moved out of the live table by the archive scheduler.
 *
 * `metricId` is the primary key the row had in `metric`, and is what the archiver de-duplicates
 * on - the two tables have unrelated id sequences.
 */
case class MetricArchive(
  archivePrimaryKey: Long,
  metricId: Long,
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
  httpCode: Int,
  correlationId: String,
  responseBody: String,
  sourceIp: String,
  targetIp: String,
  apiInstanceId: String,
  consentReferenceId: String,
  certificateTrust: String,
  certificateTrustDetail: String,
  authType: String
) extends APIMetric {
  override def getMetricId(): Long = metricId
  override def getUrl(): String = url
  override def getDate(): Date = date
  override def getDuration(): Long = duration
  override def getUserId(): String = userId
  override def getUserName(): String = userName
  override def getAppName(): String = appName
  override def getDeveloperEmail(): String = developerEmail
  override def getConsumerId(): String = consumerId
  override def getImplementedByPartialFunction(): String = implementedByPartialFunction
  override def getImplementedInVersion(): String = implementedInVersion
  override def getVerb(): String = verb
  override def getHttpCode(): Int = httpCode
  override def getCorrelationId(): String = correlationId
  override def getResponseBody(): String = responseBody
  override def getSourceIp(): String = sourceIp
  override def getTargetIp(): String = targetIp
  override def getApiInstanceId(): String = apiInstanceId
  override def getConsentReferenceId(): String = consentReferenceId
  override def getCertificateTrust(): String = certificateTrust
  override def getCertificateTrustDetail(): String = certificateTrustDetail
  override def getAuthType(): String = authType
}

object MetricArchive extends MetricStore[MetricArchive] {

  override protected val tableName: String = "metricarchive"
  override protected val hasMetricId: Boolean = true

  override protected def dateOf(row: MetricArchive): Date = row.date

  override protected def build(id: Long, row: MetricColumns): MetricArchive =
    MetricArchive(id, row.metricId.getOrElse(0L), row.userId, row.url, row.date, row.duration,
      row.userName, row.appName, row.developerEmail, row.consumerId,
      row.implementedByPartialFunction, row.implementedInVersion, row.verb, row.httpCode,
      row.correlationId, row.responseBody, row.sourceIp, row.targetIp, row.apiInstanceId,
      row.consentReferenceId, row.certificateTrust, row.certificateTrustDetail, row.authType)

  def findByMetricId(metricId: Long): Box[MetricArchive] =
    query(fr"WHERE metricid = $metricId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * Writes the archive copy of one metric, replacing an existing copy of the same source row.
   *
   * Returns whether the row is there afterwards. Mapper's save returned false rather than throwing
   * on a failed insert, and the caller uses that to skip deleting the source row, so a failure has
   * to come back as false rather than as an exception.
   */
  def upsertByMetricId(metricId: Long, userId: String, url: String, date: Date, duration: Long,
                       userName: String, appName: String, developerEmail: String,
                       consumerId: String, implementedByPartialFunction: String,
                       implementedInVersion: String, verb: String, httpCode: Option[Int],
                       correlationId: String, responseBody: String, sourceIp: String,
                       targetIp: String, apiInstanceId: String, consentReferenceId: String,
                       certificateTrust: String, certificateTrustDetail: String,
                       authType: String): Boolean =
    tryo {
      DoobieUtil.runUpdate(
        sql"DELETE FROM metricarchive WHERE metricid = $metricId".update.run)
      DoobieUtil.runUpdate(
        sql"""INSERT INTO metricarchive
              (metricid, userid, url, date_c, duration, username, appname, developeremail,
               consumerid, implementedbypartialfunction, implementedinversion, verb, httpcode,
               correlationid, responsebody, sourceip, targetip, apiinstanceid,
               consent_reference_id, certificate_trust, certificate_trust_detail, auth_type)
              VALUES ($metricId, ${opt(userId)}, ${opt(url)}, ${timestamp(date)}, $duration,
               ${opt(userName)}, ${opt(appName)}, ${opt(developerEmail)}, ${opt(consumerId)},
               ${opt(implementedByPartialFunction)}, ${opt(implementedInVersion)}, ${opt(verb)},
               ${httpCode.getOrElse(0)}, ${opt(correlationId)}, ${opt(responseBody)},
               ${opt(sourceIp)}, ${opt(targetIp)}, ${opt(apiInstanceId)},
               ${opt(consentReferenceId)}, ${opt(certificateTrust)},
               ${opt(certificateTrustDetail)}, ${opt(authType)})"""
          .update.run)
      true
    }.getOrElse(false)
}

/** The columns both metric tables share, as read back from a row. */
case class MetricColumns(
  metricId: Option[Long],
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
  httpCode: Int,
  correlationId: String,
  responseBody: String,
  sourceIp: String,
  targetIp: String,
  apiInstanceId: String,
  consentReferenceId: String,
  certificateTrust: String,
  certificateTrustDetail: String,
  authType: String
)

/**
 * The reads and writes the live metrics table and its archive share.
 *
 * They have the same columns bar the archive's metricId, and both are read by the same filters, so
 * the query building lives here once rather than being written twice.
 */
abstract class MetricStore[A] {

  protected val tableName: String
  /** Only the archive keeps the id its row had in the live table. */
  protected val hasMetricId: Boolean = false
  protected def build(id: Long, row: MetricColumns): A

  private def table: Fragment = Fragment.const(tableName)

  // The live table selects a typed NULL in the metricId slot so both tables read through the same
  // row type.
  private lazy val selectColumns: Fragment =
    Fragment.const(
      List("id", if (hasMetricId) "metricid" else "CAST(NULL AS BIGINT)", "userid", "url",
        "date_c", "duration", "username", "appname", "developeremail", "consumerid",
        "implementedbypartialfunction", "implementedinversion", "verb", "httpcode",
        "correlationid", "responsebody", "sourceip", "targetip", "apiinstanceid",
        "consent_reference_id", "certificate_trust", "certificate_trust_detail", "auth_type")
        .mkString("SELECT ", ", ", " FROM " + tableName))

  // 21 or 22 columns, so the row is read as two nested tuples.
  private type RowHead = (Long, Option[Long], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[Long], Option[String], Option[String], Option[String],
    Option[String], Option[String])
  private type RowTail = (Option[String], Option[String], Option[Int], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String])
  private type Row = (RowHead, RowTail)

  /** A timestamp read back as a plain java.util.Date, which is what MappedDateTime handed out. */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): A = row match {
    case ((id, metricId, userId, url, date, duration, userName, appName, developerEmail,
           consumerId, implementedByPartialFunction),
          (implementedInVersion, verb, httpCode, correlationId, responseBody, sourceIp, targetIp,
           apiInstanceId, consentReferenceId, certificateTrust, certificateTrustDetail,
           authType)) =>
      build(id, MetricColumns(metricId, userId.orNull, url.orNull, readDate(date),
        // A NULL number reads back as 0, which is what MappedLong and MappedInt did.
        duration.getOrElse(0L), userName.orNull, appName.orNull, developerEmail.orNull,
        consumerId.orNull, implementedByPartialFunction.orNull, implementedInVersion.orNull,
        verb.orNull, httpCode.getOrElse(0), correlationId.orNull, responseBody.orNull,
        sourceIp.orNull, targetIp.orNull, apiInstanceId.orNull, consentReferenceId.orNull,
        certificateTrust.orNull, certificateTrustDetail.orNull, authType.orNull))
  }

  protected def query(condition: Fragment): List[A] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  protected def opt(value: String): Option[String] = Option(value)

  protected def timestamp(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  def findAll(params: MetricQuery): List[A] = {
    val filters = List(
      params.fromDate.map(d => fr"date_c >= ${timestamp(d)}"),
      params.toDate.map(d => fr"date_c <= ${timestamp(d)}"),
      params.consumerId.map(v => fr"consumerid = ${opt(v)}"),
      // A bank id is matched by the shape of the url rather than by a column of its own.
      params.bankId.map(v => fr"url LIKE ${opt(s"%banks/$v%")}"),
      params.userId.map(v => fr"userid = ${opt(v)}"),
      // An empty locked set must match nothing rather than drop the filter: it means the caller
      // has no user ids to see, not "no restriction". Dropping it is how the whole clause went
      // missing before -- OBPUserIds was simply not collected here, so GET /my/metrics returned
      // every user's rows with no error anywhere.
      params.userIds.map {
        case Nil => fr"1 = 0"
        case ids => Fragments.in(fr"userid", cats.data.NonEmptyList.fromListUnsafe(ids.distinct))
      },
      params.url.map(v => fr"url = ${opt(v)}"),
      params.appName.map(v => fr"appname = ${opt(v)}"),
      params.implementedInVersion.map(v => fr"implementedinversion = ${opt(v)}"),
      params.implementedByPartialFunction.map(v => fr"implementedbypartialfunction = ${opt(v)}"),
      params.verb.map(v => fr"verb = ${opt(v)}"),
      params.correlationId.map(v => fr"correlationid = ${opt(v)}"),
      params.durationGreaterThan.map(v => fr"duration > $v"),
      params.httpStatusCode.map(v => fr"httpcode = $v"),
      params.consentReferenceId.map(v => fr"consent_reference_id = ${opt(v)}"),
      params.certificateTrust.map(v => fr"certificate_trust = ${opt(v)}"),
      // "Anonymous" is the literal four-letter string "null" in the user id column, not SQL NULL.
      // Preserved: rows written for unauthenticated calls carry that string.
      params.anon.map {
        case true => fr"userid = ${Option("null")}"
        case false => fr"NOT (userid = ${Option("null")})"
      }
    ).flatten ++
      params.excludeAppNames.toList.flatten.map(name => fr"NOT (appname = ${opt(name)})")
    val where =
      if (filters.isEmpty) Fragment.empty
      else fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)
    val ordering = params.orderBy match {
      case Some((column, ascending)) =>
        fr"ORDER BY " ++ Fragment.const(column) ++ (if (ascending) fr"ASC" else fr"DESC")
      case None => Fragment.empty
    }
    val paging =
      params.limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        params.offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(where ++ ordering ++ paging)
  }

  /** The most recent metrics of one user, newest first. */
  def findNewestByUserId(userId: String, limit: Int): List[A] =
    query(fr"WHERE userid = ${opt(userId)} ORDER BY date_c DESC LIMIT $limit")

  /** The oldest and newest dates in the table, for the integrity report. */
  def oldestDate(): Option[Date] =
    query(fr"ORDER BY date_c ASC LIMIT 1").headOption.map(dateOf)

  def newestDate(): Option[Date] =
    query(fr"ORDER BY date_c DESC LIMIT 1").headOption.map(dateOf)

  protected def dateOf(row: A): Date

  /** Oldest first, for the archiver's candidate window. */
  def findOldestOnOrBefore(date: Date, limit: Int): List[A] =
    query(fr"WHERE date_c <= ${timestamp(date)} ORDER BY date_c ASC LIMIT $limit")

  def countOnOrBefore(date: Date): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM " ++ table ++ fr"WHERE date_c <= ${timestamp(date)}")
        .query[Long].unique)

  def count(): Long =
    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM " ++ table).query[Long].unique)

  def findByPrimaryKey(id: Long): Box[A] =
    query(fr"WHERE id = $id").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def deleteByPrimaryKey(id: Long): Boolean =
    DoobieUtil.runUpdate((fr"DELETE FROM " ++ table ++ fr"WHERE id = $id").update.run) > 0

  def deleteOnOrBefore(date: Date): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM " ++ table ++ fr"WHERE date_c <= ${timestamp(date)}").update.run)

  def insert(userId: String, url: String, date: Date, duration: Long, userName: String,
             appName: String, developerEmail: String, consumerId: String,
             implementedByPartialFunction: String, implementedInVersion: String, verb: String,
             httpCode: Int, correlationId: String, responseBody: String, sourceIp: String,
             targetIp: String, apiInstanceId: String, consentReferenceId: String,
             certificateTrust: String, certificateTrustDetail: String): Long =
    DoobieUtil.runUpdate(
      (fr"INSERT INTO " ++ table ++
        fr"""(userid, url, date_c, duration, username, appname, developeremail, consumerid,
              implementedbypartialfunction, implementedinversion, verb, httpcode, correlationid,
              responsebody, sourceip, targetip, apiinstanceid, consent_reference_id,
              certificate_trust, certificate_trust_detail)
             VALUES (${opt(userId)}, ${opt(url)}, ${timestamp(date)}, $duration, ${opt(userName)},
              ${opt(appName)}, ${opt(developerEmail)}, ${opt(consumerId)},
              ${opt(implementedByPartialFunction)}, ${opt(implementedInVersion)}, ${opt(verb)},
              $httpCode, ${opt(correlationId)}, ${opt(responseBody)}, ${opt(sourceIp)},
              ${opt(targetIp)}, ${opt(apiInstanceId)}, ${opt(consentReferenceId)},
              ${opt(certificateTrust)}, ${opt(certificateTrustDetail)})""")
        .update.withUniqueGeneratedKeys[Long]("id"))

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate((fr"DELETE FROM " ++ table).update.run)
    ()
  }
}
