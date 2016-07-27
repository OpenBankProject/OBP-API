package code.metrics

import java.util.Date

import code.util.DefaultStringField
import net.liftweb.mapper._

object MappedMetrics extends APIMetrics {

  override def saveMetric(userId: String, url: String, date: Date, userName: String, appName: String, developerEmail: String): Unit = {
    MappedMetric.create.url(url).date(date).userName(userName).appName(appName).developerEmail(developerEmail).save
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

}

class MappedMetric extends APIMetric with LongKeyedMapper[MappedMetric] with IdPK {
  override def getSingleton = MappedMetric

  object userId extends DefaultStringField(this)
  object url extends DefaultStringField(this)
  object date extends MappedDateTime(this)
  object userName extends DefaultStringField(this)
  object appName extends DefaultStringField(this)
  object developerEmail extends DefaultStringField(this)


  override def getUrl(): String = url.get
  override def getDate(): Date = date.get
  override def getUserId(): String = userId.get
  override def getUserName(): String = userName.get
  override def getAppName(): String = appName.get
  override def getDeveloperEmail(): String = developerEmail.get
}

object MappedMetric extends MappedMetric with LongKeyedMetaMapper[MappedMetric] {
  override def dbIndexes = Index(userId) :: Index(url) :: Index(date) :: Index(userName) :: Index(appName) :: Index(developerEmail) :: super.dbIndexes
}
