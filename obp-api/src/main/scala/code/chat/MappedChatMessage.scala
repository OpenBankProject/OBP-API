package code.chat

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/**
 * One message in a chat room.
 *
 * `mentionedUserIds` is a comma-joined string in one column, and unread-mention counting matches it
 * with LIKE '%userId%'. That is a substring match on a delimited list, so it can over-count when one
 * user id is a substring of another — pre-existing, and reproduced rather than corrected here.
 *
 * Deletion is soft: isdeleted flips and the row stays, so threads and reaction rows keep their
 * anchor.
 */
case class ChatMessage(
  chatMessageId: String,
  chatRoomId: String,
  senderUserId: String,
  senderConsumerId: String,
  content: String,
  messageType: String,
  mentionedUserIds: List[String],
  replyToMessageId: String,
  threadId: String,
  isDeleted: Boolean,
  createdDate: Date,
  updatedDate: Date
) extends ChatMessageTrait

object ChatMessage {

  private val selectColumns =
    fr"""SELECT chatmessageid, chatroomid, senderuserid, senderconsumerid, content, messagetype,
                mentioneduserids, replytomessageid, threadid, isdeleted, createdat, updatedat
         FROM chatmessage"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def splitIds(raw: Option[String]): List[String] =
    raw.filter(_.nonEmpty).toList.flatMap(_.split(",").map(_.trim).filter(_.nonEmpty))

  private def fromRow(row: Row): ChatMessage = row match {
    case (chatMessageId, chatRoomId, senderUserId, senderConsumerId, content, messageType,
          mentionedUserIds, replyToMessageId, threadId, isDeleted, createdAt, updatedAt) =>
      ChatMessage(chatMessageId.orNull, chatRoomId.orNull, senderUserId.orNull,
        senderConsumerId.orNull, content.getOrElse(""), messageType.orNull,
        splitIds(mentionedUserIds), replyToMessageId.orNull, threadId.orNull,
        isDeleted.getOrElse(false), createdAt.orNull, updatedAt.orNull)
  }

  private def query(condition: Fragment): List[ChatMessage] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(chatRoomId: String, senderUserId: String, senderConsumerId: String, content: String,
             messageType: String, mentionedUserIds: List[String], replyToMessageId: String,
             threadId: String): ChatMessage = {
    val chatMessageId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO chatmessage
            (chatmessageid, chatroomid, senderuserid, senderconsumerid, content, messagetype,
             mentioneduserids, replytomessageid, threadid, isdeleted, createdat, updatedat)
            VALUES ($chatMessageId, $chatRoomId, $senderUserId, $senderConsumerId, $content,
             $messageType, ${mentionedUserIds.mkString(",")}, $replyToMessageId, $threadId, false,
             $now, $now)"""
        .update.run)
    ChatMessage(chatMessageId, chatRoomId, senderUserId, senderConsumerId, content, messageType,
      mentionedUserIds, replyToMessageId, threadId, isDeleted = false, now, now)
  }

  def findByChatMessageId(chatMessageId: String): Box[ChatMessage] =
    query(fr"WHERE chatmessageid = $chatMessageId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findPage(chatRoomId: String, limit: Int, offset: Int, fromDate: Date, toDate: Date): List[ChatMessage] = {
    val from = new java.sql.Timestamp(fromDate.getTime)
    val to = new java.sql.Timestamp(toDate.getTime)
    query(fr"""WHERE chatroomid = $chatRoomId AND createdat >= $from AND createdat <= $to
               ORDER BY id ASC LIMIT $limit OFFSET $offset""")
  }

  def findThreadReplies(threadId: String): List[ChatMessage] =
    query(fr"WHERE threadid = $threadId ORDER BY id ASC")

  def findMentionsForUser(userId: String, limit: Int, offset: Int): List[ChatMessage] =
    query(fr"""WHERE mentioneduserids LIKE ${"%" + userId + "%"}
               ORDER BY id DESC LIMIT $limit OFFSET $offset""")

  def countUnread(chatRoomId: String, userId: String, since: Date): Long = {
    val ts = new java.sql.Timestamp(since.getTime)
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM chatmessage
            WHERE chatroomid = $chatRoomId AND createdat > $ts AND senderuserid <> $userId"""
        .query[Long].unique)
  }

  def countUnreadMentions(chatRoomId: String, userId: String, since: Date): Long = {
    val ts = new java.sql.Timestamp(since.getTime)
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM chatmessage
            WHERE chatroomid = $chatRoomId AND createdat > $ts AND senderuserid <> $userId
              AND mentioneduserids LIKE ${"%" + userId + "%"}"""
        .query[Long].unique)
  }

  private def update(chatMessageId: String, set: Fragment): Box[ChatMessage] =
    findByChatMessageId(chatMessageId).flatMap { _ =>
      DoobieUtil.runUpdate(
        (fr"UPDATE chatmessage SET" ++ set ++
          fr", updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}" ++
          fr"WHERE chatmessageid = $chatMessageId").update.run)
      findByChatMessageId(chatMessageId)
    }

  def updateContent(chatMessageId: String, content: String): Box[ChatMessage] =
    update(chatMessageId, fr"content = $content")

  def softDelete(chatMessageId: String): Box[ChatMessage] =
    update(chatMessageId, fr"isdeleted = true")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM chatmessage".update.run)
    ()
  }
}

object MappedChatMessageProvider extends ChatMessageProvider {

  /**
   * Mapper's NotBy(SenderUserId, userId) plus By_>(createdAt, ...) is reproduced verbatim, including
   * the 60-day floor below: the count is deliberately bounded so a participant who has not read a
   * busy room for months does not trigger a full-history scan.
   */
  private def effectiveSinceDate(sinceDate: Date): Date = {
    val sixtyDaysAgo = new Date(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
    if (sinceDate.before(sixtyDaysAgo)) sixtyDaysAgo else sinceDate
  }

  override def createMessage(chatRoomId: String, senderUserId: String, senderConsumerId: String,
                             content: String, messageType: String, mentionedUserIds: List[String],
                             replyToMessageId: String, threadId: String): Box[ChatMessageTrait] =
    tryo(ChatMessage.insert(chatRoomId, senderUserId, senderConsumerId, content, messageType,
      mentionedUserIds, replyToMessageId, threadId))

  override def getMessage(chatMessageId: String): Box[ChatMessageTrait] =
    ChatMessage.findByChatMessageId(chatMessageId)

  override def getMessages(chatRoomId: String, limit: Int, offset: Int, fromDate: Date,
                           toDate: Date): Box[List[ChatMessageTrait]] =
    tryo(ChatMessage.findPage(chatRoomId, limit, offset, fromDate, toDate))

  override def getThreadReplies(threadId: String): Box[List[ChatMessageTrait]] =
    tryo(ChatMessage.findThreadReplies(threadId))

  override def getMentionsForUser(userId: String, limit: Int, offset: Int): Box[List[ChatMessageTrait]] =
    tryo(ChatMessage.findMentionsForUser(userId, limit, offset))

  override def getUnreadCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] =
    tryo(ChatMessage.countUnread(chatRoomId, userId, effectiveSinceDate(sinceDate)))

  override def getUnreadMentionCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] =
    tryo(ChatMessage.countUnreadMentions(chatRoomId, userId, effectiveSinceDate(sinceDate)))

  override def updateMessage(chatMessageId: String, content: String): Box[ChatMessageTrait] =
    ChatMessage.updateContent(chatMessageId, content)

  override def softDeleteMessage(chatMessageId: String): Box[ChatMessageTrait] =
    ChatMessage.softDelete(chatMessageId)
}
