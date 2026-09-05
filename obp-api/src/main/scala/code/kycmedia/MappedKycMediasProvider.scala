package code.kycmedias

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycMedia
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Full}

/**
 * A media item supporting a KYC check or document.
 *
 * `mid` is the caller-supplied id that decides update-vs-insert, and carries a unique index that
 * keeps that decision single-valued.
 */
case class MappedKycMedia(
  bankId: String,
  customerId: String,
  idKycMedia: String,
  customerNumber: String,
  `type`: String,
  url: String,
  date: Date,
  relatesToKycDocumentId: String,
  relatesToKycCheckId: String
) extends KycMedia

object MappedKycMedia {

  private val selectColumns =
    fr"""SELECT mbankid, mcustomerid, mid, mcustomernumber, mtype, murl, mdate,
                mrelatestokycdocumentid, mrelatestokyccheckid
         FROM mappedkycmedia"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[String], Option[String])

  private def fromRow(row: Row): MappedKycMedia = row match {
    case (bankId, customerId, id, customerNumber, mediaType, url, date, documentId, checkId) =>
      MappedKycMedia(bankId.orNull, customerId.orNull, id.orNull, customerNumber.orNull,
        mediaType.orNull, url.orNull, date.orNull, documentId.orNull, checkId.orNull)
  }

  private def query(condition: Fragment): List[MappedKycMedia] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Newest first — updatedat is what orders the caller's list, so writes must stamp it. */
  def findAllByCustomerId(customerId: String): List[MappedKycMedia] =
    query(fr"WHERE mcustomerid = $customerId ORDER BY updatedat DESC, id DESC")

  def upsert(bankId: String, customerId: String, id: String, customerNumber: String,
             mediaType: String, url: String, date: Date, relatesToKycDocumentId: String,
             relatesToKycCheckId: String): MappedKycMedia = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val ts = new java.sql.Timestamp(date.getTime)
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE mappedkycmedia SET mbankid = $bankId, mcustomerid = $customerId,
              mcustomernumber = $customerNumber, mtype = $mediaType, murl = $url, mdate = $ts,
              mrelatestokycdocumentid = $relatesToKycDocumentId,
              mrelatestokyccheckid = $relatesToKycCheckId, updatedat = $now
            WHERE mid = $id""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedkycmedia
              (mbankid, mcustomerid, mid, mcustomernumber, mtype, murl, mdate,
               mrelatestokycdocumentid, mrelatestokyccheckid, createdat, updatedat)
              VALUES ($bankId, $customerId, $id, $customerNumber, $mediaType, $url, $ts,
               $relatesToKycDocumentId, $relatesToKycCheckId, $now, $now)"""
          .update.run)
    }
    MappedKycMedia(bankId, customerId, id, customerNumber, mediaType, url, date,
      relatesToKycDocumentId, relatesToKycCheckId)
  }

  def deleteByCustomerId(customerId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycmedia WHERE mcustomerid = $customerId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycmedia".update.run)
    ()
  }
}

object MappedKycMediasProvider extends KycMediaProvider {

  override def getKycMedias(customerId: String): List[MappedKycMedia] =
    MappedKycMedia.findAllByCustomerId(customerId)

  override def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String,
                            `type`: String, url: String, date: Date, relatesToKycDocumentId: String,
                            relatesToKycCheckId: String): Box[KycMedia] =
    Full(MappedKycMedia.upsert(bankId, customerId, id, customerNumber, `type`, url, date,
      relatesToKycDocumentId, relatesToKycCheckId))
}
