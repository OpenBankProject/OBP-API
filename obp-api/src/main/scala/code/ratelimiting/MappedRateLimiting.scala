package code.ratelimiting

import code.api.Constant._
import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full, Logger}
import net.liftweb.util.Helpers.tryo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID.randomUUID

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * One rate-limiting rule, scoped to a consumer and optionally narrowed by bank, api version and
 * api name, valid over a date window.
 *
 * The three optional scope columns hold SQL NULL rather than "" when the scope is broader, and
 * that matters: getByConsumerId resolves a limit by trying four increasingly general scopes and
 * each tier matches the columns it is NOT scoping on with `IS NULL`. A row storing "" would be
 * invisible to every tier.
 *
 * The readers below are laxer than those queries — they map both NULL and "" to None — which is
 * how Lift behaved. Preserved.
 */
case class RateLimiting(
  rateLimitingId: String,
  consumerId: String,
  private val bankIdRaw: Option[String],
  private val apiVersionRaw: Option[String],
  private val apiNameRaw: Option[String],
  perSecondCallLimit: Long,
  perMinuteCallLimit: Long,
  perHourCallLimit: Long,
  perDayCallLimit: Long,
  perWeekCallLimit: Long,
  perMonthCallLimit: Long,
  fromDate: Date,
  toDate: Date,
  // Exposed because the v5.1.0 and v6.0.0 JSON factories report them on the rate-limit resource.
  createdAt: Date,
  updatedAt: Date
) extends RateLimitingTrait {
  private def nonEmpty(v: Option[String]): Option[String] = v.filter(s => s != null && s.nonEmpty)
  def apiName: Option[String] = nonEmpty(apiNameRaw)
  def apiVersion: Option[String] = nonEmpty(apiVersionRaw)
  def bankId: Option[String] = nonEmpty(bankIdRaw)
}

object RateLimiting {

