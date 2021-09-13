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
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.productFeeJsonV400
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{ProductFeeNotFoundById, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ProductFeeTest extends V400ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createProductFee))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getProductFee))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getProductFees))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.updateProductFee))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.deleteProductFee))

  lazy val testBankId = randomBankId
  lazy val parentPutProductJsonV400: PutProductJsonV400 = SwaggerDefinitionsJSON.putProductJsonV400.copy(parent_product_code ="")
  def createProduct(code: String, json: PutProductJsonV400) = {
    When("We try to create a product v4.0.0")
    val request400 = (v4_0_0_Request / "banks" / testBankId / "products" / code).PUT <@ (user1)
    val response400 = makePutRequest(request400, write(json))
    Then("We should get a 201")
    response400.code should equal(201)
    val product = response400.body.extract[ProductJsonV400]
    product.product_code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe testBankId
    product.name shouldBe json.name
    product.more_info_url shouldBe json.more_info_url
    product.terms_and_conditions_url shouldBe json.terms_and_conditions_url
    product.description shouldBe json.description
    product
  }
  
  feature("Create Product Fee v4.0.0") {
    scenario("We will call the Add endpoint with user credentials and role", 
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5) {

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProductFee.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanUpdateProductFee.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanDeleteProductFee.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanGetProductFee.toString)
      
      // Create an grandparent
      val grandparent: ProductJsonV400 = createProduct(code = "GRANDPARENT_CODE", json = parentPutProductJsonV400)

      // Create an parent
      val product: ProductJsonV400 = createProduct(code = "PARENT_CODE", json = parentPutProductJsonV400.copy(parent_product_code = grandparent.product_code))

      // Get
      val requestGet400 = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code ).GET <@(user1)
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 200")
      responseGet400.code should equal(200)
      val product1 = responseGet400.body.extract[ProductJsonV400]

      // Create an child
      val childPutProductJsonV400 = parentPutProductJsonV400.copy(parent_product_code = product.product_code)
      createProduct(code = "PRODUCT_CODE", json = childPutProductJsonV400)

      // Get
      val requestGetAll400 = (v4_0_0_Request / "banks" / product.bank_id / "products").GET <@(user1)
      val responseGetAll400 = makeGetRequest(requestGetAll400)
      Then("We should get a 200")
      responseGetAll400.code should equal(200)
      val products: ProductsJsonV400 = responseGetAll400.body.extract[ProductsJsonV400]
      products.products.size shouldBe 3

      // then we createProductFee
      val requestCreateProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fee").POST <@(user1)
      
      val responseCreateProductFee = makePostRequest(requestCreateProductFee, write(productFeeJsonV400))
      
      responseCreateProductFee.code should equal(201)
      val productFeeCreate1: ProductFeeResponseJsonV400 = responseCreateProductFee.body.extract[ProductFeeResponseJsonV400]

      productFeeCreate1.name should be(productFeeJsonV400.name)
      // then we create second Product Fee
      val responseCreateProductFee2 = makePostRequest(requestCreateProductFee, write(productFeeJsonV400))
      
      responseCreateProductFee2.code should equal(201)
      
      val productFeeCreate2: ProductFeeResponseJsonV400 = responseCreateProductFee2.body.extract[ProductFeeResponseJsonV400]
      productFeeCreate2.name should be(productFeeJsonV400.name)
      
      // then we getProductFees
      val requestGetProductFees = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees").GET <@(user1)

      val responseGetProductFees = makeGetRequest(requestGetProductFees)
      responseGetProductFees.code should equal(200)
      val productFees: ProductFeesResponseJsonV400 = responseGetProductFees.body.extract[ProductFeesResponseJsonV400]
      
      productFees.product_fees.length should be (2)
      val productFeeId = productFees.product_fees.head.product_fee_id
      
      // then we getProductFee
      val requestGetProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).GET <@(user1)
      val responseGetProductFee = makeGetRequest(requestGetProductFee)
      responseGetProductFee.code should equal(200)
      val productFee: ProductFeeResponseJsonV400 = responseGetProductFee.body.extract[ProductFeeResponseJsonV400]

      productFee should be (productFees.product_fees.head)
      
      // then we updateProductFee
      val requestPutProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).PUT <@(user1)
      val updatedName = "test Case 123"
      val responsePutProductFee = makePutRequest(requestPutProductFee,write(productFeeJsonV400.copy(name = updatedName)) )
      responsePutProductFee.code should equal(201)
      val putProductFee: ProductFeeResponseJsonV400 = responsePutProductFee.body.extract[ProductFeeResponseJsonV400]
      putProductFee.name should be (updatedName)
      
      // then we getProductFee
      val responseGetProductFeeUpdated = makeGetRequest(requestGetProductFee)
      val productFeeUpdated: ProductFeeResponseJsonV400 = responseGetProductFeeUpdated.body.extract[ProductFeeResponseJsonV400]

      productFeeUpdated.name should be (updatedName)
      
      // then we deleteProductFee
      val requestDeleteProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).DELETE <@(user1)
      val responseDeleteProductFee = makeDeleteRequest(requestDeleteProductFee)
      responseDeleteProductFee.code should equal(204)
      
      // then we getProductFee
      val responseGetProductFeeAfterDeleted = makeGetRequest(requestGetProductFee)
      responseGetProductFeeAfterDeleted.code should equal(404)
      responseGetProductFeeAfterDeleted.body.toString contains(ProductFeeNotFoundById) should be (true)
    }

    scenario("We will test the error cases",
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5) {
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProduct.toString)
      // Create an grandparent
      val grandparent: ProductJsonV400 = createProduct(code = "GRANDPARENT_CODE", json = parentPutProductJsonV400)

      // Create an parent
      val product: ProductJsonV400 = createProduct(code = "PARENT_CODE", json = parentPutProductJsonV400.copy(parent_product_code = grandparent.product_code))

      // then we createProductFee
      val requestCreateProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fee").POST
      val responseCreateProductFee = makePostRequest(requestCreateProductFee, write(productFeeJsonV400))
      responseCreateProductFee.code should equal(401)
      responseCreateProductFee.body.toString contains(UserNotLoggedIn) should be (true)
      
      {
        val requestCreateProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fee").POST <@(user1)
        val responseCreateProductFee = makePostRequest(requestCreateProductFee, write(productFeeJsonV400))
        responseCreateProductFee.code should equal(403)
        responseCreateProductFee.body.toString contains(UserHasMissingRoles) should be (true)
        responseCreateProductFee.body.toString contains(canCreateProductFee.toString()) should be (true)
      }
            

      // then we getProductFees
      val requestGetProductFees = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees").GET 
      val responseGetProductFees = makeGetRequest(requestGetProductFees)
      responseGetProductFees.code should equal(200)
      

      // then we getProductFee
      val productFeeId = "xxx"
      val requestGetProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).GET 
      val responseGetProductFee = makeGetRequest(requestGetProductFee)
      responseGetProductFee.code should equal(404)
      responseGetProductFee.body.toString contains(ProductFeeNotFoundById) should be (true)
      
    
      // then we updateProductFee
      val requestPutProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).PUT 
      val updatedName = "test Case 123"
      val responsePutProductFee = makePutRequest(requestPutProductFee,write(productFeeJsonV400.copy(name = updatedName)) )
      responsePutProductFee.code should equal(401)
      responsePutProductFee.body.toString contains(UserNotLoggedIn) should be (true)
      
      {
        val requestPutProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).PUT <@(user1)
        val updatedName = "test Case 123"
        val responsePutProductFee = makePutRequest(requestPutProductFee,write(productFeeJsonV400.copy(name = updatedName)) )
        responsePutProductFee.code should equal(403)
        responsePutProductFee.body.toString contains(UserHasMissingRoles) should be (true)
        responsePutProductFee.body.toString contains(canUpdateProductFee.toString()) should be (true)
      }

      // then we deleteProductFee
      val requestDeleteProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).DELETE 
      val responseDeleteProductFee = makeDeleteRequest(requestDeleteProductFee)
      responseDeleteProductFee.code should equal(401)
      responseDeleteProductFee.body.toString contains(UserNotLoggedIn) should be (true)
      
      {
        val requestDeleteProductFee = (v4_0_0_Request / "banks" / product.bank_id / "products" / product.product_code / "fees" / productFeeId).DELETE <@(user1)
        val responseDeleteProductFee = makeDeleteRequest(requestDeleteProductFee)
        responseDeleteProductFee.code should equal(403)
        responseDeleteProductFee.body.toString contains(UserHasMissingRoles) should be (true)
        responseDeleteProductFee.body.toString contains(canDeleteProductFee.toString()) should be (true)
      }
 
    }
  }
}
