/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
package code.api.v6_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postCustomerLegalNameJsonV510
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCustomer, CanGetCustomersAtOneBank}
import code.api.util.ErrorMessages._
import code.api.v3_1_0.PostCustomerNumberJsonV310
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.api.v6_0_0.{CustomerJsonV600, CustomerJSONsV600, CustomerWithAttributesJsonV600, PostCustomerJsonV600}
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerTest extends V600ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getCustomersByLegalName))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.getCustomerByCustomerId))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.getCustomerByCustomerNumber))

  lazy val bankId = testBankId1.value
  lazy val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123456")

  // Helper to create a customer for testing
  def createTestCustomer(): CustomerJsonV600 = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
    val postJson = PostCustomerJsonV600(
      legal_name = "Test Customer Legal Name",
      mobile_phone_number = "+44 07972 444 876"
    )
    val request = (v6_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
    val response = makePostRequest(request, write(postJson))
    response.code should equal(201)
    response.body.extract[CustomerJsonV600]
  }

  feature(s"$ApiEndpoint1 - Get Customers by Legal Name $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "legal-name").POST
      val response = makePostRequest(request, write(postCustomerLegalNameJsonV510))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "legal-name").POST <@ (user1)
      val response = makePostRequest(request, write(postCustomerLegalNameJsonV510))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
      response.body.extract[ErrorMessage].message should include(CanGetCustomersAtOneBank.toString)
    }

    scenario("We will call the endpoint with the proper role", ApiEndpoint1, VersionOfApi) {
      Given("We create a test customer")
      val customer = createTestCustomer()
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val searchJson = postCustomerLegalNameJsonV510.copy(legal_name = customer.legal_name)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "legal-name").POST <@ (user1)
      val response = makePostRequest(request, write(searchJson))
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the customer")
      val customers = response.body.extract[CustomerJSONsV600]
      customers.customers.length should be > 0
      customers.customers.exists(_.customer_id == customer.customer_id) should be(true)
    }
  }

  feature(s"$ApiEndpoint2 - Get Customer by CUSTOMER_ID $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage should startWith(UserHasMissingRoles)
      errorMessage should include(CanGetCustomersAtOneBank.toString)
    }

    scenario("We will call the endpoint with the proper role but non-existing customer", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank + " but with non-existing CUSTOMER_ID")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "NON_EXISTING_CUSTOMER_ID").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      response.code should equal(404)
      And("error should be " + CustomerNotFoundByCustomerId)
      response.body.extract[ErrorMessage].message should startWith(CustomerNotFoundByCustomerId)
    }

    scenario("We will call the endpoint with the proper role and valid customer ID", ApiEndpoint2, VersionOfApi) {
      Given("We create a test customer")
      val customer = createTestCustomer()
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / customer.customer_id).GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the customer details")
      val customerResponse = response.body.extract[CustomerWithAttributesJsonV600]
      customerResponse.customer_id should equal(customer.customer_id)
      customerResponse.legal_name should equal(customer.legal_name)
    }
  }

  feature(s"$ApiEndpoint3 - Get Customer by CUSTOMER_NUMBER $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "customer-number").POST
      val response = makePostRequest(request, write(customerNumberJson))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@ (user1)
      val response = makePostRequest(request, write(customerNumberJson))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage should startWith(UserHasMissingRoles)
      errorMessage should include(CanGetCustomersAtOneBank.toString)
    }

    scenario("We will call the endpoint with the proper role but non-existing customer number", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank + " but with non-existing customer number")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val searchJson = PostCustomerNumberJsonV310(customer_number = "999999999")
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@ (user1)
      val response = makePostRequest(request, write(searchJson))
      Then("We should get a 404")
      response.code should equal(404)
      And("error should be " + CustomerNotFound)
      response.body.extract[ErrorMessage].message should startWith(CustomerNotFound)
    }

    scenario("We will call the endpoint with the proper role and valid customer number", ApiEndpoint3, VersionOfApi) {
      Given("We create a test customer")
      val customer = createTestCustomer()
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val searchJson = PostCustomerNumberJsonV310(customer_number = customer.customer_number)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@ (user1)
      val response = makePostRequest(request, write(searchJson))
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the customer details")
      val customerResponse = response.body.extract[CustomerWithAttributesJsonV600]
      customerResponse.customer_id should equal(customer.customer_id)
      customerResponse.customer_number should equal(customer.customer_number)
      customerResponse.legal_name should equal(customer.legal_name)
    }
  }
}