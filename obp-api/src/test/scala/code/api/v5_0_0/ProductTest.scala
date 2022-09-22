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
package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole._
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.api.v4_0_0.{ProductJsonV400, ProductsJsonV400}
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.Nil

class ProductTest extends V500ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createProduct))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getProduct))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getProducts))

  lazy val testBankId = randomBankId
  lazy val parentPutProductJsonV500: PutProductJsonV500 = SwaggerDefinitionsJSON.putProductJsonV500.copy(parent_product_code ="")
  def createProduct(code: String, json: PutProductJsonV500): ProductJsonV400 = {
    When("We try to create a product v4.0.0")
    val request500 = (v5_0_0_Request / "banks" / testBankId / "products" / code).PUT <@ (user1)
    val response500 = makePutRequest(request500, write(json))
    Then("We should get a 201")
    response500.code should equal(201)
    val product = response500.body.extract[ProductJsonV400]
    product.product_code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe testBankId
    product.name shouldBe json.name
    product.more_info_url shouldBe json.more_info_url.getOrElse("")
    product.terms_and_conditions_url shouldBe json.terms_and_conditions_url.getOrElse("")
    product.description shouldBe json.description.getOrElse("")
    product
  }
  
  feature("Create Product v4.0.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v5_0_0_Request / "banks" / testBankId / "products" / "CODE").PUT
      val response400 = makePutRequest(request400, write(parentPutProductJsonV500))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request500 = (v5_0_0_Request / "banks" / testBankId / "products" / "CODE").PUT <@(user1)
      val response500 = makePutRequest(request500, write(parentPutProductJsonV500))
      Then("We should get a 403")
      response500.code should equal(403)
      val createProductEntitlements = canCreateProduct :: canCreateProductAtAnyBank ::  Nil
      val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlements.mkString(" or ")
      And("error should be " + createProductEntitlementsRequiredText)
      response500.body.extract[ErrorMessage].message contains (createProductEntitlementsRequiredText) should be (true)
    }
    scenario("We will call the Add endpoint with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)

      // Create an grandparent
      val grandparent: ProductJsonV400 = createProduct(code = "GRANDPARENT_CODE", json = parentPutProductJsonV500)

      // Create an parent
      val product: ProductJsonV400 = createProduct(code = "PARENT_CODE", json = parentPutProductJsonV500.copy(parent_product_code = grandparent.product_code))

      // Get
      val requestGet400 = (v5_0_0_Request / "banks" / product.bank_id / "products" / product.product_code ).GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 200")
      responseGet400.code should equal(200)
      val product1 = responseGet400.body.extract[ProductJsonV400]

      // Create an child
      val childPutProductJsonV400 = parentPutProductJsonV500.copy(parent_product_code = product.product_code)
      createProduct(code = "PRODUCT_CODE", json = childPutProductJsonV400)

      // Get
      val requestGetAll400 = (v5_0_0_Request / "banks" / product.bank_id / "products").GET <@(user1)
      val responseGetAll400 = makeGetRequest(requestGetAll400)
      Then("We should get a 200")
      responseGetAll400.code should equal(200)
      val products: ProductsJsonV400 = responseGetAll400.body.extract[ProductsJsonV400]
      products.products.size shouldBe 3
    }
    scenario("We will call the Add endpoint with user credentials and role and minimal PUT JSON", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      // Create an grandparent
      val grandparent: ProductJsonV400 = createProduct(
        code = "GRANDPARENT_CODE", 
        json = PutProductJsonV500(
          name = parentPutProductJsonV500.name,
          parent_product_code = parentPutProductJsonV500.parent_product_code
        )
      )
      // Create an parent
      val product: ProductJsonV400 = createProduct(code = "PARENT_CODE", json = parentPutProductJsonV500.copy(parent_product_code = grandparent.product_code))
      
      // Get
      val requestGet400 = (v5_0_0_Request / "banks" / product.bank_id / "products" / product.product_code ).GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 200")
      responseGet400.code should equal(200)
      val product1 = responseGet400.body.extract[ProductJsonV400]

      // Create an child
      val childPutProductJsonV400 = parentPutProductJsonV500.copy(parent_product_code = product.product_code)
      createProduct(code = "PRODUCT_CODE", json = childPutProductJsonV400)

      // Get
      val requestGetAll400 = (v5_0_0_Request / "banks" / product.bank_id / "products").GET <@(user1)
      val responseGetAll400 = makeGetRequest(requestGetAll400)
      Then("We should get a 200")
      responseGetAll400.code should equal(200)
      val products: ProductsJsonV400 = responseGetAll400.body.extract[ProductsJsonV400]
      products.products.size shouldBe 3
    }
  }


}
