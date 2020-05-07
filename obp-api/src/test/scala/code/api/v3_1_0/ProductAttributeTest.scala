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
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.productAttributeJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.ProductAttributeType
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ProductAttributeTest extends V310ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createProductAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getProductAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.updateProductAttribute))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.deleteProductAttribute))

  lazy val testBankId = randomBankId
  lazy val postProductAttributeJson = productAttributeJson
  lazy val putProductAttributeJson = productAttributeJson.copy(name = "updated product")
  lazy val createProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attribute")
  lazy val getProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")
  lazy val updateProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")
  lazy val deleteProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")

  feature("Create/Get, Update/Get and Delete/Get Product Attribute v3.1.0") {
    scenario("We will call the endpoints with a proper roles", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProductAttribute.toString)
      When("We make a request v3.1.0")
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
      

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanUpdateProductAttribute.toString)
      When("We make a request v3.1.0")
      val requestPut310 = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / createdProduct.product_attribute_id).PUT <@(user1)
      val responsePut310 = makePutRequest(requestPut310, write(putProductAttributeJson))
      Then("We should get a 200")
      responsePut310.code should equal(200)
      val updatedProduct = responsePut310.body.extract[ProductAttributeResponseJson]

      updatedProduct shouldBe makeGetRequest(requestGet310).body.extract[ProductAttributeResponseJson]

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanDeleteProductAttribute.toString)
      When("We make a request v3.1.0")
      val requestDelete310 = (v3_1_0_Request / "banks" / testBankId / "products" / "PRODUCT_CODE" / "attributes" / createdProduct.product_attribute_id).DELETE <@(user1)
      val responseDelete310 = makeDeleteRequest(requestDelete310)
      Then("We should get a 204")
      responseDelete310.code should equal(204)

      makeGetRequest(requestGet310).code should equal(400)

      Then("We call create endpoint with another `tpye`")
      val responseCreate310DateWithDay = makePostRequest(requestCreate310, write(postProductAttributeJson.copy(`type` = s"${ProductAttributeType.DATE_WITH_DAY}")))
      Then("We should get a 201")
      responseCreate310DateWithDay.code should equal(201)

      val responseCreate310Integer = makePostRequest(requestCreate310, write(postProductAttributeJson.copy(`type` = s"${ProductAttributeType.INTEGER}")))
      Then("We should get a 201")
      responseCreate310Integer.code should equal(201)

      val responseCreate310Double = makePostRequest(requestCreate310, write(postProductAttributeJson.copy(`type` = s"${ProductAttributeType.DOUBLE}")))
      Then("We should get a 201")
      responseCreate310Double.code should equal(201)


    }
  }

  feature("Create Product Attribute v3.1.0") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = createProductAttributeEndpoint.POST
      val response310 = makePostRequest(request310, write(postProductAttributeJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Create endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = createProductAttributeEndpoint.POST <@(user1)
      val response310 = makePostRequest(request310, write(postProductAttributeJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlementsRequiredText = UserHasMissingRoles + canCreateProductAttribute
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }
    scenario("We will call the Create endpoint but wrong `type` ", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateProductAttribute.toString)
      val request310 = createProductAttributeEndpoint.POST <@(user1)
      val response310 = makePostRequest(request310, write(postProductAttributeJson.copy(`type` = "worngType")))
      Then("We should get a 400")
      response310.code should equal(400)
      And(s"error should be $InvalidJsonFormat" )
      response310.body.extract[ErrorMessage].message contains( s"$InvalidJsonFormat The `Type` filed can only accept the following field:")
    }
  }
  
  feature("Get Product Attribute v3.1.0") {
    scenario("We will call the endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = getProductAttributeEndpoint.GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Get endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = getProductAttributeEndpoint.GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlementsRequiredText = UserHasMissingRoles + canGetProductAttribute
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }
  }
  
  feature("Update Product Attribute v3.1.0") {
    scenario("We will call the endpoint without a user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = updateProductAttributeEndpoint.PUT
      val response310 = makePutRequest(request310, write(putProductAttributeJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Update endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = updateProductAttributeEndpoint.PUT <@(user1)
      val response310 = makePutRequest(request310, write(putProductAttributeJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlementsRequiredText = UserHasMissingRoles + canUpdateProductAttribute
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }
  }
  
  feature("Delete Product Attribute v3.1.0") {
    scenario("We will call the Delete endpoint without a user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = deleteProductAttributeEndpoint.DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Delete endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = deleteProductAttributeEndpoint.DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      val createProductEntitlementsRequiredText = UserHasMissingRoles + canDeleteProductAttribute
      And("error should be " + createProductEntitlementsRequiredText)
      response310.body.extract[ErrorMessage].message should equal (createProductEntitlementsRequiredText)
    }
  }

}
