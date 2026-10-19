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

package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.consent.Consents
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JNothing
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class ConsentTests extends V400ServerSetup with DefaultUsers {

   override def beforeAll(): Unit = {
     super.beforeAll()
   }

   override def afterAll(): Unit = {
     super.afterAll()
   }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getConsents))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateConsentStatus))

  feature("Assuring that endpoint createBank works as expected - v4.0.0") {

    scenario(s"We try to consume endpoint $ApiEndpoint1 - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / "SOME_BANK" / "my" / "consents").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario(s"We try to consume endpoint $ApiEndpoint1 - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / "SOME_BANK_WHICH_SHOULD_NOT_EXIST" / "my" / "consents").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 404")
      And("We should get a message: " + ErrorMessages.BankNotFound)
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(ErrorMessages.BankNotFound)
    }

  }

  feature(s"Assuring that endpoint $ApiEndpoint2 only serves the caller's own consent - v4.0.0") {

    scenario(s"We try to update the status of another user's consent - $ApiEndpoint2", ApiEndpoint2, VersionOfApi) {
      Given("A consent owned by user1")
      val consent = Consents.consentProvider.vend.createObpConsent(resourceUser1, "12345678", None).openOrThrowException("consent")
      When("user2 updates its status")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "consents" / consent.consentId).PUT <@ (user2)
      val response = makePutRequest(request, write(PutConsentStatusJsonV400(status = "REJECTED")))
      Then("We should get a 404, as if the consent did not exist")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.ConsentNotFound)
      And("The consent's JWT is not returned")
      (response.body \ "jwt") should equal(JNothing)
    }

  }
 }