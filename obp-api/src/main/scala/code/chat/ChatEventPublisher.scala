package code.chat

import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.Serialization.write

/**
 * Publishes chat events to ChatEventBus after REST operations.
 *
 * Called from APIMethods600 after createMessage, updateMessage,
 * softDeleteMessage, and typing indicator operations.
 */
object ChatEventPublisher extends MdcLoggable {

  implicit val formats = json.DefaultFormats

  case class MessageEvent(
    event_type: String,
    chat_message_id: String,
    chat_room_id: String,
    sender_user_id: String,
    sender_consumer_id: String,
    sender_username: String,
    sender_provider: String,
    sender_consumer_name: String,
    content: String,
    message_type: String,
    mentioned_user_ids: List[String],
    reply_to_message_id: String,
    thread_id: String,
    is_deleted: Boolean,
    created_at: String,
    updated_at: String
  )

  case class TypingEvent(
    chat_room_id: String,
    user_id: String,
    username: String,
    provider: String,
    is_typing: Boolean
  )

  case class PresenceEvent(
    user_id: String,
    username: String,
    provider: String,
    is_online: Boolean
  )

  case class UnreadEvent(
    chat_room_id: String,
    unread_count: Long
  )

  private val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def afterCreate(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("new", msg, senderUsername, senderProvider, senderConsumerName)
  }

  def afterUpdate(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("updated", msg, senderUsername, senderProvider, senderConsumerName)
  }

  def afterDelete(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("deleted", msg, senderUsername, senderProvider, senderConsumerName)
  }

  def afterTyping(chatRoomId: String, userId: String, username: String, provider: String, isTyping: Boolean): Unit = {
    val event = TypingEvent(chatRoomId, userId, username, provider, isTyping)
    ChatEventBus.publishTyping(chatRoomId, write(event))
  }

  def afterPresenceChange(chatRoomId: String, userId: String, username: String, provider: String, isOnline: Boolean): Unit = {
    val event = PresenceEvent(userId, username, provider, isOnline)
    ChatEventBus.publishPresence(chatRoomId, write(event))
  }

  def afterUnreadCountChange(userId: String, chatRoomId: String, unreadCount: Long): Unit = {
    val event = UnreadEvent(chatRoomId, unreadCount)
    ChatEventBus.publishUnread(userId, write(event))
  }

  private def publishMessageEvent(
    eventType: String,
    msg: ChatMessageTrait,
    senderUsername: String,
    senderProvider: String,
    senderConsumerName: String
  ): Unit = {
    val event = MessageEvent(
      event_type = eventType,
      chat_message_id = msg.chatMessageId,
      chat_room_id = msg.chatRoomId,
      sender_user_id = msg.senderUserId,
      sender_consumer_id = msg.senderConsumerId,
      sender_username = senderUsername,
      sender_provider = senderProvider,
      sender_consumer_name = senderConsumerName,
      content = if (msg.isDeleted) "" else msg.content,
      message_type = msg.messageType,
      mentioned_user_ids = msg.mentionedUserIds,
      reply_to_message_id = msg.replyToMessageId,
      thread_id = msg.threadId,
      is_deleted = msg.isDeleted,
      created_at = dateFormat.format(msg.createdDate),
      updated_at = dateFormat.format(msg.updatedDate)
    )
    ChatEventBus.publishMessage(msg.chatRoomId, write(event))
  }
}
