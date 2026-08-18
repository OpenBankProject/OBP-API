package code.chat

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One emoji reaction by one user on one message. */
case class Reaction(
  reactionId: String,
  chatMessageId: String,
  userId: String,
  emoji: String,
  createdDate: Date
) extends ReactionTrait

object Reaction {

  private val selectColumns =
    fr"SELECT reactionid, chatmessageid, userid, emoji, createdat FROM reaction"

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): Reaction = row match {
    case (reactionId, chatMessageId, userId, emoji, createdAt) =>
      Reaction(reactionId.orNull, chatMessageId.orNull, userId.orNull, emoji.orNull,
        createdAt.orNull)
  }

  private def query(condition: Fragment): List[Reaction] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /**
   * The unique index on (chatmessageid, userid, emoji) is what makes "react" idempotent: a
   * repeated INSERT is rejected rather than stacking duplicate reactions on a message.
   */
  def insert(chatMessageId: String, userId: String, emoji: String): Reaction = {
    val reactionId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO reaction (reactionid, chatmessageid, userid, emoji, createdat, updatedat)
            VALUES ($reactionId, $chatMessageId, $userId, $emoji, $now, $now)"""
        .update.run)
    Reaction(reactionId, chatMessageId, userId, emoji, now)
  }

  def find(chatMessageId: String, userId: String, emoji: String): Box[Reaction] =
    query(fr"""WHERE chatmessageid = $chatMessageId AND userid = $userId AND emoji = $emoji
               ORDER BY id ASC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def delete(chatMessageId: String, userId: String, emoji: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM reaction
            WHERE chatmessageid = $chatMessageId AND userid = $userId AND emoji = $emoji"""
        .update.run) > 0

  def findAllByChatMessageId(chatMessageId: String): List[Reaction] =
    query(fr"WHERE chatmessageid = $chatMessageId")

  def findAllByChatMessageIds(chatMessageIds: List[String]): List[Reaction] =
    if (chatMessageIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"chatmessageid",
        cats.data.NonEmptyList.fromListUnsafe(chatMessageIds.distinct))
      query(fr"WHERE " ++ in)
    }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM reaction".update.run)
    ()
  }
}

object MappedReactionProvider extends ReactionProvider {

  override def addReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] =
    tryo(Reaction.insert(chatMessageId, userId, emoji))

  override def removeReaction(chatMessageId: String, userId: String, emoji: String): Box[Boolean] =
    Reaction.find(chatMessageId, userId, emoji)
      .flatMap(_ => tryo(Reaction.delete(chatMessageId, userId, emoji)))

  override def getReactions(chatMessageId: String): Box[List[ReactionTrait]] =
    tryo(Reaction.findAllByChatMessageId(chatMessageId))

  override def getReactionsForMessages(chatMessageIds: List[String]): Box[Map[String, List[ReactionTrait]]] =
    tryo {
      if (chatMessageIds.isEmpty) Map.empty[String, List[ReactionTrait]]
      else Reaction.findAllByChatMessageIds(chatMessageIds).groupBy(_.chatMessageId)
    }

  override def getReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] =
    Reaction.find(chatMessageId, userId, emoji)
}
