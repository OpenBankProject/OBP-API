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
import net.liftweb.common.{Box, Empty, Failure, Full}

/**
 * A synthetic participant for rooms with isOpenRoom = true.
 * No database row exists — the user is implicitly a member with no special permissions.
 */
case class ImplicitParticipant(chatRoomId: String, userId: String) extends ParticipantTrait {
  override def participantId: String = ""
  override def consumerId: String = ""
  override def permissions: List[String] = List.empty
  override def webhookUrl: String = ""
  override def joinedAt: Date = new Date()
  override def lastReadAt: Date = new Date()
  override def isMuted: Boolean = false
}

object ChatPermissions {
  val CAN_DELETE_MESSAGE = "can_delete_message"
  val CAN_REMOVE_PARTICIPANT = "can_remove_participant"
  val CAN_REFRESH_JOINING_KEY = "can_refresh_joining_key"
  val CAN_UPDATE_ROOM = "can_update_room"
  val CAN_MANAGE_PERMISSIONS = "can_manage_permissions"

  val ALL_PERMISSIONS: List[String] = List(
    CAN_DELETE_MESSAGE,
    CAN_REMOVE_PARTICIPANT,
    CAN_REFRESH_JOINING_KEY,
    CAN_UPDATE_ROOM,
    CAN_MANAGE_PERMISSIONS
  )

  /**
   * Check if user is a participant of the room. Returns the Participant record if found,
   * or a synthetic participant (via the room's isOpenRoom flag) with empty permissions.
   */
  def isParticipant(chatRoomId: String, userId: String): Box[ParticipantTrait] = {
    ParticipantTrait.participantProvider.vend.getParticipant(chatRoomId, userId) match {
      case Full(p) => Full(p)
      case _ =>
        // Check if room has isOpenRoom = true
        ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId) match {
          case Full(room) if room.isOpenRoom =>
            Full(ImplicitParticipant(chatRoomId, userId))
          case _ => Empty
        }
    }
  }

  def isParticipantByConsumerId(chatRoomId: String, consumerId: String): Box[ParticipantTrait] = {
    ParticipantTrait.participantProvider.vend.getParticipantByConsumerId(chatRoomId, consumerId)
  }

  def checkParticipantPermission(chatRoomId: String, userId: String, requiredPermission: String): Box[ParticipantTrait] = {
    isParticipant(chatRoomId, userId) match {
      case Full(p) =>
        if (p.permissions.contains(requiredPermission)) Full(p)
        else Failure(s"Participant does not have permission: $requiredPermission")
      case Empty => Empty
      case f: Failure => f
    }
  }
}
