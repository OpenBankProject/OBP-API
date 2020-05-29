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

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.productAttributeJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.{Extraction, prettyRender}
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.{List, Nil}

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
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getProducts))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.getProductTree))
  
  
  lazy val postProductAttributeJson = productAttributeJson
  lazy val putProductAttributeJson = productAttributeJson.copy(name = "updated product")
  lazy val createProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attribute")
  lazy val getProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")
  lazy val updateProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")
  lazy val deleteProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")

  lazy val testBankId = randomBankId
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
  
  feature("Create Product v3.1.0") {
    scenario("We will call the Add endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId / "products" / "CODE").PUT
      val response310 = makePutRequest(request310, write(parentPostPutProductJsonV310))
      Then("We should get a 401")
      response310.code should equal(401)
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
    scenario("We will call the Add endpoint with user credentials and role", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)

      // Create an grandparent
      val grandparent: ProductJsonV310 = createProduct(code = "GRANDPARENT_CODE", json = parentPostPutProductJsonV310)

      // Create an parent
      val product: ProductJsonV310 = createProduct(code = "PARENT_CODE", json = parentPostPutProductJsonV310.copy(parent_product_code = grandparent.code))

      // Get
      val requestGet310 = (v3_1_0_Request / "banks" / product.bank_id / "products" / product.code ).GET <@(user1)
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      val product1 = responseGet310.body.extract[ProductJsonV310]

      // Create an child
      val childPostPutProductJsonV310 = parentPostPutProductJsonV310.copy(parent_product_code = product.code)
      createProduct(code = "PRODUCT_CODE", json = childPostPutProductJsonV310)

      // Get
      val requestGetAll310 = (v3_1_0_Request / "banks" / product.bank_id / "products").GET <@(user1)
      val responseGetAll310 = makeGetRequest(requestGetAll310)
      Then("We should get a 200")
      responseGetAll310.code should equal(200)
      val products: ProductsJsonV310 = responseGetAll310.body.extract[ProductsJsonV310]
      products.products.size shouldBe 3

      // Get tree
      val requestGetTree310 = (v3_1_0_Request / "banks" / product.bank_id / "product-tree" / "PRODUCT_CODE").GET <@(user1)
      val responseGetTree310 = makeGetRequest(requestGetTree310)
      Then("We should get a 200")
      responseGetTree310.code should equal(200)
      val productTree: ProductTreeJsonV310 = responseGetTree310.body.extract[ProductTreeJsonV310]
    }
    scenario("Test the getProducts by url parameters", ApiEndpoint3, VersionOfApi) {

      When("We need to first create the products ")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      createProduct(code = "PRODUCT_CODE", json = parentPostPutProductJsonV310)
      
      
      When("we grant the CanCreateProductAttribute role")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProductAttribute.toString)
      
      When("We make a create Product request v3.1.0")
      val requestCreate310 = createProductAttributeEndpoint.POST <@(user1)
      val responseCreate310 = makePostRequest(requestCreate310, write(postProductAttributeJson))
      Then("We should get a 201")
      responseCreate310.code should equal(201)
      val createdProduct = responseCreate310.body.extract[ProductAttributeResponseJson]
      

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanGetProductAttribute.toString)
      val requestGet310 = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / createdProduct.product_attribute_id).GET <@(user1)
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      val gottenProduct = responseGet310.body.extract[ProductAttributeResponseJson]

      createdProduct shouldBe gottenProduct
      
      val requestGetAll310 = (v3_1_0_Request / "banks" / testBankId / "products").GET <@(user1)
      val responseGetAll310 = makeGetRequest(requestGetAll310)
      Then("We should get a 200")
      responseGetAll310.code should equal(200)
      val products: ProductsJsonV310 = responseGetAll310.body.extract[ProductsJsonV310]
      products.products.head.code should be ("PRODUCT_CODE")


      val requestGetAllParameter1 = (v3_1_0_Request / "banks" / testBankId / "products").GET <@(user1)<<? (List(("OVERDRAFT_START_DATE","2012-04-23")))
      val responseGetAllParameter1= makeGetRequest(requestGetAllParameter1)
      Then("We should get a 200")
      responseGetAllParameter1.code should equal(200)
      val productsParameter1: ProductsJsonV310 = responseGetAllParameter1.body.extract[ProductsJsonV310]
      productsParameter1.products.head.code should be ("PRODUCT_CODE")


      val requestGetAllParameter2 = (v3_1_0_Request / "banks" / testBankId / "products").GET <@(user1)<<? (List(("OVERDRAFT_START_DATE1","2012-04-23")))
      val responseGetAllParameter2= makeGetRequest(requestGetAllParameter2)
      Then("We should get a 200")
      responseGetAllParameter2.code should equal(200)
      val productsParameter2: ProductsJsonV310 = responseGetAllParameter2.body.extract[ProductsJsonV310]
      productsParameter2.products.length should be (0)
    }
  }


}
