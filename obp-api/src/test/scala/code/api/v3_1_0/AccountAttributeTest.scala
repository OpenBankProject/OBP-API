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
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountAttributeJson
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountAttributeTest extends V310ServerSetup {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createAccountAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.updateAccountAttribute))

  lazy val testBankId = randomBankId
  lazy val postAccountAttributeJson = accountAttributeJson
  lazy val putAccountAttributeJson = accountAttributeJson.copy(name = "updated attribute")
  lazy val createAccountAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "accounts" / testAccountId0.value / "products" / "PRODUCT_CODE" / "attribute")
  lazy val updateProductAttributeEndpoint = (v3_1_0_Request / "banks" / testBankId / "accounts" / testAccountId0.value / "products" / "PRODUCT_CODE" / "attributes" / "WHATEVER")
  lazy val parentPostPutProductJsonV310: PostPutProductJsonV310 = SwaggerDefinitionsJSON.postPutProductJsonV310.copy(parent_product_code ="")
  
  feature(s"Create/Update Account Attribute $VersionOfApi") {
    scenario("We will call the endpoints with a proper roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      val product: ProductJsonV310 = 
        createProduct(
          bankId=testBankId, 
          code=APIUtil.generateUUID(), 
          json=parentPostPutProductJsonV310
        )
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateAccountAttributeAtOneBank.toString)
      
      When(s"We make a request $VersionOfApi")
      val requestCreate310 = (v3_1_0_Request / "banks" / testBankId / "accounts" / testAccountId0.value / 
        "products" / product.code / "attribute").POST <@(user1)
      val responseCreate310 = makePostRequest(requestCreate310, write(postAccountAttributeJson))
      Then("We should get a 201")
      responseCreate310.code should equal(201)
      val createdAccountAttribute = responseCreate310.body.extract[AccountAttributeResponseJson]
      

      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanUpdateAccountAttribute.toString)
      When(s"We make a $VersionOfApi")
      val requestPut310 = (v3_1_0_Request / "banks" / testBankId / "accounts" / testAccountId0.value 
        / "products" / product.code / "attributes" / createdAccountAttribute.account_attribute_id).PUT <@(user1)
      val responsePut310 = makePutRequest(requestPut310, write(putAccountAttributeJson))
      Then("We should get a 201")
      responsePut310.code should equal(201)
      val updatedProduct = responsePut310.body.extract[AccountAttributeResponseJson]
      

      Then("We call create endpoint with another `type`")
      val responseCreate310DateWithDay = makePostRequest(requestCreate310, write(postAccountAttributeJson.copy(`type` = s"${ProductAttributeType.DATE_WITH_DAY}")))
      Then("We should get a 201")
      responseCreate310DateWithDay.code should equal(201)

      val responseCreate310Integer = makePostRequest(requestCreate310, write(postAccountAttributeJson.copy(`type` = s"${ProductAttributeType.INTEGER}")))
      Then("We should get a 201")
      responseCreate310Integer.code should equal(201)

      val responseCreate310Double = makePostRequest(requestCreate310, write(postAccountAttributeJson.copy(`type` = s"${ProductAttributeType.DOUBLE}")))
      Then("We should get a 201")
      responseCreate310Double.code should equal(201)

    }
  }

  feature(s"Create Account Attribute $VersionOfApi") {
    scenario("We will call the Create endpoint without a user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request310 = createAccountAttributeEndpoint.POST
      val response310 = makePostRequest(request310, write(postAccountAttributeJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Create endpoint without a proper role", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request310 = createAccountAttributeEndpoint.POST <@(user1)
      val response310 = makePostRequest(request310, write(postAccountAttributeJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMessageText = UserHasMissingRoles + canCreateAccountAttributeAtOneBank
      And("error should be " + errorMessageText)
      response310.body.extract[ErrorMessage].message should equal (errorMessageText)
    }
    scenario("We will call the Create endpoint but wrong `type` ", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateAccountAttributeAtOneBank.toString)
      val request310 = createAccountAttributeEndpoint.POST <@(user1)
      val response310 = makePostRequest(request310, write(postAccountAttributeJson.copy(`type` = "wrongType")))
      Then("We should get a 400")
      response310.code should equal(400)
      And(s"error should be $InvalidJsonFormat" )
      response310.body.extract[ErrorMessage].message contains( s"$InvalidJsonFormat The `Type` filed can only accept the following field:")
    }
  }
  
  feature(s"Update Account Attribute $VersionOfApi") {
    scenario("We will call the endpoint without a user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request310 = updateProductAttributeEndpoint.PUT
      val response310 = makePutRequest(request310, write(putAccountAttributeJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will call the Update endpoint without a proper role", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request310 = updateProductAttributeEndpoint.PUT <@(user1)
      val response310 = makePutRequest(request310, write(putAccountAttributeJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMessageText = UserHasMissingRoles + canUpdateAccountAttribute
      And("error should be " + errorMessageText)
      response310.body.extract[ErrorMessage].message should equal (errorMessageText)
    }
  }

}
