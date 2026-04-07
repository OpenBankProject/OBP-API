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
