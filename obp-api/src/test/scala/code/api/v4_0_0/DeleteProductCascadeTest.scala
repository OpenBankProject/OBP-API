/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanDeleteProductCascade
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.util.{APIUtil, ApiRole}
import code.api.v3_1_0.{PostPutProductJsonV310, ProductJsonV310}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.util.Random

class DeleteProductCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteProductCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "products" / "product_code").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "products" / "product_code").DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanDeleteProductCascade.toString()) should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 - Authorized access with proper role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {

      val testBankId = randomBankId
      val putProductJsonV400: PutProductJsonV400 = SwaggerDefinitionsJSON.putProductJsonV400.copy(parent_product_code ="")

      When("We first prepare the product")
      val product = createProductViaEndpoint(
          bankId = testBankId,
          code = APIUtil.generateUUID(),
          json = putProductJsonV400
        )

      val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310
        .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"), product_code = product.product_code,
        account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
      createAccountViaEndpoint(testBankId, addAccountJson, user1)

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canDeleteProductCascade.toString)
      And("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / testBankId /
        "products" / product.product_code).DELETE <@(user1)

      Then("We should get a 200")
      makeDeleteRequest(request400).code should equal(200)

      When("We try to delete one more time")
      makeDeleteRequest(request400).code should equal(403)
    }
  }

}
