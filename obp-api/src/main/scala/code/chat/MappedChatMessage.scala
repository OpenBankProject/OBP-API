package code.chat

import java.util.Date
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedChatMessageProvider extends ChatMessageProvider {

  override def createMessage(
    chatRoomId: String,
    senderUserId: String,
    senderConsumerId: String,
    content: String,
    messageType: String,
    mentionedUserIds: List[String],
    replyToMessageId: String,
    threadId: String
  ): Box[ChatMessageTrait] = {
    tryo {
      ChatMessage.create
        .ChatRoomId(chatRoomId)
        .SenderUserId(senderUserId)
        .SenderConsumerId(senderConsumerId)
        .Content(content)
        .MessageType(messageType)
        .MentionedUserIds(mentionedUserIds.mkString(","))
        .ReplyToMessageId(replyToMessageId)
        .ThreadId(threadId)
        .IsDeleted(false)
        .saveMe()
    }
  }

  override def getMessage(chatMessageId: String): Box[ChatMessageTrait] = {
    ChatMessage.find(By(ChatMessage.ChatMessageId, chatMessageId))
  }

  override def getMessages(chatRoomId: String, limit: Int, offset: Int, fromDate: Date, toDate: Date): Box[List[ChatMessageTrait]] = {
    tryo {
      ChatMessage.findAll(
        By(ChatMessage.ChatRoomId, chatRoomId),
        By_>=(ChatMessage.createdAt, fromDate),
        By_<=(ChatMessage.createdAt, toDate),
        OrderBy(ChatMessage.id, Ascending),
        MaxRows[ChatMessage](limit),
        StartAt[ChatMessage](offset)
      )
    }
  }

  override def getThreadReplies(threadId: String): Box[List[ChatMessageTrait]] = {
    tryo {
      ChatMessage.findAll(
        By(ChatMessage.ThreadId, threadId),
        OrderBy(ChatMessage.id, Ascending)
      )
    }
  }

  override def getMentionsForUser(userId: String, limit: Int, offset: Int): Box[List[ChatMessageTrait]] = {
    tryo {
      ChatMessage.findAll(
        Like(ChatMessage.MentionedUserIds, s"%$userId%"),
        OrderBy(ChatMessage.id, Descending),
        MaxRows[ChatMessage](limit),
        StartAt[ChatMessage](offset)
      )
    }
  }

  private def effectiveSinceDate(sinceDate: Date): Date = {
    val sixtyDaysAgo = new Date(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
    if (sinceDate.before(sixtyDaysAgo)) sixtyDaysAgo else sinceDate
  }

  override def getUnreadCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] = {
    tryo {
      ChatMessage.count(
        By(ChatMessage.ChatRoomId, chatRoomId),
        By_>(ChatMessage.createdAt, effectiveSinceDate(sinceDate)),
        NotBy(ChatMessage.SenderUserId, userId)
      )
    }
  }

  override def getUnreadMentionCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] = {
    tryo {
      ChatMessage.count(
        By(ChatMessage.ChatRoomId, chatRoomId),
        By_>(ChatMessage.createdAt, effectiveSinceDate(sinceDate)),
        NotBy(ChatMessage.SenderUserId, userId),
        Like(ChatMessage.MentionedUserIds, s"%$userId%")
      )
    }
  }

  override def updateMessage(chatMessageId: String, content: String): Box[ChatMessageTrait] = {
    ChatMessage.find(By(ChatMessage.ChatMessageId, chatMessageId)).flatMap { msg =>
      tryo {
        msg.Content(content).saveMe()
      }
    }
  }

  override def softDeleteMessage(chatMessageId: String): Box[ChatMessageTrait] = {
    ChatMessage.find(By(ChatMessage.ChatMessageId, chatMessageId)).flatMap { msg =>
      tryo {
        msg.IsDeleted(true).saveMe()
      }
    }
  }
}

class ChatMessage extends ChatMessageTrait with LongKeyedMapper[ChatMessage] with IdPK with CreatedUpdated {

  def getSingleton = ChatMessage

  object ChatMessageId extends MappedUUID(this)
  object ChatRoomId extends MappedString(this, 36)
  object SenderUserId extends MappedString(this, 36)
  object SenderConsumerId extends MappedString(this, 36)
  object Content extends MappedText(this)
  object MessageType extends MappedString(this, 16)
  object MentionedUserIds extends MappedText(this)
  object ReplyToMessageId extends MappedString(this, 36)
  object ThreadId extends MappedString(this, 36)
  object IsDeleted extends MappedBoolean(this)

  override def chatMessageId: String = ChatMessageId.get
  override def chatRoomId: String = ChatRoomId.get
  override def senderUserId: String = SenderUserId.get
  override def senderConsumerId: String = SenderConsumerId.get
  override def content: String = Content.get
  override def messageType: String = MessageType.get
  override def mentionedUserIds: List[String] = {
    val ids = MentionedUserIds.get
    if (ids == null || ids.isEmpty) List.empty
    else ids.split(",").map(_.trim).filter(_.nonEmpty).toList
  }
  override def replyToMessageId: String = ReplyToMessageId.get
  override def threadId: String = ThreadId.get
  override def isDeleted: Boolean = IsDeleted.get
  override def createdDate: Date = createdAt.get
  override def updatedDate: Date = updatedAt.get
}

object ChatMessage extends ChatMessage with LongKeyedMetaMapper[ChatMessage] {
  override def dbTableName = "ChatMessage"
  override def dbIndexes = UniqueIndex(ChatMessageId) :: Index(ChatRoomId) :: Index(ThreadId) :: Index(SenderUserId) :: super.dbIndexes
}
