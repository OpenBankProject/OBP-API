package code.ratelimiting

import code.api.util.APIUtil

import java.util.Date
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MappedRateLimitingProvider extends RateLimitingProviderTrait {
  def getAll(): Future[List[RateLimiting]] = Future(RateLimiting.findAll())
  def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]] = Future {
    date match {
      case None =>
        RateLimiting.findAll(
          By(RateLimiting.ConsumerId, consumerId)
        )
      case Some(date) =>
        RateLimiting.findAll(
          By(RateLimiting.ConsumerId, consumerId),
          By_<(RateLimiting.FromDate, date),
          By_>(RateLimiting.ToDate, date)
        )
    }
    
  }
  def getByConsumerId(consumerId: String, 
                      apiVersion: String, 
                      apiName: String, 
                      date: Option[Date] = None): Future[Box[RateLimiting]] = Future {
    val result = 
      date match {
        case None =>
          RateLimiting.find( // 1st try: Consumer and Version and Name
            By(RateLimiting.ConsumerId, consumerId),
            By(RateLimiting.ApiVersion, apiVersion),
            By(RateLimiting.ApiName, apiName),
            NullRef(RateLimiting.BankId)
          ).or(
            RateLimiting.find( // 2nd try: Consumer and Name
              By(RateLimiting.ConsumerId, consumerId),
              By(RateLimiting.ApiName, apiName),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiVersion)
            )
          ).or(
            RateLimiting.find( // 3rd try: Consumer and Version
              By(RateLimiting.ConsumerId, consumerId),
              By(RateLimiting.ApiVersion, apiVersion),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiName)
            )
          ).or(
            RateLimiting.find( // 4th try: Consumer
              By(RateLimiting.ConsumerId, consumerId),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiVersion),
              NullRef(RateLimiting.ApiName)
            )
          )
        case Some(date) =>
          RateLimiting.find( // 1st try: Consumer and Version and Name
            By(RateLimiting.ConsumerId, consumerId),
            By(RateLimiting.ApiVersion, apiVersion),
            By(RateLimiting.ApiName, apiName),
            NullRef(RateLimiting.BankId),
            By_<(RateLimiting.FromDate, date),
            By_>(RateLimiting.ToDate, date)
          ).or(
            RateLimiting.find( // 2nd try: Consumer and Name
              By(RateLimiting.ConsumerId, consumerId),
              By(RateLimiting.ApiName, apiName),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiVersion),
              By_<(RateLimiting.FromDate, date),
              By_>(RateLimiting.ToDate, date)
            )
          ).or(
            RateLimiting.find( // 3rd try: Consumer and Version
              By(RateLimiting.ConsumerId, consumerId),
              By(RateLimiting.ApiVersion, apiVersion),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiName),
              By_<(RateLimiting.FromDate, date),
              By_>(RateLimiting.ToDate, date)
            )
          ).or(
            RateLimiting.find( // 4th try: Consumer
              By(RateLimiting.ConsumerId, consumerId),
              NullRef(RateLimiting.BankId),
              NullRef(RateLimiting.ApiVersion),
              NullRef(RateLimiting.ApiName),
              By_<(RateLimiting.FromDate, date),
              By_>(RateLimiting.ToDate, date)
            )
          )
    }
    result
  }

  def findMostRecentRateLimit(consumerId: String,
                              bankId: Option[String],
                              apiVersion: Option[String],
                              apiName: Option[String]): Future[Option[RateLimiting]] = Future {
    findMostRecentRateLimitCommon(consumerId, bankId, apiVersion, apiName)
  }
  def findMostRecentRateLimitCommon(consumerId: String,
                                    bankId: Option[String],
                                    apiVersion: Option[String],
                                    apiName: Option[String]): Option[RateLimiting] = {
    val byConsumerParam = By(RateLimiting.ConsumerId, consumerId)
    val byBankParam = bankId.map(v => By(RateLimiting.BankId, v)).getOrElse(NullRef(RateLimiting.BankId))
    val byApiVersionParam = apiVersion.map(v => By(RateLimiting.ApiVersion, v)).getOrElse(NullRef(RateLimiting.ApiVersion))
    val byApiNameParam = apiName.map(v => By(RateLimiting.ApiName, v)).getOrElse(NullRef(RateLimiting.ApiName))

    RateLimiting.findAll(
      byConsumerParam, byBankParam, byApiVersionParam, byApiNameParam,
      OrderBy(RateLimiting.updatedAt, Descending)
    ).headOption
  }

  def createOrUpdateConsumerCallLimits(consumerId: String,
                                       fromDate: Date,
                                       toDate: Date,
                                       apiVersion: Option[String],
                                       apiName: Option[String],
                                       bankId: Option[String],
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]] = Future {

    def createOrUpdateRateLimit(c: RateLimiting): Box[RateLimiting] = {
      tryo {
        c.FromDate(fromDate)
        c.ToDate(toDate)

        perSecond.foreach(v => c.PerSecondCallLimit(v.toLong))
        perMinute.foreach(v => c.PerMinuteCallLimit(v.toLong))
        perHour.foreach(v => c.PerHourCallLimit(v.toLong))
        perDay.foreach(v => c.PerDayCallLimit(v.toLong))
        perWeek.foreach(v => c.PerWeekCallLimit(v.toLong))
        perMonth.foreach(v => c.PerMonthCallLimit(v.toLong))

        c.BankId(bankId.orNull)
        c.ApiName(apiName.orNull)
        c.ApiVersion(apiVersion.orNull)
        c.ConsumerId(consumerId)

        // 👇 bump timestamp for last-write-wins
        c.updatedAt(new Date())

        c.saveMe()
      }
    }

    val result = findMostRecentRateLimitCommon(consumerId, bankId, apiVersion, apiName) match {
      case Some(limit) => createOrUpdateRateLimit(limit)
      case None => createOrUpdateRateLimit(RateLimiting.create)
    }

    result
  }

  def deleteByRateLimitingId(rateLimitingId: String): Future[Box[Boolean]] = Future {
    tryo {
      RateLimiting.find(By(RateLimiting.RateLimitingId, rateLimitingId)) match {
        case Full(rateLimiting) => rateLimiting.delete_!
        case _ => false
      }
    }
  }

  def getByRateLimitingId(rateLimitingId: String): Future[Box[RateLimiting]] = Future {
    RateLimiting.find(By(RateLimiting.RateLimitingId, rateLimitingId))
  }

  def getActiveCallLimitsByConsumerIdAtDate(consumerId: String, date: Date): Future[List[RateLimiting]] = Future {
    RateLimiting.findAll(
      By(RateLimiting.ConsumerId, consumerId),
      By_<=(RateLimiting.FromDate, date),
      By_>=(RateLimiting.ToDate, date)
    )
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
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_second", -1)
  }
  object PerMinuteCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_minute", -1)
  }
  object PerHourCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_hour", -1)
  }
  object PerDayCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_day", -1)
  }
  object PerWeekCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_week", -1)
  }
  object PerMonthCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_month", -1)
  }
  object FromDate extends MappedDateTime(this)
  object ToDate extends MappedDateTime(this)

  def rateLimitingId: String = RateLimitingId.get
  def apiName: Option[String] = if(ApiName.get == null || ApiName.get.isEmpty) None else Some(ApiName.get)
  def apiVersion: Option[String] = if(ApiVersion.get == null || ApiVersion.get.isEmpty) None else Some(ApiVersion.get)
  def consumerId: String  = ConsumerId.get
  def bankId: Option[String] = if(BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get)
  def perSecondCallLimit: Long = PerSecondCallLimit.get
  def perMinuteCallLimit: Long = PerMinuteCallLimit.get
  def perHourCallLimit: Long = PerHourCallLimit.get
  def perDayCallLimit: Long = PerDayCallLimit.get
  def perWeekCallLimit: Long = PerWeekCallLimit.get
  def perMonthCallLimit: Long = PerMonthCallLimit.get
  def fromDate: Date = FromDate.get
  def toDate: Date = ToDate.get

}

object RateLimiting extends RateLimiting with LongKeyedMetaMapper[RateLimiting] {
  override def dbIndexes = UniqueIndex(RateLimitingId) :: super.dbIndexes
}
