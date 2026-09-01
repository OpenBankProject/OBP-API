package code.chat

import org.json4s._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.json
import org.json4s.native.Serialization.write
// No async imports needed — broadcastUnreadCounts runs synchronously (1-2 fast queries)

/**
 * Publishes chat events to ChatEventBus after REST operations.
 *
 * Called from Http4s600 after createMessage, updateMessage,
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

  /** Unread count event. The unread_count field is an increment (e.g. 1 for a new message),
   *  not an absolute count. Clients should add this to their local total.
   *  Exact counts are reconciled via the GET /users/current/chat-rooms/unread REST endpoint. */
  case class UnreadEvent(
    chat_room_id: String,
    unread_count: Long
  )

  private val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def afterCreate(msg: ChatMessageTrait, senderUsername: String, senderProvider: String, senderConsumerName: String): Unit = {
    publishMessageEvent("new", msg, senderUsername, senderProvider, senderConsumerName)
    // Sending a message means the sender has "read" the room up to this point
    ParticipantTrait.participantProvider.vend.updateLastReadAt(msg.chatRoomId, msg.senderUserId)
    // Denormalize last message info onto the ChatRoom for efficient listing
    ChatRoomTrait.chatRoomProvider.vend.updateLastMessageInfo(
      msg.chatRoomId,
      msg.createdDate,
      if (msg.isDeleted) "" else msg.content,
      senderUsername
    )
    broadcastUnreadCounts(msg)
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
   * Broadcast unread count increments to affected participants after a new message.
   *
   * Instead of computing exact counts (which requires a DB query per participant),
   * we send an increment of 1. Clients can add this to their local count.
   * Exact counts are reconciled on page load via the REST endpoint.
   *
   * "Open rooms" (isOpenRoom=true) only notify explicitly @mentioned users.
   * Private rooms notify all explicit participants except the sender.
   *
   * Uses only 2 DB queries total (room + participants), not N+2.
   */
  private def broadcastUnreadCounts(msg: ChatMessageTrait): Unit = {
    try {
      val room = ChatRoomTrait.chatRoomProvider.vend.getChatRoom(msg.chatRoomId)
      val isOpenRoom = room.map(_.isOpenRoom).openOr(false)

      if (isOpenRoom) {
        // Open rooms: only notify explicitly mentioned users — no DB query needed
        for (userId <- msg.mentionedUserIds if userId != msg.senderUserId) {
          afterUnreadCountChange(userId, msg.chatRoomId, 1L)
        }
      } else {
        // Private rooms: notify all participants except the sender
        val participants = ParticipantTrait.participantProvider.vend
          .getParticipants(msg.chatRoomId).openOr(List.empty)
        for (p <- participants if p.userId != msg.senderUserId) {
          afterUnreadCountChange(p.userId, msg.chatRoomId, 1L)
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
