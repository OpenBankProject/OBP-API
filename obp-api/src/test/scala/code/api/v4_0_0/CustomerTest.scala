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
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v3_1_0.{CustomerJsonV310, PostCustomerNumberJsonV310}
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.setup.PropsReset
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerTest extends V400ServerSetup  with PropsReset{

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
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.createCustomer))

  val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123")
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
  lazy val bankId = randomBankId

  feature("Create Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature("Create Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canCreateCustomer + " or " + canCreateCustomerAtAnyBank
      And("error should be " + errorMsg)
      val errorMessage = response310.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (canCreateCustomerAtAnyBank.toString()) should be (true)
    }
    scenario("We will call the endpoint with a user credentials and a proper role", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val infoPost = response310.body.extract[CustomerJsonV310]

      When("We make the request: Get Customer specified by CUSTOMER_ID")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      val requestGet = (v4_0_0_Request / "banks" / bankId / "customers" / infoPost.customer_id).GET <@ (user1)
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
