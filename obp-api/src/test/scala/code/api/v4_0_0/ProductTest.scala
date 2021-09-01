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
import code.api.util.ErrorMessages._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.{List, Nil}

class ProductTest extends V400ServerSetup {

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
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createProduct))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getProduct))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getProducts))

  lazy val testBankId = randomBankId
  lazy val parentPutProductJsonV400: PutProductJsonV400 = SwaggerDefinitionsJSON.putProductJsonV400.copy(parent_product_code ="")
  def createProduct(code: String, json: PutProductJsonV400) = {
    When("We try to create a product v4.0.0")
    val request400 = (v4_0_0_Request / "banks" / testBankId / "products" / code).PUT <@ (user1)
    val response400 = makePutRequest(request400, write(json))
    Then("We should get a 201")
    response400.code should equal(201)
    val product = response400.body.extract[ProductJsonV400]
    product.code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe testBankId
    product.name shouldBe json.name
    product.more_info_url shouldBe json.more_info_url
    product.terms_and_conditions_url shouldBe json.terms_and_conditions_url
    product.details shouldBe json.details
    product.description shouldBe json.description
    product
  }
  
  feature("Create Product v4.0.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId / "products" / "CODE").PUT
      val response400 = makePutRequest(request400, write(parentPutProductJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Add endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId / "products" / "CODE").PUT <@(user1)
      val response400 = makePutRequest(request400, write(parentPutProductJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      val createProductEntitlements = canCreateProduct :: canCreateProductAtAnyBank ::  Nil
      val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlements.mkString(" or ")
      And("error should be " + createProductEntitlementsRequiredText)
      response400.body.extract[ErrorMessage].message contains (createProductEntitlementsRequiredText) should be (true)
    }
    scenario("We will call the Add endpoint with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)

      // Create an grandparent
      val grandparent: ProductJsonV400 = createProduct(code = "GRANDPARENT_CODE", json = parentPutProductJsonV400)

      // Create an parent
      val product: ProductJsonV400 = createProduct(code = "PARENT_CODE", json = parentPutProductJsonV400.copy(parent_product_code = grandparent.code))

      // Get
      val requestGet400 = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.code ).GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 200")
      responseGet400.code should equal(200)
      val product1 = responseGet400.body.extract[ProductJsonV400]

      // Create an child
      val childPutProductJsonV400 = parentPutProductJsonV400.copy(parent_product_code = product.code)
      createProduct(code = "PRODUCT_CODE", json = childPutProductJsonV400)

      // Get
      val requestGetAll400 = (v4_0_0_Request / "banks" / product.bank_id / "products").GET <@(user1)
      val responseGetAll400 = makeGetRequest(requestGetAll400)
      Then("We should get a 200")
      responseGetAll400.code should equal(200)
      val products: ProductsJsonV400 = responseGetAll400.body.extract[ProductsJsonV400]
      products.products.size shouldBe 3

    }
    scenario("Test the getProducts by url parameters", ApiEndpoint3, VersionOfApi) {

      When("We need to first create the products ")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      createProduct(code = "PRODUCT_CODE", json = parentPutProductJsonV400)
      
      
      When("we grant the CanCreateProductAttribute role")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProductAttribute.toString)
      
      val requestGetAll400 = (v4_0_0_Request / "banks" / testBankId / "products").GET <@(user1)
      val responseGetAll400 = makeGetRequest(requestGetAll400)
      Then("We should get a 200")
      responseGetAll400.code should equal(200)
      val products: ProductsJsonV400 = responseGetAll400.body.extract[ProductsJsonV400]
      products.products.head.code should be ("PRODUCT_CODE")

    }
  }


}
