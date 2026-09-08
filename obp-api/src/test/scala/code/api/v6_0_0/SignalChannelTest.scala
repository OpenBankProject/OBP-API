package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanDeleteSignalChannel
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{SignalMessageContainsDangerousCharacters, SignalMessageTooLong, UserHasMissingRoles}
import code.signal.SignalContentPolicy
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.JValue
import org.scalatest.Tag

/**
 * Validation / error-path tests for the /signal-channels endpoints.
 *
 * Every scenario here fails BEFORE RedisMessaging is touched (auth and role
 * checks run in the middleware; the size and character checks run in the
 * handler ahead of the Redis publish), so no Redis instance is needed.
 * The cursor scenario needs a reachable Redis and is cancelled, not failed, without one.
 */
class SignalChannelTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointPublish extends Tag("publishSignalMessage")
  object ApiEndpointDelete extends Tag("deleteSignalChannel")
  object ApiEndpointGetMessages extends Tag("getSignalMessages")

  private def redisReachable: Boolean = scala.util.Try {
    val jedis = code.api.cache.Redis.jedisPool.getResource
    try jedis.ping() finally jedis.close()
  }.isSuccess

  private def publishRequest = (v6_0_0_Request / "signal-channels" / "test-channel" / "messages").POST
  private def deleteRequest = (v6_0_0_Request / "signal-channels" / "test-channel").DELETE

  feature(s"Publish Signal Message - POST /obp/v6.0.0/signal-channels/CHANNEL_NAME/messages - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpointPublish, VersionOfApi) {
      val response = makePostRequest(publishRequest, """{"payload":{"hello":"world"}}""")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Body over the size cap should fail with 400 OBP-39019", ApiEndpointPublish, VersionOfApi) {
      val oversized = "x" * (SignalContentPolicy.maxPayloadLength + 1)
      val body = s"""{"payload":{"data":"$oversized"}}"""
      val response = makePostRequest(publishRequest <@ (user1), body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(SignalMessageTooLong)
    }

    scenario("Payload containing a bidi override character should fail with 400 OBP-39020", ApiEndpointPublish, VersionOfApi) {
      // ASCII backslash-u escape on the wire; parses to the RLO code point.
      val body = "{\"payload\":{\"note\":\"click\\u202ehere\"}}"
      val response = makePostRequest(publishRequest <@ (user1), body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should equal(SignalMessageContainsDangerousCharacters)
    }

    scenario("Payload containing a control character should fail with 400 OBP-39020", ApiEndpointPublish, VersionOfApi) {
      val body = "{\"payload\":{\"note\":\"abc\\u0000def\"}}"
      val response = makePostRequest(publishRequest <@ (user1), body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should equal(SignalMessageContainsDangerousCharacters)
    }

    scenario("message_type containing a dangerous character should fail with 400 OBP-39020", ApiEndpointPublish, VersionOfApi) {
      val body = "{\"payload\":{\"note\":\"fine\"},\"message_type\":\"te\\u202ext\"}"
      val response = makePostRequest(publishRequest <@ (user1), body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should equal(SignalMessageContainsDangerousCharacters)
    }

    scenario("Clean unicode passes the character check (fails later on channel name, not OBP-39020)", ApiEndpointPublish, VersionOfApi) {
      // A channel name over 128 characters is invalid, so the request fails
      // AFTER the size and character checks without reaching Redis — proving
      // legitimate international text is not rejected as dangerous.
      val longName = "a" * 129
      val request = (v6_0_0_Request / "signal-channels" / longName / "messages").POST <@ (user1)
      val body = """{"payload":{"note":"Grüße aus Berlin, 東京"}}"""
      val response = makePostRequest(request, body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidSignalChannelName)
    }
  }

  feature(s"Get Signal Messages - GET /obp/v6.0.0/signal-channels/CHANNEL_NAME/messages - $VersionOfApi") {

    scenario("a non-numeric after_sequence should fail with 400 OBP-10002", ApiEndpointGetMessages, VersionOfApi) {
      val request = (v6_0_0_Request / "signal-channels" / "test-channel" / "messages").GET <@ (user1) <<? List(("after_sequence", "later"))
      val response = makeGetRequest(request)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidNumber)
    }

    scenario("after_sequence returns only newer messages and next_after_sequence continues the cursor", ApiEndpointGetMessages, VersionOfApi) {
      if (!redisReachable) cancel("Redis is not reachable from this test JVM")
      val channelName = s"rest-cursor-${java.util.UUID.randomUUID().toString.take(8)}"
      val publish = (v6_0_0_Request / "signal-channels" / channelName / "messages").POST <@ (user1)
      val first = makePostRequest(publish, """{"payload":{"n":1}}""")
      first.code should equal(201)
      val firstSeq = (first.body \ "sequence").extract[Long]
      firstSeq should be > 0L
      makePostRequest(publish, """{"payload":{"n":2}}""").code should equal(201)
      makePostRequest(publish, """{"payload":{"n":3}}""").code should equal(201)

      val read = (v6_0_0_Request / "signal-channels" / channelName / "messages").GET <@ (user1)
      val newer = makeGetRequest(read <<? List(("after_sequence", firstSeq.toString)))
      newer.code should equal(200)
      val messages = newer.body \ "messages"
      (messages \ "payload" \ "n").extract[List[Int]] should equal(List(2, 3))
      val sequences = (messages \ "sequence").extract[List[Long]]
      sequences.forall(_ > firstSeq) should equal(true)
      (newer.body \ "has_more").extract[Boolean] should equal(false)
      // Counts describe the whole channel, not the page: three messages, all broadcasts.
      (newer.body \ "total_count").extract[Long] should equal(3L)
      (newer.body \ "visible_count").extract[Long] should equal(3L)
      val nextAfter = (newer.body \ "next_after_sequence").extract[Long]
      nextAfter should equal((newer.body \ "latest_sequence").extract[Long])

      val nothingNew = makeGetRequest(read <<? List(("after_sequence", nextAfter.toString)))
      (nothingNew.body \ "messages").extract[List[JValue]] shouldBe empty
      (nothingNew.body \ "next_after_sequence").extract[Long] should equal(nextAfter)

      code.api.cache.RedisMessaging.deleteChannel(channelName)
    }
  }

  feature(s"Delete Signal Channel - DELETE /obp/v6.0.0/signal-channels/CHANNEL_NAME - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpointDelete, VersionOfApi) {
      val response = makeDeleteRequest(deleteRequest)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without CanDeleteSignalChannel role should fail with 403", ApiEndpointDelete, VersionOfApi) {
      val response = makeDeleteRequest(deleteRequest <@ (user1))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteSignalChannel)
    }
  }
}
