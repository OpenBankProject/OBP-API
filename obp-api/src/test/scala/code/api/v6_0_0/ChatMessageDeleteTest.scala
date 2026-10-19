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
import code.api.util.ErrorMessages.{ChatMessageNotFound, ChatRoomNotFound}
import code.chat.{ChatMessageTrait, ChatPermissions, ChatRoomTrait, ParticipantTrait}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * A message can only be deleted through the room (and, for bank rooms, the bank) it belongs to.
 */
class ChatMessageDeleteTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointDeleteBank extends Tag("deleteBankChatMessage")
  object ApiEndpointDeleteSystem extends Tag("deleteSystemChatMessage")

  // a room at bankId in which user1 may delete any message
  private def createRoomWithDeleter(bankId: String): String = {
    val room = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"room-${java.util.UUID.randomUUID()}", "test room", resourceUser2.userId).openOrThrowException("room")
    ParticipantTrait.participantProvider.vend.addParticipant(
      room.chatRoomId, resourceUser1.userId, "", List(ChatPermissions.CAN_DELETE_MESSAGE), "").openOrThrowException("participant")
    room.chatRoomId
  }

  // a message by user2 in a room user1 is not in
  private def createMessageInOtherRoom(bankId: String): String = {
    val otherRoom = ChatRoomTrait.chatRoomProvider.vend.createChatRoom(
      bankId, s"other-${java.util.UUID.randomUUID()}", "other room", resourceUser2.userId).openOrThrowException("room")
    ChatMessageTrait.chatMessageProvider.vend.createMessage(
      otherRoom.chatRoomId, resourceUser2.userId, "", "secret", "text", Nil, "", "").openOrThrowException("message").chatMessageId
  }

  private def isDeleted(chatMessageId: String): Boolean =
    ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId).map(_.isDeleted).openOr(false)

  feature(s"Delete Bank Chat Message - DELETE /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID - $VersionOfApi") {

    scenario("A message of another room is refused with 404 and not deleted", ApiEndpointDeleteBank, VersionOfApi) {
      val roomId = createRoomWithDeleter(testBankId1.value)
      val messageId = createMessageInOtherRoom(testBankId1.value)
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId).DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
      isDeleted(messageId) should equal(false)
    }

    scenario("A room of another bank is refused with 404", ApiEndpointDeleteBank, VersionOfApi) {
      val roomId = createRoomWithDeleter(testBankId1.value)
      val messageId = ChatMessageTrait.chatMessageProvider.vend.createMessage(
        roomId, resourceUser2.userId, "", "hello", "text", Nil, "", "").openOrThrowException("message").chatMessageId
      val request = (v6_0_0_Request / "banks" / testBankId2.value / "chat-rooms" / roomId / "messages" / messageId).DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatRoomNotFound)
      isDeleted(messageId) should equal(false)
    }

    scenario("A message of the room in the path is deleted with 204", ApiEndpointDeleteBank, VersionOfApi) {
      val roomId = createRoomWithDeleter(testBankId1.value)
      val messageId = ChatMessageTrait.chatMessageProvider.vend.createMessage(
        roomId, resourceUser2.userId, "", "hello", "text", Nil, "", "").openOrThrowException("message").chatMessageId
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "chat-rooms" / roomId / "messages" / messageId).DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      response.code should equal(204)
      isDeleted(messageId) should equal(true)
    }
  }

  feature(s"Delete System Chat Message - DELETE /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID - $VersionOfApi") {

    scenario("A message of another room is refused with 404 and not deleted", ApiEndpointDeleteSystem, VersionOfApi) {
      val roomId = createRoomWithDeleter("")
      val messageId = createMessageInOtherRoom("")
      val request = (v6_0_0_Request / "chat-rooms" / roomId / "messages" / messageId).DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(ChatMessageNotFound)
      isDeleted(messageId) should equal(false)
    }
  }
}
