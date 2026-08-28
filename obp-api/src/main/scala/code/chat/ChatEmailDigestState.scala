package code.chat

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

/**
 * Per-user state for the chat email digest: when we last emailed them. One row per user, created
 * lazily on first digest.
 *
 * Arrived from upstream as a Lift Mapper entity, the only one added since this branch emptied
 * ToSchemify.models. Carried across rather than added back: with `models = Nil` Schemifier creates
 * nothing, so a Mapper entity here would compile and then fail at runtime against a table Liquibase
 * never made. The changelog creates `chat_email_digest_state` instead.
 */
case class ChatEmailDigestState(userId: String, lastNotifiedAt: Option[Date])

object ChatEmailDigestState {

  private type Row = (Option[String], Option[java.sql.Timestamp])

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def fromRow(r: Row): ChatEmailDigestState =
    ChatEmailDigestState(r._1.orNull, r._2.map(t => new Date(t.getTime)))

  private def find(userId: String): Box[ChatEmailDigestState] =
    DoobieUtil.runQuery(
      sql"""SELECT user_id, last_notified_at FROM chat_email_digest_state
            WHERE user_id = $userId LIMIT 1""".query[Row].option
    ) match {
      case Some(r) => Full(fromRow(r))
      case None    => Empty
    }

  def lastNotifiedAt(userId: String): Option[Date] = find(userId).toOption.flatMap(_.lastNotifiedAt)

  /**
   * Upsert, expressed as the Mapper version was - look, then insert or update - rather than as a
   * vendor-specific ON CONFLICT, since the changelog targets several databases.
   */
  def recordNotified(userId: String, at: Date): Box[ChatEmailDigestState] = tryo {
    val ts = new java.sql.Timestamp(at.getTime)
    find(userId) match {
      case Full(_) =>
        DoobieUtil.runUpdate(
          sql"UPDATE chat_email_digest_state SET last_notified_at = $ts WHERE user_id = $userId"
            .update.run)
      case _ =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO chat_email_digest_state (user_id, last_notified_at)
                VALUES ($userId, $ts)""".update.run)
    }
    ChatEmailDigestState(userId, Some(at))
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM chat_email_digest_state".update.run)
    ()
  }
}
