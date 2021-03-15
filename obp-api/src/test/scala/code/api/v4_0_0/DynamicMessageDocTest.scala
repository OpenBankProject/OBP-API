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
import code.api.util.ErrorMessages.{UserHasMissingRoles, DynamicMessageDocNotFound}
import code.api.util.{ApiRole}
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.dynamicMessageDoc.{JsonDynamicMessageDoc}
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JArray
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class DynamicMessageDocTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createDynamicMessageDoc))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateDynamicMessageDoc))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getDynamicMessageDoc))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getAllDynamicMessageDocs))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.deleteDynamicMessageDoc))

  feature("Test the DynamicMessageDoc endpoints") {
    scenario("We create my DynamicMessageDoc and get,update", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicMessageDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetDynamicMessageDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllDynamicMessageDocs.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canUpdateDynamicMessageDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canDeleteDynamicMessageDoc.toString)

      val request = (v4_0_0_Request / "management" / "dynamic-message-docs").POST <@ (user1)

      lazy val postDynamicMessageDoc = SwaggerDefinitionsJSON.jsonDynamicMessageDoc.copy(dynamicMessageDocId = None)

      val postBody = write(postDynamicMessageDoc)
      val response = makePostRequest(request, postBody)
      Then("We should get a 201")
      response.code should equal(201)

      val dynamicMessageDoc = response.body.extract[JsonDynamicMessageDoc]

      dynamicMessageDoc.dynamicMessageDocId shouldNot be (null)
      dynamicMessageDoc.process should be (postDynamicMessageDoc.process)
      dynamicMessageDoc.messageFormat should be (postDynamicMessageDoc.messageFormat)
      dynamicMessageDoc.description  should be (postDynamicMessageDoc.description)
      dynamicMessageDoc.outboundTopic  should be (postDynamicMessageDoc.outboundTopic)
      dynamicMessageDoc.inboundTopic  should be (postDynamicMessageDoc.inboundTopic)
      dynamicMessageDoc.exampleOutboundMessage  should be (postDynamicMessageDoc.exampleOutboundMessage)
      dynamicMessageDoc.exampleInboundMessage  should be (postDynamicMessageDoc.exampleInboundMessage)
      dynamicMessageDoc.outboundAvroSchema  should be (postDynamicMessageDoc.outboundAvroSchema)
      dynamicMessageDoc.inboundAvroSchema  should be (postDynamicMessageDoc.inboundAvroSchema)
      dynamicMessageDoc.adapterImplementation should be (postDynamicMessageDoc.adapterImplementation)


      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "dynamic-message-docs" / {dynamicMessageDoc.dynamicMessageDocId.getOrElse("")}).GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val dynamicMessageDocJsonGet400 = responseGet.body.extract[JsonDynamicMessageDoc]

      dynamicMessageDoc.dynamicMessageDocId shouldNot be (postDynamicMessageDoc.dynamicMessageDocId)
      dynamicMessageDoc.process should be (postDynamicMessageDoc.process)
      dynamicMessageDoc.messageFormat should be (postDynamicMessageDoc.messageFormat)
      dynamicMessageDoc.description  should be (postDynamicMessageDoc.description)
      dynamicMessageDoc.outboundTopic  should be (postDynamicMessageDoc.outboundTopic)
      dynamicMessageDoc.inboundTopic  should be (postDynamicMessageDoc.inboundTopic)
      dynamicMessageDoc.exampleOutboundMessage  should be (postDynamicMessageDoc.exampleOutboundMessage)
      dynamicMessageDoc.exampleInboundMessage  should be (postDynamicMessageDoc.exampleInboundMessage)
      dynamicMessageDoc.outboundAvroSchema  should be (postDynamicMessageDoc.outboundAvroSchema)
      dynamicMessageDoc.inboundAvroSchema  should be (postDynamicMessageDoc.inboundAvroSchema)
      dynamicMessageDoc.adapterImplementation should be (postDynamicMessageDoc.adapterImplementation)


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "dynamic-message-docs").GET <@ (user1)


      val responseGetAll = makeGetRequest(requestGetAll)
      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val dynamicMessageDocsJsonGetAll = responseGetAll.body \ "dynamic-message-docs"

      dynamicMessageDocsJsonGetAll shouldBe a [JArray]

      val dynamicMessageDocs = dynamicMessageDocsJsonGetAll(0)

      (dynamicMessageDocs \ "dynamic_message_doc_id").values.toString should equal (dynamicMessageDoc.dynamicMessageDocId.get)
      (dynamicMessageDocs \ "process").values.toString should equal (postDynamicMessageDoc.process)
      (dynamicMessageDocs \ "message_format").values.toString should equal (postDynamicMessageDoc.messageFormat)
      (dynamicMessageDocs \ "description").values.toString should equal (postDynamicMessageDoc.description)
      (dynamicMessageDocs \ "outbound_topic").values.toString should equal (postDynamicMessageDoc.outboundTopic)
      (dynamicMessageDocs \ "inbound_topic").values.toString should equal (postDynamicMessageDoc.inboundTopic)
      (dynamicMessageDocs \ "example_outbound_message") should equal (postDynamicMessageDoc.exampleOutboundMessage)
      (dynamicMessageDocs \ "example_inbound_message") should equal (postDynamicMessageDoc.exampleInboundMessage)
      (dynamicMessageDocs \ "outbound_avro_schema").values.toString should equal (postDynamicMessageDoc.outboundAvroSchema)
      (dynamicMessageDocs \ "inbound_avro_schema").values.toString should equal (postDynamicMessageDoc.inboundAvroSchema)
      (dynamicMessageDocs \ "adapter_implementation").values.toString should equal (postDynamicMessageDoc.adapterImplementation)


      Then(s"we test the $ApiEndpoint4")
      val requestUpdate = (v4_0_0_Request / "management" / "dynamic-message-docs" / {dynamicMessageDoc.dynamicMessageDocId.getOrElse("")}).PUT <@ (user1)

      val postDynamicMessageDocBody = SwaggerDefinitionsJSON.jsonDynamicMessageDoc.copy(process="getAccount")

      val responseUpdate = makePutRequest(requestUpdate,write(postDynamicMessageDocBody))
      Then("We should get a 200")
      responseUpdate.code should equal(200)

      val responseGetAfterUpdated = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterUpdated.code should equal(200)

      val dynamicMessageDocJsonGetAfterUpdated = responseGetAfterUpdated.body.extract[JsonDynamicMessageDoc]

      dynamicMessageDocJsonGetAfterUpdated.process should be (postDynamicMessageDocBody.process)


      Then(s"we test the $ApiEndpoint5")
      val requestDelete = (v4_0_0_Request / "management" / "dynamic-message-docs" / {dynamicMessageDoc.dynamicMessageDocId.getOrElse("")}).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDeleted = makeGetRequest(requestGet)
      Then("We should get a 400")
      Then("We should get a 400")
      responseGetAfterDeleted.code should equal(400)
      responseGetAfterDeleted.body.extract[ErrorMessage].message contains(DynamicMessageDocNotFound) should be (true)
    }
  }

  feature("Test the DynamicMessageDoc endpoints error cases") {
//    may need it later
//    scenario("We create my DynamicMessageDoc -- duplicated DynamicMessageDoc Name", ApiEndpoint1, VersionOfApi) {
//      When("We make a request v4.0.0")
//
//      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicMessageDoc.toString)
//
//
//      val request = (v4_0_0_Request / "management" / "dynamic-message-docs").POST <@ (user1)
//
//      lazy val postDynamicMessageDoc = SwaggerDefinitionsJSON.jsonDynamicMessageDoc
//
//      val response = makePostRequest(request, write(postDynamicMessageDoc))
//      Then("We should get a 201")
//      response.code should equal(201)
//
//      val dynamicMessageDoc = response.body.extract[JsonDynamicMessageDoc]
//
//      Then(s"we test the $ApiEndpoint1 with the same methodName")
//
//      val response2 = makePostRequest(request, write(postDynamicMessageDoc))
//      Then("We should get a 400")
//      response2.code should equal(400)
//      response2.body.extract[ErrorMessage].message contains(DynamicMessageDocAlreadyExists) should be (true)
//    }

    scenario("We create/get/getAll/update my DynamicMessageDoc without our proper roles", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      val request = (v4_0_0_Request / "management" / "dynamic-message-docs").POST <@ (user1)
      lazy val postDynamicMessageDoc = SwaggerDefinitionsJSON.jsonDynamicMessageDoc
      val response = makePostRequest(request, write(postDynamicMessageDoc))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanCreateDynamicMessageDoc}")

      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "dynamic-message-docs" / "xx").GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetDynamicMessageDoc}")


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "dynamic-message-docs").GET <@ (user1)

      val responseGetAll = makeGetRequest(requestGetAll)
      responseGetAll.code should equal(403)
      responseGetAll.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetAllDynamicMessageDocs}")


      Then(s"we test the $ApiEndpoint4")

      val requestUpdate = (v4_0_0_Request / "management" / "dynamic-message-docs" / "xx").PUT <@ (user1)
      val responseUpdate = makePutRequest(requestUpdate,write(postDynamicMessageDoc))

      responseUpdate.code should equal(403)
      responseUpdate.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanUpdateDynamicMessageDoc}")

      Then(s"we test the $ApiEndpoint5")

      val requestDelete = (v4_0_0_Request / "management" / "dynamic-message-docs" / "xx").DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)

      responseDelete.code should equal(403)
      responseDelete.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanDeleteDynamicMessageDoc}")
    }
  }


}
