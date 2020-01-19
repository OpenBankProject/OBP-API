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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateProduct, CanMaintainProductCollection}
import com.openbankproject.commons.util.ApiVersion
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import net.liftweb.json.prettyRender
import org.scalatest.Tag

class ProductCollectionTest extends V310ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createProductCollection))

  lazy val testBankId = randomBankId
  lazy val putProductCollectionsV310 = SwaggerDefinitionsJSON.putProductCollectionsV310.copy(parent_product_code = "A", children_product_codes = List("B", "C", "D"))

  lazy val parentPostPutProductJsonV310: PostPutProductJsonV310 = SwaggerDefinitionsJSON.postPutProductJsonV310.copy(parent_product_code ="")
  def createProduct(code: String, json: PostPutProductJsonV310) = {
    When("We try to create a product v3.1.0")
    val request310 = (v3_1_0_Request / "banks" / testBankId / "products" / code).PUT <@ (user1)
    val response310 = makePutRequest(request310, write(json))
    Then("We should get a 201")
    response310.code should equal(201)
    val product = response310.body.extract[ProductJsonV310]
    product.code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe testBankId
    product.name shouldBe json.name
    product.category shouldBe json.category
    product.super_family shouldBe json.super_family
    product.family shouldBe json.family
    product.more_info_url shouldBe json.more_info_url
    product.details shouldBe json.details
    product.description shouldBe json.description
    product
  }

  feature("Create Product Collections v3.1.0") {
    scenario("We will call the endpoint with a user credentials", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      createProduct(code = "A", json = parentPostPutProductJsonV310)
      createProduct(code = "B", json = parentPostPutProductJsonV310)
      createProduct(code = "C", json = parentPostPutProductJsonV310)
      createProduct(code = "D", json = parentPostPutProductJsonV310)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanMaintainProductCollection.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "product-collections" / "A portfolio of car loans is a ABS").PUT <@ (user1)
      val response310 = makePutRequest(request310, write(putProductCollectionsV310))
      Then("We should get a 201")
      response310.code should equal(201)
      response310.body.extract[ProductCollectionsJsonV310]
    }
  }

}
