/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetOidcConsent
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{ConsentNotFound, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.consent.Consents
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * This suite covers GET /obp/v7.0.0/oidc/consents/CONSENT_ID, which an OIDC provider calls to
 * finish a consent flow from OBP-API's own record of the Consent rather than from the
 * user, status and consumer carried on the browser's callback URL.
 */
class GetOidcConsentTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Http4s700OpenIdConnect.getOidcConsent))

  private def withRole[T](block: => T): T = {
    val addedEntitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetOidcConsent.toString)
    try block finally Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
  }

  feature(s"Get OIDC Consent - GET /obp/v7.0.0/oidc/consents/CONSENT_ID - $VersionOfApi") {

    scenario("Anonymous access should fail with 401", ApiEndpoint, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v7_0_0_Request / "oidc" / "consents" / "nonexistent_consent_id").GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.ApplicationNotIdentified)
    }

    scenario("Authenticated user without role should fail with 403", ApiEndpoint, VersionOfApi) {
      When("We make the request as an authenticated user without the required role")
      val request = (v7_0_0_Request / "oidc" / "consents" / "nonexistent_consent_id").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetOidcConsent)
    }

    scenario("An unknown CONSENT_ID should fail with 404", ApiEndpoint, VersionOfApi) {
      When("We request a consent that does not exist")
      val request = (v7_0_0_Request / "oidc" / "consents" / "nonexistent_consent_id").GET <@ (user1)
      val response = withRole(makeGetRequest(request))

      Then("We should get a 404")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(ConsentNotFound)
    }

    scenario("An existing consent returns its own user, consumer and consent request", ApiEndpoint, VersionOfApi) {
      Given("A consent created by user2 for testConsumer2 from a consent request")
      val consentRequestId = java.util.UUID.randomUUID().toString
      val consent = Consents.consentProvider.vend
        .createObpConsent(resourceUser2, "12345678", Some(consentRequestId), Some(testConsumer2))
        .openOrThrowException("test consent could not be created")

      When("A caller holding CanGetOidcConsent reads it")
      val request = (v7_0_0_Request / "oidc" / "consents" / consent.consentId).GET <@ (user1)
      val response = withRole(makeGetRequest(request))

      Then("We get the facts from the stored consent, not from the caller")
      response.code should equal(200)
      val body = response.body.extract[OpenIdConnectConsentJsonV700]
      body.consent_id should equal(consent.consentId)
      body.status should equal(consent.status)
      body.consent_request_id should equal(Some(consentRequestId))
      body.consumer_id should equal(testConsumer2.consumerId.get)
      body.client_id should equal(Some(testConsumer2.key.get))
      body.user_id should equal(resourceUser2.userId)
      body.username should equal(resourceUser2.name)
      body.provider should equal(resourceUser2.provider)
    }
  }
}
