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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_1_0.CustomerJsonV310
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class CustomerOverviewTest extends V500ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.getCustomerOverview))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getCustomerOverviewFlat))

  lazy val bankId = testBankId1.value
  val getCustomerJson = SwaggerDefinitionsJSON.postCustomerOverviewJsonV500
  
  feature(s"$ApiEndpoint1 $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview").POST
      val response = makePostRequest(request, write(PostCustomerOverviewJsonV500))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"$ApiEndpoint1 $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview").POST <@(user1)
      val response = makePostRequest(request, write(getCustomerJson))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + canGetCustomerOverview)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (canGetCustomerOverview.toString()) should be (true)
    }
    scenario(s"We will call the endpoint $ApiEndpoint1 with a user credentials and a proper role", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerOverview.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview").POST <@(user1)
      val response = makePostRequest(request, write(getCustomerJson))
      Then("We should get a 404")
      response.code should equal(404)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage contains (CustomerNotFound) should be (true)
    }
    scenario(s"We will call the endpoint $ApiEndpoint1 with a user credentials and a proper role and successful result", ApiEndpoint1, VersionOfApi) {
      val legalName = "Evelin Doe"
      val mobileNumber = "+44 123 456"
      val customer: CustomerJsonV310 = createCustomerEndpointV500(bankId, legalName, mobileNumber)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerOverview.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview").POST <@(user1)
      val response = makePostRequest(request, write(PostCustomerOverviewJsonV500(customer.customer_number)))
      Then("We should get a 200")
      response.code should equal(200)
      val infoPost = response.body.extract[CustomerOverviewJsonV500]
      infoPost.legal_name should equal(legalName)
      infoPost.mobile_phone_number should equal(mobileNumber)
    }
  }

  
  
  // Overview Flat
  feature(s"$ApiEndpoint2 $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview-flat").POST
      val response = makePostRequest(request, write(PostCustomerOverviewJsonV500))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"$ApiEndpoint2 $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview-flat").POST <@(user1)
      val response = makePostRequest(request, write(getCustomerJson))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + canGetCustomerOverviewFlat)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (canGetCustomerOverviewFlat.toString()) should be (true)
    }
    scenario(s"We will call the endpoint $ApiEndpoint2 with a user credentials and a proper role", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerOverviewFlat.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview-flat").POST <@(user1)
      val response = makePostRequest(request, write(getCustomerJson))
      Then("We should get a 404")
      response.code should equal(404)
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage contains (CustomerNotFound) should be (true)
    }
    scenario(s"We will call the endpoint $ApiEndpoint2 with a user credentials and a proper role and successful result", ApiEndpoint2, VersionOfApi) {
      val legalName = "Evelin Doe"
      val mobileNumber = "+44 123 456"
      val customer: CustomerJsonV310 = createCustomerEndpointV500(bankId, legalName, mobileNumber)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerOverviewFlat.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_0_0_Request / "banks" / bankId / "customers" / "customer-number-query" / "overview-flat").POST <@(user1)
      val response = makePostRequest(request, write(PostCustomerOverviewJsonV500(customer.customer_number)))
      Then("We should get a 200")
      response.code should equal(200)
      val infoPost = response.body.extract[CustomerOverviewFlatJsonV500]
      infoPost.legal_name should equal(legalName)
      infoPost.mobile_phone_number should equal(mobileNumber)
    }
  }
  
}
