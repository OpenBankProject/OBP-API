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
package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_1_0.CustomerJsonV310
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.Nil

class CustomerMessageTest extends V400ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createCustomerMessage))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getCustomerMessages))

  lazy val testBankId = randomBankId
  lazy val createMessageJsonV400: CreateMessageJsonV400 = SwaggerDefinitionsJSON.createMessageJsonV400
  
  
  feature("Create Customer Message v4.0.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").POST
      val response400 = makePostRequest(request400, write(createMessageJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)

      val requestGet400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").GET
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 401")
      responseGet400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      responseGet400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").POST <@(user1)
      val response400 = makePostRequest(request400, write(createMessageJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message contains (canCreateCustomerMessage.toString()) should be (true)


      val requestGet400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 403")
      responseGet400.code should equal(403)
      responseGet400.body.extract[ErrorMessage].message contains (canGetCustomerMessages.toString()) should be (true)
    }
    
    
    scenario("We will call the Add endpoint with user credentials and role but no customerId", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, canCreateCustomerMessage.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, canGetCustomerMessages.toString)
      val request400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").POST <@(user1)
      val response400 = makePostRequest(request400, write(createMessageJsonV400))
      response400.code should equal(404)
      response400.body.extract[ErrorMessage].message contains CustomerNotFoundByCustomerId should be (true)

      val requestGet400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ "testCustomerId" / "messages").GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 404")
      responseGet400.code should equal(404)
      responseGet400.body.extract[ErrorMessage].message contains CustomerNotFoundByCustomerId should be (true)
      
    }
     
    scenario("We will call the Add endpoint with user credentials and role with proper customerId", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {

      //1st: Prepare the customer 
      val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateCustomer.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v4_0_0_Request / "banks" / testBankId / "customers").POST <@(user1)
      val response = makePostRequest(request, write(postCustomerJson))
      Then("We should get a 201")
      response.code should equal(201)
      val newCustomer = response.body.extract[CustomerJsonV310]
      val customerId = newCustomer.customer_id
      
      
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, canCreateCustomerMessage.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, canGetCustomerMessages.toString)
      val request400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ customerId / "messages").POST  <@(user1)
      val response400 = makePostRequest(request400, write(createMessageJsonV400))
      response400.code should equal(201)

      val requestGet400 = (v4_0_0_Request / "banks" / testBankId / "customers"/ customerId / "messages").GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      responseGet400.code should equal(200)
      val messages = responseGet400.body.extract[CustomerMessagesJsonV400].messages
      messages.length > 0 shouldBe true

      messages.head.from_department should be(createMessageJsonV400.from_department)
      messages.head.from_person should be(createMessageJsonV400.from_person)
      messages.head.message should be(createMessageJsonV400.message)
      messages.head.transport should be(createMessageJsonV400.transport) 
      
    }
    
  }


}
