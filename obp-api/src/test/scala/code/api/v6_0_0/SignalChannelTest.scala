package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanDeleteSignalChannel
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{SignalMessageContainsDangerousCharacters, SignalMessageTooLong, UserHasMissingRoles}
import code.signal.SignalContentPolicy
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.jvalue2extractable
import org.scalatest.Tag

/**
 * Validation / error-path tests for the /signal/channels endpoints.
 *
 * Every scenario here fails BEFORE RedisMessaging is touched (auth and role
 * checks run in the middleware; the size and character checks run in the
 * handler ahead of the Redis publish), so no Redis instance is needed.
 * Success paths (publish 201, delete 200) require Redis and are not covered.
 */
class SignalChannelTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpointPublish extends Tag("publishSignalMessage")
  object ApiEndpointDelete extends Tag("deleteSignalChannel")

  private def publishRequest = (v6_0_0_Request / "signal" / "channels" / "test-channel" / "messages").POST
  private def deleteRequest = (v6_0_0_Request / "signal" / "channels" / "test-channel").DELETE

  feature(s"Publish Signal Message - POST /obp/v6.0.0/signal/channels/CHANNEL_NAME/messages - $VersionOfApi") {

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
      val request = (v6_0_0_Request / "signal" / "channels" / longName / "messages").POST <@ (user1)
      val body = """{"payload":{"note":"Grüße aus Berlin, 東京"}}"""
      val response = makePostRequest(request, body)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidSignalChannelName)
    }
  }

  feature(s"Delete Signal Channel - DELETE /obp/v6.0.0/signal/channels/CHANNEL_NAME - $VersionOfApi") {

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
