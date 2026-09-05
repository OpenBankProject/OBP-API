package code.directdebit

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.DirectDebitTrait
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box

/**
 * A direct debit mandate on an account.
 *
 * `dateCancelled` is written by no code path, so it is always NULL and reads back as null. The
 * trait types it as a bare Date rather than an Option, so the absent value is surfaced as null
 * rather than pretended present.
 */
case class DirectDebit(
  directDebitId: String,
  bankId: String,
  accountId: String,
  customerId: String,
  userId: String,
  counterpartyId: String,
  dateSigned: Date,
  dateCancelled: Date,
  dateStarts: Date,
  dateExpires: Date,
  active: Boolean
) extends DirectDebitTrait

object DirectDebit {

  private val selectColumns =
    fr"""SELECT directdebitid, bankid, accountid, customerid, userid, counterpartyid, datesigned,
                datecancelled, datestarts, dateexpires, active
         FROM directdebit"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[Boolean])

  private def fromRow(row: Row): DirectDebit = row match {
    case (directDebitId, bankId, accountId, customerId, userId, counterpartyId, dateSigned,
          dateCancelled, dateStarts, dateExpires, active) =>
      DirectDebit(directDebitId.orNull, bankId.orNull, accountId.orNull, customerId.orNull,
        userId.orNull, counterpartyId.orNull, dateSigned.orNull, dateCancelled.orNull,
        dateStarts.orNull, dateExpires.orNull, active.getOrElse(false))
  }

  private def query(condition: Fragment): List[DirectDebit] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /**
   * The unique index on (bankid, accountid, customerid, counterpartyid) is what stops a second
   * mandate being set up for the same customer and counterparty on one account; the caller's tryo
   * turns the violation into a Failure.
   */
  def insert(bankId: String, accountId: String, customerId: String, userId: String,
             counterpartyId: String, dateSigned: Date, dateStarts: Date,
             dateExpires: Option[Date]): DirectDebit = {
    val directDebitId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val signed = new java.sql.Timestamp(dateSigned.getTime)
    val starts = new java.sql.Timestamp(dateStarts.getTime)
    val expires = dateExpires.map(d => new java.sql.Timestamp(d.getTime))
    DoobieUtil.runUpdate(
      sql"""INSERT INTO directdebit
            (directdebitid, bankid, accountid, customerid, userid, counterpartyid, datesigned,
             datestarts, dateexpires, active, createdat, updatedat)
            VALUES ($directDebitId, $bankId, $accountId, $customerId, $userId, $counterpartyId,
             $signed, $starts, $expires, true, $now, $now)"""
        .update.run)
    DirectDebit(directDebitId, bankId, accountId, customerId, userId, counterpartyId, dateSigned,
      null, dateStarts, dateExpires.orNull, active = true)
  }

  // Newest first — updatedat orders every listing below, so any future write must stamp it.
  def findAllByBankAccount(bankId: String, accountId: String): List[DirectDebit] =
    query(fr"WHERE bankid = $bankId AND accountid = $accountId ORDER BY updatedat DESC, id DESC")

  def findAllByCustomerId(customerId: String): List[DirectDebit] =
    query(fr"WHERE customerid = $customerId ORDER BY updatedat DESC, id DESC")

  def findAllByUserId(userId: String): List[DirectDebit] =
    query(fr"WHERE userid = $userId ORDER BY updatedat DESC, id DESC")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM directdebit".update.run)
    ()
  }
}

object MappedDirectDebitProvider extends DirectDebitProvider {

  def createDirectDebit(bankId: String, accountId: String, customerId: String, userId: String,
                        counterpartyId: String, dateSigned: Date, dateStarts: Date,
                        dateExpires: Option[Date]): Box[DirectDebit] = Box.tryo {
    DirectDebit.insert(bankId, accountId, customerId, userId, counterpartyId, dateSigned,
      dateStarts, dateExpires)
  }

  def getDirectDebitsByBankAccount(bankId: String, accountId: String): List[DirectDebit] =
    DirectDebit.findAllByBankAccount(bankId, accountId)

  def getDirectDebitsByCustomer(customerId: String): List[DirectDebit] =
    DirectDebit.findAllByCustomerId(customerId)

  def getDirectDebitsByUser(userId: String): List[DirectDebit] =
    DirectDebit.findAllByUserId(userId)
}
