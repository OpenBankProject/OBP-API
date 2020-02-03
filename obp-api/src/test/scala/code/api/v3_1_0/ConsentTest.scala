/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.RequestHeader
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_0_0.APIMethods300
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.consent.MappedConsent
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

class ConsentTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createConsent))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.answerConsentChallenge))

  object VersionOfApi2 extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint3 extends Tag(nameOf(APIMethods300.Implementations3_0_0.getUserByUserId))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccount(bankId)
  lazy val entitlements = List(EntitlementJsonV400("", CanGetAnyUser.toString()))
  lazy val postConsentEmailJsonV310 = SwaggerDefinitionsJSON.postConsentEmailJsonV310
    .copy(entitlements=entitlements)
    .copy(consumer_id=None)
    .copy(views=Nil)
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access")
  {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request")
      val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "EMAIL" ).POST
      val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario("We will call the endpoint with user credentials but wrong SCA method", ApiEndpoint1, VersionOfApi) {
      When("We make a request")
      val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "NOT_EMAIL_NEITHER_SMS" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(ConsentAllowedScaMethods)
    }

    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint3, VersionOfApi, VersionOfApi2) {
      When("We make a request")
      val request400 = (v3_1_0_Request / "banks" / bankId / "my" / "consents" / "EMAIL" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(RolesAllowedInConsent)

      Then("We grant the role and test it again")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      val secondResponse400 = makePostRequest(request400, write(postConsentEmailJsonV310))
      Then("We should get a 201")
      secondResponse400.code should equal(201)
      
      val consentId = secondResponse400.body.extract[ConsentJsonV310].consent_id
      val jwt = secondResponse400.body.extract[ConsentJsonV310].jwt
      val header = List((RequestHeader.`Consent-Id`, jwt))
      
      val requestGetUserByUserId400 = (v3_1_0_Request / "users" / "user_id" / resourceUser1.userId ).GET <@(user2)
      val responseGetUserByUserId400 = makeGetRequest(requestGetUserByUserId400, header)
      APIUtil.getPropsAsBoolValue(nameOfProperty="consents.allowed", defaultValue=false) match {
        case true => 
          responseGetUserByUserId400.body.extract[ErrorMessage].message should include(ConsentStatusIssue)
          
          val answerConsentChallengeRequest = (v3_1_0_Request / "banks" / bankId / "consents" / consentId / "challenge" ).POST <@(user1)
          val challenge = MappedConsent.find(By(MappedConsent.mConsentId, consentId)).map(_.challenge).getOrElse("")
          val post = PostConsentChallengeJsonV310(answer = challenge)
          val response400 = makePostRequest(answerConsentChallengeRequest, write(post))
          Then("We should get a 201")
          response400.code should equal(201)

          makeGetRequest(requestGetUserByUserId400, header)
            .body.extract[ErrorMessage].message should include(ConsumerKeyHeaderMissing)
          
          val headerConsumerKey = List((RequestHeader.`Consumer-Key`, "NON_EXISTING_VALUE"))
          makeGetRequest(requestGetUserByUserId400, header ::: headerConsumerKey)
            .body.extract[ErrorMessage].message should include(ConsentDoesntMatchApp)
          
        case false => 
          responseGetUserByUserId400.body.extract[ErrorMessage].message should include(ConsentDisabled)
      }
    }
  }
}
