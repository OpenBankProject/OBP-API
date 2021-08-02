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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class EndpointTagTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createSystemLevelEndpointTag))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateSystemLevelEndpointTag))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getSystemLevelEndpointTags))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteSystemLevelEndpointTag))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.createBankLevelEndpointTag))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.updateBankLevelEndpointTag))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.getBankLevelEndpointTags))
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.deleteBankLevelEndpointTag))

  val operationIdInUrl = "OBPv4.0.0-getBanks"
  
  feature("Test System Level Endpoint Tag Endpoints ") {
    scenario("We test CURD the Endpoint Tag Endpoints", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateSystemLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateSystemLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetSystemLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteSystemLevelEndpointTag.toString)
      
      When(s"First we test the $ApiEndpoint1")
      val request = (v4_0_0_Request / "management" / "endpoints" / operationIdInUrl /"tags").POST <@ (user1)
      lazy val endpointTagJson= SwaggerDefinitionsJSON.endpointTagJson400

      val response = makePostRequest(request, write(endpointTagJson))
      Then("We should get a 201")
      response.code should equal(201)

      val endpointTagResponseJson400 = response.body.extract[SystemLevelEndpointTagResponseJson400]

      val endpointTagId = endpointTagResponseJson400.endpoint_tag_id
      val tagName = endpointTagResponseJson400.tag_name
      tagName should equal(endpointTagJson.tag_name)  
      
      Then(s"we test the $ApiEndpoint2")
      val updateRequestEndpointTag = (v4_0_0_Request / "management" / "endpoints" / operationIdInUrl /"tags"/ endpointTagId).PUT <@ (user1)

      lazy val endpointTagUpdatedJson = endpointTagJson.copy(tag_name = "update1")

      val updateResponseEndpointTagJson = makePutRequest(updateRequestEndpointTag, write(endpointTagUpdatedJson))
      Then("We should get a 201")
      updateResponseEndpointTagJson.code should equal(201)
      val updatedEndpointTag = updateResponseEndpointTagJson.body.extract[SystemLevelEndpointTagResponseJson400]

      val  updatedTagName = updatedEndpointTag.tag_name
      updatedTagName should equal(endpointTagUpdatedJson.tag_name)

      
      Then(s"we test the $ApiEndpoint3")
      val requestGet = (v4_0_0_Request  / "management" / "endpoints" / operationIdInUrl /"tags").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val getEndpointTags = responseGet.body.extract[List[SystemLevelEndpointTagResponseJson400]]

      getEndpointTags.length should be (1)
      getEndpointTags.head should be (updatedEndpointTag)

      Then(s"we test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request  / "management" / "endpoints" / operationIdInUrl /"tags"/ endpointTagId).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDelete = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterDelete.code should equal(200)

      val endpointTagsJsonGetAfterDelete = responseGetAfterDelete.body.extract[List[SystemLevelEndpointTagResponseJson400]]

      endpointTagsJsonGetAfterDelete.length should be (0)
    }

    scenario("We test roles the Endpoint Tag Endpoints", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When(s"First we test the $ApiEndpoint1")
      val request = (v4_0_0_Request / "management" / "endpoints" / operationIdInUrl /"tags").POST <@ (user1)
      lazy val endpointTagJson= SwaggerDefinitionsJSON.endpointTagJson400

      val response = makePostRequest(request, write(endpointTagJson))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.toString contains(s"${ApiRole.CanCreateSystemLevelEndpointTag.toString}") should be (true)
      
      
      Then(s"we test the $ApiEndpoint2")
      val updateRequestEndpointTag = (v4_0_0_Request / "management" / "endpoints" / operationIdInUrl /"tags"/ "1").PUT <@ (user1)

      lazy val endpointTagUpdatedJson = endpointTagJson.copy(tag_name = "update1")

      val updateResponseEndpointTagJson = makePutRequest(updateRequestEndpointTag, write(endpointTagUpdatedJson))
      Then("We should get a 403")
      updateResponseEndpointTagJson.code should equal(403)
      updateResponseEndpointTagJson.body.toString contains(s"${ApiRole.CanUpdateSystemLevelEndpointTag.toString}") should be (true)


      Then(s"we test the $ApiEndpoint3")
      val requestGet = (v4_0_0_Request  / "management" / "endpoints" / operationIdInUrl /"tags").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)

     responseGet.body.toString contains(s"${ApiRole.CanGetSystemLevelEndpointTag.toString}") should be (true)



      Then(s"we test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request  / "management" / "endpoints" / operationIdInUrl /"tags"/ "1").DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 403")
      responseDelete.code should equal(403)
      responseDelete.body.toString contains(s"${ApiRole.CanDeleteSystemLevelEndpointTag.toString}") should be (true)
     
    }
  }
  
  feature("Test Bank Level Endpoint Tag Endpoints ") {
    scenario("We test CURD the Endpoint Tag Endpoints", ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateBankLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanUpdateBankLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanGetBankLevelEndpointTag.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanDeleteBankLevelEndpointTag.toString)
      
      When(s"First we test the $ApiEndpoint5")
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags").POST <@ (user1)
      lazy val endpointTagJson= SwaggerDefinitionsJSON.endpointTagJson400

      val response = makePostRequest(request, write(endpointTagJson))
      Then("We should get a 201")
      response.code should equal(201)

      val endpointTagResponseJson400 = response.body.extract[BankLevelEndpointTagResponseJson400]

      val endpointTagId = endpointTagResponseJson400.endpoint_tag_id
      val tagName = endpointTagResponseJson400.tag_name
      tagName should equal(endpointTagJson.tag_name)  
      
      Then(s"we test the $ApiEndpoint6")
      val updateRequestEndpointTag = (v4_0_0_Request / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags"/ endpointTagId).PUT <@ (user1)

      lazy val endpointTagUpdatedJson = endpointTagJson.copy(tag_name = "update1")

      val updateResponseEndpointTagJson = makePutRequest(updateRequestEndpointTag, write(endpointTagUpdatedJson))
      Then("We should get a 201")
      updateResponseEndpointTagJson.code should equal(201)
      val updatedEndpointTag = updateResponseEndpointTagJson.body.extract[BankLevelEndpointTagResponseJson400]

      val  updatedTagName = updatedEndpointTag.tag_name
      updatedTagName should equal(endpointTagUpdatedJson.tag_name)

      
      Then(s"we test the $ApiEndpoint7")
      val requestGet = (v4_0_0_Request  / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val getEndpointTags = responseGet.body.extract[List[BankLevelEndpointTagResponseJson400]]

      getEndpointTags.length should be (1)
      getEndpointTags.head should be (updatedEndpointTag)
      

      Then(s"we test the $ApiEndpoint8")
      val requestDelete = (v4_0_0_Request  / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags"/ endpointTagId).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDelete = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterDelete.code should equal(200)

      val endpointTagsJsonGetAfterDelete = responseGetAfterDelete.body.extract[List[BankLevelEndpointTagResponseJson400]]

      endpointTagsJsonGetAfterDelete.length should be (0)
    }
    
    scenario("We test roles the Endpoint Tag Endpoints", ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, ApiEndpoint8, VersionOfApi) {
      When(s"First we test the $ApiEndpoint5")
      val request = (v4_0_0_Request / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags").POST <@ (user1)
      lazy val endpointTagJson= SwaggerDefinitionsJSON.endpointTagJson400

      val response = makePostRequest(request, write(endpointTagJson))
      Then("We should get a 403")
      response.code should equal(403)

      response.body.toString contains(s"${ApiRole.CanCreateBankLevelEndpointTag.toString}") should be (true)
      
      Then(s"we test the $ApiEndpoint6")
      val updateRequestEndpointTag = (v4_0_0_Request / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags"/ "1").PUT <@ (user1)

      lazy val endpointTagUpdatedJson = endpointTagJson.copy(tag_name = "update1")

      val updateResponseEndpointTagJson = makePutRequest(updateRequestEndpointTag, write(endpointTagUpdatedJson))
      Then("We should get a 403")
      updateResponseEndpointTagJson.code should equal(403)
      updateResponseEndpointTagJson.body.toString contains(s"${ApiRole.CanUpdateBankLevelEndpointTag.toString}") should be (true)

      
      Then(s"we test the $ApiEndpoint7")
      val requestGet = (v4_0_0_Request  / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.toString contains(s"${ApiRole.CanGetBankLevelEndpointTag.toString}") should be (true)
      

      Then(s"we test the $ApiEndpoint8")
      val requestDelete = (v4_0_0_Request  / "management" /"banks"/testBankId1.value / "endpoints" / operationIdInUrl /"tags"/ "1").DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 403")
      responseDelete.code should equal(403)
      responseDelete.body.toString contains(s"${ApiRole.CanDeleteBankLevelEndpointTag.toString}") should be (true)

    }
  }

}
