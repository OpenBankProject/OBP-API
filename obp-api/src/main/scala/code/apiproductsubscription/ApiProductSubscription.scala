package code.apiproductsubscription

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * One Consumer holding one API Product for a period, with a status.
 * See API_PRODUCT_SUBSCRIPTION_PLAN.md and the Glossary item "API Product Subscription".
 *
 * Upstream declares this as a Lift Mapper entity; this branch has no Lift Mapper and no
 * Schemifier, so it is a case class over a Doobie store with its table in
 * db.changelog-develop-merge-2.yaml.
 */
case class ApiProductSubscription(
  apiProductSubscriptionId: String,
  bankId: String,
  apiProductCode: String,
  consumerId: String,
  status: String,
  startDate: Date,
  /** None = open-ended. */
  endDate: Option[Date],
  createdByUserId: String,
  /** The RateLimiting row created for this subscription; None when there is none. */
  rateLimitingId: Option[String],
  createdAtDate: Date,
  updatedAtDate: Date
) extends ApiProductSubscriptionTrait

object ApiProductSubscription {

  private val selectColumns =
    fr"""SELECT apiproductsubscriptionid, bankid, apiproductcode, consumerid, status, startdate,
                enddate, createdbyuserid, ratelimitingid, createdat, updatedat
         FROM apiproductsubscription"""

  // Nullable columns read through Option: a bare String/Date Get throws NonNullableColumnRead on
  // a SQL NULL and fails the whole query rather than the one row.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def readDate(value: Option[java.sql.Timestamp]): Option[Date] =
    value.map(t => new Date(t.getTime))

  private def fromRow(row: Row): ApiProductSubscription = row match {
    case (apiProductSubscriptionId, bankId, apiProductCode, consumerId, status, startDate,
          endDate, createdByUserId, rateLimitingId, createdAt, updatedAt) =>
      ApiProductSubscription(
        apiProductSubscriptionId.orNull, bankId.orNull, apiProductCode.orNull, consumerId.orNull,
        status.orNull, readDate(startDate).orNull, readDate(endDate), createdByUserId.orNull,
        // The Mapper getter was Option(RateLimitingId.get).filter(_.nonEmpty): "" means "none".
        rateLimitingId.filter(_.nonEmpty),
        readDate(createdAt).orNull, readDate(updatedAt).orNull)
  }

  private def query(condition: Fragment): List[ApiProductSubscription] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[ApiProductSubscription] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findById(apiProductSubscriptionId: String): Box[ApiProductSubscription] =
    one(fr"WHERE apiproductsubscriptionid = $apiProductSubscriptionId")

  def findByConsumerId(consumerId: String): List[ApiProductSubscription] =
    query(fr"WHERE consumerid = $consumerId ORDER BY id ASC")

  /** An empty id list means "match nothing", the semantics Mapper's ByList had. */
  def findByConsumerIds(consumerIds: List[String]): List[ApiProductSubscription] =
    if (consumerIds.isEmpty) Nil
    else {
      val ids = consumerIds.map(id => fr"$id").reduce((a, b) => a ++ fr"," ++ b)
      query(fr"WHERE consumerid IN (" ++ ids ++ fr") ORDER BY id ASC")
    }

  def findByBankIdAndProductCode(bankId: String, apiProductCode: String): List[ApiProductSubscription] =
    query(fr"WHERE bankid = $bankId AND apiproductcode = $apiProductCode ORDER BY id ASC")

  def findNonCancelled(consumerId: String, bankId: String,
                       apiProductCode: String): Box[ApiProductSubscription] =
    one(fr"""WHERE consumerid = $consumerId AND bankid = $bankId
             AND apiproductcode = $apiProductCode
             AND status <> ${ApiProductSubscriptionStatus.Cancelled}""")

  def insert(bankId: String, apiProductCode: String, consumerId: String, status: String,
             startDate: Date, endDate: Option[Date],
             createdByUserId: String): ApiProductSubscription = {
    val apiProductSubscriptionId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val start = Option(startDate).map(d => new java.sql.Timestamp(d.getTime))
    val end = endDate.map(d => new java.sql.Timestamp(d.getTime))
    DoobieUtil.runUpdate(
      sql"""INSERT INTO apiproductsubscription
            (apiproductsubscriptionid, bankid, apiproductcode, consumerid, status, startdate,
             enddate, createdbyuserid, ratelimitingid, createdat, updatedat)
            VALUES ($apiProductSubscriptionId, ${Option(bankId)}, ${Option(apiProductCode)},
             ${Option(consumerId)}, ${Option(status)}, $start, $end, ${Option(createdByUserId)},
             ${Option("")}, $now, $now)""".update.run)
    findById(apiProductSubscriptionId)
      .openOrThrowException("the subscription just inserted must be readable")
  }

  def updateStatus(apiProductSubscriptionId: String, newStatus: String,
                   endDate: Option[Date]): Box[ApiProductSubscription] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    // An absent endDate leaves the stored one alone, as the Mapper path did.
    val endSet = endDate
      .map(d => fr", enddate = ${new java.sql.Timestamp(d.getTime)}")
      .getOrElse(Fragment.empty)
    DoobieUtil.runUpdate(
      (fr"UPDATE apiproductsubscription SET status = ${Option(newStatus)}, updatedat = $now" ++
        endSet ++
        fr"WHERE apiproductsubscriptionid = $apiProductSubscriptionId").update.run)
    findById(apiProductSubscriptionId)
  }

  def setRateLimitingId(apiProductSubscriptionId: String,
                        rateLimitingId: Option[String]): Box[ApiProductSubscription] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE apiproductsubscription
              SET ratelimitingid = ${Option(rateLimitingId.getOrElse(""))}, updatedat = $now
            WHERE apiproductsubscriptionid = $apiProductSubscriptionId""".update.run)
    findById(apiProductSubscriptionId)
  }

  def delete(apiProductSubscriptionId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM apiproductsubscription
            WHERE apiproductsubscriptionid = $apiProductSubscriptionId""".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM apiproductsubscription".update.run)
    ()
  }
}

trait ApiProductSubscriptionTrait {
  def apiProductSubscriptionId: String
  def bankId: String
  def apiProductCode: String
  def consumerId: String
  def status: String
  def startDate: Date
  def endDate: Option[Date]
  def createdByUserId: String
  def rateLimitingId: Option[String]
  def createdAtDate: Date
  def updatedAtDate: Date
}

/** The status machine. Only the transitions listed here are legal; `cancelled` is terminal. */
object ApiProductSubscriptionStatus {
  val Requested = "requested"
  val Active = "active"
  val PastDue = "past_due"
  val Suspended = "suspended"
  val Cancelled = "cancelled"

  val all: List[String] = List(Requested, Active, PastDue, Suspended, Cancelled)

  def isValid(status: String): Boolean = all.contains(status)

  private val transitions: Map[String, Set[String]] = Map(
    Requested -> Set(Active, Cancelled),
    Active    -> Set(PastDue, Suspended, Cancelled),
    PastDue   -> Set(Active, Suspended, Cancelled),
    Suspended -> Set(Active, Cancelled),
    Cancelled -> Set.empty
  )

  def canTransition(from: String, to: String): Boolean = transitions.get(from).exists(_.contains(to))

  def allowedFrom(from: String): Set[String] = transitions.getOrElse(from, Set.empty)
}
