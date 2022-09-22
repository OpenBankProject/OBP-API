/**
Open Bank Project - API
Copyright (C) 2011-2022, TESOBE GmbH

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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{postUserAuthContextJson, postUserAuthContextUpdateJsonV310}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v2_1_0.CustomerJSONs
import code.api.v3_0_0.CustomerJSONsV300
import code.api.v3_1_0.CustomerJsonV310
import code.api.v4_0_0.CustomersMinimalJsonV400
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import java.util.Date
import scala.language.postfixOps

class CustomerTest extends V500ServerSetupAsync {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    CustomerX.customerProvider.vend.bulkDeleteCustomers()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.getMyCustomersAtAnyBank))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getMyCustomersAtBank))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.getCustomersAtOneBank))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_0_0.getCustomersMinimalAtOneBank))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_0_0.createCustomer))

  lazy val bankId = testBankId1.value
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310.copy(last_ok_date= new Date())
  
  feature(s"$ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3 $ApiEndpoint4 successful cases") {

    scenario(s"We will call $ApiEndpoint1 with credentials", ApiEndpoint1, VersionOfApi) {

      
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("we prepare the user1's customer")
      val postRequestCreateCustomer = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponseCreateCustomer = makePostRequest(postRequestCreateCustomer, write(postCustomerJson))
      Then("We should get a 201")
      postResponseCreateCustomer.code should equal(201)
      val customerIdUser1 = postResponseCreateCustomer.body.extract[CustomerJsonV310].customer_id

      lazy val createUserCustomerLinkJson = SwaggerDefinitionsJSON.createUserCustomerLinkJson
        .copy(user_id = resourceUser1.userId, customer_id = customerIdUser1)
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateUserCustomerLink.toString())
      val createRequestCustomerLinkUser1 = (v5_0_0_Request / "banks" / bankId / "user_customer_links" ).POST <@(user1)
      val createResponseCustomerLinkUser1 = makePostRequest(createRequestCustomerLinkUser1, write(createUserCustomerLinkJson))
      Then("We should get a 201")
      createResponseCustomerLinkUser1.code should equal(201)
      

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, CanCreateCustomer.toString)
      Then("we prepare the user2's customer")
      val postRequestCreateCustomerUser2 = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user2)
      val postResponseCreateCustomerUser2 = makePostRequest(postRequestCreateCustomerUser2, write(postCustomerJson))
      Then("We should get a 201")
      postResponseCreateCustomerUser2.code should equal(201)
      val customerIdUser2 = postResponseCreateCustomerUser2.body.extract[CustomerJsonV310].customer_id

      lazy val createUserCustomerLinkJsonUser2 = SwaggerDefinitionsJSON.createUserCustomerLinkJson
        .copy(user_id = resourceUser2.userId, customer_id = customerIdUser2)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, CanCreateUserCustomerLink.toString())
      val createRequest = (v5_0_0_Request / "banks" / bankId / "user_customer_links" ).POST <@(user1)
      val createResponse = makePostRequest(createRequest, write(createUserCustomerLinkJsonUser2))
      Then("We should get a 201")
      createResponse.code should equal(201)

      Then(s"We test $ApiEndpoint1")
      val requestApiEndpoint1 = (v5_0_0_Request / "my"/ "customers").GET <@(user1)
      val responseApiEndpoint1 = makeGetRequest(requestApiEndpoint1)
      Then("We should get a 200")
      responseApiEndpoint1.code should equal(200)
      responseApiEndpoint1.body.extract[CustomerJSONs].customers.length == 1 should be (true)

      Then(s"We test $ApiEndpoint2")
      val requestApiEndpoint2 = (v5_0_0_Request / "banks"/ bankId /"my"/ "customers").GET <@(user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 200")
      responseApiEndpoint2.code should equal(200)
      responseApiEndpoint2.body.extract[CustomerJSONs].customers.length == 1 should be (true)
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomers.toString)
      Then(s"We test $ApiEndpoint3")
      val requestApiEndpoint3 = (v5_0_0_Request / "banks"/ bankId /"customers").GET <@(user1)
      val responseApiEndpoint3 = makeGetRequest(requestApiEndpoint3)
      Then("We should get a 200")
      responseApiEndpoint3.code should equal(200)
      responseApiEndpoint3.body.extract[CustomerJSONsV300].customers.length == 2 should be (true) 
      
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersMinimal.toString)
      Then(s"We test $ApiEndpoint4")
      val requestApiEndpoint4 = (v5_0_0_Request / "banks"/ bankId /"customers-minimal").GET <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 200")
      responseApiEndpoint4.code should equal(200)
      responseApiEndpoint4.body.extract[CustomersMinimalJsonV400].customers.length == 2 should be (true)
    }
  }

  feature(s"$ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3 $ApiEndpoint4 error cases") {
    scenario(s"$ApiEndpoint1 without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint1 = (v5_0_0_Request / "my"/ "customers").GET 
      val responseApiEndpoint1 = makeGetRequest(requestApiEndpoint1)
      Then("We should get a 401")
      responseApiEndpoint1.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint1.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario(s"$ApiEndpoint2 without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint2 = (v5_0_0_Request / "my"/ "customers").GET 
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)
      Then("We should get a 401")
      responseApiEndpoint2.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint2.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario(s"$ApiEndpoint3 without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint3 = (v5_0_0_Request / "banks"/ bankId /"customers").GET 
      val responseApiEndpoint3 = makeGetRequest(requestApiEndpoint3)
      Then("We should get a 401")
      responseApiEndpoint3.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint3.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario(s"$ApiEndpoint3 miss role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint3 = (v5_0_0_Request / "banks"/ bankId /"customers").GET  <@(user1)
      val responseApiEndpoint3 = makeGetRequest(requestApiEndpoint3)
      Then("We should get a 403")
      responseApiEndpoint3.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetUserAuthContext)
      responseApiEndpoint3.body.extract[ErrorMessage].message contains (UserHasMissingRoles ) should be (true)
      responseApiEndpoint3.body.extract[ErrorMessage].message contains (CanGetCustomers.toString()) should be (true)
    }

    scenario(s"$ApiEndpoint4 without a user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint4 = (v5_0_0_Request / "banks"/ bankId /"customers-minimal").GET 
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 401")
      responseApiEndpoint4.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseApiEndpoint4.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }

    scenario(s"$ApiEndpoint4 miss role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v5.0.0")
      val requestApiEndpoint4 = (v5_0_0_Request / "banks"/ bankId /"customers-minimal").GET <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      Then("We should get a 403")
      responseApiEndpoint4.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetUserAuthContext)
      responseApiEndpoint4.body.extract[ErrorMessage].message contains (UserHasMissingRoles ) should be (true)
      responseApiEndpoint4.body.extract[ErrorMessage].message contains (CanGetCustomersMinimal.toString()) should be (true)
    }
  }


  feature(s"Create Customer $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint5, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers").POST
      val response = makePostRequest(request, write(postCustomerJson))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"Create Customer $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response = makePostRequest(request, write(postCustomerJson))
      Then("We should get a 403")
      response.code should equal(403)
      val errorMsg = UserHasMissingRoles + canCreateCustomer + " or " + canCreateCustomerAtAnyBank
      And("error should be " + errorMsg)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (canCreateCustomerAtAnyBank.toString()) should be (true)
    }
    scenario("We will call the endpoint with a user credentials and a proper role", ApiEndpoint5, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response = makePostRequest(request, write(postCustomerJson))
      Then("We should get a 201")
      response.code should equal(201)
      val infoPost = response.body.extract[CustomerJsonV310]

      When("We make the request: Get Customer specified by CUSTOMER_ID")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      val requestGet = (v5_0_0_Request / "banks" / bankId / "customers" / infoPost.customer_id).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV310]
      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)
    }
    scenario("We will call the endpoint with a user credentials and a proper role and minimal POST JSON", ApiEndpoint5, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response = makePostRequest(request, write(PostCustomerJsonV500(legal_name = "Legal Name",mobile_phone_number = "+44 07972 444 876")))
      Then("We should get a 201")
      response.code should equal(201)
      val infoPost = response.body.extract[CustomerJsonV310]

      When("We make the request: Get Customer specified by CUSTOMER_ID")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      val requestGet = (v5_0_0_Request / "banks" / bankId / "customers" / infoPost.customer_id).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV310]
      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)
    }
  }

  
}
