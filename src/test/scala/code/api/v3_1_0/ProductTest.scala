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
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getProduct))

  lazy val testBankId = randomBankId
  lazy val parentPostPutProductJsonV310 = SwaggerDefinitionsJSON.postPutProductJsonV310.copy(bank_id = testBankId, parent_product_code ="")

  feature("Create Product v3.1.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "products" / "CODE").PUT
      val response310 = makePutRequest(request310, write(parentPostPutProductJsonV310))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "products" / "CODE").PUT <@(user1)
      val response310 = makePutRequest(request310, write(parentPostPutProductJsonV310))
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlements = canCreateProduct :: canCreateProductAtAnyBank ::  Nil
      val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlements.mkString(" or ")
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }

    scenario("We will call the Add endpoint with user credentials and role", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      // Create
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      When("We try to create a product v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "products" / "PARENT_CODE").PUT <@(user1)
      val response310 = makePutRequest(request310, write(parentPostPutProductJsonV310))
      Then("We should get a 201")
      response310.code should equal(201)
      val product = response310.body.extract[ProductJsonV310]
      product.code shouldBe "PARENT_CODE"
      product.parent_product_code shouldBe ""
      product.bank_id shouldBe parentPostPutProductJsonV310.bank_id
      product.name shouldBe parentPostPutProductJsonV310.name
      product.category shouldBe parentPostPutProductJsonV310.category
      product.super_family shouldBe parentPostPutProductJsonV310.super_family
      product.family shouldBe parentPostPutProductJsonV310.family
      product.more_info_url shouldBe parentPostPutProductJsonV310.more_info_url
      product.details shouldBe parentPostPutProductJsonV310.details
      product.description shouldBe parentPostPutProductJsonV310.description

      // Get
      val requestGet310 = (v3_1_0_Request / "banks" / product.bank_id / "products" / product.code ).GET <@(user1)
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      responseGet310.body.extract[ProductJsonV310]
      
      // Create
      val childPostPutProductJsonV310 = parentPostPutProductJsonV310.copy(parent_product_code = product.code)
      When("We try to create a product v3.1.0")
      val createChildRequest310 = (v3_1_0_Request / "banks" / testBankId / "products" / "CHILD_CODE").PUT <@(user1)
      val createChildResponse310 = makePutRequest(createChildRequest310, write(childPostPutProductJsonV310))
      Then("We should get a 201")
      createChildResponse310.code should equal(201)
      val childProduct = createChildResponse310.body.extract[ProductJsonV310]
      childProduct.code shouldBe "CHILD_CODE"
      childProduct.parent_product_code shouldBe "PARENT_CODE"
      childProduct.bank_id shouldBe childPostPutProductJsonV310.bank_id
      childProduct.name shouldBe childPostPutProductJsonV310.name
      childProduct.category shouldBe childPostPutProductJsonV310.category
      childProduct.super_family shouldBe childPostPutProductJsonV310.super_family
      childProduct.family shouldBe childPostPutProductJsonV310.family
      childProduct.more_info_url shouldBe childPostPutProductJsonV310.more_info_url
      childProduct.details shouldBe childPostPutProductJsonV310.details
      childProduct.description shouldBe childPostPutProductJsonV310.description
    }
  }


}
