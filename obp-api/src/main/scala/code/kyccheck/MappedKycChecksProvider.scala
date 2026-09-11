package code.kycchecks

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycCheck
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * A KYC check performed on a customer by a member of staff.
 *
 * `mid` is the caller-supplied id that decides update-vs-insert, and carries a unique index that
 * keeps that decision single-valued.
 */
case class MappedKycCheck(
  bankId: String,
  customerId: String,
  idKycCheck: String,
  customerNumber: String,
  date: Date,
  how: String,
  staffUserId: String,
  staffName: String,
  satisfied: Boolean,
  comments: String
) extends KycCheck

object MappedKycCheck {

  private val selectColumns =
    fr"""SELECT mbankid, mcustomerid, mid, mcustomernumber, mdate, mhow, mstaffuserid, mstaffname,
                msatisfied, mcomments
         FROM mappedkyccheck"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[String], Option[String], Option[String], Option[Boolean],
    Option[String])

  private def fromRow(row: Row): MappedKycCheck = row match {
    case (bankId, customerId, id, customerNumber, date, how, staffUserId, staffName, satisfied, comments) =>
      MappedKycCheck(bankId.orNull, customerId.orNull, id.orNull, customerNumber.orNull,
        date.orNull, how.orNull, staffUserId.orNull, staffName.orNull, satisfied.getOrElse(false),
        comments.orNull)
  }

  private def query(condition: Fragment): List[MappedKycCheck] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Newest first — updatedat is what orders the caller's list, so writes must stamp it. */
  def findAllByCustomerId(customerId: String): List[MappedKycCheck] =
    query(fr"WHERE mcustomerid = $customerId ORDER BY updatedat DESC, id DESC")

  def upsert(bankId: String, customerId: String, id: String, customerNumber: String, date: Date,
             how: String, staffUserId: String, staffName: String, satisfied: Boolean,
             comments: String): MappedKycCheck = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val ts = new java.sql.Timestamp(date.getTime)
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE mappedkyccheck SET mbankid = $bankId, mcustomerid = $customerId,
              mcustomernumber = $customerNumber, mdate = $ts, mhow = $how,
              mstaffuserid = $staffUserId, mstaffname = $staffName, msatisfied = $satisfied,
              mcomments = $comments, updatedat = $now
            WHERE mid = $id""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedkyccheck
              (mbankid, mcustomerid, mid, mcustomernumber, mdate, mhow, mstaffuserid, mstaffname,
               msatisfied, mcomments, createdat, updatedat)
              VALUES ($bankId, $customerId, $id, $customerNumber, $ts, $how, $staffUserId,
               $staffName, $satisfied, $comments, $now, $now)"""
          .update.run)
    }
    MappedKycCheck(bankId, customerId, id, customerNumber, date, how, staffUserId, staffName,
      satisfied, comments)
  }

  def deleteByCustomerId(customerId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkyccheck WHERE mcustomerid = $customerId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkyccheck".update.run)
    ()
  }
}

object MappedKycChecksProvider extends KycCheckProvider {

  override def getKycChecks(customerId: String): List[MappedKycCheck] =
    MappedKycCheck.findAllByCustomerId(customerId)

  override def addKycChecks(bankId: String, customerId: String, id: String, customerNumber: String,
                            date: Date, how: String, staffUserId: String, mStaffName: String,
                            mSatisfied: Boolean, comments: String): Box[KycCheck] =
    Full(MappedKycCheck.upsert(bankId, customerId, id, customerNumber, date, how, staffUserId,
      mStaffName, mSatisfied, comments))
}
