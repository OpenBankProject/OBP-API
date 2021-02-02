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
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{InternalConnectorAlreadyExists, UserHasMissingRoles}
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import code.internalconnector.JsonInternalConnector
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JArray
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class InternalConnectorTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createInternalConnector))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getInternalConnector))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getAllInternalConnectors))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.updateInternalConnector))

  feature("Test the InternalConnector endpoints") {
    scenario("We create my InternalConnector and get,update", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateInternalConnector.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetInternalConnector.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllInternalConnectors.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canUpdateInternalConnector.toString)
      
      val request = (v4_0_0_Request / "management" / "internal-connectors").POST <@ (user1)

      lazy val postInternalConnector = SwaggerDefinitionsJSON.jsonInternalConnector

      val response = makePostRequest(request, write(postInternalConnector))
      Then("We should get a 201")
      response.code should equal(201)

      val internalConnector = response.body.extract[JsonInternalConnector]

      internalConnector.methodName should be (postInternalConnector.methodName)
      internalConnector.methodBody should be (postInternalConnector.methodBody)
      internalConnector.internalConnectorId shouldNot be (null)
      
      
      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "internal-connectors" / {internalConnector.internalConnectorId.getOrElse("")}).GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val internalConnectorJsonGet400 = responseGet.body.extract[JsonInternalConnector]

      internalConnectorJsonGet400.methodName should be (postInternalConnector.methodName)
      internalConnectorJsonGet400.methodBody should be (postInternalConnector.methodBody)
      internalConnector.internalConnectorId should be (internalConnectorJsonGet400.internalConnectorId)


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "internal-connectors").GET <@ (user1)


      val responseGetAll = makeGetRequest(requestGetAll)
      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val internalConnectorsJsonGetAll = responseGetAll.body \ "internal_connectors"

      internalConnectorsJsonGetAll shouldBe a [JArray]

      val internalConnectors = internalConnectorsJsonGetAll(0)
      (internalConnectors \ "method_name").values.toString should equal (postInternalConnector.methodName)
      (internalConnectors \ "method_body").values.toString should equal (postInternalConnector.methodBody)
      (internalConnectors \ "internal_connector_id").values.toString should be (internalConnectorJsonGet400.internalConnectorId.get)
      
      
      Then(s"we test the $ApiEndpoint4")
      val requestUpdate = (v4_0_0_Request / "management" / "internal-connectors" / {internalConnector.internalConnectorId.getOrElse("")}).PUT <@ (user1)

      lazy val postInternalConnectorMethodBody = SwaggerDefinitionsJSON.jsonInternalConnectorMethodBody
      
      val responseUpdate = makePutRequest(requestUpdate,write(postInternalConnectorMethodBody))
      Then("We should get a 200")
      responseUpdate.code should equal(200)

      val responseGetAfterUpdated = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterUpdated.code should equal(200)

      val internalConnectorJsonGetAfterUpdated = responseGetAfterUpdated.body.extract[JsonInternalConnector]

      internalConnectorJsonGetAfterUpdated.methodBody should be (postInternalConnectorMethodBody.methodBody)
      internalConnectorJsonGetAfterUpdated.methodName should be (internalConnectorJsonGet400.methodName)
      internalConnectorJsonGetAfterUpdated.internalConnectorId should be (internalConnectorJsonGet400.internalConnectorId)
    }
  }

  feature("Test the InternalConnector endpoints error cases") {
    scenario("We create my InternalConnector -- duplicated InternalConnector Name", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateInternalConnector.toString)


      val request = (v4_0_0_Request / "management" / "internal-connectors").POST <@ (user1)

      lazy val postInternalConnector = SwaggerDefinitionsJSON.jsonInternalConnector

      val response = makePostRequest(request, write(postInternalConnector))
      Then("We should get a 201")
      response.code should equal(201)

      val internalConnector = response.body.extract[JsonInternalConnector]

      internalConnector.methodName should be (postInternalConnector.methodName)
      internalConnector.methodBody should be (postInternalConnector.methodBody)
      internalConnector.internalConnectorId shouldNot be (null)


      Then(s"we test the $ApiEndpoint1 with the same methodName")

      val response2 = makePostRequest(request, write(postInternalConnector))
      Then("We should get a 400")
      response2.code should equal(400)
      response2.body.extract[ErrorMessage].message contains(InternalConnectorAlreadyExists) should be (true)

    }
    
    scenario("We create/get/getAll/update my InternalConnector without our proper roles", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      val request = (v4_0_0_Request / "management" / "internal-connectors").POST <@ (user1)
      lazy val postInternalConnector = SwaggerDefinitionsJSON.jsonInternalConnector
      val response = makePostRequest(request, write(postInternalConnector))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanCreateInternalConnector}")

      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "internal-connectors" / "xx").GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetInternalConnector}")


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "internal-connectors").GET <@ (user1)

      val responseGetAll = makeGetRequest(requestGetAll)
      responseGetAll.code should equal(403)
      responseGetAll.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetAllInternalConnectors}")


      Then(s"we test the $ApiEndpoint4")
      lazy val postInternalConnectorMethodBody = SwaggerDefinitionsJSON.jsonInternalConnectorMethodBody

      val requestUpdate = (v4_0_0_Request / "management" / "internal-connectors" / "xx").PUT <@ (user1)
      val responseUpdate = makePutRequest(requestUpdate,write(postInternalConnectorMethodBody))

      responseUpdate.code should equal(403)
      responseUpdate.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanUpdateInternalConnector}")
    }
  }

}
