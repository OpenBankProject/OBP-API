package code.chat

import java.util.Date

import code.api.util.APIUtil.generateUUID
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/**
 * A chat room.
 *
 * `isOpenRoom` rooms have implicit participants ("everyone"), so they carry no Participant rows —
 * which is why membership queries have to union them in separately rather than joining.
 *
 * `lastMessageAt`/`lastMessagePreview`/`lastMessageSenderUsername` are a denormalised copy of the
 * newest message, maintained by updateLastMessageInfo so a room list does not need a per-room
 * message query.
 */
case class ChatRoom(
  chatRoomId: String,
  bankId: String,
  name: String,
  description: String,
  joiningKey: String,
  createdByUserId: String,
  isOpenRoom: Boolean,
  isArchived: Boolean,
  lastMessageAt: Option[Date],
  lastMessagePreview: String,
  lastMessageSenderUsername: String,
  createdDate: Date,
  updatedDate: Date
) extends ChatRoomTrait

object ChatRoom {

  private val selectColumns =
    fr"""SELECT chatroomid, bankid, name, description, joiningkey, createdbyuserid, isopenroom,
                isarchived, lastmessageat, lastmessagepreview, lastmessagesenderusername,
                createdat, updatedat
         FROM chatroom"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Boolean], Option[Boolean], Option[java.sql.Timestamp],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def fromRow(row: Row): ChatRoom = row match {
    case (chatRoomId, bankId, name, description, joiningKey, createdByUserId, isOpenRoom,
          isArchived, lastMessageAt, lastMessagePreview, lastMessageSenderUsername,
          createdAt, updatedAt) =>
      ChatRoom(chatRoomId.orNull, bankId.orNull, name.orNull, description.getOrElse(""),
        joiningKey.orNull, createdByUserId.orNull, isOpenRoom.getOrElse(false),
        isArchived.getOrElse(false), lastMessageAt.map(ts => ts: Date), lastMessagePreview.orNull,
        lastMessageSenderUsername.orNull, createdAt.orNull, updatedAt.orNull)
  }

  private def query(condition: Fragment): List[ChatRoom] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[ChatRoom] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(bankId: String, name: String, description: String, createdByUserId: String,
             isOpenRoom: Boolean): ChatRoom = {
    val chatRoomId = generateUUID()
    val joiningKey = generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO chatroom
            (chatroomid, bankid, name, description, joiningkey, createdbyuserid, isopenroom,
             isarchived, lastmessageat, lastmessagepreview, lastmessagesenderusername,
             createdat, updatedat)
            VALUES ($chatRoomId, $bankId, $name, $description, $joiningKey, $createdByUserId,
             $isOpenRoom, false, NULL, '', '', $now, $now)"""
        .update.run)
    ChatRoom(chatRoomId, bankId, name, description, joiningKey, createdByUserId, isOpenRoom,
      isArchived = false, None, "", "", now, now)
  }

  def findByChatRoomId(chatRoomId: String): Box[ChatRoom] =
    one(fr"WHERE chatroomid = $chatRoomId")

  def findByBankIdAndName(bankId: String, name: String): Box[ChatRoom] =
    one(fr"WHERE bankid = $bankId AND name = $name")

  def findByJoiningKey(joiningKey: String): Box[ChatRoom] =
    one(fr"WHERE joiningkey = $joiningKey")

  def findAllByBankId(bankId: String): List[ChatRoom] =
    query(fr"WHERE bankid = $bankId")

  def findAllByChatRoomIds(chatRoomIds: List[String]): List[ChatRoom] =
    if (chatRoomIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"chatroomid",
        cats.data.NonEmptyList.fromListUnsafe(chatRoomIds.distinct))
      query(fr"WHERE " ++ in)
    }

  def findAllByBankIdAndChatRoomIds(bankId: String, chatRoomIds: List[String]): List[ChatRoom] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows — not "no filter".
    if (chatRoomIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"chatroomid",
        cats.data.NonEmptyList.fromListUnsafe(chatRoomIds.distinct))
      query(fr"WHERE bankid = $bankId AND " ++ in)
    }

  def findAllOpenByBankId(bankId: String): List[ChatRoom] =
    query(fr"WHERE bankid = $bankId AND isopenroom = true")

  private def update(chatRoomId: String, set: Fragment): Box[ChatRoom] =
    findByChatRoomId(chatRoomId).flatMap { _ =>
      DoobieUtil.runUpdate(
        (fr"UPDATE chatroom SET" ++ set ++
          fr", updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}" ++
          fr"WHERE chatroomid = $chatRoomId").update.run)
      findByChatRoomId(chatRoomId)
    }

  def updateNameAndDescription(chatRoomId: String, name: Option[String],
                               description: Option[String]): Box[ChatRoom] = {
    val sets = List(name.map(n => fr"name = $n"), description.map(d => fr"description = $d")).flatten
    // Both absent means the caller asked for no change; return the room rather than issuing an
    // UPDATE with an empty SET list, which is not valid SQL.
    if (sets.isEmpty) findByChatRoomId(chatRoomId)
    else update(chatRoomId, sets.reduce((a, b) => a ++ fr"," ++ b))
  }

  def updateIsOpenRoom(chatRoomId: String, isOpenRoom: Boolean): Box[ChatRoom] =
    update(chatRoomId, fr"isopenroom = $isOpenRoom")

  def updateLastMessageInfo(chatRoomId: String, lastMessageAt: Date, preview: String,
                            senderUsername: String): Box[ChatRoom] =
    update(chatRoomId,
      fr"lastmessageat = ${new java.sql.Timestamp(lastMessageAt.getTime)}," ++
      fr"lastmessagepreview = $preview, lastmessagesenderusername = $senderUsername")

  def archive(chatRoomId: String): Box[ChatRoom] = update(chatRoomId, fr"isarchived = true")

  def updateJoiningKey(chatRoomId: String, joiningKey: String): Box[ChatRoom] =
    update(chatRoomId, fr"joiningkey = $joiningKey")

  def delete(chatRoomId: String): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM chatroom WHERE chatroomid = $chatRoomId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM chatroom".update.run)
    ()
  }
}

