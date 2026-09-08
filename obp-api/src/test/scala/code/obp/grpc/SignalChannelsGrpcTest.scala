package code.obp.grpc

import code.api.cache.Redis
import code.api.util.ErrorMessages.{InvalidJsonFormat, InvalidSignalChannelName, SignalMessageContainsDangerousCharacters, SignalMessageTooLong}
import code.obp.grpc.signal.api._
import code.setup.ServerSetupWithTestData
import code.signal.{SignalContentPolicy, SignalEventBus}
import io.grpc.stub.{MetadataUtils, StreamObserver}
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata, Status, StatusRuntimeException}
import org.scalatest.Tag

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.util.Try

/**
 * SignalChannelsService over a real socket, following ObpGrpcServerSmokeTest.
 *
 * The validation scenarios fail before Redis is touched (same as the REST
 * SignalChannelTest), so they run anywhere. The round-trip scenarios need a
 * reachable Redis and are cancelled, not failed, without one.
 */
class SignalChannelsGrpcTest extends ServerSetupWithTestData {

  object GrpcSignal extends Tag("GrpcSignal")

  private var grpcServer: ObpGrpcServer = _
  private var channel: ManagedChannel = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    grpcServer = new ObpGrpcServer(scala.concurrent.ExecutionContext.global, port = 0)
    grpcServer.start()
    channel = ManagedChannelBuilder
      .forAddress("localhost", grpcServer.boundPort)
      .usePlaintext()
      .asInstanceOf[ManagedChannelBuilder[_]]
      .build()
  }

  override def afterAll(): Unit = {
    if (channel != null) channel.shutdownNow()
    if (grpcServer != null) grpcServer.stop()
    super.afterAll()
  }

  private def authMetadata(token: String): Metadata = {
    val metadata = new Metadata()
    metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), s"""DirectLogin token="$token"""")
    metadata
  }

  private def tokenOf(user: Option[(_, code.api.util.APIUtil.OAuth.Token)]): String =
    user.map(_._2.value).getOrElse(fail("no DirectLogin token"))

  private def blockingStub(token: String): SignalChannelsServiceGrpc.SignalChannelsServiceBlockingStub =
    SignalChannelsServiceGrpc.blockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authMetadata(token)))

  private def asyncStub(token: String): SignalChannelsServiceGrpc.SignalChannelsServiceStub =
    SignalChannelsServiceGrpc.stub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authMetadata(token)))

  private def statusOf(body: => Any): Status = intercept[StatusRuntimeException](body).getStatus

  private def redisReachable: Boolean = Try {
    val jedis = Redis.jedisPool.getResource
    try jedis.ping() finally jedis.close()
  }.isSuccess

  private val cleanPayload = """{"message":"Please report what time it is where you are"}"""

  feature("SignalChannelsService validation matches the REST endpoints") {

    scenario("a call with no credentials is rejected", GrpcSignal) {
      val status = statusOf(SignalChannelsServiceGrpc.blockingStub(channel).publish(PublishRequest("test-channel", "", "", cleanPayload)))
      status.getCode should equal(Status.Code.UNAUTHENTICATED)
    }

    scenario("an invalid channel name is INVALID_ARGUMENT with the REST error message", GrpcSignal) {
      val status = statusOf(blockingStub(tokenOf(user1)).publish(PublishRequest("bad channel name!", "", "", cleanPayload)))
      status.getCode should equal(Status.Code.INVALID_ARGUMENT)
      status.getDescription should startWith(InvalidSignalChannelName)
    }

    scenario("a payload that is not JSON is INVALID_ARGUMENT", GrpcSignal) {
      val status = statusOf(blockingStub(tokenOf(user1)).publish(PublishRequest("test-channel", "", "", "not json")))
      status.getCode should equal(Status.Code.INVALID_ARGUMENT)
      status.getDescription should startWith(InvalidJsonFormat)
    }

    scenario("a payload over the size cap is INVALID_ARGUMENT OBP-39019", GrpcSignal) {
      val oversized = "x" * (SignalContentPolicy.maxPayloadLength + 1)
      val status = statusOf(blockingStub(tokenOf(user1)).publish(PublishRequest("test-channel", "", "", s"""{"data":"$oversized"}""")))
      status.getCode should equal(Status.Code.INVALID_ARGUMENT)
      status.getDescription should startWith(SignalMessageTooLong)
    }

    scenario("a payload containing a bidi override character is INVALID_ARGUMENT OBP-39020", GrpcSignal) {
      // Backslash-u escape on the wire; parses to the RLO code point, as in SignalChannelTest.
      val status = statusOf(blockingStub(tokenOf(user1)).publish(PublishRequest("test-channel", "", "", "{\"note\":\"click\\u202ehere\"}")))
      status.getCode should equal(Status.Code.INVALID_ARGUMENT)
      status.getDescription should equal(SignalMessageContainsDangerousCharacters)
    }

    scenario("a message_type containing a control character is INVALID_ARGUMENT OBP-39020", GrpcSignal) {
      val withNul = "te" + 0.toChar + "xt"
      val status = statusOf(blockingStub(tokenOf(user1)).publish(PublishRequest("test-channel", "", withNul, cleanPayload)))
      status.getCode should equal(Status.Code.INVALID_ARGUMENT)
      status.getDescription should equal(SignalMessageContainsDangerousCharacters)
    }

    scenario("Fetch and Subscribe reject an invalid channel name before touching Redis", GrpcSignal) {
      statusOf(blockingStub(tokenOf(user1)).fetch(FetchRequest("a" * 129, 0, 10))).getCode should equal(Status.Code.INVALID_ARGUMENT)
      // The blocking iterator only fails when it is first read.
      statusOf(blockingStub(tokenOf(user1)).subscribe(SubscribeRequest("a" * 129)).hasNext).getCode should equal(Status.Code.INVALID_ARGUMENT)
    }
  }

  feature("SignalChannelsService reads and writes the same Redis storage as REST") {

    scenario("Publish, Fetch and ListChannels round-trip a broadcast, and a private message stays private", GrpcSignal) {
      if (!redisReachable) cancel("Redis is not reachable from this test JVM")
      val channelName = s"grpc-test-${java.util.UUID.randomUUID().toString.take(8)}"
      val publisher = blockingStub(tokenOf(user1))
      val publisherUserId = resourceUser1.userId
      val otherUserId = resourceUser2.userId

      val broadcast = publisher.publish(PublishRequest(channelName, "", "task-request", cleanPayload))
      broadcast.channelName should equal(channelName)
      broadcast.messageId should not be empty
      broadcast.channelMessageCount should equal(1L)
      broadcast.timestamp.isDefined should equal(true)

      val privateMsg = publisher.publish(PublishRequest(channelName, otherUserId, "private", """{"secret":true}"""))
      privateMsg.channelMessageCount should equal(2L)
      // A private message to a third party is invisible to everyone but sender and recipient.
      publisher.publish(PublishRequest(channelName, "someone-else", "private", """{"secret":true}""")).channelMessageCount should equal(3L)

      val seenBySender = publisher.fetch(FetchRequest(channelName, 0, 10))
      seenBySender.totalCount should equal(3L)
      // The sender sees everything it sent, so visible_count matches total_count for it.
      seenBySender.visibleCount should equal(3L)
      // Sequences are stamped, strictly increasing, and reported consistently.
      broadcast.sequence should be > 0L
      privateMsg.sequence should be > broadcast.sequence
      seenBySender.messages.map(_.sequence) should equal(seenBySender.messages.map(_.sequence).sorted)
      seenBySender.latestSequence should be > privateMsg.sequence
      seenBySender.nextAfterSequence should equal(seenBySender.latestSequence)
      // Cursor read: only what came after the broadcast.
      val afterBroadcast = publisher.fetch(FetchRequest(channelName, 0, 10, broadcast.sequence))
      afterBroadcast.messages.map(_.messageId) should equal(Seq(privateMsg.messageId, seenBySender.messages.last.messageId))
      afterBroadcast.hasMore should equal(false)
      publisher.fetch(FetchRequest(channelName, 0, 10, seenBySender.latestSequence)).messages shouldBe empty
      // The stranger's cursor advances past private messages it cannot see.
      val strangerAfter = blockingStub(tokenOf(user3)).fetch(FetchRequest(channelName, 0, 10, broadcast.sequence))
      strangerAfter.messages shouldBe empty
      strangerAfter.nextAfterSequence should equal(seenBySender.latestSequence)
      seenBySender.messages.map(_.messageId) should contain allOf (broadcast.messageId, privateMsg.messageId)
      seenBySender.messages.size should equal(3)
      val first = seenBySender.messages.find(_.messageId == broadcast.messageId).get
      first.payloadJson should equal(cleanPayload)
      first.messageType should equal("task-request")
      first.senderUserId should equal(publisherUserId)
      first.toUserId should equal("")

      val seenByRecipient = blockingStub(tokenOf(user2)).fetch(FetchRequest(channelName, 0, 10))
      seenByRecipient.messages.map(_.messageId).toSet should equal(Set(broadcast.messageId, privateMsg.messageId))
      // total_count still counts the message to someone else; visible_count does not.
      seenByRecipient.totalCount should equal(3L)
      seenByRecipient.visibleCount should equal(2L)

      val seenByStranger = blockingStub(tokenOf(user3)).fetch(FetchRequest(channelName, 0, 10))
      seenByStranger.messages.map(_.messageId) should equal(Seq(broadcast.messageId))
      seenByStranger.totalCount should equal(3L)
      seenByStranger.visibleCount should equal(1L)

      publisher.listChannels(ListChannelsRequest()).channels.map(_.channelName) should contain(channelName)

      code.api.cache.RedisMessaging.deleteChannel(channelName)
    }

    scenario("a cursor read survives the channel being trimmed, where an offset read skips messages", GrpcSignal) {
      if (!redisReachable) cancel("Redis is not reachable from this test JVM")
      val channelName = s"grpc-trim-${java.util.UUID.randomUUID().toString.take(8)}"
      val reader = blockingStub(tokenOf(user1))
      def envelope(n: Int) =
        s"""{"message_id":"m$n","channel_name":"$channelName","sender_consumer_id":"","sender_user_id":"${resourceUser1.userId}","timestamp":"2026-09-05T10:00:00Z","message_type":"","payload":{"n":$n}}"""
      // Cap the channel at 3 so trimming happens on the 4th publish.
      val seqs = (1 to 3).map(n => code.api.cache.RedisMessaging.publishMessage(channelName, envelope(n), maxMessages = 3)._1)
      seqs should equal(seqs.sorted)

      // A poller that read m1..m3 by offset now asks for offset 3 ...
      (4 to 5).foreach(n => code.api.cache.RedisMessaging.publishMessage(channelName, envelope(n), maxMessages = 3))
      val byOffset = reader.fetch(FetchRequest(channelName, 3, 10))
      // ... and gets nothing: the list now holds m3,m4,m5 at positions 0..2. m4 and m5 are lost to it.
      byOffset.messages shouldBe empty
      byOffset.totalCount should equal(3L)

      // A poller holding m3's sequence gets exactly the two it has not seen.
      val byCursor = reader.fetch(FetchRequest(channelName, 0, 10, seqs.last))
      byCursor.messages.map(_.messageId) should equal(Seq("m4", "m5"))
      byCursor.hasMore should equal(false)
      byCursor.nextAfterSequence should equal(byCursor.latestSequence)

      code.api.cache.RedisMessaging.deleteChannel(channelName)
    }

    scenario("Subscribe streams a message published after the stream opened", GrpcSignal) {
      if (!redisReachable) cancel("Redis is not reachable from this test JVM")
      SignalEventBus.isRunning should equal(true)
      val channelName = s"grpc-sub-${java.util.UUID.randomUUID().toString.take(8)}"

      val received = new java.util.concurrent.ConcurrentLinkedQueue[SignalMessage]()
      val latch = new CountDownLatch(1)
      val observer = new StreamObserver[SignalMessage] {
        override def onNext(value: SignalMessage): Unit = { received.add(value); latch.countDown() }
        override def onError(t: Throwable): Unit = ()
        override def onCompleted(): Unit = ()
      }
      asyncStub(tokenOf(user1)).subscribe(SubscribeRequest(channelName), observer)

      // The observer is registered on the server inside subscribe(), but the call itself travels
      // over the socket: wait until the server-side registration is visible.
      val deadline = System.currentTimeMillis() + 10000
      while (SignalEventBus.subscriberCount(channelName) == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
      SignalEventBus.subscriberCount(channelName) should equal(1)

      val published = blockingStub(tokenOf(user2)).publish(PublishRequest(channelName, "", "hello", """{"hi":"there"}"""))

      latch.await(10, TimeUnit.SECONDS) should equal(true)
      val streamed = received.peek()
      streamed.messageId should equal(published.messageId)
      streamed.channelName should equal(channelName)
      streamed.payloadJson should equal("""{"hi":"there"}""")
      streamed.senderUserId should equal(resourceUser2.userId)
      streamed.sequence should equal(published.sequence)

      code.api.cache.RedisMessaging.deleteChannel(channelName)
    }
  }
}
