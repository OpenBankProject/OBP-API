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

package code.chat

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * Doobie queries for chat message retrieval.
 *
 * Tables used:
 * - chatmessage  (Lift Mapper table: ChatMessage)
 * - resourceuser (Lift Mapper table: ResourceUser) — joined for sender username/provider
 * - consumer     (Lift Mapper table: Consumer)    — joined for sender consumer app name
 * - reaction     (Lift Mapper table: Reaction)
 */
object DoobieChatMessageQueries extends MdcLoggable {

  case class ChatMessageRow(
    chatMessageId: String,
    chatRoomId: String,
    senderUserId: String,
    senderConsumerId: String,
    senderUsername: String,
    senderProvider: String,
    senderConsumerName: String,
    content: String,
    messageType: String,
    mentionedUserIds: Option[String],
    replyToMessageId: String,
    threadId: String,
    isDeleted: Boolean,
    createdAt: Timestamp,
    updatedAt: Timestamp
  )

  case class ReactionRow(
    reactionId: String,
    chatMessageId: String,
    userId: String,
    emoji: String,
    createdAt: Timestamp
  )

  def getMessagesWithReactions(
    chatRoomId: String,
    fromDate: Date,
    toDate: Date,
    limit: Int,
    offset: Int
  ): (List[ChatMessageRow], Map[String, List[ReactionRow]]) = {
    val fromTs = new Timestamp(fromDate.getTime)
    val toTs = new Timestamp(toDate.getTime)

    val messagesQuery: ConnectionIO[List[ChatMessageRow]] =
      sql"""SELECT m.chatmessageid, m.chatroomid, m.senderuserid, m.senderconsumerid,
                   COALESCE(u.name_, ''), COALESCE(u.provider_, ''),
                   COALESCE(c.name, ''),
                   m.content, m.messagetype, m.mentioneduserids, m.replytomessageid,
                   m.threadid, m.isdeleted, m.createdat, m.updatedat
            FROM chatmessage m
            LEFT JOIN resourceuser u ON u.userid_ = m.senderuserid
            LEFT JOIN consumer c ON c.consumerid = m.senderconsumerid
            WHERE m.chatroomid = $chatRoomId
              AND m.createdat >= $fromTs
              AND m.createdat <= $toTs
            ORDER BY m.id ASC
            LIMIT $limit OFFSET $offset"""
        .query[ChatMessageRow]
        .to[List]

    val messages = DoobieUtil.runQuery(messagesQuery)

    val reactions: Map[String, List[ReactionRow]] = if (messages.isEmpty) {
      Map.empty
    } else {
      val messageIds = messages.map(_.chatMessageId)
      val inClause = messageIds.map(id => fr"$id").reduceLeft((a, b) => a ++ fr"," ++ b)

      val reactionsQuery: ConnectionIO[List[ReactionRow]] =
        (fr"""SELECT reactionid, chatmessageid, userid, emoji, createdat
              FROM reaction
              WHERE chatmessageid IN (""" ++ inClause ++ fr")")
          .query[ReactionRow]
          .to[List]

      DoobieUtil.runQuery(reactionsQuery).groupBy(_.chatMessageId)
    }

    (messages, reactions)
  }
}
