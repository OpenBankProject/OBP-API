package code.standingorders

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper
import code.util.Helper.convertToSmallestCurrencyUnits
import com.openbankproject.commons.model.StandingOrderTrait
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box

import scala.math.BigDecimal

/**
 * A standing order on an account.
 *
 * Unlike DIRECTDEBIT this table has no unique index: repeat standing orders between the same
 * parties are legitimate, differing by amount, schedule or start date.
 *
 * `amountValue` is stored as a BIGINT in the currency's smallest unit and converted on the way in
 * and out. `dateCancelled` is written by no code path and so is always null, as is `whenDetail`
 * beyond the empty default createStandingOrder leaves behind.
 */
case class StandingOrder(
  standingOrderId: String,
  bankId: String,
  accountId: String,
  customerId: String,
  userId: String,
  counterpartyId: String,
  amountValue: BigDecimal,
  amountCurrency: String,
  whenFrequency: String,
  whenDetail: String,
  dateSigned: Date,
  dateCancelled: Date,
  dateStarts: Date,
  dateExpires: Date,
  active: Boolean
) extends StandingOrderTrait

object StandingOrder {

  private val selectColumns =
    fr"""SELECT standingorderid, bankid, accountid, customerid, userid, couterpartyid, amountvalue,
                amountcurrency, whenfrequency, whendetail, datesigned, datecancelled, datestarts,
                dateexpires, active
         FROM standingorder"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Long], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[Boolean])

  private def fromRow(row: Row): StandingOrder = row match {
    case (standingOrderId, bankId, accountId, customerId, userId, counterpartyId, amountValue,
          amountCurrency, whenFrequency, whenDetail, dateSigned, dateCancelled, dateStarts,
          dateExpires, active) =>
      StandingOrder(standingOrderId.orNull, bankId.orNull, accountId.orNull, customerId.orNull,
        userId.orNull, counterpartyId.orNull,
        Helper.smallestCurrencyUnitToBigDecimal(amountValue.getOrElse(0L), amountCurrency.orNull),
        amountCurrency.orNull, whenFrequency.orNull, whenDetail.orNull, dateSigned.orNull,
        dateCancelled.orNull, dateStarts.orNull, dateExpires.orNull, active.getOrElse(false))
  }

  private def query(condition: Fragment): List[StandingOrder] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(bankId: String, accountId: String, customerId: String, userId: String,
             counterpartyId: String, amountValue: BigDecimal, amountCurrency: String,
             whenFrequency: String, dateSigned: Date, dateStarts: Date,
             dateExpires: Option[Date]): StandingOrder = {
    val standingOrderId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val signed = new java.sql.Timestamp(dateSigned.getTime)
    val starts = new java.sql.Timestamp(dateStarts.getTime)
    val expires = dateExpires.map(d => new java.sql.Timestamp(d.getTime))
    val smallestUnits = convertToSmallestCurrencyUnits(amountValue, amountCurrency)
    // whenDetail is never supplied on create; Mapper stored MappedString's "" default, so the
    // column starts empty rather than NULL.
    DoobieUtil.runUpdate(
      sql"""INSERT INTO standingorder
            (standingorderid, bankid, accountid, customerid, userid, couterpartyid, amountvalue,
             amountcurrency, whenfrequency, whendetail, datesigned, datestarts, dateexpires, active,
             createdat, updatedat)
            VALUES ($standingOrderId, $bankId, $accountId, $customerId, $userId, $counterpartyId,
             $smallestUnits, $amountCurrency, $whenFrequency, '', $signed, $starts, $expires, true,
             $now, $now)"""
        .update.run)
    StandingOrder(standingOrderId, bankId, accountId, customerId, userId, counterpartyId,
      Helper.smallestCurrencyUnitToBigDecimal(smallestUnits, amountCurrency), amountCurrency,
      whenFrequency, "", dateSigned, null, dateStarts, dateExpires.orNull, active = true)
  }

  // Newest first — updatedat orders every listing below, so any future write must stamp it.
  def findAllByBankAccount(bankId: String, accountId: String): List[StandingOrder] =
    query(fr"WHERE bankid = $bankId AND accountid = $accountId ORDER BY updatedat DESC, id DESC")

  def findAllByCustomerId(customerId: String): List[StandingOrder] =
    query(fr"WHERE customerid = $customerId ORDER BY updatedat DESC, id DESC")

  def findAllByUserId(userId: String): List[StandingOrder] =
    query(fr"WHERE userid = $userId ORDER BY updatedat DESC, id DESC")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM standingorder".update.run)
    ()
  }
}

object MappedStandingOrderProvider extends StandingOrderProvider {

  def createStandingOrder(bankId: String, accountId: String, customerId: String, userId: String,
                          couterpartyId: String, amountValue: BigDecimal, amountCurrency: String,
                          whenFrequency: String, whenDetail: String, dateSigned: Date,
                          dateStarts: Date, dateExpires: Option[Date]): Box[StandingOrder] = Box.tryo {
    // whenDetail is accepted and then dropped — Mapper never wrote it either. Preserved verbatim
    // so a caller relying on the current (absent) behaviour is not silently changed.
    StandingOrder.insert(bankId, accountId, customerId, userId, couterpartyId, amountValue,
      amountCurrency, whenFrequency, dateSigned, dateStarts, dateExpires)
  }

  def getStandingOrdersByBankAccount(bankId: String, accountId: String): List[StandingOrder] =
    StandingOrder.findAllByBankAccount(bankId, accountId)

  def getStandingOrdersByCustomer(customerId: String): List[StandingOrder] =
    StandingOrder.findAllByCustomerId(customerId)

  def getStandingOrdersByUser(userId: String): List[StandingOrder] =
    StandingOrder.findAllByUserId(userId)
}
