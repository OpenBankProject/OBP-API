package code.meetings

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.{BankId, ContactDetails, Invitee, Meeting, MeetingKeys, MeetingPresent, User}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

/**
 * A scheduled meeting between a customer and bank staff.
 *
 * `present` reports the two participants' public user ids, resolved from the numeric RESOURCEUSER
 * keys the columns actually hold. `mstaffuserid` is never written — createMeeting has that line
 * commented out — so the staff id is always "".
 *
 * `invitees` came from a Lift OneToMany ordered by id ascending; the query below keeps that order,
 * which is the order they were supplied in.
 */
case class MappedMeeting(
  meetingId: String,
  bankId: String,
  when: Date,
  providerId: String,
  purposeId: String,
  private val sessionId: String,
  private val customerToken: String,
  private val staffToken: String,
  private val creatorName: String,
  private val creatorPhone: String,
  private val creatorEmail: String,
  private val staffUserId: String,
  private val customerUserId: String,
  private val meetingKey: Long
) extends Meeting {
  override def keys: MeetingKeys = MeetingKeys(sessionId, customerToken, staffToken)
  override def present: MeetingPresent = MeetingPresent(staffUserId, customerUserId)
  override def creator: ContactDetails = ContactDetails(creatorName, creatorPhone, creatorEmail)
  override def invitees: List[Invitee] = MappedMeetingInvitee.findAllByMeetingKey(meetingKey)
}

object MappedMeeting {

  // mcustomeruserid / mstaffuserid hold RESOURCEUSER's numeric key, so the public ids come from
  // the joins. An unresolved key yields "" — which is what mStaffUserId always does, since it is
  // never written.
  private val selectColumns =
    fr"""SELECT m.mmeetingid, m.mbankid, m.mwhen, m.mproviderid, m.mpurposeid, m.msessionid,
                m.mcustomertoken, m.mstafftoken, m.mcreatorname, m.mcreatorphone, m.mcreatoremail,
                COALESCE(s.userid_, ''), COALESCE(c.userid_, ''), m.id
         FROM mappedmeeting m
         LEFT JOIN resourceuser s ON s.id = m.mstaffuserid
         LEFT JOIN resourceuser c ON c.id = m.mcustomeruserid"""

  private type Row = (String, String, java.sql.Timestamp, String, String, String, String, String,
    String, String, String, String, String, Long)

  private def fromRow(row: Row): MappedMeeting = row match {
    case (meetingId, bankId, when, providerId, purposeId, sessionId, customerToken, staffToken,
          creatorName, creatorPhone, creatorEmail, staffUserId, customerUserId, meetingKey) =>
      MappedMeeting(meetingId, bankId, when, providerId, purposeId, sessionId, customerToken,
        staffToken, creatorName, creatorPhone, creatorEmail, staffUserId, customerUserId, meetingKey)
  }

  private def query(condition: Fragment): List[MappedMeeting] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(bankId: String, meetingId: String): Box[MappedMeeting] =
    query(fr"""WHERE m.mbankid = $bankId AND m.mmeetingid = $meetingId
               ORDER BY m.mwhen DESC, m.id DESC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByBankId(bankId: String): List[MappedMeeting] =
    query(fr"WHERE m.mbankid = $bankId ORDER BY m.mwhen DESC, m.id DESC")

  /** Returns the new row's numeric key, which the invitees hang off. */
  def insert(bankId: String, customerUserKey: Long, providerId: String, purposeId: String,
             when: Date, sessionId: String, customerToken: String, staffToken: String,
             creator: ContactDetails): Long = {
    val meetingId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedmeeting
            (mmeetingid, mbankid, mcustomeruserid, mproviderid, mpurposeid, mwhen, msessionid,
             mcustomertoken, mstafftoken, mcreatorname, mcreatorphone, mcreatoremail, createdat,
             updatedat)
            VALUES ($meetingId, $bankId, $customerUserKey, $providerId, $purposeId,
             ${new java.sql.Timestamp(when.getTime)}, $sessionId, $customerToken, $staffToken,
             ${creator.name}, ${creator.phone}, ${creator.email}, $now, $now)"""
        .update.run)
    DoobieUtil.runQuery(
      sql"SELECT id FROM mappedmeeting WHERE mmeetingid = $meetingId".query[Long].unique)
  }

  def findByKey(meetingKey: Long): Box[MappedMeeting] =
    query(fr"WHERE m.id = $meetingKey LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeeting".update.run)
    ()
  }
}

object MappedMeetingInvitee {

  def findAllByMeetingKey(meetingKey: Long): List[Invitee] =
    DoobieUtil.runQuery(
      sql"""SELECT mname, mphone, memail, mstatus FROM mappedmeetinginvitee
            WHERE mmappedmeeting = $meetingKey ORDER BY id ASC"""
        .query[(String, String, String, String)].to[List])
      .map { case (name, phone, email, status) =>
        Invitee(ContactDetails(name, phone, email), status) }

  def insert(meetingKey: Long, invitee: Invitee): Unit = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedmeetinginvitee (mmappedmeeting, mname, mphone, memail, mstatus)
            VALUES ($meetingKey, ${invitee.contactDetails.name}, ${invitee.contactDetails.phone},
             ${invitee.contactDetails.email}, ${invitee.status})"""
        .update.run)
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeetinginvitee".update.run)
    ()
  }
}

object MappedMeetingProvider extends MeetingProvider {

  override def getMeeting(bankId: BankId, user: User, meetingId: String): Box[Meeting] =
    // TODO Need to check permissions (user)
    MappedMeeting.find(bankId.toString, meetingId)

  override def getMeetings(bankId: BankId, user: User): Box[List[Meeting]] =
    // TODO Need to check permissions (user)
    tryo(MappedMeeting.findAllByBankId(bankId.toString))

  override def createMeeting(
    bankId: BankId,
    staffUser: User,
    customerUser: User,
    providerId: String,
    purposeId: String,
    when: Date,
    sessionId: String,
    customerToken: String,
    staffToken: String,
    creator: ContactDetails,
    invitees: List[Invitee],
  ): Box[Meeting] =
    for {
      // staffUser is accepted and not stored: Mapper's .mStaffUserId line is commented out, so the
      // column has always been NULL and present.staffUserId has always been "". Preserved.
      meetingKey <- tryo {
        MappedMeeting.insert(bankId.value.toString, customerUser.userPrimaryKey.value, providerId,
          purposeId, when, sessionId, customerToken, staffToken, creator)
      } ?~! ErrorMessages.CreateMeetingException
      _ <- tryo {
        invitees.foreach(MappedMeetingInvitee.insert(meetingKey, _))
      } ?~! ErrorMessages.CreateMeetingInviteeException
      createdMeeting <- MappedMeeting.findByKey(meetingKey)
    } yield createdMeeting
}
