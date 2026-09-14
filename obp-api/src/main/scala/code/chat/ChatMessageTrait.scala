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

object ChatMessageTrait extends SimpleInjector {
  val chatMessageProvider = new Inject(() => buildOne) {}
  def buildOne: ChatMessageProvider = MappedChatMessageProvider
}

trait ChatMessageProvider {
  def createMessage(
    chatRoomId: String,
    senderUserId: String,
    senderConsumerId: String,
    content: String,
    messageType: String,
    mentionedUserIds: List[String],
    replyToMessageId: String,
    threadId: String
  ): Box[ChatMessageTrait]

  def getMessage(chatMessageId: String): Box[ChatMessageTrait]
  def getMessages(chatRoomId: String, limit: Int, offset: Int, fromDate: Date, toDate: Date): Box[List[ChatMessageTrait]]
  def getThreadReplies(threadId: String): Box[List[ChatMessageTrait]]
  def getMentionsForUser(userId: String, limit: Int, offset: Int): Box[List[ChatMessageTrait]]
  def getUnreadCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long]
  def getUnreadMentionCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long]

  def updateMessage(chatMessageId: String, content: String): Box[ChatMessageTrait]
  def softDeleteMessage(chatMessageId: String): Box[ChatMessageTrait]
}

trait ChatMessageTrait {
  def chatMessageId: String
  def chatRoomId: String
  def senderUserId: String
  def senderConsumerId: String
  def content: String
  def messageType: String
  def mentionedUserIds: List[String]
  def replyToMessageId: String
  def threadId: String
  def isDeleted: Boolean
  def createdDate: Date
  def updatedDate: Date
}
