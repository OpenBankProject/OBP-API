package code.chat

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import java.util.Date

/**
 * Per-user state for the chat email digest: when we last emailed them.
 * One row per user, created lazily on first digest.
 */
class ChatEmailDigestState extends LongKeyedMapper[ChatEmailDigestState] with IdPK {
  def getSingleton = ChatEmailDigestState

  object UserId extends MappedString(this, 36) {
    override def dbColumnName = "user_id"
  }
  object LastNotifiedAt extends MappedDateTime(this) {
    override def dbColumnName = "last_notified_at"
  }
}

object ChatEmailDigestState extends ChatEmailDigestState with LongKeyedMetaMapper[ChatEmailDigestState] {
  override def dbTableName = "chat_email_digest_state"
  override def dbIndexes = UniqueIndex(UserId) :: super.dbIndexes

  def lastNotifiedAt(userId: String): Option[Date] =
    find(By(UserId, userId)).map(_.LastNotifiedAt.get).filter(_ != null).toOption

  def recordNotified(userId: String, at: Date): Box[ChatEmailDigestState] = tryo {
    find(By(UserId, userId)) match {
      case net.liftweb.common.Full(row) => row.LastNotifiedAt(at).saveMe()
      case _ => create.UserId(userId).LastNotifiedAt(at).saveMe()
    }
  }
}