  private val selectColumns =
    fr"""SELECT ratelimitingid, consumerid, bankid, apiversion, apiname, persecondcalllimit,
                perminutecalllimit, perhourcalllimit, perdaycalllimit, perweekcalllimit,
                permonthcalllimit, fromdate, todate, createdat, updatedat
         FROM ratelimiting"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Long], Option[Long], Option[Long], Option[Long], Option[Long],
    Option[Long], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  // MappedDateTime's reader is `st(if (isNull) Empty else Full(...))` and its defaultValue is
  // null, so Lift read a NULL date as null rather than failing. The conversion matters as much as
  // the Option: the driver hands back a java.sql.Timestamp, which is a java.util.Date subclass and
  // so type-checks in the Date field, but json4s serializes it as an empty JSON object - and these
  // four are reported on the rate-limit resource.
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  // MappedLong's reader is `if (isNull) defaultValue else v`, and each of these fields declared
  // its default as APIUtil.getPropsAsLongValue("rate_limiting_per_*", -1) - the instance's
  // configured limit, read from props on every access. A row written before the column existed
  // holds NULL, so reading a bare Long fails the query outright, and this is the table
  // RateLimitingUtil enforces from. The write path already resolves the same props through
  // limitOrDefault; this is the read half of it.
  private def readLimit(value: Option[Long], propName: String): Long =
    value.getOrElse(APIUtil.getPropsAsLongValue(propName, -1))

  private def fromRow(row: Row): RateLimiting = row match {
    case (rateLimitingId, consumerId, bankId, apiVersion, apiName, perSecond, perMinute, perHour,
          perDay, perWeek, perMonth, fromDate, toDate, createdAt, updatedAt) =>
      RateLimiting(rateLimitingId.orNull, consumerId.orNull, bankId, apiVersion, apiName,
        readLimit(perSecond, "rate_limiting_per_second"),
        readLimit(perMinute, "rate_limiting_per_minute"),
        readLimit(perHour, "rate_limiting_per_hour"), readLimit(perDay, "rate_limiting_per_day"),
        readLimit(perWeek, "rate_limiting_per_week"),
        readLimit(perMonth, "rate_limiting_per_month"), readDate(fromDate), readDate(toDate),
        readDate(createdAt), readDate(updatedAt))
  }

  private def query(condition: Fragment): List[RateLimiting] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[RateLimiting] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** `None` means "this column must be NULL", matching Lift's NullRef — not "don't filter". */
  private def scoped(column: Fragment, value: Option[String]): Fragment = value match {
    case Some(v) => column ++ fr" = $v"
    case None => column ++ fr" IS NULL"
  }

  private def ts(d: Date): java.sql.Timestamp = new java.sql.Timestamp(d.getTime)

  def findAll(): List[RateLimiting] = query(fr"ORDER BY id ASC")

  def findAllByConsumerId(consumerId: String): List[RateLimiting] =
    query(fr"WHERE consumerid = $consumerId ORDER BY id ASC")

  def findAllByConsumerIdAtDate(consumerId: String, date: Date): List[RateLimiting] =
    query(fr"""WHERE consumerid = $consumerId AND fromdate < ${ts(date)} AND todate > ${ts(date)}
               ORDER BY id ASC""")

  /** Rows whose window overlaps [start, end]. */
  def findAllActiveBetween(consumerId: String, start: Date, end: Date): List[RateLimiting] =
    query(fr"""WHERE consumerid = $consumerId AND fromdate <= ${ts(end)} AND todate >= ${ts(start)}
               ORDER BY id ASC""")

  def findScoped(consumerId: String, bankId: Option[String], apiVersion: Option[String],
                 apiName: Option[String], date: Option[Date]): Box[RateLimiting] = {
    val window = date.map(d => fr"AND fromdate < ${ts(d)} AND todate > ${ts(d)}")
      .getOrElse(Fragment.empty)
    one(fr"WHERE consumerid = $consumerId AND " ++ scoped(fr"bankid", bankId) ++
      fr"AND " ++ scoped(fr"apiversion", apiVersion) ++
      fr"AND " ++ scoped(fr"apiname", apiName) ++ window)
  }

  /** The newest row for a scope; the scope columns are matched exactly, NULL included. */
  def findMostRecentScoped(consumerId: String, bankId: Option[String], apiVersion: Option[String],
                           apiName: Option[String]): Option[RateLimiting] =
    query(fr"WHERE consumerid = $consumerId AND " ++ scoped(fr"bankid", bankId) ++
      fr"AND " ++ scoped(fr"apiversion", apiVersion) ++
      fr"AND " ++ scoped(fr"apiname", apiName) ++
      fr"ORDER BY updatedat DESC, id DESC").headOption

  def findByRateLimitingId(rateLimitingId: String): Box[RateLimiting] =
    one(fr"WHERE ratelimitingid = $rateLimitingId")

  /**
   * Unsupplied call limits fall back to the props defaults, which is where Lift's field defaults
   * came from — the columns carry no database default.
   */
  private def limitOrDefault(supplied: Option[String], propName: String): Long =
    supplied.map(_.toLong).getOrElse(APIUtil.getPropsAsLongValue(propName, -1))

  def insert(consumerId: String, fromDate: Date, toDate: Date, apiVersion: Option[String],
             apiName: Option[String], bankId: Option[String], perSecond: Option[String],
             perMinute: Option[String], perHour: Option[String], perDay: Option[String],
             perWeek: Option[String], perMonth: Option[String]): RateLimiting =
    insertWithLimits(consumerId, fromDate, toDate, apiVersion, apiName, bankId,
      limitOrDefault(perSecond, "rate_limiting_per_second"),
      limitOrDefault(perMinute, "rate_limiting_per_minute"),
      limitOrDefault(perHour, "rate_limiting_per_hour"),
      limitOrDefault(perDay, "rate_limiting_per_day"),
      limitOrDefault(perWeek, "rate_limiting_per_week"),
      limitOrDefault(perMonth, "rate_limiting_per_month"))

  def insertWithLimits(consumerId: String, fromDate: Date, toDate: Date, apiVersion: Option[String],
                       apiName: Option[String], bankId: Option[String], perSecond: Long,
                       perMinute: Long, perHour: Long, perDay: Long, perWeek: Long,
                       perMonth: Long): RateLimiting = {
    val rateLimitingId = randomUUID().toString
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO ratelimiting
            (ratelimitingid, consumerid, bankid, apiversion, apiname, persecondcalllimit,
             perminutecalllimit, perhourcalllimit, perdaycalllimit, perweekcalllimit,
             permonthcalllimit, fromdate, todate, createdat, updatedat)
            VALUES ($rateLimitingId, $consumerId, $bankId, $apiVersion, $apiName, $perSecond,
             $perMinute, $perHour, $perDay, $perWeek, $perMonth, ${ts(fromDate)}, ${ts(toDate)},
             $now, $now)"""
        .update.run)
    findByRateLimitingId(rateLimitingId)
      .openOrThrowException("the rate limit just inserted must be readable")
  }

  /**
   * Only the supplied call limits move; an omitted one keeps the value already stored, which is
   * what Mapper's `perSecond.foreach(...)` did on an existing row.
   */
  def update(rateLimitingId: String, fromDate: Date, toDate: Date, apiVersion: Option[String],
             apiName: Option[String], bankId: Option[String], perSecond: Option[String],
             perMinute: Option[String], perHour: Option[String], perDay: Option[String],
             perWeek: Option[String], perMonth: Option[String]): Box[RateLimiting] = {
    val limits = List(
      perSecond.map(v => fr"persecondcalllimit = ${v.toLong}"),
      perMinute.map(v => fr"perminutecalllimit = ${v.toLong}"),
      perHour.map(v => fr"perhourcalllimit = ${v.toLong}"),
      perDay.map(v => fr"perdaycalllimit = ${v.toLong}"),
      perWeek.map(v => fr"perweekcalllimit = ${v.toLong}"),
      perMonth.map(v => fr"permonthcalllimit = ${v.toLong}")
    ).flatten
    val sets = List(
      fr"fromdate = ${ts(fromDate)}",
      fr"todate = ${ts(toDate)}",
      fr"bankid = $bankId",
      fr"apiname = $apiName",
      fr"apiversion = $apiVersion",
      fr"updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}"
    ) ++ limits
    DoobieUtil.runUpdate(
      (fr"UPDATE ratelimiting SET" ++ sets.reduce((a, b) => a ++ fr"," ++ b) ++
        fr"WHERE ratelimitingid = $rateLimitingId").update.run)
    findByRateLimitingId(rateLimitingId)
  }

  def delete(rateLimitingId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM ratelimiting WHERE ratelimitingid = $rateLimitingId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM ratelimiting".update.run)
    ()
  }
}

