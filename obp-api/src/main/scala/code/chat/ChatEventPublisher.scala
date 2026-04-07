package code.chat

import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.Serialization.write
import scala.concurrent.{Future, ExecutionContext}

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
    Future { broadcastUnreadCounts(msg) }(ExecutionContext.global)
  }

  def afterUpdate(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("updated", msg, senderUsername, senderProvider, senderConsumerName)
  }

  def afterDelete(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("deleted", msg, senderUsername, senderProvider, senderConsumerName)
  }

  def afterReactionAdd(chatRoomId: String, chatMessageId: String, emoji: String,
                       userId: String, username: String, provider: String): Unit = {
    publishReactionEvent("reacted", chatRoomId, chatMessageId, emoji, userId, username, provider)
  }

  def afterReactionRemove(chatRoomId: String, chatMessageId: String, emoji: String,
                          userId: String, username: String, provider: String): Unit = {
    publishReactionEvent("unreacted", chatRoomId, chatMessageId, emoji, userId, username, provider)
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

  /**
   * Broadcast unread counts to affected participants after a new message.
   *
   * "Open rooms" (isOpenRoom=true)
   * only notify users who are explicitly @mentioned, to avoid generating
   * hundreds of thousands of publish events for large rooms.
   *
   * Private rooms notify all participants except the sender.
   *
   * Unread counts respect a 60-day cutoff — older messages are ignored.
   */
  private def broadcastUnreadCounts(msg: ChatMessageTrait): Unit = {
    try {
      val room = ChatRoomTrait.chatRoomProvider.vend.getChatRoom(msg.chatRoomId)
      val isOpenRoom = room.map(_.isOpenRoom).openOr(false)

      val participants = ParticipantTrait.participantProvider.vend
        .getParticipants(msg.chatRoomId).openOr(List.empty)

      for (p <- participants if p.userId != msg.senderUserId) {
        if (isOpenRoom) {
          // Open rooms: only notify explicitly mentioned users
          if (msg.mentionedUserIds.contains(p.userId)) {
            val count = ChatMessageTrait.chatMessageProvider.vend
              .getUnreadMentionCount(msg.chatRoomId, p.userId, p.lastReadAt).openOr(0L)
            afterUnreadCountChange(p.userId, msg.chatRoomId, count)
          }
        } else {
          // Private rooms: notify all participants
          val count = ChatMessageTrait.chatMessageProvider.vend
            .getUnreadCount(msg.chatRoomId, p.lastReadAt).openOr(0L)
          afterUnreadCountChange(p.userId, msg.chatRoomId, count)
        }
      }
    } catch {
      case e: Throwable => logger.error(s"Failed to broadcast unread counts: ${e.getMessage}")
    }
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

  private def publishReactionEvent(
    eventType: String,
    chatRoomId: String,
    chatMessageId: String,
    emoji: String,
    userId: String,
    username: String,
    provider: String
  ): Unit = {
    val now = dateFormat.format(new java.util.Date())
    val event = MessageEvent(
      event_type = eventType,
      chat_message_id = chatMessageId,
      chat_room_id = chatRoomId,
      sender_user_id = userId,
      sender_consumer_id = "",
      sender_username = username,
      sender_provider = provider,
      sender_consumer_name = "",
      content = emoji,
      message_type = "",
      mentioned_user_ids = List.empty,
      reply_to_message_id = "",
      thread_id = "",
      is_deleted = false,
      created_at = now,
      updated_at = now
    )
    ChatEventBus.publishMessage(chatRoomId, write(event))
  }
}
