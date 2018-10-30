/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.customer.Customer
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerAddressTest extends V310ServerSetup {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Customer.customerProvider.vend.bulkDeleteCustomers()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createCustomerAddress))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCustomerAddresses))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.deleteCustomerAddress))

  val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123")
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
  val postCustomerAddressJson = SwaggerDefinitionsJSON.postCustomerAddressJsonV310
  lazy val bankId = randomBankId

  feature("Add/Get/Delete Customer Address v3.1.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateCustomer)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCreateCustomer)
    }

    scenario("We will call the Get endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the Get endpoint without a proper role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomer)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetCustomer)
    }

    scenario("We will call the Delete endpoint without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses" / "CUSTOMER_ADDRESS_ID").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will call the Delete endpoint without a proper role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses" / "CUSTOMER_ADDRESS_ID").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateCustomer)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCreateCustomer)
    }

    scenario("We will call the Add, Get and Delete endpoints with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We try to create a customer address with non existing customer v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFoundByCustomerId)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We try to create the customer v3.1.0")
      val requestCustomer310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val responseCustomer310 = makePostRequest(requestCustomer310, write(postCustomerJson))
      Then("We should get a 200")
      responseCustomer310.code should equal(200)
      val customerJson = responseCustomer310.body.extract[CustomerJsonV310]

      When("We try to create the customer address v3.1.0")
      val successReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "address").POST <@(user1)
      val successRes = makePostRequest(successReq, write(postCustomerAddressJson))
      Then("We should get a 200")
      successRes.code should equal(200)
      successRes.body.extract[CustomerAddressJsonV310]

      When("We try to make the GET request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      val successGetReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "address").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val addresses = successGetRes.body.extract[CustomerAddressesJsonV310]
      val customerAddressId = addresses.addresses.map(_.customer_address_id).headOption.getOrElse("CUSTOMER_ADDRESS_ID")

      When("We try to make the DELETE request v3.1.0")
      val successDeleteReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "addresses" / customerAddressId).DELETE <@(user1)
      val successDeleteRes = makeDeleteRequest(successDeleteReq)
      Then("We should get a 200")
      successDeleteRes.code should equal(200)
    }
  }


}
