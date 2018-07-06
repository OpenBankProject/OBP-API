package code.metrics

import java.sql.{PreparedStatement, Time, Timestamp}
import java.util.Date

import code.api.util.ErrorMessages._
import code.bankconnectors.{OBPImplementedByPartialFunction, _}
import code.util.Helper.MdcLoggable
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{Index, _}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable.List
import scala.concurrent.Future

object MappedMetrics extends APIMetrics with MdcLoggable{

  override def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String,correlationId: String): Unit = {
    MappedMetric.create
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
      .save
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
    val consumerId = queryParams.collect { case OBPConsumerId(value) => By(MappedMetric.consumerId, value) }.headOption
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
  
  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    val optionalParams = getQueryParams(queryParams)
    MappedMetric.findAll(optionalParams: _*)
  }
  
  override def getAllAggregateMetrics(queryParams: OBPUrlQueryParams): List[AggregateMetrics] = {

    // null,API-EXPLORER,null,null --> Set(null,API-EXPLORER)
    val excludeAppNames = queryParams.excludeAppNames.split(",").toSet
    val excludeImplementedByPartialfunctions = queryParams.excludeImplementedByPartialfunctions.split(",").toSet
    //
    def extendCurrentQuery (length: Int) ={
      // --> "?,?,"
      val a = for(i <- 1 to (length-1) ) yield {"?,"}
      //"?,?,--> "?,?,?"
      a.mkString("").concat("?")
    }

    val extendedExclueAppNameQueries = extendCurrentQuery(excludeAppNames.size)
    val extedndedExcludeImplementedByPartialfunctionsQueries = extendCurrentQuery(excludeImplementedByPartialfunctions.size)

    
    val dbQuery = 
      "SELECT count(*), avg(duration), min(duration), max(duration) "+ 
      "FROM mappedmetric "+   
      "WHERE date_c >= ? "+ 
      "AND date_c <= ? "+ 
      "AND (? or consumerid = ?) "+ 
      "AND (? or userid = ?) "+ 
      "AND (? or implementedbypartialfunction = ? ) "+ 
      "AND (? or implementedinversion = ?) "+ 
      "AND (? or url= ?) "+ 
      "And (? or appname = ?) "+ 
      "AND (? or verb = ? ) "+ 
      "AND (? or userid = 'null' ) " +  // mapping `S.param("anon")` anon == null, (if null ignore) , anon == true (return where user_id is null.) 
      "AND (? or userid != 'null' ) " +  // anon == false (return where user_id is not null.)
      "AND (? or url NOT LIKE ?) "+
      s"AND (? or appname not in ($extendedExclueAppNameQueries)) " +
      s"AND (? or implementedbypartialfunction not in ($extedndedExcludeImplementedByPartialfunctionsQueries)) "
    
    logger.debug(s"getAllAggregateMetrics.dbQuery = $dbQuery")
    /**
      * Example of a Tuple response
      * (List(count, avg, min, max),List(List(7503, 70.3398640543782487, 0, 9039)))
      * First value of the Tuple is a List of field names returned by SQL query.
      * Second value of the Tuple is a List of rows of the result returned by SQL query. Please note it's only one row.
      */
      
    def extendPrepareStement(startLine: Int, stmt:PreparedStatement, excludeFiledValues : Set[String]) = {
      for(i <- 0 until  excludeFiledValues.size) yield {
        stmt.setString(startLine+i, excludeFiledValues.toList(i))
      }
    }
    
    val (_, List(count :: avg :: min :: max :: _)) = DB.use(DefaultConnectionIdentifier)
    {
      conn =>
          DB.prepareStatement(dbQuery, conn)
          {
            stmt =>
              stmt.setTimestamp(1, new Timestamp(queryParams.startDate.getTime))
              stmt.setTimestamp(2, new Timestamp(queryParams.endDate.getTime))
              stmt.setBoolean(3, if (queryParams.consumerId=="true") true else false)
              stmt.setString(4, queryParams.consumerId)
              stmt.setBoolean(5, if (queryParams.userId=="true") true else false)
              stmt.setString(6, queryParams.userId)
              stmt.setBoolean(7, if (queryParams.implementedByPartialFunction=="true") true else false)
              stmt.setString(8, queryParams.implementedByPartialFunction)
              stmt.setBoolean(9, if (queryParams.implementedInVersion=="true") true else false)
              stmt.setString(10, queryParams.implementedInVersion)
              stmt.setBoolean(11, if (queryParams.url=="true") true else false)
              stmt.setString(12, queryParams.url)
              stmt.setBoolean(13, if (queryParams.appName=="true") true else false)
              stmt.setString(14,queryParams.appName)
              stmt.setBoolean(15, if (queryParams.verb=="true") true else false)
              stmt.setString(16, queryParams.verb)
              stmt.setBoolean(17, if (queryParams.anon=="true") false else true) // anon == true (return where user_id is null.) 
              stmt.setBoolean(18, if (queryParams.anon=="false") false  else true) // anon == false (return where user_id is not null.)
              stmt.setBoolean(19, if (queryParams.excludeUrlPattern=="true") true else false)
              stmt.setString(20, queryParams.excludeUrlPattern)
              stmt.setBoolean(21, if (queryParams.excludeAppNames=="true") true else false)
              extendPrepareStement(22, stmt, excludeAppNames)
              stmt.setBoolean(22+excludeAppNames.size, if (queryParams.excludeImplementedByPartialfunctions=="true") true else false)
              extendPrepareStement(22+excludeAppNames.size+1,stmt, excludeImplementedByPartialfunctions)
              DB.resultSetTo(stmt.executeQuery())
          }
    }
    

    val totalCount = if (count != null ) count.toInt else 0 
    val avgResponseTime = if (avg != null ) "%.2f".format(avg.toDouble).toDouble else 0
    val minResponseTime = if (min != null ) min.toDouble else 0
    val maxResponseTime = if (max != null ) max.toDouble else 0


    List(AggregateMetrics(totalCount, avgResponseTime, minResponseTime, maxResponseTime))
  }

  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }
  
  //This is tricky for now, we call it only in Actor. 
  //@RemotedataMetricsActor.scala see how this is used, return a box to the sender!
  def getTopApisBox(queryParams: OBPUrlDateQueryParam): Box[List[TopApi]] = {
    for{
       dbQuery <- Full("SELECT count(*), mappedmetric.implementedbypartialfunction, mappedmetric.implementedinversion " + 
                       "FROM mappedmetric " +
                       "WHERE (? or date_c >= ?) "+ 
                       "AND (? or date_c <= ?) "+
                       "GROUP BY mappedmetric.implementedbypartialfunction, mappedmetric.implementedinversion " +
                       "ORDER BY count(*) DESC")
       resultSet <- tryo(DB.use(DefaultConnectionIdentifier)
         {
          conn =>
              DB.prepareStatement(dbQuery, conn)
              {
                stmt =>
                  stmt.setBoolean(1, if (queryParams.startDate.isEmpty) true else false)
                  stmt.setTimestamp(2, if (queryParams.startDate.isEmpty) null else new Timestamp(queryParams.startDate.get.getTime))
                  stmt.setBoolean(3, if (queryParams.endDate.isEmpty) true else false)
                  stmt.setTimestamp(4, if (queryParams.endDate.isEmpty) null else new Timestamp(queryParams.endDate.get.getTime))
                  DB.resultSetTo(stmt.executeQuery())
                  
              }
         })?~! {logger.error(s"getTopApisBox.DB.runQuery(dbQuery) read database error. please this in database:  $dbQuery "); s"$UnknownError getTopApisBox.DB.runQuery(dbQuery) read database issue. "}
       
       topApis <- tryo(resultSet._2.map(
         a =>
           TopApi(
             if (a(0) != null) a(0).toInt else 0,
             if (a(1) != null) a(1).toString else "",
             if (a(2) != null) a(2).toString else ""))) ?~! {logger.error(s"getTopApisBox.create TopApi class error. Here is the result from database $resultSet ");s"$UnknownError getTopApisBox.create TopApi class error. "}
      
    } yield {
      topApis
    }
  }
  
  override def getTopApisFuture(queryParams: OBPUrlDateQueryParam): Future[Box[List[TopApi]]] = Future{getTopApisBox(queryParams)}
  
  //This is tricky for now, we call it only in Actor. 
  //@RemotedataMetricsActor.scala see how this is used, return a box to the sender!
  def getTopConsumersBox(queryParams: OBPUrlDateQueryParam): Box[List[TopConsumer]] = {
    for{
       dbQuery <- Full("SELECT count(*), consumer.consumerid, consumer.name " + 
                       "FROM consumer "+
                       "WHERE (? or createdat >= ?) "+ 
                       "AND (? or createdat <= ?) "+
                       "GROUP BY consumer.consumerid, consumer.name "+
                       "ORDER BY count(*) DESC ")
       
       
       
       resultSet <- tryo(DB.use(DefaultConnectionIdentifier)
         {
          conn =>
              DB.prepareStatement(dbQuery, conn)
              {
                stmt =>
                  stmt.setBoolean(1, if (queryParams.startDate.isEmpty) true else false)
                  stmt.setTimestamp(2, if (queryParams.startDate.isEmpty) null else new Timestamp(queryParams.startDate.get.getTime))
                  stmt.setBoolean(3, if (queryParams.endDate.isEmpty) true else false)
                  stmt.setTimestamp(4, if (queryParams.endDate.isEmpty) null else new Timestamp(queryParams.endDate.get.getTime))
                  DB.resultSetTo(stmt.executeQuery())
                  
              }
         })?~! {logger.error(s"getTopConsumersBox.DB.runQuery(dbQuery) read database error. please this in database:  $dbQuery "); s"$UnknownError getTopConsumersBox.DB.runQuery(dbQuery) read database issue. "}
       
       topApis <- tryo(resultSet._2.map(
         a =>
           TopConsumer(
             if (a(0) != null) a(0).toInt else 0,
             if (a(1) != null) a(1).toString else "", 
             if (a(2) != null) a(2).toString else ""))) ?~! {logger.error(s"getTopConsumersBox.create TopConsumer class error. Here is the result from database $resultSet ");s"$UnknownError getTopConsumersBox.create TopApi class error. "}
      
    } yield {
      topApis
    }
  }
  
  override def getTopConsumersFuture(queryParams: OBPUrlDateQueryParam): Future[Box[List[TopConsumer]]] = Future{getTopConsumersBox(queryParams: OBPUrlDateQueryParam)}
  

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
  object correlationId extends MappedUUID(this)


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
  override def getCorrelationId(): String = correlationId.get
}

object MappedMetric extends MappedMetric with LongKeyedMetaMapper[MappedMetric] {
  //override def dbIndexes = Index(userId) :: Index(url) :: Index(date) :: Index(userName) :: Index(appName) :: Index(developerEmail) :: super.dbIndexes
  override def dbIndexes = Index(date) :: super.dbIndexes
}
