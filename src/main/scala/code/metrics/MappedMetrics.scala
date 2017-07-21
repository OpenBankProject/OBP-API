package code.metrics

import java.util.Date

import code.bankconnectors.{OBPImplementedByPartialFunction, _}
import code.util.{UUIDString, MappedUUID}
import net.liftweb.mapper._

object MappedMetrics extends APIMetrics {

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

  override def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedMetric](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedMetric](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedMetric.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedMetric.date, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedMetric.date, Ascending)
          case OBPDescending => OrderBy(MappedMetric.date, Descending)
        }
    }
    // the optional variables:
    val toConsumerId = queryParams.collect {case OBPConsumerId(value) => By(MappedMetric.consumerId, value)}.headOption
    val toUserId = queryParams.collect {case OBPUserId(value) => By(MappedMetric.userId, value)}.headOption
    val toUrl = queryParams.collect {case OBPUrl(value) => By(MappedMetric.url, value)}.headOption
    val toAppName = queryParams.collect {case OBPAppName(value) => By(MappedMetric.appName, value)}.headOption
    val toImplementedInVersion = queryParams.collect {case OBPImplementedInVersion(value) => By(MappedMetric.implementedInVersion, value)}.headOption
    val toImplementedByPartialFunction = queryParams.collect {case OBPImplementedByPartialFunction(value) => By(MappedMetric.implementedByPartialFunction, value)}.headOption
    val toVerb = queryParams.collect {case OBPVerb(value) => By(MappedMetric.verb, value)}.headOption
    
    val optionalParams : Seq[QueryParam[MappedMetric]] = Seq(
      offset.toSeq, 
      fromDate.toSeq, 
      toDate.toSeq, 
      ordering,
      toConsumerId.toSeq, 
      toUserId.toSeq,
      toUrl.toSeq,
      toAppName.toSeq,
      toImplementedInVersion.toSeq,
      toImplementedByPartialFunction.toSeq,
      toVerb.toSeq,
      limit.toSeq
    ).flatten

    MappedMetric.findAll(optionalParams: _*)
  }

  override def bulkDeleteMetrics(): Boolean = {
    MappedMetric.bulkDelete_!!()
  }

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
  override def dbIndexes = super.dbIndexes
}
