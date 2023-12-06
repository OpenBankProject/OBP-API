package code.metrics

import java.sql.{PreparedStatement, Timestamp}
import java.util.Date
import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.APIUtil.generateUUID
import code.api.util._
import code.model.MappedConsumersProvider
import code.util.Helper.MdcLoggable
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.Box
import net.liftweb.db.DB
import net.liftweb.mapper.{Index, _}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable
import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.duration._

object MappedMetrics extends APIMetrics with MdcLoggable{

  val cachedAllMetrics = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getAllMetrics", "7").toInt
  val cachedAllAggregateMetrics = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getAllAggregateMetrics", "7").toInt
  val cachedTopApis = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getTopApis", "3600").toInt
  val cachedTopConsumers = APIUtil.getPropsValue(s"MappedMetrics.cache.ttl.seconds.getTopConsumers", "3600").toInt

  // If consumerId is Int, if consumerId is not Int, convert it to primary key.
  // Since version 3.1.0 we do not use a primary key externally. I.e. we use UUID instead of it as the value exposed to end users.
  private def consumerIdToPrimaryKey(consumerId: String): Option[String] = consumerId match {
    // Do NOT search by primary key at all
    case str if StringUtils.isBlank(str) => Option.empty[String] 
    // Search by primary key
    case str if str.matches("\\d+") => Some(str) 
    // Get consumer by UUID, extract a primary key and then search by the primary key
    // This can not be empty here, it need return the value back as the parameter 
    case str => MappedConsumersProvider.getConsumerByConsumerId(str).map(_.id.get.toString).toOption.orElse(Some(str)) 
  }

