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

package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.ChatMessageNotFound
import code.chat.{ChatMessageTrait, ChatRoomTrait, ParticipantTrait}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * Thread replies and reactions of a message can only be read through the room the message belongs to.
 */
class ChatThreadAndReactionsTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointBankThread extends Tag("getBankThreadReplies")
  object ApiEndpointSystemThread extends Tag("getSystemThreadReplies")
  object ApiEndpointBankReactions extends Tag("getBankReactions")
  object ApiEndpointSystemReactions extends Tag("getSystemReactions")

  // a room at bankId in which user1 is a participant
  private def createRoomWithUser1(bankId: String): String = {
    val room = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"room-${java.util.UUID.randomUUID()}", "test room", resourceUser2.userId).openOrThrowException("room")
    ParticipantTrait.participantProvider.vend.addParticipant(
      room.chatRoomId, resourceUser1.userId, "", Nil, "").openOrThrowException("participant")
    room.chatRoomId
  }

  // a message by user2 in a room user1 is not in
  private def createMessageInOtherRoom(bankId: String): String = {
    val otherRoom = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"other-${java.util.UUID.randomUUID()}", "other room", resourceUser2.userId).openOrThrowException("room")
    ChatMessageTrait.chatMessageProvider.vend.createMessage(
      otherRoom.chatRoomId, resourceUser2.userId, "", "secret", "text", Nil, "", "").openOrThrowException("message").chatMessageId
  }

  feature(s"Get Bank Thread Replies - GET /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread - $VersionOfApi") {
    scenario("A message of another room is refused with 404", ApiEndpointBankThread, VersionOfApi) {
      val roomId = createRoomWithUser1(testBankId1.value)
      val messageId = createMessageInOtherRoom(testBankId1.value)
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId / "thread").GET <@ (user1)
      val response = makeGetRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
    }
  }

  feature(s"Get System Thread Replies - GET /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread - $VersionOfApi") {
    scenario("A message of another room is refused with 404", ApiEndpointSystemThread, VersionOfApi) {
      val roomId = createRoomWithUser1("")
      val messageId = createMessageInOtherRoom("")
      val request = (v6_0_0_Request / "chat-rooms" / roomId / "messages" / messageId / "thread").GET <@ (user1)
      val response = makeGetRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
    }
  }

  feature(s"Get Bank Reactions - GET /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions - $VersionOfApi") {
    scenario("A message of another room is refused with 404", ApiEndpointBankReactions, VersionOfApi) {
      val roomId = createRoomWithUser1(testBankId1.value)
      val messageId = createMessageInOtherRoom(testBankId1.value)
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId / "reactions").GET <@ (user1)
      val response = makeGetRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
    }
  }

  feature(s"Get System Reactions - GET /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions - $VersionOfApi") {
    scenario("A message of another room is refused with 404", ApiEndpointSystemReactions, VersionOfApi) {
      val roomId = createRoomWithUser1("")
      val messageId = createMessageInOtherRoom("")
      val request = (v6_0_0_Request / "chat-rooms" / roomId / "messages" / messageId / "reactions").GET <@ (user1)
      val response = makeGetRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
    }
  }
}
