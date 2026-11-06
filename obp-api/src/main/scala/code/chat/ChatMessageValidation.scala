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

/**
 * Referential integrity checks for chat message input — the ids and enums a
 * message carries must make sense within the room it is posted to. See also
 * ChatContentPolicy (length, characters) and ChatLinkPolicy (link hosts).
 */
object ChatMessageValidation {

  /** message_type values the chat endpoints accept. */
  val AllowedMessageTypes: Set[String] = Set("text", "system")

  def isAllowedMessageType(messageType: Option[String]): Boolean =
    messageType.forall(AllowedMessageTypes.contains)

  /**
   * Mentioned user ids that are not participants of the room. In open rooms
   * ChatPermissions treats any user as an implicit participant, so mentions
   * are unrestricted there by design.
   */
  def nonParticipantMentions(chatRoomId: String, mentionedUserIds: List[String]): List[String] =
    mentionedUserIds.distinct.filter(userId =>
      ChatPermissions.isParticipant(chatRoomId, userId).isEmpty)

  /** True when the referenced message id is empty or names a message in this room. */
  def referenceInRoom(chatRoomId: String, messageId: String): Boolean =
    messageId.isEmpty ||
      ChatMessageTrait.chatMessageProvider.vend.getMessage(messageId).exists(_.chatRoomId == chatRoomId)
}