object MappedRateLimitingProvider extends RateLimitingProviderTrait with Logger {

  def getAll(): Future[List[RateLimiting]] = Future(RateLimiting.findAll())

  def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]] =
    Future {
      date match {
        case None => RateLimiting.findAllByConsumerId(consumerId)
        case Some(d) => RateLimiting.findAllByConsumerIdAtDate(consumerId, d)
      }
    }

  /**
   * Four increasingly general scopes, first match wins. bankId is required to be NULL throughout —
   * this resolution path is for system-level limits only.
   */
  def getByConsumerId(consumerId: String,
                      apiVersion: String,
                      apiName: String,
                      date: Option[Date] = None): Future[Box[RateLimiting]] = Future {
    RateLimiting.findScoped(consumerId, None, Some(apiVersion), Some(apiName), date) // 1st: Consumer and Version and Name
      .or(RateLimiting.findScoped(consumerId, None, None, Some(apiName), date))      // 2nd: Consumer and Name
      .or(RateLimiting.findScoped(consumerId, None, Some(apiVersion), None, date))   // 3rd: Consumer and Version
      .or(RateLimiting.findScoped(consumerId, None, None, None, date))               // 4th: Consumer
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
                                    apiName: Option[String]): Option[RateLimiting] =
    RateLimiting.findMostRecentScoped(consumerId, bankId, apiVersion, apiName)

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
    val result = tryo {
      RateLimiting.insert(consumerId, fromDate, toDate, apiVersion, apiName, bankId, perSecond,
        perMinute, perHour, perDay, perWeek, perMonth)
    }
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
    findMostRecentRateLimitCommon(consumerId, bankId, apiVersion, apiName) match {
      case Some(limit) =>
        tryo {
          RateLimiting.update(limit.rateLimitingId, fromDate, toDate, apiVersion, apiName, bankId,
            perSecond, perMinute, perHour, perDay, perWeek, perMonth)
        }.flatMap(box => box)
      case None =>
        tryo {
          RateLimiting.insert(consumerId, fromDate, toDate, apiVersion, apiName, bankId, perSecond,
            perMinute, perHour, perDay, perWeek, perMonth)
        }
    }
    // Deliberately does NOT invalidate the cache — createConsumerCallLimits and
    // updateConsumerCallLimits both do, this one never did. Preserved.
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
    val result = RateLimiting.findByRateLimitingId(rateLimitingId).flatMap { _ =>
      RateLimiting.update(rateLimitingId, fromDate, toDate, apiVersion, apiName, bankId, perSecond,
        perMinute, perHour, perDay, perWeek, perMonth)
    }
    // Invalidate cache when updating rate limit
    result.foreach(rl => Caching.invalidateRateLimitCache(rl.consumerId))
    result
  }

  def getByRateLimitingId(rateLimitingId: String): Future[Box[RateLimiting]] =
    Future(RateLimiting.findByRateLimitingId(rateLimitingId))

  def deleteByRateLimitingId(rateLimitingId: String): Future[Box[Boolean]] = Future {
    val rl = RateLimiting.findByRateLimitingId(rateLimitingId)
    val result = rl.map(r => RateLimiting.delete(r.rateLimitingId))
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
        val results = RateLimiting.findAllActiveBetween(consumerId, startDate, endDate)
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
