package code.metrics

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie._
import doobie.implicits._
import code.api.util.{DBUtil, DoobieUtil}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

/**
 * Doobie-based query implementations for metrics.
 *
 * These queries replace the raw SQL queries in MappedMetrics that were using
 * Lift's DB.runQuery (via DBUtil.runQuery). Doobie provides:
 * - Type-safe query results
 * - Type-safe parameters (no SQL injection risk)
 * - Proper JDBC type handling for all databases (including SQL Server NVARCHAR)
 *
 * Usage:
 * {{{
 * val result: List[TopApi] = DoobieMetricsQueries.getTopApis(fromDate, toDate, limit, filters)
 * }}}
 */
object DoobieMetricsQueries {

  /**
   * Get aggregate metrics (count, avg, min, max duration) for the given time range and filters.
   *
   * @param fromDate Start date for the query
   * @param toDate End date for the query
   * @param filters Filter options (consumerId, userId, appName, verb, etc.)
   * @param isNewVersion Whether to use include patterns (true) or exclude patterns (false)
   * @return List of AggregateMetrics (typically a single element)
   */
  def getAggregateMetrics(
    fromDate: Date,
    toDate: Date,
    filters: MetricsQueryFilters,
    isNewVersion: Boolean
  ): List[AggregateMetrics] = {
    val query = buildAggregateMetricsQuery(fromDate, toDate, filters, isNewVersion)
    DoobieUtil.runQuery(query)
  }

  def getAggregateMetricsFuture(
    fromDate: Date,
    toDate: Date,
    filters: MetricsQueryFilters,
    isNewVersion: Boolean
  )(implicit ec: ExecutionContext): Future[List[AggregateMetrics]] = {
    Future {
      getAggregateMetrics(fromDate, toDate, filters, isNewVersion)
    }
  }

  private def buildAggregateMetricsQuery(
    fromDate: Date,
    toDate: Date,
    filters: MetricsQueryFilters,
    isNewVersion: Boolean
  ): ConnectionIO[List[AggregateMetrics]] = {
    // Convert dates to SQL timestamps
    val fromTs = new java.sql.Timestamp(fromDate.getTime)
    val toTs = new java.sql.Timestamp(toDate.getTime)

    // Build dynamic WHERE conditions
    val baseQuery = fr"""
      SELECT count(*), avg(duration), min(duration), max(duration)
      FROM metric
      WHERE date_c >= $fromTs
        AND date_c <= $toTs
    """

    val conditions = buildFilterConditions(filters, isNewVersion)
    val fullQuery = baseQuery ++ conditions

    fullQuery.query[(Long, Option[Double], Option[Double], Option[Double])].to[List].map { rows =>
      rows.map { case (count, avgOpt, minOpt, maxOpt) =>
        AggregateMetrics(
          count.toInt,
          avgOpt.map(d => BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble).getOrElse(0.0),
          minOpt.getOrElse(0.0),
          maxOpt.getOrElse(0.0)
        )
      }
    }
  }

  /**
   * Get top APIs by call count for the given time range.
   *
   * @param fromDate Start date for the query
   * @param toDate End date for the query
   * @param limit Maximum number of results
   * @param filters Filter options
   * @return List of TopApi sorted by count descending
   */
  def getTopApis(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  ): List[TopApi] = {
    val query = buildTopApisQuery(fromDate, toDate, limit, filters)
    DoobieUtil.runQuery(query)
  }

  def getTopApisFuture(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  )(implicit ec: ExecutionContext): Future[List[TopApi]] = {
    Future {
      getTopApis(fromDate, toDate, limit, filters)
    }
  }

  private def buildTopApisQuery(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  ): ConnectionIO[List[TopApi]] = {
    val fromTs = new java.sql.Timestamp(fromDate.getTime)
    val toTs = new java.sql.Timestamp(toDate.getTime)

    val isSqlServer = DBUtil.isSqlServer

    // SQL Server uses TOP, others use LIMIT
    val baseQuery = if (isSqlServer) {
      fr"""
        SELECT TOP($limit) count(*), metric.implementedbypartialfunction, metric.implementedinversion
        FROM metric
        WHERE date_c >= $fromTs
          AND date_c <= $toTs
      """
    } else {
      fr"""
        SELECT count(*), metric.implementedbypartialfunction, metric.implementedinversion
        FROM metric
        WHERE date_c >= $fromTs
          AND date_c <= $toTs
      """
    }

    val conditions = buildFilterConditions(filters, isNewVersion = false)

    val groupAndOrder = fr"""
      GROUP BY metric.implementedbypartialfunction, metric.implementedinversion
      ORDER BY count(*) DESC
    """

    val limitClause = if (isSqlServer) fr"" else fr"LIMIT $limit"

    val fullQuery = baseQuery ++ conditions ++ groupAndOrder ++ limitClause

    fullQuery.query[(Long, String, String)].to[List].map { rows =>
      rows.map { case (count, partialFunction, version) =>
        TopApi(count.toInt, partialFunction, version)
      }
    }
  }

