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
package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postCustomerLegalNameJsonV510
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetCustomersAtOneBank
import code.api.util.ErrorMessages._
import code.api.v3_0_0.CustomerJSONsV300
import code.api.v3_1_0.CustomerJsonV310
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
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

class CustomerTest extends V510ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getCustomersForUserIdsOnly))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getCustomersByLegalName))

  lazy val bankId = testBankId1.value
  val getCustomerJson = SwaggerDefinitionsJSON.postCustomerOverviewJsonV500
  
  feature(s"$ApiEndpoint1 $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "users" / "current" / "customers" / "customer_ids").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"$ApiEndpoint1 $VersionOfApi - Authorized access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with a user credentials and successful result", ApiEndpoint1, VersionOfApi) {
      val legalName = "Evelin Doe"
      val mobileNumber = "+44 123 456"
      val customer: CustomerJsonV310 = createCustomerEndpointV510(bankId, legalName, mobileNumber)
      UserCustomerLink.userCustomerLink.vend.getOCreateUserCustomerLink(resourceUser1.userId, customer.customer_id, new Date(), true)
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "users" / "current" / "customers" / "customer_ids").GET <@(user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val ids = response.body.extract[CustomersIdsJsonV510]
      ids.customers.map(_.id).filter(_ == customer.customer_id).length should equal(1)
    }
  }

  feature(s"$ApiEndpoint2 $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "banks" / bankId / "customers" / "legal-name").POST
      val response = makePostRequest(request, write(postCustomerLegalNameJsonV510))
      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"$ApiEndpoint2 $VersionOfApi - Authorized access without proper role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "banks" / bankId / "customers" / "legal-name").POST <@(user1)
      val response = makePostRequest(request, write(postCustomerLegalNameJsonV510))
      Then("We should get a 403")
      Then("error should be " + UserHasMissingRoles + CanGetCustomersAtOneBank)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetCustomersAtOneBank)
    }
  }
  feature(s"$ApiEndpoint2 $VersionOfApi - Authorized access with proper role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "customers" / "legal-name").POST <@(user1)
      val response = makePostRequest(request, write(postCustomerLegalNameJsonV510))
      Then("We should get a 200")
      response.code should equal(200)
      val customers = response.body.extract[CustomerJSONsV300]
    }
  }
  
  
}
