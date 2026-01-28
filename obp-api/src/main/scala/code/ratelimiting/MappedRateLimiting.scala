package code.ratelimiting

import code.api.util.APIUtil
import code.api.cache.Caching
import code.api.Constant._

import java.util.Date
import java.util.UUID.randomUUID
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full, Logger}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.tesobe.CacheKeyFromArguments

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object MappedRateLimitingProvider extends RateLimitingProviderTrait with Logger {

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

  def createConsumerCallLimits(consumerId: String,
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

    def createRateLimit(c: RateLimiting): Box[RateLimiting] = {
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

        c.updatedAt(new Date())

        c.saveMe()
      }
    }
    val result = createRateLimit(RateLimiting.create)
    // Invalidate cache when creating new rate limit
    result.foreach(_ => Caching.invalidateRateLimitCache(consumerId))
    result
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
  def updateConsumerCallLimits(rateLimitingId: String,
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
    val result = RateLimiting.find(
      By(RateLimiting.RateLimitingId, rateLimitingId)
    ) map { c =>
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

      c.updatedAt(new Date())

      c.saveMe()
    }
    // Invalidate cache when updating rate limit
    result.foreach(rl => Caching.invalidateRateLimitCache(rl.consumerId))
    result
  }

  def getByRateLimitingId(rateLimitingId: String): Future[Box[RateLimiting]] = Future {
    RateLimiting.find(By(RateLimiting.RateLimitingId, rateLimitingId))
  }

  def deleteByRateLimitingId(rateLimitingId: String): Future[Box[Boolean]] = Future {
    val rl = RateLimiting.find(By(RateLimiting.RateLimitingId, rateLimitingId))
    val result = rl.map(_.delete_!)
    // Invalidate cache when deleting rate limit
    rl.foreach(r => Caching.invalidateRateLimitCache(r.consumerId))
    result
  }

  private def getActiveCallLimitsByConsumerIdAtDateCached(consumerId: String, dateWithHour: String): List[RateLimiting] = {
    // Cache key uses standardized prefix: rl_active_{consumerId}_{dateWithHour}
    // Create Date objects for start and end of the hour from the date_with_hour string
    // IMPORTANT: Hour format is in UTC for consistency across all servers
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
    val localDateTime = LocalDateTime.parse(dateWithHour, formatter)

    // Start of hour: 00 mins, 00 seconds (UTC)
    val startOfHour = localDateTime.withMinute(0).withSecond(0)
    val startInstant = startOfHour.atZone(java.time.ZoneOffset.UTC).toInstant()
    val startDate = Date.from(startInstant)

    // End of hour: 59 mins, 59 seconds (UTC)
    val endOfHour = localDateTime.withMinute(59).withSecond(59)
    val endInstant = endOfHour.atZone(java.time.ZoneOffset.UTC).toInstant()
    val endDate = Date.from(endInstant)

    val cacheKey = s"${RATE_LIMIT_ACTIVE_PREFIX}${consumerId}_${dateWithHour}"
      Caching.memoizeSyncWithProvider(Some(cacheKey))(RATE_LIMIT_ACTIVE_CACHE_TTL second) {
        // Find rate limits that are active at any point during this hour
        // A rate limit is active if: fromDate <= endOfHour AND toDate >= startOfHour
        debug(s"[RateLimiting] Query: consumerId=$consumerId, dateWithHour=$dateWithHour, startDate=$startDate, endDate=$endDate")
        val results = RateLimiting.findAll(
          By(RateLimiting.ConsumerId, consumerId),
          By_<=(RateLimiting.FromDate, endDate),
          By_>=(RateLimiting.ToDate, startDate)
        )
        debug(s"[RateLimiting] Found ${results.size} rate limits for consumerId=$consumerId at dateWithHour=$dateWithHour")
        results
      }
  }

  def getActiveCallLimitsByConsumerIdAtDate(consumerId: String, dateUtc: Date): Future[List[RateLimiting]] = Future {
    // Convert the provided date parameter (not current time!) to hour format
    // Date is timezone-agnostic (millis since epoch), we interpret it as UTC
    def dateWithHour: String = {
      val instant = dateUtc.toInstant()
      val localDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
      localDateTime.format(formatter)
    }
    getActiveCallLimitsByConsumerIdAtDateCached(consumerId, dateWithHour)
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