  override def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, httpCode: Option[Int], correlationId: String,
                          responseBody: String, sourceIp: String, targetIp: String): Unit = {
    val metric = MappedMetric.create
      .userId(userId)
      .url(url)
      .date(date)
      .duration(duration)
      .userName(userName)
      .appName(appName)
      .developerEmail(developerEmail)
      .consumerId(consumerId)
      .implementedByPartialFunction(implementedByPartialFunction)
      .implementedInVersion(implementedInVersion)
      .verb(verb)
      .correlationId(correlationId)
      .responseBody(responseBody)
      .sourceIp(sourceIp)
      .targetIp(targetIp)
      
    httpCode match {
      case Some(code) => metric.httpCode(code)
      case None =>
    }
    metric.save
  }
  override def saveMetricsArchive(primaryKey: Long, userId: String,
                                  url: String, date: Date, duration: Long, userName: String,
                                  appName: String, developerEmail: String, consumerId: String,
                                  implementedByPartialFunction: String, implementedInVersion: String,
                                  verb: String, httpCode: Option[Int], correlationId: String,
                                  responseBody: String, sourceIp: String, targetIp: String): Unit = {
    val metric = MetricArchive.find(By(MetricArchive.id, primaryKey)).getOrElse(MetricArchive.create)
    metric
      .metricId(primaryKey)
      .userId(userId)
      .url(url)
      .date(date)
      .duration(duration)
      .userName(userName)
      .appName(appName)
      .developerEmail(developerEmail)
      .consumerId(consumerId)
      .implementedByPartialFunction(implementedByPartialFunction)
      .implementedInVersion(implementedInVersion)
      .verb(verb)
      .correlationId(correlationId)
      .responseBody(responseBody)
      .sourceIp(sourceIp)
      .targetIp(targetIp)

    httpCode match {
      case Some(code) => metric.httpCode(code)
      case None =>
    }
    metric.save
  }

  private def trueOrFalse(condition: Boolean): String = if (condition) s"1=1" else s"0=1"
  private def falseOrTrue(condition: Boolean): String = if (condition) s"0=1" else s"1=1"
  
  private def sqlFriendly(value : Option[String]): String = {
    value match {
      case Some(value) => s"'$value'"
      case None => "null"
        
    }
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
 private def getQueryParams(queryParams: List[OBPQueryParam]) = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedMetric](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedMetric](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedMetric.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedMetric.date, date) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(field, dir) =>
        val direction = dir match {
          case OBPAscending => Ascending
          case OBPDescending => Descending
        }
        field match {
          case Some(s) if s == "user_id" => OrderBy(MappedMetric.userId, direction)
          case Some(s) if s == "user_name" => OrderBy(MappedMetric.userName, direction)
          case Some(s) if s == "developer_email" => OrderBy(MappedMetric.developerEmail, direction)
          case Some(s) if s == "app_name" => OrderBy(MappedMetric.appName, direction)
          case Some(s) if s == "url" => OrderBy(MappedMetric.url, direction)
          case Some(s) if s == "date" => OrderBy(MappedMetric.date, direction)
          case Some(s) if s == "consumer_id" => OrderBy(MappedMetric.consumerId, direction)
          case Some(s) if s == "verb" => OrderBy(MappedMetric.verb, direction)
          case Some(s) if s == "implemented_in_version" => OrderBy(MappedMetric.implementedInVersion, direction)
          case Some(s) if s == "implemented_by_partial_function" => OrderBy(MappedMetric.implementedByPartialFunction, direction)
          case Some(s) if s == "correlation_id" => OrderBy(MappedMetric.correlationId, direction)
          case Some(s) if s == "duration" => OrderBy(MappedMetric.duration, direction)
          case _ => OrderBy(MappedMetric.date, Descending)
        }
    }
    // he optional variables:
    val consumerId = queryParams.collect { case OBPConsumerId(value) => value}.headOption
      .flatMap(consumerIdToPrimaryKey)
      .map(By(MappedMetric.consumerId, _) )

    val bankId = queryParams.collect { case OBPBankId(value) => Like(MappedMetric.url, s"%banks/$value%") }.headOption
    val userId = queryParams.collect { case OBPUserId(value) => By(MappedMetric.userId, value) }.headOption
    val url = queryParams.collect { case OBPUrl(value) => By(MappedMetric.url, value) }.headOption
    val appName = queryParams.collect { case OBPAppName(value) => By(MappedMetric.appName, value) }.headOption
    val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => By(MappedMetric.implementedInVersion, value) }.headOption
    val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => By(MappedMetric.implementedByPartialFunction, value) }.headOption
    val verb = queryParams.collect { case OBPVerb(value) => By(MappedMetric.verb, value) }.headOption
    val correlationId = queryParams.collect { case OBPCorrelationId(value) => By(MappedMetric.correlationId, value) }.headOption
    val duration = queryParams.collect { case OBPDuration(value) => By(MappedMetric.duration, value) }.headOption
    val anon = queryParams.collect {
      case OBPAnon(true) => By(MappedMetric.userId, "null")
      case OBPAnon(false) => NotBy(MappedMetric.userId, "null")
    }.headOption
    val excludeAppNames = queryParams.collect { 
      case OBPExcludeAppNames(values) => 
        values.map(NotBy(MappedMetric.appName, _)) 
    }.headOption

    Seq(
      offset.toSeq,
      fromDate.toSeq,
      toDate.toSeq,
      ordering,
      consumerId.toSeq,
      userId.toSeq,
      bankId.toSeq,
      url.toSeq,
      appName.toSeq,
      implementedInVersion.toSeq,
      implementedByPartialFunction.toSeq,
      verb.toSeq,
      limit.toSeq,
      correlationId.toSeq,
      duration.toSeq,
      anon.toSeq,
      excludeAppNames.toSeq.flatten
    ).flatten
  }

  // TODO Cache this as long as fromDate and toDate are in the past (before now)
  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value field with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey { 
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedAllMetrics days){
          val optionalParams = getQueryParams(queryParams)
          MappedMetric.findAll(optionalParams: _*)
      }
    }
  }
  
  private def extendLikeQuery(params:  List[String], isLike: Boolean): String = {
    val isLikeQuery = if (isLike) s"" else s"NOT"
    
    if (params.length == 1)
      s"'${params.head}'"
    else
    {
      val sqlList: immutable.Seq[String] = for (i <- 1 to (params.length - 2)) yield
        {
          s" and url ${isLikeQuery} LIKE ('${params(i)}')"
        }
        
      val sqlSingleLine = if (sqlList.length>1)
        sqlList.reduce(_+_)
      else
        s""
        
      s"'${params.head}')"+ sqlSingleLine + s" and url  ${isLikeQuery} LIKE ('${params.last}'"
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
  

  // TODO Cache this as long as fromDate and toDate are in the past (before now)
  def getAllAggregateMetricsBox(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Box[List[AggregateMetrics]] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value field with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey { Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedAllAggregateMetrics days){
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption.flatMap(consumerIdToPrimaryKey)
      val userId = queryParams.collect { case OBPUserId(value) => value }.headOption
      val url = queryParams.collect { case OBPUrl(value) => value }.headOption
      val appName = queryParams.collect { case OBPAppName(value) => value }.headOption
      val excludeAppNames = queryParams.collect { case OBPExcludeAppNames(value) => value }.headOption
      val includeAppNames = queryParams.collect { case OBPIncludeAppNames(value) => value }.headOption
      val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption
      val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption
      val verb = queryParams.collect { case OBPVerb(value) => value }.headOption
      val anon = queryParams.collect { case OBPAnon(value) => value }.headOption
      val correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption
      val duration = queryParams.collect { case OBPDuration(value) => value }.headOption
      val excludeUrlPatterns = queryParams.collect { case OBPExcludeUrlPatterns(value) => value }.headOption
      val includeUrlPatterns = queryParams.collect { case OBPIncludeUrlPatterns(value) => value }.headOption
      val excludeImplementedByPartialFunctions = queryParams.collect { case OBPExcludeImplementedByPartialFunctions(value) => value }.headOption
      val includeImplementedByPartialFunctions = queryParams.collect { case OBPIncludeImplementedByPartialFunctions(value) => value }.headOption

      val excludeUrlPatternsList= excludeUrlPatterns.getOrElse(List(""))
      val excludeAppNamesList = excludeAppNames.getOrElse(List("")).map(i => s"'$i'").mkString(",")
      val excludeImplementedByPartialFunctionsList = 
        excludeImplementedByPartialFunctions.getOrElse(List("")).map(i => s"'$i'").mkString(",")

      val excludeUrlPatternsQueries = extendLikeQuery(excludeUrlPatternsList, false)
      
      val includeUrlPatternsList= includeUrlPatterns.getOrElse(List(""))
      val includeAppNamesList = includeAppNames.getOrElse(List("")).map(i => s"'$i'").mkString(",")
      val includeImplementedByPartialFunctionsList = 
        includeImplementedByPartialFunctions.getOrElse(List("")).map(i => s"'$i'").mkString(",")

      val includeUrlPatternsQueries = extendLikeQuery(includeUrlPatternsList, true)
      val includeUrlPatternsQueriesSql = s"$includeUrlPatternsQueries" 
      
      val result = {
        val sqlQuery = if(isNewVersion) // in the version, we use includeXxx instead of excludeXxx, the performance should be better. 
          s"""SELECT count(*), avg(duration), min(duration), max(duration)  
              FROM metric
              WHERE date_c >= '${new Timestamp(fromDate.get.getTime)}' 
              AND date_c <= '${new Timestamp(toDate.get.getTime)}'
              AND (${trueOrFalse(consumerId.isEmpty)} or consumerid = ${sqlFriendly(consumerId)})
              AND (${trueOrFalse(userId.isEmpty)} or userid = ${sqlFriendly(userId)})
              AND (${trueOrFalse(implementedByPartialFunction.isEmpty)} or implementedbypartialfunction = ${sqlFriendly(implementedByPartialFunction)})
              AND (${trueOrFalse(implementedInVersion.isEmpty)} or implementedinversion = ${sqlFriendly(implementedInVersion)})
              AND (${trueOrFalse(url.isEmpty)} or url = ${sqlFriendly(url)})
              AND (${trueOrFalse(appName.isEmpty)} or appname = ${sqlFriendly(appName)})
              AND (${trueOrFalse(verb.isEmpty)} or verb = ${sqlFriendly(verb)})
              AND (${falseOrTrue(anon.isDefined && anon.equals(Some(true)))} or userid = 'null')
              AND (${falseOrTrue(anon.isDefined && anon.equals(Some(false)))} or userid != 'null') 
              AND (${trueOrFalse(correlationId.isEmpty)} or correlationId = ${sqlFriendly(correlationId)})
              AND (${trueOrFalse(includeUrlPatterns.isEmpty) } or (url LIKE ($includeUrlPatternsQueriesSql)))
              AND (${trueOrFalse(includeAppNames.isEmpty) } or (appname in ($includeAppNamesList)))
              AND (${trueOrFalse(includeImplementedByPartialFunctions.isEmpty) } or implementedbypartialfunction in ($includeImplementedByPartialFunctionsList))
              """.stripMargin
        else
          s"""SELECT count(*), avg(duration), min(duration), max(duration)  
            FROM metric
            WHERE date_c >= '${new Timestamp(fromDate.get.getTime)}' 
            AND date_c <= '${new Timestamp(toDate.get.getTime)}'
            AND (${trueOrFalse(consumerId.isEmpty)} or consumerid = ${sqlFriendly(consumerId)})
            AND (${trueOrFalse(userId.isEmpty)} or userid = ${sqlFriendly(userId)})
            AND (${trueOrFalse(implementedByPartialFunction.isEmpty)} or implementedbypartialfunction = ${sqlFriendly(implementedByPartialFunction)})
            AND (${trueOrFalse(implementedInVersion.isEmpty)} or implementedinversion = ${sqlFriendly(implementedInVersion)})
            AND (${trueOrFalse(url.isEmpty)} or url = ${sqlFriendly(url)})
            AND (${trueOrFalse(appName.isEmpty)} or appname = ${sqlFriendly(appName)})
            AND (${trueOrFalse(verb.isEmpty)} or verb = ${sqlFriendly(verb)})
            AND (${falseOrTrue(anon.isDefined && anon.equals(Some(true)))} or userid = 'null')
            AND (${falseOrTrue(anon.isDefined && anon.equals(Some(false)))} or userid != 'null')
            AND (${trueOrFalse(correlationId.isEmpty)} or correlationId = ${sqlFriendly(correlationId)})
            AND (${trueOrFalse(excludeUrlPatterns.isEmpty) } or (url NOT LIKE ($excludeUrlPatternsQueries)))
            AND (${trueOrFalse(excludeAppNames.isEmpty) } or appname not in ($excludeAppNamesList))
            AND (${trueOrFalse(excludeImplementedByPartialFunctions.isEmpty) } or implementedbypartialfunction not in ($excludeImplementedByPartialFunctionsList))
            """.stripMargin
        val (_, rows) = DB.runQuery(sqlQuery, List())
        logger.debug("code.metrics.MappedMetrics.getAllAggregateMetricsBox.sqlQuery --:  " + sqlQuery)
        val sqlResult = rows.map(
              rs => // Map result to case class
                AggregateMetrics(
                  tryo(rs(0).toInt).getOrElse(0),
                  tryo("%.2f".format(rs(1).toDouble).toDouble).getOrElse(0),
                  tryo(rs(2).toDouble).getOrElse(0),
                  tryo(rs(3).toDouble).getOrElse(0)
                )
        )
        logger.debug("code.metrics.MappedMetrics.getAllAggregateMetricsBox.sqlResult --:  " + sqlResult)
        sqlResult
      }
      tryo(result)
    }}
  }
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam], isNewVersion: Boolean): Future[Box[List[AggregateMetrics]]] = Future{
    getAllAggregateMetricsBox(queryParams: List[OBPQueryParam], isNewVersion)
  }
  
  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }

  // TODO Cache this as long as fromDate and toDate are in the past (before now)
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] = Future{
  /**                                                                                        
  * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUU
  * is just a temporary value field with UUID values in order to prevent any ambiguity.
  * The real value will be assigned by Macro during compile time at this line of a code:   
  * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/t
  */                                                                                       
  var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)       
  CacheKeyFromArguments.buildCacheKey {Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedTopApis seconds){   
    {
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption.flatMap(consumerIdToPrimaryKey)
      val userId = queryParams.collect { case OBPUserId(value) => value }.headOption
      val url = queryParams.collect { case OBPUrl(value) => value }.headOption
      val appName = queryParams.collect { case OBPAppName(value) => value }.headOption
      val excludeAppNames: Option[List[String]] = queryParams.collect { case OBPExcludeAppNames(value) => value }.headOption
      val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption
      val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption
      val verb = queryParams.collect { case OBPVerb(value) => value }.headOption
      val anon = queryParams.collect { case OBPAnon(value) => value }.headOption
      val correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption
      val duration = queryParams.collect { case OBPDuration(value) => value }.headOption
      val excludeUrlPatterns = queryParams.collect { case OBPExcludeUrlPatterns(value) => value }.headOption
      val excludeImplementedByPartialFunctions = queryParams.collect { case OBPExcludeImplementedByPartialFunctions(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(10)
      
      val excludeUrlPatternsList= excludeUrlPatterns.getOrElse(List(""))
      val excludeAppNamesNumberList = excludeAppNames.getOrElse(List("")).map(i => s"'$i'").mkString(",")
      val excludeImplementedByPartialFunctionsNumberList = 
        excludeImplementedByPartialFunctions.getOrElse(List("")).map(i => s"'$i'").mkString(",")

      val excludeUrlPatternsQueries: String = extendLikeQuery(excludeUrlPatternsList, false)
      
      val (dbUrl, _, _) = DBUtil.getDbConnectionParameters

      val result: List[TopApi] = {
        // MS SQL server has the specific syntax for limiting number of rows
        val msSqlLimit = if (dbUrl.contains("sqlserver")) s"TOP ($limit)" else s""
        // TODO Make it work in case of Oracle database
        val otherDbLimit = if (dbUrl.contains("sqlserver")) s"" else s"LIMIT $limit"
        val sqlQuery: String =
          s"""SELECT ${msSqlLimit} count(*), metric.implementedbypartialfunction, metric.implementedinversion 
                FROM metric 
                WHERE 
                date_c >= '${new Timestamp(fromDate.get.getTime)}' AND
                date_c <= '${new Timestamp(toDate.get.getTime)}'
                AND (${trueOrFalse(consumerId.isEmpty)} or consumerid = ${consumerId.getOrElse("null")})
                AND (${trueOrFalse(userId.isEmpty)} or userid = ${userId.getOrElse("null")})
                AND (${trueOrFalse(implementedByPartialFunction.isEmpty)} or implementedbypartialfunction = ${implementedByPartialFunction.getOrElse("null")})
                AND (${trueOrFalse(implementedInVersion.isEmpty)} or implementedinversion = ${implementedInVersion.getOrElse("null")})
                AND (${trueOrFalse(url.isEmpty)} or url = ${url.getOrElse("null")})
                AND (${trueOrFalse(appName.isEmpty)} or appname = ${appName.getOrElse("null")})
                AND (${trueOrFalse(verb.isEmpty)} or verb = ${verb.getOrElse("null")})
                AND (${falseOrTrue(anon.isDefined && anon.equals(Some(true)))} or userid = null) 
                AND (${falseOrTrue(anon.isDefined && anon.equals(Some(false)))} or userid != null) 
                AND (${trueOrFalse(excludeUrlPatterns.isEmpty)} or (url NOT LIKE ($excludeUrlPatternsQueries)))
                AND (${trueOrFalse(excludeAppNames.isEmpty)} or appname not in ($excludeAppNamesNumberList))
                AND (${trueOrFalse(excludeImplementedByPartialFunctions.isEmpty)} or implementedbypartialfunction not in ($excludeImplementedByPartialFunctionsNumberList))
                GROUP BY metric.implementedbypartialfunction, metric.implementedinversion 
                ORDER BY count(*) DESC
                ${otherDbLimit}
                """.stripMargin

        val (_, rows) = DB.runQuery(sqlQuery, List())
        val sqlResult =
          rows.map { rs => // Map result to case class
            TopApi(
              rs(0).toInt,
              rs(1),
              rs(2)
            )
          }
        sqlResult
      }
      tryo(result)
    }}
  }}

  // TODO Cache this as long as fromDate and toDate are in the past (before now)
  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]] = Future {
  /**                                                                                        
  * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUU
  * is just a temporary value field with UUID values in order to prevent any ambiguity.
  * The real value will be assigned by Macro during compile time at this line of a code:   
  * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/t
  */                                                                                       
  var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)       
  CacheKeyFromArguments.buildCacheKey {Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedTopConsumers seconds){   
  
      val fromDate = queryParams.collect { case OBPFromDate(value) => value }.headOption
      val toDate = queryParams.collect { case OBPToDate(value) => value }.headOption
      val consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption.flatMap(consumerIdToPrimaryKey)
      val userId = queryParams.collect { case OBPUserId(value) => value }.headOption
      val url = queryParams.collect { case OBPUrl(value) => value }.headOption
      val appName = queryParams.collect { case OBPAppName(value) => value }.headOption
      val excludeAppNames = queryParams.collect { case OBPExcludeAppNames(value) => value }.headOption
      val implementedByPartialFunction = queryParams.collect { case OBPImplementedByPartialFunction(value) => value }.headOption
      val implementedInVersion = queryParams.collect { case OBPImplementedInVersion(value) => value }.headOption
      val verb = queryParams.collect { case OBPVerb(value) => value }.headOption
      val anon = queryParams.collect { case OBPAnon(value) => value }.headOption
      val correlationId = queryParams.collect { case OBPCorrelationId(value) => value }.headOption
      val duration = queryParams.collect { case OBPDuration(value) => value }.headOption
      val excludeUrlPatterns = queryParams.collect { case OBPExcludeUrlPatterns(value) => value }.headOption
      val excludeImplementedByPartialFunctions = queryParams.collect { case OBPExcludeImplementedByPartialFunctions(value) => value }.headOption
      val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse("500")

      val excludeUrlPatternsList = excludeUrlPatterns.getOrElse(List(""))
      val excludeAppNamesList = excludeAppNames.getOrElse(List("")).map(i => s"'$i'").mkString(",")
      val excludeImplementedByPartialFunctionsList =
        excludeImplementedByPartialFunctions.getOrElse(List("")).map(i => s"'$i'").mkString(",")

      val excludeUrlPatternsQueries: String = extendLikeQuery(excludeUrlPatternsList, false)

      val (dbUrl, _, _) = DBUtil.getDbConnectionParameters

      // MS SQL server has the specific syntax for limiting number of rows
      val msSqlLimit = if (dbUrl.contains("sqlserver")) s"TOP ($limit)" else s""
      // TODO Make it work in case of Oracle database
      val otherDbLimit: String = if (dbUrl.contains("sqlserver")) s"" else s"LIMIT $limit"

      val result: List[TopConsumer] = {
        val sqlQuery =
          s"""SELECT ${msSqlLimit} count(*) as count, consumer.id as consumerprimaryid, metric.appname as appname, 
                consumer.developeremail as email, consumer.consumerid as consumerid  
                FROM metric, consumer 
                WHERE metric.appname = consumer.name  
                AND date_c >= '${new Timestamp(fromDate.get.getTime)}'
                AND date_c <= '${new Timestamp(toDate.get.getTime)}'
                AND (${trueOrFalse(consumerId.isEmpty)} or consumer.consumerid = ${sqlFriendly(consumerId)})
                AND (${trueOrFalse(userId.isEmpty)} or userid = ${sqlFriendly(userId)})
                AND (${trueOrFalse(implementedByPartialFunction.isEmpty)} or implementedbypartialfunction = ${sqlFriendly(implementedByPartialFunction)})
                AND (${trueOrFalse(implementedInVersion.isEmpty)} or implementedinversion = ${sqlFriendly(implementedInVersion)})
                AND (${trueOrFalse(url.isEmpty)} or url = ${sqlFriendly(url)})
                AND (${trueOrFalse(appName.isEmpty)} or appname = ${sqlFriendly(appName)})
                AND (${trueOrFalse(verb.isEmpty)} or verb = ${sqlFriendly(verb)})
                AND (${falseOrTrue(anon.isDefined && anon.equals(Some(true)))} or userid = null) 
                AND (${falseOrTrue(anon.isDefined && anon.equals(Some(false)))} or userid != null) 
                AND (${trueOrFalse(excludeUrlPatterns.isEmpty) } or (url NOT LIKE ($excludeUrlPatternsQueries)))
                AND (${trueOrFalse(excludeAppNames.isEmpty) } or appname not in ($excludeAppNamesList))
                AND (${trueOrFalse(excludeImplementedByPartialFunctions.isEmpty) } or implementedbypartialfunction not in ($excludeImplementedByPartialFunctionsList))
                GROUP BY appname,	consumer.developeremail, consumer.id,	consumer.consumerid
                ORDER BY count DESC
                ${otherDbLimit}
                """.stripMargin
        val (_, rows) = DB.runQuery(sqlQuery, List())
        val sqlResult =
          rows.map { rs => // Map result to case class
            TopConsumer(
              rs(0).toInt,
              rs(4),
              rs(2),
              rs(3)
            )
          }
        sqlResult
      }
      tryo(result)
    }
  }}

}

