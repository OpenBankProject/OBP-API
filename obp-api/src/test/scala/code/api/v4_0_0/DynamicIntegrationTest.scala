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
import code.api.util.ApiRole._
import code.api.util.ExampleValue._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class DynamicIntegrationTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object DynamicIntegration extends Tag("Dynamic Entity/Dynamic/Mapping")
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createBankLevelEndpointMapping))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.createBankLevelDynamicEndpoint))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.createBankLevelDynamicEntity))



  val mapping = endpointMappingRequestBodyExample
  val dynamicEntity = dynamicEntityRequestBodyExample.copy(bankId = None)
  val dynamicEndpoint = dynamicEndpointRequestBodyExample
  
  feature(s"test Dynamic Entity/Endpoint and endpoint mappings together $ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3") {
    scenario("test Dynamic Entity/Endpoint and endpoint mappings together ", DynamicIntegration, VersionOfApi) {
      //First, we need to prepare the dynamic entity, it should have two fields: name, balance.
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
      val requestEntity = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "dynamic-entities").POST <@(user1)
      val responseEntity = makePostRequest(requestEntity, write(dynamicEntity))
      Then("We should get a 201")
      responseEntity.code should equal(201)
      
      //second, we need to prepare the dynamic endpoint, It has the filed name, balance. 
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelDynamicEndpoint.toString)
      val requestEndpoint = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "dynamic-endpoints").POST <@(user1)
      val responseEndpoint = makePostRequest(requestEndpoint, write(dynamicEndpoint))
      Then("We should get a 201")
      responseEndpoint.code should equal(201)
      
      
      // 3rd, we need to prepare the mappings, we need to mapping entity.name --> swagger.name , entity.balance --> swagger.balance
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelEndpointMapping.toString)
      val requestMapping = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").POST <@(user1)
      val responseMapping = makePostRequest(requestMapping, write(mapping))
      Then("We should get a 201")
      responseMapping.code should equal(201)
      val customerJson = responseMapping.body.extract[EndpointMappingCommons]
    }
  }
  
}
