package code.obp.grpc.signal

import code.api.cache.RedisMessaging
import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidSignalChannelName, SignalMessageContainsDangerousCharacters, SignalMessageTooLong}
import code.api.util.SelfServiceRateLimiter
import code.api.v6_0_0.{PostSignalMessageJsonV600, SignalMessageJsonV600}
import code.obp.grpc.chat.AuthInterceptor
import code.obp.grpc.signal.api._
import code.signal.{SignalChannels, SignalContentPolicy, SignalEventBus}
import code.util.DangerousCharacters
import code.util.Helper.MdcLoggable
import com.google.protobuf.timestamp.Timestamp
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.JsonAliases
import io.grpc.{Status, StatusRuntimeException}
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import net.liftweb.common.Full
import org.json4s.JsonAST.JValue

import java.time.Instant
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

/**
 * gRPC SignalChannelsService: the same four operations as the REST signal
 * endpoints (publish, fetch, list) plus a live Subscribe stream, over the
 * same Redis storage and the same SignalChannels helper, so a message
 * published on one transport is read on the other unchanged.
 *
 * Auth: the shared AuthInterceptor validates the token at call open and puts
 * the User in gRPC Context. Validation failures map to INVALID_ARGUMENT with
 * the REST error message as the description, so a client sees the same
 * OBP-xxxxx code either way.
 *
 * Size cap: REST caps the raw request body. The gRPC analogue is the
 * JSON-encoded payload plus message_type, checked before the payload is parsed.
 */
object SignalChannelsServiceImpl extends SignalChannelsServiceGrpc.SignalChannelsService with MdcLoggable {

  private def unauthenticated: StatusRuntimeException =
    Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException()

  private def invalid(message: String): StatusRuntimeException =
    Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException()

