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

object ParticipantTrait extends SimpleInjector {
  val participantProvider = new Inject(() => buildOne) {}
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