object MappedChatRoomProvider extends ChatRoomProvider {

  override def createChatRoom(bankId: String, name: String, description: String,
                              createdByUserId: String): Box[ChatRoomTrait] =
    tryo(ChatRoom.insert(bankId, name, description, createdByUserId, isOpenRoom = false))

  override def getChatRoom(chatRoomId: String): Box[ChatRoomTrait] =
    ChatRoom.findByChatRoomId(chatRoomId)

  override def getChatRoomByBankIdAndName(bankId: String, name: String): Box[ChatRoomTrait] =
    ChatRoom.findByBankIdAndName(bankId, name)

  override def getChatRoomsByBankId(bankId: String): Box[List[ChatRoomTrait]] =
    tryo(ChatRoom.findAllByBankId(bankId))

  override def getChatRoomsByBankIdForUser(bankId: String, userId: String): Box[List[ChatRoomTrait]] =
    tryo {
      val participantRoomIds = Participant.findAllByUserId(userId).map(_.chatRoomId)
      val explicitRooms = ChatRoom.findAllByBankIdAndChatRoomIds(bankId, participantRoomIds)
      val openRooms = ChatRoom.findAllOpenByBankId(bankId)
      (explicitRooms ++ openRooms).groupBy(_.chatRoomId).values.map(_.head).toList
    }

  override def getChatRoomByJoiningKey(joiningKey: String): Box[ChatRoomTrait] =
    ChatRoom.findByJoiningKey(joiningKey)

  override def searchChatRoomsForUserWithParticipants(
    userId: String,
    requiredParticipantUserIds: List[String],
    exactParticipants: Boolean
  ): Box[List[ChatRoomTrait]] = {
    tryo {
      // 1. Find every room where the current user is an explicit participant.
      val myRoomIds = Participant.findAllByUserId(userId).map(_.chatRoomId).distinct
      val myRooms = ChatRoom.findAllByChatRoomIds(myRoomIds)

      // 2. For each candidate room, fetch the full participant set and apply
      //    the requested filters.
      val requiredSet = requiredParticipantUserIds.toSet
      val expectedExactSize = requiredSet.size + 1 // +1 for the current user

      myRooms.filter { room =>
        // Open rooms have implicit participants ("everyone"), so an exact-match
        // query is meaningless against them — exclude them in that case.
        if (exactParticipants && room.isOpenRoom) {
          false
        } else {
          val participantUserIds = Participant.findAllByChatRoomId(room.chatRoomId)
            .map(_.userId)
            .toSet
          val containsAllRequired = requiredSet.subsetOf(participantUserIds)
          if (!containsAllRequired) {
            false
          } else if (exactParticipants) {
            participantUserIds.size == expectedExactSize
          } else {
            true
          }
        }
      }
    }
  }

  override def updateChatRoom(chatRoomId: String, name: Option[String],
                              description: Option[String]): Box[ChatRoomTrait] =
    ChatRoom.updateNameAndDescription(chatRoomId, name, description)

  override def setIsOpenRoom(chatRoomId: String, isOpenRoom: Boolean): Box[ChatRoomTrait] =
    ChatRoom.updateIsOpenRoom(chatRoomId, isOpenRoom)

  override def updateLastMessageInfo(chatRoomId: String, lastMessageAt: Date, preview: String,
                                     senderUsername: String): Box[ChatRoomTrait] =
    ChatRoom.updateLastMessageInfo(chatRoomId, lastMessageAt,
      if (preview.length > 100) preview.substring(0, 100) else preview, senderUsername)

  override def archiveChatRoom(chatRoomId: String): Box[ChatRoomTrait] =
    ChatRoom.archive(chatRoomId)

  override def deleteChatRoom(chatRoomId: String): Box[Boolean] =
    ChatRoom.findByChatRoomId(chatRoomId).flatMap(_ => tryo(ChatRoom.delete(chatRoomId)))

  override def refreshJoiningKey(chatRoomId: String): Box[ChatRoomTrait] =
    ChatRoom.updateJoiningKey(chatRoomId, generateUUID())

  override def getOrCreateDefaultRoom(): Box[ChatRoomTrait] = {
    getChatRoomByBankIdAndName("", "general") match {
      case Full(room) => Full(room)
      case _ =>
        tryo {
          // "system" here is a sentinel, not a real user_id — the default room is
          // auto-provisioned and has no human creator. Every other caller passes a real user_id.
          ChatRoom.insert("", "general", "Default system-wide chat room for all users", "system",
            isOpenRoom = true)
        }
    }
  }
}
