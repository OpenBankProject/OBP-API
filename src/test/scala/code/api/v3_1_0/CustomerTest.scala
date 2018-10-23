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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetCustomer, canGetCustomer}
import code.api.util.ErrorMessages._
import code.api.util.ApiVersion
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.getCustomerByCustomerId))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCustomerByCustomerNumber))

  val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123")

  feature("Get Customer by CUSTOMER_ID v3.1.0 - Unauthorized access")
  {
    scenario("We will call the endpoint without user credentials", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }

  feature("Get Customer by CUSTOMER_ID v3.1.0 - Authorized access")
  {
    scenario("We will call the endpoint without the proper Role " + canGetCustomer, ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0 without a Role " + canGetCustomer)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomer)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetCustomer)
    }

    scenario("We will call the endpoint with the proper Role " + canGetCustomer, ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      When("We make a request v3.1.0 with the Role " + canGetCustomer + " but with non existing CUSTOMER_ID")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFoundByCustomerId)
    }
  }


  feature("Get Customer by customer number v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }
  feature("Get Customer by customer number v3.1.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canGetCustomer, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      When("We make a request v3.1.0 without a Role " + canGetCustomer)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@(user1)
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomer)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetCustomer)
    }

    scenario("We will call the endpoint with the proper Role " + canGetCustomer, ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      When("We make a request v3.1.0 with the Role " + canGetCustomer + " but with non existing customer number")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@(user1)
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFound)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFound)
    }
  }

}
