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
