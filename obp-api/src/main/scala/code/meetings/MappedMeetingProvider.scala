/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
        .query[(Option[String], Option[String], Option[String], Option[String])].to[List])
      .map { case (name, phone, email, status) =>
        Invitee(ContactDetails(name.orNull, phone.orNull, email.orNull), status.orNull) }

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

  // The user takes part in the meeting as its customer or staff user, else it is none of their business.
  // present carries the public user ids the numeric RESOURCEUSER keys resolve to, and an unresolved
  // key is "", which no user id equals.
  private def isParticipant(meeting: MappedMeeting, user: User): Boolean =
    meeting.present.customerUserId == user.userId || meeting.present.staffUserId == user.userId

  override def getMeeting(bankId: BankId, user: User, meetingId: String): Box[Meeting] =
    // Only a meeting the user takes part in is found, so nobody reads other people's contact details or meeting keys
    MappedMeeting.find(bankId.toString, meetingId).filter(isParticipant(_, user))

  override def getMeetings(bankId: BankId, user: User): Box[List[Meeting]] =
    // Only the meetings the user takes part in, so nobody lists other people's contact details or meeting keys
    tryo(MappedMeeting.findAllByBankId(bankId.toString).filter(isParticipant(_, user)))

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
