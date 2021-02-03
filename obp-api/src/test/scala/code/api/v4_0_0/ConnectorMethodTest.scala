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
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{ConnectorMethodAlreadyExists, UserHasMissingRoles}
import code.api.util.{ApiRole, CallContext}
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.bankconnectors.InternalConnector
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{Bank, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.json.JArray
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ConnectorMethodTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createConnectorMethod))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getConnectorMethod))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getAllConnectorMethods))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.updateConnectorMethod))

  feature("Test the ConnectorMethod endpoints") {
    scenario("We create my ConnectorMethod and get,update", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateConnectorMethod.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetConnectorMethod.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllConnectorMethods.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canUpdateConnectorMethod.toString)

      val request = (v4_0_0_Request / "management" / "connector-methods").POST <@ (user1)

      lazy val postConnectorMethod = SwaggerDefinitionsJSON.jsonConnectorMethod

      val response = makePostRequest(request, write(postConnectorMethod))
      Then("We should get a 201")
      response.code should equal(201)

      val connectorMethod = response.body.extract[JsonConnectorMethod]

      connectorMethod.methodName should be (postConnectorMethod.methodName)
      connectorMethod.methodBody should be (postConnectorMethod.methodBody)
      connectorMethod.internalConnectorId shouldNot be (null)


      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "connector-methods" / {connectorMethod.internalConnectorId.getOrElse("")}).GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val connectorMethodJsonGet400 = responseGet.body.extract[JsonConnectorMethod]

      connectorMethodJsonGet400.methodName should be (postConnectorMethod.methodName)
      connectorMethodJsonGet400.methodBody should be (postConnectorMethod.methodBody)
      connectorMethod.internalConnectorId should be (connectorMethodJsonGet400.internalConnectorId)


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "connector-methods").GET <@ (user1)


      val responseGetAll = makeGetRequest(requestGetAll)
      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val connectorMethodsJsonGetAll = responseGetAll.body \ "connector_methods"

      connectorMethodsJsonGetAll shouldBe a [JArray]

      val connectorMethods = connectorMethodsJsonGetAll(0)
      (connectorMethods \ "method_name").values.toString should equal (postConnectorMethod.methodName)
      (connectorMethods \ "method_body").values.toString should equal (postConnectorMethod.methodBody)
      (connectorMethods \ "internal_connector_id").values.toString should be (connectorMethodJsonGet400.internalConnectorId.get)


      Then(s"we test the $ApiEndpoint4")
      val requestUpdate = (v4_0_0_Request / "management" / "connector-methods" / {connectorMethod.internalConnectorId.getOrElse("")}).PUT <@ (user1)

      lazy val postConnectorMethodMethodBody = SwaggerDefinitionsJSON.jsonConnectorMethodMethodBody

      val responseUpdate = makePutRequest(requestUpdate,write(postConnectorMethodMethodBody))
      Then("We should get a 200")
      responseUpdate.code should equal(200)

      val responseGetAfterUpdated = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterUpdated.code should equal(200)

      val connectorMethodJsonGetAfterUpdated = responseGetAfterUpdated.body.extract[JsonConnectorMethod]

      connectorMethodJsonGetAfterUpdated.methodBody should be (postConnectorMethodMethodBody.methodBody)
      connectorMethodJsonGetAfterUpdated.methodName should be (connectorMethodJsonGet400.methodName)
      connectorMethodJsonGetAfterUpdated.internalConnectorId should be (connectorMethodJsonGet400.internalConnectorId)
    }
  }

  feature("Test the ConnectorMethod endpoints error cases") {
    scenario("We create my ConnectorMethod -- duplicated ConnectorMethod Name", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateConnectorMethod.toString)


      val request = (v4_0_0_Request / "management" / "connector-methods").POST <@ (user1)

      lazy val postConnectorMethod = SwaggerDefinitionsJSON.jsonConnectorMethod

      val response = makePostRequest(request, write(postConnectorMethod))
      Then("We should get a 201")
      response.code should equal(201)

      val connectorMethod = response.body.extract[JsonConnectorMethod]

      connectorMethod.methodName should be (postConnectorMethod.methodName)
      connectorMethod.methodBody should be (postConnectorMethod.methodBody)
      connectorMethod.internalConnectorId shouldNot be (null)


      Then(s"we test the $ApiEndpoint1 with the same methodName")

      val response2 = makePostRequest(request, write(postConnectorMethod))
      Then("We should get a 400")
      response2.code should equal(400)
      response2.body.extract[ErrorMessage].message contains(ConnectorMethodAlreadyExists) should be (true)

    }

    scenario("We create/get/getAll/update my ConnectorMethod without our proper roles", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      val request = (v4_0_0_Request / "management" / "connector-methods").POST <@ (user1)
      lazy val postConnectorMethod = SwaggerDefinitionsJSON.jsonConnectorMethod
      val response = makePostRequest(request, write(postConnectorMethod))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanCreateConnectorMethod}")

      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "connector-methods" / "xx").GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetConnectorMethod}")


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "connector-methods").GET <@ (user1)

      val responseGetAll = makeGetRequest(requestGetAll)
      responseGetAll.code should equal(403)
      responseGetAll.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetAllConnectorMethods}")


      Then(s"we test the $ApiEndpoint4")
      lazy val postConnectorMethodMethodBody = SwaggerDefinitionsJSON.jsonConnectorMethodMethodBody

      val requestUpdate = (v4_0_0_Request / "management" / "connector-methods" / "xx").PUT <@ (user1)
      val responseUpdate = makePutRequest(requestUpdate,write(postConnectorMethodMethodBody))

      responseUpdate.code should equal(403)
      responseUpdate.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanUpdateConnectorMethod}")
    }
  }

  feature("Test the InternalConnector method") {
    scenario("We create a ConnectorMethod -- call the method, it should response correct result", VersionOfApi) {
      When("We make create a ConnectorMethod")
      val methodBody =
        """
          |Future.successful(
          |  Full((BankCommons(
          |    BankId("Hello_bank_id"),
          |    "shortName:" + bankId.value,
          |    "fullName:" + bankId.value,
          |    "logoUrl value",
          |    "websiteUrl value",
          |    "bankRoutingScheme value",
          |    "bankRoutingAddress value",
          |    "swiftBic value",
          |    "nationalIdentifier value"
          |  ), callContext))
          |)
          |""".stripMargin
      val encodedMethodBody = URLEncoder.encode(methodBody, "UTF-8")
      ConnectorMethodProvider.provider.vend.create(JsonConnectorMethod(Some("Hello_bank_id"), "getBank", encodedMethodBody))
      val connectorMethod = InternalConnector.instance

      Then("Call dynamic method")
      val future = connectorMethod.getBank(BankId("Hello_bank_id"), None)
      val result = Await.result(future, Duration.apply(10, TimeUnit.SECONDS))

      result shouldBe a[Full[(Bank, Option[CallContext])]]
      val Full((bank, _)) = result

      bank.bankId.value shouldBe "Hello_bank_id"
      bank.shortName shouldBe "shortName:Hello_bank_id"
      bank.fullName shouldBe "fullName:Hello_bank_id"
      bank.bankRoutingAddress shouldBe "bankRoutingAddress value"
    }
  }

}
