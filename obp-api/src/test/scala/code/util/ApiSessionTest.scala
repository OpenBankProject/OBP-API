/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.util

import code.api.util.{ApiSession, CallContext}
import code.api.util.APIUtil.HTTPParam
import code.model.dataAccess.ResourceUser
import code.util.Helper.MdcLoggable
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import net.liftweb.common.{Box, Full}

import java.util.Date

class ApiSessionTest extends AnyFeatureSpec with Matchers with GivenWhenThen with MdcLoggable  {
  
  Feature("test ApiSession.createSessionId method") {
    Scenario("update the CallContext Session Id") {
      val callContext = CallContext() 
      
      val callContextUpdated = ApiSession.createSessionId(Some(callContext))
      callContext.sessionId should be (None)
      callContextUpdated.get.sessionId should not be (None)
    }
  }
  
  Feature("test ApiSession.updateCallContextSessionId method") {
    Scenario("update the CallContext Session Id") {
      val callContext = CallContext() 
      
      val callContextUpdated = ApiSession.updateSessionId(Some(callContext), "12345")
      callContext.sessionId should be (None)
      callContextUpdated.get.sessionId should be (Some("12345"))
    }
  }
  
  Feature("CallContext.toLight is like for like with CallContext") {
    // Any field name that exists on BOTH CallContext and CallContextLight must carry the
    // same value through toLight — CallContext may have more fields, but overlapping names
    // must never diverge. Reflection over the case-class fields keeps this true for fields
    // added in the future without anyone remembering to extend this test.
    Scenario("every same-named field survives toLight unchanged")
    {
      val principal = ResourceUser(userId = "principal-user-id", name = "principal-name")

      // Populate every shared-name field with a distinctive, non-default value so a wrong
      // mapping cannot hide behind matching defaults.
      val cc = CallContext(
        gatewayLoginRequestPayload = Some(ApiSession.emptyPayloadOfJwt),
        gatewayLoginResponseHeader = Some("gateway-response-jwt"),
        spelling = Some("ISO20022"),
        user = Full(principal),
        startTime = Some(new Date(1000L)),
        endTime = Some(new Date(2000L)),
        correlationId = "corr-123",
        url = "/obp/v6.0.0/banks",
        verb = "GET",
        implementedInVersion = "v6.0.0",
        operationId = Some("OBPv6.0.0-getBanks"),
        authReqHeaderField = Full("Bearer abc"),
        directLoginParams = Map("token" -> "dl-token"),
        httpCode = Some(201),
        httpBody = Some("""{"ok":true}"""),
        requestHeaders = List(HTTPParam("X-Request-ID", List("rid-1"))),
        xRateLimitLimit = 100L,
        xRateLimitRemaining = 99L,
        xRateLimitReset = 42L,
        paginationOffset = Some("10"),
        paginationLimit = Some("50"),
        consentReferenceId = Some("consent-ref-1"),
        certificateTrust = Some("forwarded"),
        certificateTrustDetail = Some("cn=proxy")
      )
      val light = cc.toLight

      // Box (on CallContext) vs Option (on CallContextLight) is a representation
      // difference, not a value difference — compare through Option.
      def normalise(value: Any): Any = value match {
        case box: Box[_] => box.toOption
        case other => other
      }

      val ccFields = cc.productElementNames.zip(cc.productIterator).toMap
      val lightFields = light.productElementNames.zip(light.productIterator).toMap
      val sharedNames = ccFields.keySet.intersect(lightFields.keySet)

      // Guard the guard: if a rename ever shrinks the overlap, fail loudly instead of
      // silently comparing less.
      sharedNames should contain allOf(
        "correlationId", "url", "verb", "implementedInVersion", "startTime", "endTime",
        "operationId", "httpCode", "httpBody", "authReqHeaderField", "requestHeaders",
        "consentReferenceId", "certificateTrust", "certificateTrustDetail",
        "paginationOffset", "paginationLimit",
        "xRateLimitLimit", "xRateLimitRemaining", "xRateLimitReset")

      for (name <- sharedNames) {
        withClue(s"CallContext.$name vs CallContextLight.$name: ") {
          normalise(lightFields(name)) should be(normalise(ccFields(name)))
        }
      }
    }

    // The differently-named fields are a deliberate projection, pinned here by hand:
    // userId/userName come from the AUTHENTICATED principal (CallContext.user), never from
    // a resolved human. Under a consent the principal is the consent's shadow user; the
    // human stays on the context as consenter/onBehalfOfUser and is resolved at read time
    // via the consent table, never baked into stored rows.
    Scenario("userId and userName carry the AUTHENTICATED principal, even when consenter and onBehalfOfUser are set")
    {
      val principal = ResourceUser(userId = "principal-user-id", name = "principal-name")
      val human = ResourceUser(userId = "human-user-id", name = "human-name")

      val light = CallContext(
        user = Full(principal),
        consenter = Full(human),
        onBehalfOfUser = Full(human),
        directLoginParams = Map("token" -> "dl-token")
      ).toLight

      light.userId should be(Some("principal-user-id"))
      light.userName should be(Some("principal-name"))
      light.directLoginToken should be("dl-token")
      light.partialFunctionName should be("")
    }

    Scenario("without consent context, userId is simply the authenticated user")
    {
      val user = ResourceUser(userId = "plain-user-id", name = "plain-name")
      val light = CallContext(user = Full(user)).toLight
      light.userId should be(Some("plain-user-id"))
      light.userName should be(Some("plain-name"))
    }
  }

  Feature("test CallContext toString secure logging masking") {
    Scenario("toString should mask sensitive data") {
      val callContextWithSensitiveData = CallContext(
        directLoginParams = Map("password" -> "supersecret", "client_secret" -> "my_client_secret")
      )

      val toStringResult = callContextWithSensitiveData.toString

      // Verify that sensitive data is masked - should NOT contain the actual sensitive values
      toStringResult should not contain "supersecret"
      toStringResult should not contain "my_client_secret"

      // Verify that the result contains the case class structure (not just object reference)
      toStringResult should include("CallContext")

      // Verify that masking occurs by checking for masked patterns or lack of sensitive data
      val containsActualSensitiveData = toStringResult.contains("supersecret") ||
                                       toStringResult.contains("my_client_secret")

      containsActualSensitiveData should be (false)
    }
  }
}