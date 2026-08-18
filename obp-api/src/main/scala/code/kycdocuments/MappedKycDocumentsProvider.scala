package code.kycdocuments

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycDocument
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * An identity document held for a customer.
 *
 * `mid` is the caller-supplied id that decides update-vs-insert, and carries a unique index that
 * keeps that decision single-valued.
 */
case class MappedKycDocument(
  bankId: String,
  customerId: String,
  idKycDocument: String,
  customerNumber: String,
  `type`: String,
  number: String,
  issueDate: Date,
  issuePlace: String,
  expiryDate: Date
) extends KycDocument

object MappedKycDocument {

  private val selectColumns =
    fr"""SELECT mbankid, mcustomerid, mid, mcustomernumber, mtype, mnumber, missuedate, missueplace,
                mexpirydate
         FROM mappedkycdocument"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedKycDocument = row match {
    case (bankId, customerId, id, customerNumber, docType, number, issueDate, issuePlace, expiryDate) =>
      MappedKycDocument(bankId.orNull, customerId.orNull, id.orNull, customerNumber.orNull,
        docType.orNull, number.orNull, issueDate.orNull, issuePlace.orNull, expiryDate.orNull)
  }

  private def query(condition: Fragment): List[MappedKycDocument] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Newest first — updatedat is what orders the caller's list, so writes must stamp it. */
  def findAllByCustomerId(customerId: String): List[MappedKycDocument] =
    query(fr"WHERE mcustomerid = $customerId ORDER BY updatedat DESC, id DESC")

  def upsert(bankId: String, customerId: String, id: String, customerNumber: String,
             docType: String, number: String, issueDate: Date, issuePlace: String,
             expiryDate: Date): MappedKycDocument = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val issue = new java.sql.Timestamp(issueDate.getTime)
    val expiry = new java.sql.Timestamp(expiryDate.getTime)
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE mappedkycdocument SET mbankid = $bankId, mcustomerid = $customerId,
              mcustomernumber = $customerNumber, mtype = $docType, mnumber = $number,
              missuedate = $issue, missueplace = $issuePlace, mexpirydate = $expiry,
              updatedat = $now
            WHERE mid = $id""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedkycdocument
              (mbankid, mcustomerid, mid, mcustomernumber, mtype, mnumber, missuedate, missueplace,
               mexpirydate, createdat, updatedat)
              VALUES ($bankId, $customerId, $id, $customerNumber, $docType, $number, $issue,
               $issuePlace, $expiry, $now, $now)"""
          .update.run)
    }
    MappedKycDocument(bankId, customerId, id, customerNumber, docType, number, issueDate,
      issuePlace, expiryDate)
  }

  def deleteByCustomerId(customerId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycdocument WHERE mcustomerid = $customerId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycdocument".update.run)
    ()
  }
}

object MappedKycDocumentsProvider extends KycDocumentProvider {

  // TODO Add bankId (customerNumber is not unique)
  override def getKycDocuments(customerId: String): List[MappedKycDocument] =
    MappedKycDocument.findAllByCustomerId(customerId)

  override def addKycDocuments(bankId: String, customerId: String, id: String,
                               customerNumber: String, `type`: String, number: String,
                               issueDate: Date, issuePlace: String,
                               expiryDate: Date): Box[MappedKycDocument] =
    Full(MappedKycDocument.upsert(bankId, customerId, id, customerNumber, `type`, number,
      issueDate, issuePlace, expiryDate))
}
