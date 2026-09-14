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

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ReactionTrait extends SimpleInjector {
  val reactionProvider = new Inject(() => buildOne) {}
  def buildOne: ReactionProvider = MappedReactionProvider
}

trait ReactionProvider {
  def addReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait]
  def removeReaction(chatMessageId: String, userId: String, emoji: String): Box[Boolean]
  def getReactions(chatMessageId: String): Box[List[ReactionTrait]]
  def getReactionsForMessages(chatMessageIds: List[String]): Box[Map[String, List[ReactionTrait]]]
  def getReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait]
}

trait ReactionTrait {
  def reactionId: String
  def chatMessageId: String
  def userId: String
  def emoji: String
  def createdDate: Date
}
