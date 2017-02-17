package code.metrics

import java.util.Date

import code.model.Consumer
import code.util.DefaultStringField
import net.liftweb.http.S
import net.liftweb.mapper._

object MappedMetrics extends APIMetrics {

  override def saveMetric(userId: String, url: String, date: Date, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String): Unit = {
    MappedMetric.create
      .userId(userId)
      .url(url)
      .date(date)
      .userName(userName)
      .appName(appName)
      .developerEmail(developerEmail)
      .consumerId(consumerId)
      .implementedByPartialFunction(implementedByPartialFunction)
      .implementedInVersion(implementedInVersion)
      .verb(verb)
      .save
  }

  override def getAllGroupedByUserId(): Map[String, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(_.getUserId)
  }

  override def getAllGroupedByDay(): Map[Date, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(APIMetrics.getMetricDay)
  }

  override def getAllGroupedByUrl(): Map[String, List[APIMetric]] = {
    //TODO: do this all at the db level using an actual group by query
    MappedMetric.findAll.groupBy(_.getUrl())
  }

  override def getAllMetrics(): List[APIMetric] = {
    MappedMetric.findAll
  }

}

class MappedMetric extends APIMetric with LongKeyedMapper[MappedMetric] with IdPK {
  override def getSingleton = MappedMetric

  object userId extends DefaultStringField(this)
  object url extends DefaultStringField(this)
  object date extends MappedDateTime(this)
  object userName extends DefaultStringField(this)
  object appName extends DefaultStringField(this)
  object developerEmail extends DefaultStringField(this)

  //The consumerId, Foreign key to Consumer not key
  object consumerId extends DefaultStringField(this)
  //name of the Scala Partial Function being used for the endpoint
  object implementedByPartialFunction  extends DefaultStringField(this)
  //name of version where the call is implemented) -- S.request.get.view
  object implementedInVersion  extends DefaultStringField(this)
  //(GET, POST etc.) --S.request.get.requestType
  object verb extends DefaultStringField(this)


  override def getUrl(): String = url.get
  override def getDate(): Date = date.get
  override def getUserId(): String = userId.get
  override def getUserName(): String = userName.get
  override def getAppName(): String = appName.get
  override def getDeveloperEmail(): String = developerEmail.get
  override def getConsumerId(): String = consumerId.get
  override def getImplementedByPartialFunction(): String = implementedByPartialFunction.get
  override def getImplementedInVersion(): String = implementedInVersion.get
  override def getVerb(): String = verb.get
}

object MappedMetric extends MappedMetric with LongKeyedMetaMapper[MappedMetric] {
  override def dbIndexes = Index(userId) :: Index(url) :: Index(date) :: Index(userName) :: Index(appName) :: Index(developerEmail) :: super.dbIndexes
}
