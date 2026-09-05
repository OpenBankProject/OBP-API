package code.chat

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/**
 * One user's membership of one chat room.
 *
 * `permissions` is a comma-joined string in a single column rather than a child table. The reader
 * tolerates NULL and empty because rows predating a given permission set have both.
 */
case class Participant(
  participantId: String,
  chatRoomId: String,
  userId: String,
  consumerId: String,
  permissions: List[String],
  webhookUrl: String,
  joinedAt: Date,
  lastReadAt: Date,
  isMuted: Boolean
) extends ParticipantTrait

object Participant {

  private val selectColumns =
    fr"""SELECT participantid, chatroomid, userid, consumerid, permissions, webhookurl, joinedat,
                lastreadat, ismuted
         FROM participant"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[Boolean])

  private def splitPermissions(raw: Option[String]): List[String] =
    raw.filter(_.nonEmpty).toList.flatMap(_.split(",").map(_.trim).filter(_.nonEmpty))

  private def fromRow(row: Row): Participant = row match {
    case (participantId, chatRoomId, userId, consumerId, permissions, webhookUrl, joinedAt,
          lastReadAt, isMuted) =>
      Participant(participantId.orNull, chatRoomId.orNull, userId.orNull, consumerId.orNull,
        splitPermissions(permissions), webhookUrl.orNull, joinedAt.orNull, lastReadAt.orNull,
        isMuted.getOrElse(false))
  }

  private def query(condition: Fragment): List[Participant] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(chatRoomId: String, userId: String, consumerId: String, permissions: List[String],
             webhookUrl: String): Participant = {
    val participantId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO participant
            (participantid, chatroomid, userid, consumerid, permissions, webhookurl, joinedat,
             lastreadat, ismuted)
            VALUES ($participantId, $chatRoomId, $userId, $consumerId, ${permissions.mkString(",")},
             $webhookUrl, $now, $now, false)"""
        .update.run)
    Participant(participantId, chatRoomId, userId, consumerId, permissions, webhookUrl, now, now,
      isMuted = false)
  }

  def find(chatRoomId: String, userId: String): Box[Participant] =
    query(fr"WHERE chatroomid = $chatRoomId AND userid = $userId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findByConsumerId(chatRoomId: String, consumerId: String): Box[Participant] =
    query(fr"WHERE chatroomid = $chatRoomId AND consumerid = $consumerId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllByChatRoomId(chatRoomId: String): List[Participant] =
    query(fr"WHERE chatroomid = $chatRoomId")

  def findAllByUserId(userId: String): List[Participant] =
    query(fr"WHERE userid = $userId")

  /** Every membership, for the digest scheduler, which groups them by user itself. */
  def findAll(): List[Participant] = query(Fragment.empty)

  private def update(chatRoomId: String, userId: String, set: Fragment): Box[Participant] =
    find(chatRoomId, userId).flatMap { _ =>
      DoobieUtil.runUpdate(
        (fr"UPDATE participant SET" ++ set ++
          fr"WHERE chatroomid = $chatRoomId AND userid = $userId").update.run)
      find(chatRoomId, userId)
    }

  def updatePermissions(chatRoomId: String, userId: String, permissions: List[String]): Box[Participant] =
    update(chatRoomId, userId, fr"permissions = ${permissions.mkString(",")}")

  def updateWebhookUrl(chatRoomId: String, userId: String, webhookUrl: String): Box[Participant] =
    update(chatRoomId, userId, fr"webhookurl = $webhookUrl")

  def updateLastReadAt(chatRoomId: String, userId: String): Box[Participant] =
    update(chatRoomId, userId,
      fr"lastreadat = ${new java.sql.Timestamp(System.currentTimeMillis())}")

  def updateMuted(chatRoomId: String, userId: String, isMuted: Boolean): Box[Participant] =
    update(chatRoomId, userId, fr"ismuted = $isMuted")

  def delete(chatRoomId: String, userId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM participant WHERE chatroomid = $chatRoomId AND userid = $userId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM participant".update.run)
    ()
  }
}

object MappedParticipantProvider extends ParticipantProvider {

  override def addParticipant(chatRoomId: String, userId: String, consumerId: String,
                              permissions: List[String], webhookUrl: String): Box[ParticipantTrait] =
    tryo(Participant.insert(chatRoomId, userId, consumerId, permissions, webhookUrl))

  override def getParticipant(chatRoomId: String, userId: String): Box[ParticipantTrait] =
    Participant.find(chatRoomId, userId)

  override def getParticipantByConsumerId(chatRoomId: String, consumerId: String): Box[ParticipantTrait] =
    Participant.findByConsumerId(chatRoomId, consumerId)

  override def getParticipants(chatRoomId: String): Box[List[ParticipantTrait]] =
    tryo(Participant.findAllByChatRoomId(chatRoomId))

  override def getParticipantRoomsByUserId(userId: String): Box[List[ParticipantTrait]] =
    tryo(Participant.findAllByUserId(userId))

  override def updateParticipantPermissions(chatRoomId: String, userId: String,
                                            permissions: List[String]): Box[ParticipantTrait] =
    Participant.updatePermissions(chatRoomId, userId, permissions)

  override def updateWebhookUrl(chatRoomId: String, userId: String,
                                webhookUrl: String): Box[ParticipantTrait] =
    Participant.updateWebhookUrl(chatRoomId, userId, webhookUrl)

  override def updateLastReadAt(chatRoomId: String, userId: String): Box[ParticipantTrait] =
    Participant.updateLastReadAt(chatRoomId, userId)

  override def updateMuted(chatRoomId: String, userId: String, isMuted: Boolean): Box[ParticipantTrait] =
    Participant.updateMuted(chatRoomId, userId, isMuted)

  override def removeParticipant(chatRoomId: String, userId: String): Box[Boolean] =
    Participant.find(chatRoomId, userId).flatMap(_ => tryo(Participant.delete(chatRoomId, userId)))
}
