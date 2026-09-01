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

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCustomer, CanGetCustomersAtOneBank}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class RetailAndCorporateCustomerTest extends V600ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createRetailCustomer))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.getRetailCustomersAtOneBank))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.createCorporateCustomer))
  object ApiEndpoint4 extends Tag(nameOf(Implementations6_0_0.getCorporateCustomersAtOneBank))
  object ApiEndpoint5 extends Tag(nameOf(Implementations6_0_0.getCustomerChildren))
  object ApiEndpoint6 extends Tag(nameOf(Implementations6_0_0.getCorporateCustomerSubsidiaries))

  lazy val bankId = testBankId1.value

  // Helper to create a retail customer for testing
  def createTestRetailCustomer(legalName: String = "Test Retail Customer"): CustomerJsonV600 = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
    val postJson = PostRetailCustomerJsonV600(
      legal_name = legalName,
      mobile_phone_number = "+44 07972 444 876",
      email = Some("retail@example.com"),
      date_of_birth = Some("1990-05-15")
    )
    val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").POST <@ (user1)
    val response = makePostRequest(request, write(postJson))
    response.code should equal(201)
    val customer = response.body.extract[CustomerJsonV600]
    customer.customer_type should equal("INDIVIDUAL")
    customer
  }

  // Helper to create a corporate customer for testing
  def createTestCorporateCustomer(
    legalName: String = "Test Corporation Ltd",
    customerType: Option[String] = None,
    parentCustomerId: Option[String] = None
  ): CustomerJsonV600 = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
    val postJson = PostCorporateCustomerJsonV600(
      legal_name = legalName,
      mobile_phone_number = "+44 020 7946 0958",
      email = Some("corporate@example.com"),
      customer_type = customerType,
      parent_customer_id = parentCustomerId
    )
    val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
    val response = makePostRequest(request, write(postJson))
    response.code should equal(201)
    response.body.extract[CustomerJsonV600]
  }

  feature(s"$ApiEndpoint1 - Create Retail Customer $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val postJson = PostRetailCustomerJsonV600(
        legal_name = "Test Customer",
        mobile_phone_number = "+44 07972 444 876"
      )
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").POST
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanCreateCustomer)
      val postJson = PostRetailCustomerJsonV600(
        legal_name = "Test Customer",
        mobile_phone_number = "+44 07972 444 876"
      )
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateCustomer)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will create a retail customer successfully", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi with the role " + CanCreateCustomer)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostRetailCustomerJsonV600(
        legal_name = "John Doe",
        mobile_phone_number = "+44 07972 444 876",
        email = Some("john.doe@example.com"),
        date_of_birth = Some("1985-03-20"),
        relationship_status = Some("Single"),
        dependants = Some(0),
        dob_of_dependants = Some(List())
      )
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201")
      response.code should equal(201)
      And("The customer_type should be INDIVIDUAL")
      val customer = response.body.extract[CustomerJsonV600]
      customer.customer_type should equal("INDIVIDUAL")
      customer.legal_name should equal("John Doe")
      customer.mobile_phone_number should equal("+44 07972 444 876")
      customer.parent_customer_id should equal("")
    }

    scenario("We will create a retail customer with invalid date format", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi with invalid date_of_birth format")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostRetailCustomerJsonV600(
        legal_name = "Jane Doe",
        mobile_phone_number = "+44 07972 444 877",
        date_of_birth = Some("03/20/1985") // Invalid format
      )
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 400")
      response.code should equal(400)
      And("error should mention date format")
      response.body.extract[ErrorMessage].message should include("YYYY-MM-DD")
    }
  }

  feature(s"$ApiEndpoint2 - Get Retail Customers at Bank $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will get retail customers successfully", ApiEndpoint2, VersionOfApi) {
      Given("We create a retail customer")
      val customer = createTestRetailCustomer("Retail Customer for List")
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "retail-customers").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain only INDIVIDUAL customers")
      val customers = response.body.extract[CustomerJSONsV600]
      customers.customers.length should be > 0
      customers.customers.foreach(c => c.customer_type should equal("INDIVIDUAL"))
      customers.customers.exists(_.customer_id == customer.customer_id) should be(true)
    }
  }

  feature(s"$ApiEndpoint3 - Create Corporate Customer $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "Test Corp",
        mobile_phone_number = "+44 020 7946 0958"
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanCreateCustomer)
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "Test Corp",
        mobile_phone_number = "+44 020 7946 0958"
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateCustomer)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will create a corporate customer successfully", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $VersionOfApi with the role " + CanCreateCustomer)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "ACME Corporation Ltd",
        mobile_phone_number = "+44 020 7946 0958",
        email = Some("info@acme.com"),
        customer_type = Some("CORPORATE")
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201")
      response.code should equal(201)
      And("The customer_type should be CORPORATE")
      val customer = response.body.extract[CustomerJsonV600]
      customer.customer_type should equal("CORPORATE")
      customer.legal_name should equal("ACME Corporation Ltd")
      customer.parent_customer_id should equal("")
    }

    scenario("We will create a subsidiary customer with parent", ApiEndpoint3, VersionOfApi) {
      Given("We create a parent corporate customer")
      val parentCustomer = createTestCorporateCustomer("Parent Corporation", Some("CORPORATE"))
      
      When(s"We create a subsidiary customer with parent_customer_id")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "Subsidiary Company Ltd",
        mobile_phone_number = "+44 020 7946 0959",
        customer_type = Some("SUBSIDIARY"),
        parent_customer_id = Some(parentCustomer.customer_id)
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 201")
      response.code should equal(201)
      And("The customer_type should be SUBSIDIARY and parent_customer_id should be set")
      val customer = response.body.extract[CustomerJsonV600]
      customer.customer_type should equal("SUBSIDIARY")
      customer.parent_customer_id should equal(parentCustomer.customer_id)
    }

    scenario("We will fail to create subsidiary with non-existing parent", ApiEndpoint3, VersionOfApi) {
      When(s"We create a subsidiary customer with invalid parent_customer_id")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "Orphan Subsidiary Ltd",
        mobile_phone_number = "+44 020 7946 0960",
        customer_type = Some("SUBSIDIARY"),
        parent_customer_id = Some("non-existing-customer-id")
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 404")
      response.code should equal(404)
      And("error should mention customer not found")
      response.body.extract[ErrorMessage].message should include("Customer")
    }

    scenario("We will fail to create corporate customer with invalid customer_type", ApiEndpoint3, VersionOfApi) {
      When(s"We create a corporate customer with customer_type=INDIVIDUAL")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val postJson = PostCorporateCustomerJsonV600(
        legal_name = "Invalid Type Corp",
        mobile_phone_number = "+44 020 7946 0961",
        customer_type = Some("INDIVIDUAL") // Invalid for corporate endpoint
      )
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").POST <@ (user1)
      val response = makePostRequest(request, write(postJson))
      Then("We should get a 400")
      response.code should equal(400)
      And("error should mention invalid customer type")
      response.body.extract[ErrorMessage].message should include("customer_type")
    }
  }

  feature(s"$ApiEndpoint4 - Get Corporate Customers at Bank $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint4, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will get corporate customers successfully", ApiEndpoint4, VersionOfApi) {
      Given("We create a corporate customer")
      val customer = createTestCorporateCustomer("Corporate Customer for List", Some("CORPORATE"))
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain only CORPORATE or SUBSIDIARY customers")
      val customers = response.body.extract[CustomerJSONsV600]
      customers.customers.length should be > 0
      customers.customers.foreach { c =>
        List("CORPORATE", "SUBSIDIARY") should contain(c.customer_type)
      }
      customers.customers.exists(_.customer_id == customer.customer_id) should be(true)
    }
  }

  feature(s"$ApiEndpoint5 - Get Customer Children $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint5, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "children").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint5, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "children").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will get customer children successfully", ApiEndpoint5, VersionOfApi) {
      Given("We create a parent customer and child customers")
      val parentCustomer = createTestCorporateCustomer("Parent for Children Test", Some("CORPORATE"))
      val child1 = createTestCorporateCustomer("Child 1", Some("SUBSIDIARY"), Some(parentCustomer.customer_id))
      val child2 = createTestCorporateCustomer("Child 2", Some("SUBSIDIARY"), Some(parentCustomer.customer_id))
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / parentCustomer.customer_id / "children").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the child customers")
      val children = response.body.extract[CustomerJSONsV600]
      children.customers.length should be >= 2
      children.customers.exists(_.customer_id == child1.customer_id) should be(true)
      children.customers.exists(_.customer_id == child2.customer_id) should be(true)
      children.customers.foreach(_.parent_customer_id should equal(parentCustomer.customer_id))
    }

    scenario("We will get empty list for customer with no children", ApiEndpoint5, VersionOfApi) {
      Given("We create a customer with no children")
      val customer = createTestCorporateCustomer("Childless Customer", Some("CORPORATE"))
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "customers" / customer.customer_id / "children").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain an empty list")
      val children = response.body.extract[CustomerJSONsV600]
      children.customers.length should equal(0)
    }
  }

  feature(s"$ApiEndpoint6 - Get Customer Subsidiaries $VersionOfApi") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $VersionOfApi without user credentials")
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers" / "CUSTOMER_ID" / "subsidiaries").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will call the endpoint without the proper role", ApiEndpoint6, VersionOfApi) {
      When(s"We make a request $VersionOfApi without the role " + CanGetCustomersAtOneBank)
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers" / "CUSTOMER_ID" / "subsidiaries").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
    }

    scenario("We will get customer subsidiaries successfully", ApiEndpoint6, VersionOfApi) {
      Given("We create a corporate customer and subsidiaries")
      val corporateCustomer = createTestCorporateCustomer("Corporate for Subsidiaries Test", Some("CORPORATE"))
      val subsidiary1 = createTestCorporateCustomer("Subsidiary 1", Some("SUBSIDIARY"), Some(corporateCustomer.customer_id))
      val subsidiary2 = createTestCorporateCustomer("Subsidiary 2", Some("SUBSIDIARY"), Some(corporateCustomer.customer_id))
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers" / corporateCustomer.customer_id / "subsidiaries").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain the subsidiary customers")
      val subsidiaries = response.body.extract[CustomerJSONsV600]
      subsidiaries.customers.length should be >= 2
      subsidiaries.customers.exists(_.customer_id == subsidiary1.customer_id) should be(true)
      subsidiaries.customers.exists(_.customer_id == subsidiary2.customer_id) should be(true)
      subsidiaries.customers.foreach(_.parent_customer_id should equal(corporateCustomer.customer_id))
    }

    scenario("We will get empty list for customer with no subsidiaries", ApiEndpoint6, VersionOfApi) {
      Given("We create a corporate customer with no subsidiaries")
      val customer = createTestCorporateCustomer("No Subsidiaries Corp", Some("CORPORATE"))
      
      When(s"We make a request $VersionOfApi with the role " + CanGetCustomersAtOneBank)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v6_0_0_Request / "banks" / bankId / "corporate-customers" / customer.customer_id / "subsidiaries").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      And("The response should contain an empty list")
      val subsidiaries = response.body.extract[CustomerJSONsV600]
      subsidiaries.customers.length should equal(0)
    }
  }
}