class MappedMetric extends APIMetric with LongKeyedMapper[MappedMetric] with IdPK {

  override def getSingleton = MappedMetric

  object userId extends UUIDString(this)
  object url extends MappedString(this, 2000) // TODO Introduce / use class for Mapped URLs
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)
  object userName extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert
  object appName extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert
  object developerEmail extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert

  //The consumerId, Foreign key to Consumer not key
  object consumerId extends UUIDString(this)
  //name of the Scala Partial Function being used for the endpoint
  object implementedByPartialFunction  extends MappedString(this, 128)
  //name of version where the call is implemented) -- S.request.get.view
  object implementedInVersion  extends MappedString(this, 16)
  //(GET, POST etc.) --S.request.get.requestType
  object verb extends MappedString(this, 16)
  object httpCode extends MappedInt(this)
  object correlationId extends MappedString(this, 256) {
    override def dbNotNull_? = true
    override def defaultValue = generateUUID()
  }
  object responseBody extends MappedText(this)
  object sourceIp extends MappedString(this, 64)
  object targetIp extends MappedString(this, 64)

  override def getMetricId(): Long = id.get
  override def getUrl(): String = url.get
  override def getDate(): Date = date.get
  override def getDuration(): Long = duration.get
  override def getUserId(): String = userId.get
  override def getUserName(): String = userName.get
  override def getAppName(): String = appName.get
  override def getDeveloperEmail(): String = developerEmail.get
  override def getConsumerId(): String = consumerId.get
  override def getImplementedByPartialFunction(): String = implementedByPartialFunction.get
  override def getImplementedInVersion(): String = implementedInVersion.get
  override def getVerb(): String = verb.get
  override def getHttpCode(): Int = httpCode.get
  override def getCorrelationId(): String = correlationId.get
  override def getResponseBody(): String = responseBody.get
  override def getSourceIp(): String = sourceIp.get
  override def getTargetIp(): String = targetIp.get
}

