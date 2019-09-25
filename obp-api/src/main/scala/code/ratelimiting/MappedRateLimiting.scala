package code.ratelimiting

import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedRateLimitingProvider extends RateLimitingProviderTrait {
  def getAll(): Future[List[RateLimiting]] = Future(RateLimiting.findAll())
  def getByConsumerId(consumerId: String): Future[Box[RateLimiting]] = Future {
    RateLimiting.find(
      By(RateLimiting.ConsumerId, consumerId),
      NullRef(RateLimiting.BankId),
      NullRef(RateLimiting.ApiVersion),
      NullRef(RateLimiting.ApiName)
    )
  }
  def createOrUpdateConsumerCallLimits(consumerId: String,
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]] = Future {
    
    def createRateLimit(c: RateLimiting): Box[RateLimiting] = {
      tryo {
        perSecond match {
          case Some(v) => c.PerSecondCallLimit(v.toLong)
          case None =>
        }
        perMinute match {
          case Some(v) => c.PerMinuteCallLimit(v.toLong)
          case None =>
        }
        perHour match {
          case Some(v) => c.PerHourCallLimit(v.toLong)
          case None =>
        }
        perDay match {
          case Some(v) => c.PerDayCallLimit(v.toLong)
          case None =>
        }
        perWeek match {
          case Some(v) => c.PerWeekCallLimit(v.toLong)
          case None =>
        }
        perMonth match {
          case Some(v) => c.PerMonthCallLimit(v.toLong)
          case None =>
        }
        c.BankId(null)
        c.ApiName(null)
        c.ApiVersion(null)
        c.ConsumerId(consumerId)
        c.saveMe()
      }
    }

    val rateLimit = RateLimiting.find(
      By(RateLimiting.ConsumerId, consumerId),
      NullRef(RateLimiting.BankId),
      NullRef(RateLimiting.ApiVersion),
      NullRef(RateLimiting.ApiName)
    )
    rateLimit match {
      case Full(limit) => createRateLimit(limit)
      case _ => createRateLimit(RateLimiting.create)
    }
  }
}

class RateLimiting extends RateLimitingTrait with LongKeyedMapper[RateLimiting] with IdPK with CreatedUpdated {
  override def getSingleton = RateLimiting
  object RateLimitingId extends MappedUUID(this)
  object ApiVersion extends MappedString(this, 250)
  object ApiName extends MappedString(this, 250)
  object ConsumerId extends MappedString(this, 250)
  object BankId extends UUIDString(this)
  object PerSecondCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object PerMinuteCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object PerHourCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object PerDayCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object PerWeekCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object PerMonthCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }

  def rateLimitingId: String = RateLimitingId.get
  def apiName: String = ApiName.get
  def apiVersion: String = ApiVersion.get
  def consumerId: String = ConsumerId.get
  def bankId: String = BankId.get
  def perSecondCallLimit: Long = PerSecondCallLimit.get
  def perMinuteCallLimit: Long = PerMinuteCallLimit.get
  def perHourCallLimit: Long = PerHourCallLimit.get
  def perDayCallLimit: Long = PerDayCallLimit.get
  def perWeekCallLimit: Long = PerWeekCallLimit.get
  def perMonthCallLimit: Long = PerMonthCallLimit.get

}

object RateLimiting extends RateLimiting with LongKeyedMetaMapper[RateLimiting] {
  override def dbIndexes = UniqueIndex(RateLimitingId) :: super.dbIndexes
}
