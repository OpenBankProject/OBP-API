package code.chat

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ChatMessageTrait extends SimpleInjector {
  val chatMessageProvider = new Inject(buildOne _) {}
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