object MappedMetric extends MappedMetric with LongKeyedMetaMapper[MappedMetric] {
  // Please note that the old table name was "MappedMetric"
  // Renaming implications:
  //   - at an existing sandbox the table "MappedMetric" still exists with rows until this change is deployed at it
  //     and new rows are stored in the table "Metric"      
  //   - at a fresh sandbox there is no the table "MappedMetric", only "Metric" is present
  override def dbTableName = "Metric" // define the DB table name
  override def dbIndexes = Index(date) :: Index(consumerId) :: super.dbIndexes
}


class MetricArchive extends APIMetric with LongKeyedMapper[MetricArchive] with IdPK {
  override def getSingleton = MetricArchive

  object metricId extends MappedLong(this)
  object userId extends UUIDString(this)
  object url extends MappedString(this, 2000) // TODO Introduce / use class for Mapped URLs
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)
  object userName extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert
  object appName extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert
  object developerEmail extends MappedString(this, 64) // TODO constrain source value length / truncate value on insert

  //The consumerId, Foreign key to Consumer not key
  object consumerId extends UUIDString(this)
  //name of the Scala Partial Function being used for the endpoint
  object implementedByPartialFunction  extends MappedString(this, 128)
  //name of version where the call is implemented) -- S.request.get.view
  object implementedInVersion  extends MappedString(this, 16)
  //(GET, POST etc.) --S.request.get.requestType
  object verb extends MappedString(this, 16)
  object httpCode extends MappedInt(this)
  object correlationId extends MappedUUID(this){
    override def dbNotNull_? = true
  }
  object responseBody extends MappedText(this)
  object sourceIp extends MappedString(this, 64)
  object targetIp extends MappedString(this, 64)


  override def getMetricId(): Long = metricId.get
  override def getUrl(): String = url.get
  override def getDate(): Date = date.get
  override def getDuration(): Long = duration.get
  override def getUserId(): String = userId.get
  override def getUserName(): String = userName.get
  override def getAppName(): String = appName.get
  override def getDeveloperEmail(): String = developerEmail.get
  override def getConsumerId(): String = consumerId.get
  override def getImplementedByPartialFunction(): String = implementedByPartialFunction.get
  override def getImplementedInVersion(): String = implementedInVersion.get
  override def getVerb(): String = verb.get
  override def getHttpCode(): Int = httpCode.get
  override def getCorrelationId(): String = correlationId.get
  override def getResponseBody(): String = responseBody.get
  override def getSourceIp(): String = sourceIp.get
  override def getTargetIp(): String = targetIp.get
}
object MetricArchive extends MetricArchive with LongKeyedMetaMapper[MetricArchive] {
  override def dbIndexes = 
    Index(userId) :: Index(consumerId) :: Index(url) :: Index(date) :: Index(userName) :: 
      Index(appName) :: Index(developerEmail) :: super.dbIndexes
}