  /**
   * Get top consumers by API call count for the given time range.
   *
   * @param fromDate Start date for the query
   * @param toDate End date for the query
   * @param limit Maximum number of results
   * @param filters Filter options
   * @return List of TopConsumer sorted by count descending
   */
  def getTopConsumers(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  ): List[TopConsumer] = {
    val query = buildTopConsumersQuery(fromDate, toDate, limit, filters)
    DoobieUtil.runQuery(query)
  }

  def getTopConsumersFuture(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  )(implicit ec: ExecutionContext): Future[List[TopConsumer]] = {
    Future {
      getTopConsumers(fromDate, toDate, limit, filters)
    }
  }

  private def buildTopConsumersQuery(
    fromDate: Date,
    toDate: Date,
    limit: Int,
    filters: MetricsQueryFilters
  ): ConnectionIO[List[TopConsumer]] = {
    val fromTs = new java.sql.Timestamp(fromDate.getTime)
    val toTs = new java.sql.Timestamp(toDate.getTime)

    val isSqlServer = DBUtil.isSqlServer

    // SQL Server uses TOP, others use LIMIT
    val baseQuery = if (isSqlServer) {
      fr"""
        SELECT TOP($limit) count(*) as count, consumer.id as consumerprimaryid, metric.appname as appname,
               consumer.developeremail as email, consumer.consumerid as consumerid
        FROM metric, consumer
        WHERE metric.appname = consumer.name
          AND date_c >= $fromTs
          AND date_c <= $toTs
      """
    } else {
      fr"""
        SELECT count(*) as count, consumer.id as consumerprimaryid, metric.appname as appname,
               consumer.developeremail as email, consumer.consumerid as consumerid
        FROM metric, consumer
        WHERE metric.appname = consumer.name
          AND date_c >= $fromTs
          AND date_c <= $toTs
      """
    }

    val conditions = buildFilterConditions(filters, isNewVersion = false)

    val groupAndOrder = fr"""
      GROUP BY appname, consumer.developeremail, consumer.id, consumer.consumerid
      ORDER BY count DESC
    """

    val limitClause = if (isSqlServer) fr"" else fr"LIMIT $limit"

    val fullQuery = baseQuery ++ conditions ++ groupAndOrder ++ limitClause

    fullQuery.query[(Long, Long, String, String, String)].to[List].map { rows =>
      rows.map { case (count, _, appName, email, consumerId) =>
        TopConsumer(count.toInt, consumerId, appName, email)
      }
    }
  }

  /**
   * Build WHERE clause conditions from filter options.
   */
  private def buildFilterConditions(filters: MetricsQueryFilters, isNewVersion: Boolean): Fragment = {
    val conditions = List(
      filters.consumerId.map(v => fr"AND consumerid = $v"),
      filters.userId.map(v => fr"AND userid = $v"),
      filters.implementedByPartialFunction.map(v => fr"AND implementedbypartialfunction = $v"),
      filters.implementedInVersion.map(v => fr"AND implementedinversion = $v"),
      filters.url.map(v => fr"AND url = $v"),
      filters.appName.map(v => fr"AND appname = $v"),
      filters.verb.map(v => fr"AND verb = $v"),
      filters.correlationId.map(v => fr"AND correlationid = $v"),
      filters.httpStatusCode.map(v => fr"AND httpcode = $v"),
      filters.anon.flatMap {
        case true => Some(fr"AND userid = 'null'")
        case false => Some(fr"AND userid != 'null'")
      }
    ).flatten

    conditions.foldLeft(fr"")(_ ++ _)
  }
}

/**
 * Filter options for metrics queries.
 */
case class MetricsQueryFilters(
  consumerId: Option[String] = None,
  userId: Option[String] = None,
  url: Option[String] = None,
  appName: Option[String] = None,
  implementedByPartialFunction: Option[String] = None,
  implementedInVersion: Option[String] = None,
  verb: Option[String] = None,
  anon: Option[Boolean] = None,
  correlationId: Option[String] = None,
  httpStatusCode: Option[Int] = None,
  excludeAppNames: Option[List[String]] = None,
  includeAppNames: Option[List[String]] = None,
  excludeUrlPatterns: Option[List[String]] = None,
  includeUrlPatterns: Option[List[String]] = None,
  excludeImplementedByPartialFunctions: Option[List[String]] = None,
  includeImplementedByPartialFunctions: Option[List[String]] = None
)