  private def withUser[T](body: User => T): Future[T] = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) Future.failed(unauthenticated)
    else Future(body(user)).recoverWith {
      case e: StatusRuntimeException => Future.failed(e)
      case NonFatal(e) =>
        logger.error(s"SignalChannelsServiceImpl says: ${e.getMessage}", e)
        Future.failed(Status.INTERNAL.withDescription(e.getMessage).asRuntimeException())
    }
  }

  private def toTimestamp(iso: String): Option[Timestamp] =
    Try(Instant.parse(iso)).toOption.map(i => Timestamp(seconds = i.getEpochSecond, nanos = i.getNano))

  private def toProto(msg: SignalMessageJsonV600): SignalMessage =
    SignalMessage(
      messageId = msg.message_id,
      channelName = msg.channel_name,
      senderConsumerId = msg.sender_consumer_id,
      senderUserId = msg.sender_user_id,
      toUserId = msg.to_user_id.getOrElse(""),
      timestamp = toTimestamp(msg.timestamp),
      messageType = msg.message_type,
      payloadJson = JsonAliases.compactRender(msg.payload),
      sequence = msg.sequence)

  override def publish(request: PublishRequest): Future[PublishResponse] = withUser { user =>
    val callContext = Option(AuthInterceptor.CALL_CONTEXT_KEY.get())
    // Same order as the REST handler: size cap before parsing, then JSON, name, characters.
    if (request.payloadJson.length + request.messageType.length > SignalContentPolicy.maxPayloadLength)
      throw invalid(s"$SignalMessageTooLong Maximum: ${SignalContentPolicy.maxPayloadLength} characters.")
    val payload: JValue =
      try JsonAliases.parse(request.payloadJson)
      catch { case NonFatal(_) => throw invalid(s"$InvalidJsonFormat payload_json must be a JSON document.") }
    if (!RedisMessaging.validateChannelName(request.channelName)) throw invalid(InvalidSignalChannelName)
    if (SignalContentPolicy.containsDangerousCharacters(payload) || DangerousCharacters.containsAny(request.messageType))
      throw invalid(SignalMessageContainsDangerousCharacters)

    val post = PostSignalMessageJsonV600(
      payload = payload,
      message_type = Option(request.messageType).filter(_.nonEmpty),
      to_user_id = Option(request.toUserId).filter(_.nonEmpty))
    val consumerId = callContext.flatMap(_.consumer match { case Full(c) => Some(c.consumerId.get); case _ => None }).getOrElse("")
    // Channel creation cap (scope signal_channel_create), keyed by the client IP address exactly
    // as SelfServiceRateLimitMiddleware keys it for REST, so one caller shares one counter on
    // both transports. AuthInterceptor captures the peer address into CallContext.ipAddress;
    // if none is available the key falls back to the Consumer, then the User. Publishing to an
    // existing channel is never counted.
    if (RedisMessaging.channelInfo(request.channelName).isEmpty) {
      val ip = callContext.map(_.ipAddress).getOrElse("").trim
      val (key, keyKind) =
        if (ip.nonEmpty && !ip.equalsIgnoreCase("unknown")) (ip, "ip")
        else if (consumerId.nonEmpty) (consumerId, "consumer")
        else (user.userId, "consumer")
      SelfServiceRateLimiter.check("signal_channel_create", key, keyKind) match {
        case SelfServiceRateLimiter.Blocked(scope, _, exceeded) =>
          throw Status.RESOURCE_EXHAUSTED
            .withDescription(SelfServiceRateLimiter.blockedMessage(scope, exceeded))
            .asRuntimeException()
        case _ => // Allowed, Warned (already logged) or Skipped
      }
    }
    val published = SignalChannels.publish(request.channelName, user.userId, consumerId, post)
    PublishResponse(
      messageId = published.message_id,
      channelName = published.channel_name,
      timestamp = toTimestamp(published.timestamp),
      channelMessageCount = published.channel_message_count,
      sequence = published.sequence)
  }

  override def fetch(request: FetchRequest): Future[FetchResponse] = withUser { user =>
    if (!RedisMessaging.validateChannelName(request.channelName)) throw invalid(InvalidSignalChannelName)
    val offset = math.max(0, request.offset)
    val limit = if (request.limit <= 0) 50 else request.limit
    // proto3 cannot tell "unset" from 0, so 0 means offset mode; use offset 0 for a first read
    // and continue with next_after_sequence.
    val afterSequence = if (request.afterSequence > 0L) Some(request.afterSequence) else None
    val page = SignalChannels.fetch(request.channelName, offset, limit, afterSequence, user.userId)
    FetchResponse(
      channelName = page.channel_name,
      messages = page.messages.map(toProto),
      totalCount = page.total_count,
      hasMore = page.has_more,
      latestSequence = page.latest_sequence,
      nextAfterSequence = page.next_after_sequence,
      visibleCount = page.visible_count)
  }

  override def listChannels(request: ListChannelsRequest): Future[ListChannelsResponse] = withUser { _ =>
    ListChannelsResponse(SignalChannels.listBroadcastChannels().map(c =>
      SignalChannelInfo(channelName = c.channel_name, messageCount = c.message_count, ttlSeconds = c.ttl_seconds)))
  }

  override def subscribe(request: SubscribeRequest, responseObserver: StreamObserver[SignalMessage]): Unit = {
    val user = AuthInterceptor.USER_CONTEXT_KEY.get()
    if (user == null) {
      responseObserver.onError(unauthenticated)
      return
    }
    val channelName = request.channelName
    if (!RedisMessaging.validateChannelName(channelName)) {
      responseObserver.onError(invalid(InvalidSignalChannelName))
      return
    }
    val userId = user.userId
    logger.info(s"SignalChannelsServiceImpl says: User $userId subscribed to signal channel $channelName")

    val bridge = new StreamObserver[String] {
      override def onNext(envelopeJson: String): Unit =
        SignalChannels.parseMessage(envelopeJson) match {
          case Some(msg) if SignalChannels.isVisibleTo(msg, userId) => responseObserver.onNext(toProto(msg))
          case Some(_) => // private message for someone else
          case None => logger.warn(s"SignalChannelsServiceImpl says: Dropped unparseable envelope on $channelName")
        }
      override def onError(t: Throwable): Unit = responseObserver.onError(t)
      override def onCompleted(): Unit = responseObserver.onCompleted()
    }

    SignalEventBus.subscribe(channelName, bridge)

    responseObserver match {
      case ssco: ServerCallStreamObserver[_] =>
        ssco.setOnCancelHandler(() => {
          SignalEventBus.unsubscribe(channelName, bridge)
          logger.info(s"SignalChannelsServiceImpl says: User $userId unsubscribed from signal channel $channelName")
        })
      case _ =>
    }
  }
}
