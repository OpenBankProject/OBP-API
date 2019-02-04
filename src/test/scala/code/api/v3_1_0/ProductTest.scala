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
import code.api.v2_2_0.ProductJsonV220
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.Nil

class ProductTest extends V310ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createProduct))
  
  val postPutProductJsonV310 = SwaggerDefinitionsJSON.productJsonV310
  lazy val bankId = randomBankId

  feature("Create Product v3.1.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "products" / "CODE").PUT
      val response310 = makePutRequest(request310, write(postPutProductJsonV310))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "products" / "CODE").PUT <@(user1)
      val response310 = makePutRequest(request310, write(postPutProductJsonV310))
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlements = canCreateProduct :: canCreateProductAtAnyBank ::  Nil
      val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlements.mkString(" or ")
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }

    scenario("We will call the Add endpoint with user credentials and role", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateProduct.toString)
      When("We try to create a product v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "products" / "CODE").PUT <@(user1)
      val response310 = makePutRequest(request310, write(postPutProductJsonV310))
      Then("We should get a 201")
      response310.code should equal(201)
      val product = response310.body.extract[ProductJsonV220]
      product.code shouldBe "CODE"
      product.bank_id shouldBe postPutProductJsonV310.bank_id
      product.name shouldBe postPutProductJsonV310.name
      product.category shouldBe postPutProductJsonV310.category
      product.super_family shouldBe postPutProductJsonV310.super_family
      product.family shouldBe postPutProductJsonV310.family
      product.more_info_url shouldBe postPutProductJsonV310.more_info_url
      product.details shouldBe postPutProductJsonV310.details
      product.description shouldBe postPutProductJsonV310.description
    }
  }


}
