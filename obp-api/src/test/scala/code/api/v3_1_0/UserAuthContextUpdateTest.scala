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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCustomer, CanGetUserAuthContext, CanGetUserCustomerLink}
import code.api.util.ApiRole
import code.api.v2_0_0.UserCustomerLinksJson
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.consumer.Consumers
import code.context.UserAuthContextUpdateProvider
import code.entitlement.Entitlement
import code.scope.Scope
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.UserAuthContextUpdateStatus
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class UserAuthContextUpdateTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createUserAuthContextUpdateRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.answerUserAuthContextUpdateChallenge))

  val postUserAuthContextJson = SwaggerDefinitionsJSON.postUserAuthContextJson
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310

  feature("Create User Auth Context Update Request v3.1.0") {
    scenario("We will call the Create endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We try to create the User Auth Context Update v3.1.0")
      val bankId = randomBankId
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(bankId, consumerId, ApiRole.canCreateUserAuthContextUpdate.toString())
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val infoPost = response310.body.extract[CustomerJsonV310]
      
      val scaMethod = StrongCustomerAuthentication.SMS.toString
      val requestUserAuthContextUpdate310 = (v3_1_0_Request / "banks" / bankId / "users" / "current" / "auth-context-updates" / scaMethod).POST <@(user1)
      val responseUserAuthContextUpdate310 = makePostRequest(requestUserAuthContextUpdate310, write(postUserAuthContextJson.copy(value = infoPost.customer_number)))
      Then("We should get a 201")
      responseUserAuthContextUpdate310.code should equal(201)
      responseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson]
    }
    scenario("We will call the Answer endpoint with user credentials and wrong challenge answer", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We try to answer the User Auth Context Update v3.1.0")
      val bankId = randomBankId
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(bankId, consumerId, ApiRole.canCreateUserAuthContextUpdate.toString())
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val infoPost = response310.body.extract[CustomerJsonV310]
      
      val scaMethod = StrongCustomerAuthentication.SMS.toString
      val createRequestUserAuthContextUpdate310 = (v3_1_0_Request / "banks" / bankId / "users" / "current" / "auth-context-updates" / scaMethod).POST <@(user1)
      val createResponseUserAuthContextUpdate310 = makePostRequest(createRequestUserAuthContextUpdate310, write(postUserAuthContextJson.copy(value = infoPost.customer_number)))
      Then("We should get a 201")
      createResponseUserAuthContextUpdate310.code should equal(201)
      val authContextUpdateId = createResponseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].user_auth_context_update_id
      val wrongAnswerJson = PostUserAuthContextUpdateJsonV310(answer = "1234567")
      
      val requestUserAuthContextUpdate310 = (v3_1_0_Request/ "banks" / bankId / "users" / "current" / "auth-context-updates" / authContextUpdateId / "challenge").POST <@(user1)
      val responseUserAuthContextUpdate310 = makePostRequest(requestUserAuthContextUpdate310, write(wrongAnswerJson))
      Then("We should get a 200")
      responseUserAuthContextUpdate310.code should equal(200)
      val status = responseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].status
      status should equal(UserAuthContextUpdateStatus.REJECTED.toString)
    }
    scenario("We will call the Answer endpoint with user credentials and right challenge answer", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We try to answer the User Auth Context Update v3.1.0")
      val bankId = randomBankId
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(bankId, consumerId, ApiRole.canCreateUserAuthContextUpdate.toString())
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val infoPost = response310.body.extract[CustomerJsonV310]

      val postUserAuthContextJson1 = SwaggerDefinitionsJSON.postUserAuthContextJson.copy(value = infoPost.customer_number)
      
      val scaMethod = StrongCustomerAuthentication.SMS.toString
      val createRequestUserAuthContextUpdate310 = (v3_1_0_Request / "banks" / bankId / "users" / "current" / "auth-context-updates" / scaMethod).POST <@(user1)
      val createResponseUserAuthContextUpdate310 = makePostRequest(createRequestUserAuthContextUpdate310, write(postUserAuthContextJson1))
      Then("We should get a 201")
      createResponseUserAuthContextUpdate310.code should equal(201)
      val authContextUpdateId = createResponseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].user_auth_context_update_id

      val challenge = UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.getUserAuthContextUpdatesBox(resourceUser1.userId) match {
        case Full(list) if list.filter(_.userAuthContextUpdateId == authContextUpdateId).size == 1 =>
          list.filter(_.userAuthContextUpdateId == authContextUpdateId).map(_.challenge).head
        case _ =>
          ""
      }
      val rightAnswerJson = PostUserAuthContextUpdateJsonV310(answer = challenge)

      val requestUserAuthContextUpdate310 = (v3_1_0_Request/ "banks" / bankId / "users" / "current" / "auth-context-updates" / authContextUpdateId / "challenge").POST <@(user1)
      val responseUserAuthContextUpdate310 = makePostRequest(requestUserAuthContextUpdate310, write(rightAnswerJson))
      Then("We should get a 200")
      responseUserAuthContextUpdate310.code should equal(200)
      val status = responseUserAuthContextUpdate310.body.extract[UserAuthContextUpdateJson].status
      status should equal(UserAuthContextUpdateStatus.ACCEPTED.toString)

      When("We try to make the GET request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetUserAuthContext.toString)
      val successGetReq = (v3_1_0_Request / "users" / userId.value / "auth-context").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val userAuthContexts = successGetRes.body.extract[UserAuthContextsJson].user_auth_contexts
      userAuthContexts.map(i => (i.key, i.value) == (postUserAuthContextJson1.key, postUserAuthContextJson1.value)) shouldBe (List(true))

      //Get User Customer Link v4.0.0
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetUserCustomerLink.toString())
      val getRequest = (baseRequest / "obp" / "v4.0.0" / "banks" / bankId / "user_customer_links" / "customers" / infoPost.customer_id).GET <@(user1)
      val getResponse = makeGetRequest(getRequest)
      Then("We should get a 200")
      getResponse.code should equal(200)
      val size = getResponse.body.extract[UserCustomerLinksJson]
        .user_customer_links.filter(_.customer_id == infoPost.customer_id).size
      size should be equals 1
    }
  }
}
