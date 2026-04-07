package code.chat

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ChatRoomTrait extends SimpleInjector {
  val chatRoomProvider = new Inject(buildOne _) {}
  def buildOne: ChatRoomProvider = MappedChatRoomProvider
}

trait ChatRoomProvider {
  def createChatRoom(
    bankId: String,
    name: String,
    description: String,
    createdBy: String
  ): Box[ChatRoomTrait]

  def getChatRoom(chatRoomId: String): Box[ChatRoomTrait]
  def getChatRoomByBankIdAndName(bankId: String, name: String): Box[ChatRoomTrait]
  def getChatRoomsByBankId(bankId: String): Box[List[ChatRoomTrait]]
  def getChatRoomsByBankIdForUser(bankId: String, userId: String): Box[List[ChatRoomTrait]]
  def getChatRoomByJoiningKey(joiningKey: String): Box[ChatRoomTrait]

  def updateChatRoom(
    chatRoomId: String,
    name: Option[String],
    description: Option[String]
  ): Box[ChatRoomTrait]

  def setIsOpenRoom(chatRoomId: String, isOpenRoom: Boolean): Box[ChatRoomTrait]
  def archiveChatRoom(chatRoomId: String): Box[ChatRoomTrait]
  def deleteChatRoom(chatRoomId: String): Box[Boolean]
  def refreshJoiningKey(chatRoomId: String): Box[ChatRoomTrait]
  def getOrCreateDefaultRoom(): Box[ChatRoomTrait]
}

trait ChatRoomTrait {
  def chatRoomId: String
  def bankId: String
  def name: String
  def description: String
  def joiningKey: String
  def createdBy: String
  /** Whether this is an "open room" where all users are implicit participants. */
  def isOpenRoom: Boolean
  def isArchived: Boolean
  def createdDate: Date
  def updatedDate: Date
}
