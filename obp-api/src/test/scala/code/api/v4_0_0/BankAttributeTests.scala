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

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateBankAttribute, CanDeleteBankAttribute, CanGetBankAttribute, CanUpdateBankAttribute}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class BankAttributeTests extends V400ServerSetup with DefaultUsers {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createBankAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateBankAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.deleteBankAttribute))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getBankAttributes))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getBankAttribute))

  lazy val bankId = randomBankId

  feature(s"Assuring that endpoint $ApiEndpoint1 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint1 - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / bankId / "attribute").POST
      val responseGet = makePostRequest(requestGet, write(bankAttributeJsonV400))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint1 without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / bankId / "attribute").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(bankAttributeJsonV400))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateBankAttribute entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanCreateBankAttribute)
    }
  }


  feature(s"Assuring that endpoint $ApiEndpoint2 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint2 - Anonymous access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").PUT
      val responseGet = makePutRequest(requestGet, write(bankAttributeJsonV400))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 without proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(bankAttributeJsonV400))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanUpdateBankAttribute entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanUpdateBankAttribute)
    }
  }



  feature(s"Assuring that endpoint $ApiEndpoint3 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint3 - Anonymous access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").DELETE
      val response = makeDeleteRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint3 without proper role - Authorized access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanDeleteBankAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanDeleteBankAttribute)
    }
  }

  
  feature(s"Assuring that endpoint $ApiEndpoint4 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint4 - Anonymous access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint4 without proper role - Authorized access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanGetBankAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetBankAttribute)
    }
  }
  
  feature(s"Assuring that endpoint $ApiEndpoint5 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint4 - Anonymous access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint5 without proper role - Authorized access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val request = (v4_0_0_Request / "banks" / bankId / "attributes" / "DOES_NOT_MATTER").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanGetBankAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetBankAttribute)
    }
  }

  feature(s"Assuring that endpoints $ApiEndpoint1, $ApiEndpoint2, $ApiEndpoint3, $ApiEndpoint5 work as expected - $VersionOfApi") {
    scenario(s"Test successful CRUD operations", ApiEndpoint1, VersionOfApi) {
      // Create
      When("We make the request")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankAttribute.toString)
      val requestPost = (v4_0_0_Request / "banks" / bankId / "attribute").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(bankAttributeJsonV400))
      Then("We should get a 201")
      responsePost.code should equal(201)
      val jsonPost = responsePost.body.extract[BankAttributeResponseJsonV400]
      jsonPost.name should equal(bankAttributeJsonV400.name)
      jsonPost.is_active should equal(Some(true))

      // Update
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateBankAttribute.toString)
      val requestPut = (v4_0_0_Request / "banks" / bankId / "attributes" / jsonPost.bank_attribute_id).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(bankAttributeJsonV400.copy(is_active = Some(false))))
      val jsonPut = responsePut.body.extract[BankAttributeResponseJsonV400]
      jsonPut.is_active should equal(Some(false))

      // Get
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetBankAttribute.toString)
      val requestGet = (v4_0_0_Request / "banks" / bankId / "attributes" / jsonPost.bank_attribute_id).GET <@ (user1)
      makeGetRequest(requestGet).code should equal(200)
      
      // Delete
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteBankAttribute.toString)
      val requestDelete = (v4_0_0_Request / "banks" / bankId / "attributes" / jsonPost.bank_attribute_id).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should equal(204)

      // Get
      val requestGet2 = (v4_0_0_Request / "banks" / bankId / "attributes" / jsonPost.bank_attribute_id).GET <@ (user1)
      makeGetRequest(requestGet2).code should equal(400)
    }
  }


 }