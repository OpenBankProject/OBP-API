package code.chat

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ParticipantTrait extends SimpleInjector {
  val participantProvider = new Inject(buildOne _) {}
  def buildOne: ParticipantProvider = MappedParticipantProvider
}

trait ParticipantProvider {
  def addParticipant(
    chatRoomId: String,
    userId: String,
    consumerId: String,
    permissions: List[String],
    webhookUrl: String
  ): Box[ParticipantTrait]

  def getParticipant(chatRoomId: String, userId: String): Box[ParticipantTrait]
  def getParticipantByConsumerId(chatRoomId: String, consumerId: String): Box[ParticipantTrait]
  def getParticipants(chatRoomId: String): Box[List[ParticipantTrait]]
  def getParticipantRoomsByUserId(userId: String): Box[List[ParticipantTrait]]

  def updateParticipantPermissions(
    chatRoomId: String,
    userId: String,
    permissions: List[String]
  ): Box[ParticipantTrait]

  def updateWebhookUrl(
    chatRoomId: String,
    userId: String,
    webhookUrl: String
  ): Box[ParticipantTrait]

  def updateLastReadAt(chatRoomId: String, userId: String): Box[ParticipantTrait]
  def updateMuted(chatRoomId: String, userId: String, isMuted: Boolean): Box[ParticipantTrait]
  def removeParticipant(chatRoomId: String, userId: String): Box[Boolean]
}

trait ParticipantTrait {
  def participantId: String
  def chatRoomId: String
  def userId: String
  def consumerId: String
  def permissions: List[String]
  def webhookUrl: String
  def joinedAt: Date
  def lastReadAt: Date
  def isMuted: Boolean
}
