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

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{ChatMessageNotFound, ChatRoomNotFound}
import code.chat.{ChatMessageTrait, ChatRoomTrait, ParticipantTrait}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * A message can only be edited through the room (and, for bank rooms, the bank) it belongs to.
 */
class ChatMessageEditTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointEditBank extends Tag("editBankChatMessage")
  object ApiEndpointEditSystem extends Tag("editSystemChatMessage")

  private val newContentBody = """{"content":"changed text"}"""

  // a room at bankId that user1 is a participant of
  private def createRoomWithUser1(bankId: String): String = {
    val room = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"room-${java.util.UUID.randomUUID()}", "test room", resourceUser2.userId).openOrThrowException("room")
    ParticipantTrait.participantProvider.vend.addParticipant(
      room.chatRoomId, resourceUser1.userId, "", Nil, "").openOrThrowException("participant")
    room.chatRoomId
  }

  // a message by user1 in a room user1 is not in (as if user1 had been removed from it)
  private def createOwnMessageInOtherRoom(bankId: String): String = {
    val otherRoom = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"other-${java.util.UUID.randomUUID()}", "other room", resourceUser2.userId).openOrThrowException("room")
    ChatMessageTrait.chatMessageProvider.vend.createMessage(
      otherRoom.chatRoomId, resourceUser1.userId, "", "original text", "text", Nil, "", "").openOrThrowException("message").chatMessageId
  }

  private def contentOf(chatMessageId: String): String =
    ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId).map(_.content).openOr("")

  feature(s"Edit Bank Chat Message - PUT /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID - $VersionOfApi") {

    scenario("A message of another room is refused with 404 and not changed", ApiEndpointEditBank, VersionOfApi) {
      val roomId = createRoomWithUser1(testBankId1.value)
      val messageId = createOwnMessageInOtherRoom(testBankId1.value)
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId).PUT <@ (user1)
      val response = makePutRequest(request, newContentBody)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
      contentOf(messageId) should equal("original text")
    }

    scenario("A room of another bank is refused with 404 and the message not changed", ApiEndpointEditBank, VersionOfApi) {
      val roomId = createRoomWithUser1(testBankId1.value)
      val messageId = ChatMessageTrait.chatMessageProvider.vend.createMessage(
        roomId, resourceUser1.userId, "", "original text", "text", Nil, "", "").openOrThrowException("message").chatMessageId
      val request = (v6_0_0_Request / "banks" / testBankId2.value / "chat-rooms" / roomId / "messages" / messageId).PUT <@ (user1)
      val response = makePutRequest(request, newContentBody)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatRoomNotFound)
      contentOf(messageId) should equal("original text")
    }

    scenario("A message of the room in the path is edited with 200", ApiEndpointEditBank, VersionOfApi) {
      val roomId = createRoomWithUser1(testBankId1.value)
      val messageId = ChatMessageTrait.chatMessageProvider.vend.createMessage(
        roomId, resourceUser1.userId, "", "original text", "text", Nil, "", "").openOrThrowException("message").chatMessageId
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId).PUT <@ (user1)
      val response = makePutRequest(request, newContentBody)
      response.code should equal(200)
      contentOf(messageId) should equal("changed text")
    }
  }

  feature(s"Edit System Chat Message - PUT /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID - $VersionOfApi") {

    scenario("A message of another room is refused with 404 and not changed", ApiEndpointEditSystem, VersionOfApi) {
      val roomId = createRoomWithUser1("")
      val messageId = createOwnMessageInOtherRoom("")
      val request = (v6_0_0_Request / "chat-rooms" / roomId / "messages" / messageId).PUT <@ (user1)
      val response = makePutRequest(request, newContentBody)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
      contentOf(messageId) should equal("original text")
    }
  }
}
