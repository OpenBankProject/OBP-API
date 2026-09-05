package code.kycstatuses

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycStatus
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * A customer's KYC status, one row per (bank, customer).
 *
 * There is no unique index behind that pairing: addKycStatus looks the row up and updates it, or
 * inserts if absent, so two concurrent first-time writes for the same customer can both insert.
 * Pre-existing; see V072's comment.
 */
case class MappedKycStatus(
  bankId: String,
  customerId: String,
  customerNumber: String,
  ok: Boolean,
  date: Date
) extends KycStatus

object MappedKycStatus {

  private val selectColumns =
    fr"SELECT mbankid, mcustomerid, mcustomernumber, mok, mdate FROM mappedkycstatus"

  private type Row = (Option[String], Option[String], Option[String], Option[Boolean],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedKycStatus = row match {
    case (bankId, customerId, customerNumber, ok, date) =>
      MappedKycStatus(bankId.orNull, customerId.orNull, customerNumber.orNull, ok.getOrElse(false),
        date.orNull)
  }

  private def query(condition: Fragment): List[MappedKycStatus] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Newest first — updatedat is what orders the caller's list, so writes must stamp it. */
  def findAllByCustomerId(customerId: String): List[MappedKycStatus] =
    query(fr"WHERE mcustomerid = $customerId ORDER BY updatedat DESC, id DESC")

  def upsert(bankId: String, customerId: String, customerNumber: String, ok: Boolean,
             date: Date): MappedKycStatus = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val ts = new java.sql.Timestamp(date.getTime)
    // Mapper's find(By(mBankId), By(mCustomerId)) took whichever row the database returned first;
    // id ASC pins that to the oldest so a duplicated pair updates deterministically.
    val existingId = DoobieUtil.runQuery(
      sql"""SELECT id FROM mappedkycstatus
            WHERE mbankid = $bankId AND mcustomerid = $customerId ORDER BY id ASC LIMIT 1"""
        .query[Long].option)
    existingId match {
      case Some(id) =>
        DoobieUtil.runUpdate(
          sql"""UPDATE mappedkycstatus SET mbankid = $bankId, mcustomerid = $customerId,
                  mcustomernumber = $customerNumber, mok = $ok, mdate = $ts, updatedat = $now
                WHERE id = $id""".update.run)
      case None =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO mappedkycstatus
                (mbankid, mcustomerid, mcustomernumber, mok, mdate, createdat, updatedat)
                VALUES ($bankId, $customerId, $customerNumber, $ok, $ts, $now, $now)"""
            .update.run)
    }
    MappedKycStatus(bankId, customerId, customerNumber, ok, date)
  }

  def deleteByCustomerId(customerId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycstatus WHERE mcustomerid = $customerId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycstatus".update.run)
    ()
  }
}

object MappedKycStatusesProvider extends KycStatusProvider {

  override def getKycStatuses(customerId: String): List[MappedKycStatus] =
    MappedKycStatus.findAllByCustomerId(customerId)

  override def addKycStatus(bankId: String, customerId: String, customerNumber: String,
                            ok: Boolean, date: Date): Box[KycStatus] =
    Full(MappedKycStatus.upsert(bankId, customerId, customerNumber, ok, date))
}
