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
package code.api.v5_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{postUserAuthContextJson, postUserAuthContextUpdateJsonV310}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.CustomerJsonV310
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class UserAuthContextTest extends V500ServerSetupAsync {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createUserAuthContext))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getUserAuthContexts))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.createUserAuthContextUpdateRequest))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.answerUserAuthContextUpdateChallenge))

  val postUserAuthContextJsonV310 = SwaggerDefinitionsJSON.postUserAuthContextJson
  val postUserAuthContextJsonV5002 = SwaggerDefinitionsJSON.postUserAuthContextJson.copy(key="TOKEN")

  feature("Add/Get Auth Context v5.0.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "users" / userId1.value / "auth-context").POST
      val response500 = makePostRequest(request500, write(postUserAuthContextJsonV310))
      Then("We should get a 401")
      response500.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "users" / userId1.value / "auth-context").POST <@(user1)
      val response500 = makePostRequest(request500, write(postUserAuthContextJsonV310))
      Then("We should get a 403")
      response500.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateUserAuthContext)
      response500.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateUserAuthContext)
    }

    scenario("We will call the Get endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "users" / userId1.value / "auth-context").GET
      val response500 = makeGetRequest(request500)
      Then("We should get a 401")
      response500.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Get endpoint without a proper role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "users" / userId1.value / "auth-context").GET <@(user1)
      val response500 = makeGetRequest(request500)
      Then("We should get a 403")
      response500.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetUserAuthContext)
      response500.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetUserAuthContext)
    }


    scenario("We will call the Add, Get and Delete endpoints with user credentials and role", ApiEndpoint1, ApiEndpoint2,  VersionOfApi) {
      When("We try to create the UserAuthContext v5.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateUserAuthContext.toString)
      val requestUserAuthContext500 = (v5_0_0_Request / "users" / userId1.value / "auth-context").POST <@(user1)
      val responseUserAuthContext500 = makePostRequest(requestUserAuthContext500, write(postUserAuthContextJsonV310))
      Then("We should get a 201")
      responseUserAuthContext500.code should equal(201)
      val customerJson = responseUserAuthContext500.body.extract[UserAuthContextJsonV500]

      When("We try to create the UserAuthContext v5.0.0")
      val successReq = (v5_0_0_Request / "users" / userId1.value / "auth-context").POST <@(user1)
      val successRes = makePostRequest(successReq, write(postUserAuthContextJsonV5002))
      Then("We should get a 201")
      successRes.code should equal(201)
      successRes.body.extract[UserAuthContextJsonV500]

      When("We try to make the GET request v5.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetUserAuthContext.toString)
      val successGetReq = (v5_0_0_Request / "users" / userId1.value / "auth-context").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val userAuthContexts = successGetRes.body.extract[UserAuthContextsJsonV500]
      userAuthContexts.user_auth_contexts.map(_.user_id).forall(userId1.value ==) shouldBe (true)
      userAuthContexts.user_auth_contexts.map(_.consumer_id).forall(testConsumer.consumerId.get ==) shouldBe (true)
    }


  }
  
  feature("Add/Get User Auth Context Update Request v5.0.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "banks"/testBankId1.value /  "users" / "current" / "auth-context-updates" / "SMS").POST
      val response500 = makePostRequest(request500, write(postUserAuthContextJson))
      Then("We should get a 401")
      response500.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario("We will call the Get endpoint without a user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "banks"/testBankId1.value /  "users" / "current" / "auth-context-updates" / "123"/"challenge").POST
      val response500 = makePostRequest(request500, write(postUserAuthContextUpdateJsonV310))
      Then("We should get a 401")
      response500.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response500.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario("We will call the Add, Get and Delete endpoints with user credentials and role", ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {

      When("We need to prepare the bankId first.")
      val requestCreateCustomer = (v5_0_0_Request / "banks" / testBankId1.value / "customers").POST <@(user1)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCustomerAtAnyBank.toString)
      val responseCustomer = makePostRequest(requestCreateCustomer, write(SwaggerDefinitionsJSON.postCustomerJsonV310))
      Then("We should get a 201")
      responseCustomer.code should equal(201)
      val infoPost = responseCustomer.body.extract[CustomerJsonV310]
      val customerNumner = infoPost.customer_number
      
      When(s"We try to create the UserAuthContextRequestUpdate v5.0.0 $ApiEndpoint3")
      val requestUserAuthContext500 = (v5_0_0_Request / "banks"/testBankId1.value /  "users" / "current" / "auth-context-updates" / "SMS" ).POST <@(user1)
      val responseUserAuthContext500 = makePostRequest(requestUserAuthContext500, write(postUserAuthContextJsonV310.copy(value = customerNumner)))
      Then("We should get a 201")
      responseUserAuthContext500.code should equal(201)
      val userAuthContextUpdateJsonV500 = responseUserAuthContext500.body.extract[UserAuthContextUpdateJsonV500]
      val userAuthContextId = userAuthContextUpdateJsonV500.user_auth_context_update_id
      
      
      When(s"We try to answer the UserAuthContextRequestUpdate v5.0.0 $ApiEndpoint4")
      val successReq = (v5_0_0_Request / "banks"/testBankId1.value /  "users" / "current" / "auth-context-updates" /userAuthContextId /"challenge").POST <@(user1)
      val successRes = makePostRequest(successReq, write(postUserAuthContextUpdateJsonV310))
      Then("We should get a 200")
      successRes.code should equal(200)
      val result = successRes.body.extract[UserAuthContextUpdateJsonV500]
      

      When("We try to make the GET request v5.0.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetUserAuthContext.toString)
      val successGetReq = (v5_0_0_Request / "users" / userId1.value / "auth-context").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val userAuthContexts = successGetRes.body.extract[UserAuthContextsJsonV500]
      userAuthContexts.user_auth_contexts.map(_.user_id).forall(userId1.value ==) shouldBe (true)
      userAuthContexts.user_auth_contexts.map(_.consumer_id).forall(testConsumer.consumerId.get ==) shouldBe (true)
    }
    
    
  }
}
