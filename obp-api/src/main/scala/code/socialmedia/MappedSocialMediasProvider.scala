package code.socialmedia

import java.util.Date

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * A social-media handle claimed by a customer.
 *
 * mcustomernumber carries a unique index, so a customer has at most one handle row — addSocialMedias
 * returning false for a repeat customer number is the constraint firing, not a validation check.
 */
case class MappedSocialMedia(
  customerNumber: String,
  `type`: String,
  handle: String,
  dateAdded: Date,
  dateActivated: Date
) extends SocialMedia

object MappedSocialMedia {

  private val selectColumns =
    fr"SELECT mcustomernumber, mtype, mhandle, mdateadded, mdateactivated FROM mappedsocialmedia"

  private type Row = (Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedSocialMedia = row match {
    case (customerNumber, mediaType, handle, dateAdded, dateActivated) =>
      MappedSocialMedia(customerNumber.orNull, mediaType.orNull, handle.orNull, dateAdded.orNull,
        dateActivated.orNull)
  }

  private def query(condition: Fragment): List[MappedSocialMedia] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Newest first — updatedat is what orders the caller's list, so writes must stamp it. */
  def findAllByCustomerNumber(customerNumber: String): List[MappedSocialMedia] =
    query(fr"WHERE mcustomernumber = $customerNumber ORDER BY updatedat DESC, id DESC")

  /**
   * Mapper's `.save` swallowed a failing write and returned false; the unique index on
   * mcustomernumber makes a second handle for the same customer exactly that case, so the
   * INSERT is caught rather than allowed to propagate.
   */
  def insert(customerNumber: String, mediaType: String, handle: String, dateAdded: Date,
             dateActivated: Date): Boolean = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val added = new java.sql.Timestamp(dateAdded.getTime)
    val activated = new java.sql.Timestamp(dateActivated.getTime)
    net.liftweb.util.Helpers.tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedsocialmedia
              (mcustomernumber, mtype, mhandle, mdateadded, mdateactivated, createdat, updatedat)
              VALUES ($customerNumber, $mediaType, $handle, $added, $activated, $now, $now)"""
          .update.run)
    }.isDefined
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedsocialmedia".update.run)
    ()
  }
}

object MappedSocialMediasProvider extends SocialMediaHandleProvider {

  override def getSocialMedias(customerNumber: String): List[MappedSocialMedia] =
    MappedSocialMedia.findAllByCustomerNumber(customerNumber)

  override def addSocialMedias(customerNumber: String, `type`: String, handle: String,
                               dateAdded: Date, dateActivated: Date): Boolean =
    MappedSocialMedia.insert(customerNumber, `type`, handle, dateAdded, dateActivated)
}
