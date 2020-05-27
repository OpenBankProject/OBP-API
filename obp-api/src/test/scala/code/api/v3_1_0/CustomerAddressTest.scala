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

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.List

class CustomerAddressTest extends V310ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createCustomerAddress))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCustomerAddresses))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.deleteCustomerAddress))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.updateCustomerAddress))

  val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123")
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
  val postCustomerAddressJson = SwaggerDefinitionsJSON.postCustomerAddressJsonV310.copy(tags = List("mailing", "home"))
  lazy val bankId = randomBankId

  feature("Add/Get/Delete Customer Address v3.1.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Create endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateCustomerAddress)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateCustomerAddress)
    }

    scenario("We will call the Get endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Get endpoint without a proper role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomerAddress)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetCustomerAddress)
    }

    scenario("We will call the Delete endpoint without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses" / "CUSTOMER_ADDRESS_ID").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Delete endpoint without a proper role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "addresses" / "CUSTOMER_ADDRESS_ID").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteCustomerAddress)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanDeleteCustomerAddress)
    }

    scenario("We will call the Add, Get and Delete endpoints with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerAddress.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomerAddress.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteCustomerAddress.toString)
      When("We try to create a customer address with non existing customer v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "address").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerAddressJson))
      Then("We should get a 404")
      response310.code should equal(404)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].message should startWith (CustomerNotFoundByCustomerId)
      
      When("We try to create the customer v3.1.0")
      val requestCustomer310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val responseCustomer310 = makePostRequest(requestCustomer310, write(postCustomerJson))
      Then("We should get a 201")
      responseCustomer310.code should equal(201)
      val customerJson = responseCustomer310.body.extract[CustomerJsonV310]

      When("We try to create the customer address v3.1.0")
      val successReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "address").POST <@(user1)
      val successRes = makePostRequest(successReq, write(postCustomerAddressJson))
      Then("We should get a 201")
      successRes.code should equal(201)
      val customerAddress = successRes.body.extract[CustomerAddressJsonV310]
      
      When("We try to update the customer address v3.1.0")
      val successUpdateReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "addresses" / customerAddress.customer_address_id).PUT <@(user1)
      val successUpdateRes = makePutRequest(successUpdateReq, write(postCustomerAddressJson.copy(city = "Novi Sad")))
      Then("We should get a 200")
      successUpdateRes.code should equal(200)
      val address = successUpdateRes.body.extract[CustomerAddressJsonV310]
      address.city shouldBe "Novi Sad"

      When("We try to make the GET request v3.1.0")
      val successGetReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "addresses").GET <@(user1)
      val successGetRes = makeGetRequest(successGetReq)
      Then("We should get a 200")
      successGetRes.code should equal(200)
      val addresses = successGetRes.body.extract[CustomerAddressesJsonV310]
      val customerAddressId = addresses.addresses.map(_.customer_address_id).headOption.getOrElse("CUSTOMER_ADDRESS_ID")
      addresses.addresses.flatMap(_.tags).sorted shouldBe  List("mailing", "home").sorted

      When("We try to make the DELETE request v3.1.0")
      val successDeleteReq = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "addresses" / customerAddressId).DELETE <@(user1)
      val successDeleteRes = makeDeleteRequest(successDeleteReq)
      Then("We should get a 200")
      successDeleteRes.code should equal(200)
    }
  }


}
