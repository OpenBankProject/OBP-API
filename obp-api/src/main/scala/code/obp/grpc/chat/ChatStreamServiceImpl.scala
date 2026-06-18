package code.obp.grpc.chat

import org.json4s._
import code.chat.{ChatEventBus, ChatPermissions, ParticipantTrait}
import code.obp.grpc.chat.api._
import code.util.Helper.MdcLoggable
import com.google.protobuf.timestamp.Timestamp
import io.grpc.Status
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JValue

import java.time.Instant
import scala.util.Try

/**
 * gRPC service implementation for chat streaming.
 *
 * All methods require authentication via AuthInterceptor.
 * User is retrieved from gRPC Context.
 */
object ChatStreamServiceImpl extends ChatStreamServiceGrpc.ChatStreamService with MdcLoggable {

  implicit val formats = json.DefaultFormats

  // --- StreamMessages: server-side stream ---

  override def streamMessages(
    request: StreamMessagesRequest,
    responseObserver: StreamObserver[ChatMessageEvent]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val chatRoomId = request.chatRoomId
    val userId = user.userId

    // Verify participant
    if (ChatPermissions.isParticipant(chatRoomId, userId).isEmpty) {
      responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Not a participant of this chat room").asRuntimeException())
      return
    }

    logger.info(s"ChatStreamServiceImpl says: User $userId subscribed to messages in room $chatRoomId")

    // Create a bridge observer that parses JSON events into ChatMessageEvent protobuf messages
    val jsonObserver = new StreamObserver[String] {
      override def onNext(jsonPayload: String): Unit = {
        try {
          val jValue = json.parse(jsonPayload)
          val event = jsonToChatMessageEvent(jValue)
          responseObserver.onNext(event)
        } catch {
          case e: Throwable =>
            logger.warn(s"ChatStreamServiceImpl says: Failed to parse message event: ${e.getMessage}")
        }
      }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    ChatEventBus.subscribe(s"message:$chatRoomId", jsonObserver)

    // Unsubscribe when client disconnects
    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          ChatEventBus.unsubscribe(s"message:$chatRoomId", jsonObserver)
          logger.info(s"ChatStreamServiceImpl says: User $userId unsubscribed from messages in room $chatRoomId")
        })
      case _ =>
    }
  }

  // --- StreamTyping: bidi stream ---

  override def streamTyping(
    responseObserver: StreamObserver[TypingIndicator]
  ): StreamObserver[TypingEvent] = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return new StreamObserver[TypingEvent] {
        override def onNext(value: TypingEvent): Unit = {}
        override def onError(t: Throwable): Unit = {}
        override def onCompleted(): Unit = {}
      }
    }

    val userId = user.userId
    val username = user.name
    val provider = user.provider

    // Track which rooms this client has subscribed to for typing
    var subscribedRooms = Set.empty[String]
    var jsonObservers = Map.empty[String, StreamObserver[String]]

    // Return a request observer that handles incoming typing events from this client
    new StreamObserver[TypingEvent] {
      override def onNext(event: TypingEvent): Unit = {
        val chatRoomId = event.chatRoomId

        // Subscribe to typing channel for this room if not already
        if (!subscribedRooms.contains(chatRoomId)) {
          val jsonObserver = new StreamObserver[String] {
            override def onNext(jsonPayload: String): Unit = {
              try {
                val jValue = json.parse(jsonPayload)
                val indicator = jsonToTypingIndicator(jValue)
                // Filter out own typing events
                if (indicator.userId != userId) {
                  responseObserver.synchronized {
                    responseObserver.onNext(indicator)
                  }
                }
              } catch {
                case e: Throwable =>
                  logger.warn(s"ChatStreamServiceImpl says: Failed to parse typing event: ${e.getMessage}")
              }
            }
            override def onError(t: Throwable): Unit = {}
            override def onCompleted(): Unit = {}
          }
          ChatEventBus.subscribe(s"typing:$chatRoomId", jsonObserver)
          subscribedRooms += chatRoomId
          jsonObservers += (chatRoomId -> jsonObserver)
        }

        // Publish this client's typing event
        val payload = json.Serialization.write(
          Map(
            "chat_room_id" -> chatRoomId,
            "user_id" -> userId,
            "username" -> username,
            "provider" -> provider,
            "is_typing" -> event.isTyping
          )
        )
        ChatEventBus.publishTyping(chatRoomId, payload)
      }

      override def onError(t: Throwable): Unit = cleanup()
      override def onCompleted(): Unit = cleanup()

      private def cleanup(): Unit = {
        jsonObservers.foreach { case (room, obs) =>
          ChatEventBus.unsubscribe(s"typing:$room", obs)
        }
        subscribedRooms = Set.empty
        jsonObservers = Map.empty
      }
    }
  }

  // --- StreamPresence: server-side stream ---

  override def streamPresence(
    request: StreamPresenceRequest,
    responseObserver: StreamObserver[PresenceEvent]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val chatRoomId = request.chatRoomId
    val userId = user.userId

    if (ChatPermissions.isParticipant(chatRoomId, userId).isEmpty) {
      responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Not a participant of this chat room").asRuntimeException())
      return
    }

    logger.info(s"ChatStreamServiceImpl says: User $userId subscribed to presence in room $chatRoomId")

    // Publish online event for this user
    val onlinePayload = json.Serialization.write(
      Map("user_id" -> userId, "username" -> user.name, "provider" -> user.provider, "is_online" -> true)
    )
    ChatEventBus.publishPresence(chatRoomId, onlinePayload)

    // Subscribe to presence events
    val jsonObserver = new StreamObserver[String] {
      override def onNext(jsonPayload: String): Unit = {
        try {
          val jValue = json.parse(jsonPayload)
          val event = jsonToPresenceEvent(jValue)
          responseObserver.onNext(event)
        } catch {
          case e: Throwable =>
            logger.warn(s"ChatStreamServiceImpl says: Failed to parse presence event: ${e.getMessage}")
        }
      }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    ChatEventBus.subscribe(s"presence:$chatRoomId", jsonObserver)

    // On disconnect: publish offline and unsubscribe
    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          ChatEventBus.unsubscribe(s"presence:$chatRoomId", jsonObserver)
          val offlinePayload = json.Serialization.write(
            Map("user_id" -> userId, "username" -> user.name, "provider" -> user.provider, "is_online" -> false)
          )
          ChatEventBus.publishPresence(chatRoomId, offlinePayload)
          logger.info(s"ChatStreamServiceImpl says: User $userId went offline in room $chatRoomId")
        })
      case _ =>
    }
  }

  // --- StreamUnreadCounts: server-side stream ---

  override def streamUnreadCounts(
    request: StreamUnreadCountsRequest,
    responseObserver: StreamObserver[UnreadCountEvent]
  ): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException())
      return
    }

    val userId = user.userId

    logger.info(s"ChatStreamServiceImpl says: User $userId subscribed to unread counts")

    // Subscribe to unread count updates for this user
    // Initial counts can be fetched via the REST GET unread-counts endpoint.
    // This stream pushes real-time updates as new messages arrive.
    val jsonObserver = new StreamObserver[String] {
      override def onNext(jsonPayload: String): Unit = {
        try {
          val jValue = json.parse(jsonPayload)
          val event = jsonToUnreadCountEvent(jValue)
          responseObserver.onNext(event)
        } catch {
          case e: Throwable =>
            logger.warn(s"ChatStreamServiceImpl says: Failed to parse unread event: ${e.getMessage}")
        }
      }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    ChatEventBus.subscribe(s"unread:$userId", jsonObserver)

    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          ChatEventBus.unsubscribe(s"unread:$userId", jsonObserver)
          logger.info(s"ChatStreamServiceImpl says: User $userId unsubscribed from unread counts")
        })
      case _ =>
    }
  }

  // --- JSON to protobuf conversion helpers ---

  private def jsonToChatMessageEvent(jv: JValue): ChatMessageEvent = {
    ChatMessageEvent(
      eventType = (jv \ "event_type").extractOrElse[String](""),
      chatMessageId = (jv \ "chat_message_id").extractOrElse[String](""),
      chatRoomId = (jv \ "chat_room_id").extractOrElse[String](""),
      senderUserId = (jv \ "sender_user_id").extractOrElse[String](""),
      senderConsumerId = (jv \ "sender_consumer_id").extractOrElse[String](""),
      senderUsername = (jv \ "sender_username").extractOrElse[String](""),
      senderProvider = (jv \ "sender_provider").extractOrElse[String](""),
      senderConsumerName = (jv \ "sender_consumer_name").extractOrElse[String](""),
      content = (jv \ "content").extractOrElse[String](""),
      messageType = (jv \ "message_type").extractOrElse[String](""),
      mentionedUserIds = (jv \ "mentioned_user_ids").extractOrElse[List[String]](List.empty),
      replyToMessageId = (jv \ "reply_to_message_id").extractOrElse[String](""),
      threadId = (jv \ "thread_id").extractOrElse[String](""),
      isDeleted = (jv \ "is_deleted").extractOrElse[Boolean](false),
      createdAt = parseIsoToProtoTimestamp((jv \ "created_at").extractOrElse[String]("")),
      updatedAt = parseIsoToProtoTimestamp((jv \ "updated_at").extractOrElse[String](""))
    )
  }

  /**
   * Parse an ISO-8601 timestamp string (e.g. "2026-04-04T12:58:47Z") into a
   * protobuf Timestamp. Returns None for empty or unparseable values so the
   * wire format stays unchanged in the degenerate case.
   */
  private def parseIsoToProtoTimestamp(iso: String): Option[Timestamp] = {
    if (iso.isEmpty) None
    else Try(Instant.parse(iso)).toOption.map(i =>
      Timestamp(seconds = i.getEpochSecond, nanos = i.getNano)
    )
  }

  private def jsonToTypingIndicator(jv: JValue): TypingIndicator = {
    TypingIndicator(
      chatRoomId = (jv \ "chat_room_id").extractOrElse[String](""),
      userId = (jv \ "user_id").extractOrElse[String](""),
      username = (jv \ "username").extractOrElse[String](""),
      provider = (jv \ "provider").extractOrElse[String](""),
      isTyping = (jv \ "is_typing").extractOrElse[Boolean](false)
    )
  }

  private def jsonToPresenceEvent(jv: JValue): PresenceEvent = {
    PresenceEvent(
      userId = (jv \ "user_id").extractOrElse[String](""),
      username = (jv \ "username").extractOrElse[String](""),
      provider = (jv \ "provider").extractOrElse[String](""),
      isOnline = (jv \ "is_online").extractOrElse[Boolean](false)
    )
  }

  private def jsonToUnreadCountEvent(jv: JValue): UnreadCountEvent = {
    UnreadCountEvent(
      chatRoomId = (jv \ "chat_room_id").extractOrElse[String](""),
      unreadCount = (jv \ "unread_count").extractOrElse[Long](0L)
    )
  }
}
