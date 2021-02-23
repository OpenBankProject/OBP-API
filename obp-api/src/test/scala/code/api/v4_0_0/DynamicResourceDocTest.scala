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
import code.api.util.ErrorMessages.{DynamicResourceDocAlreadyExists, DynamicResourceDocNotFound, UserHasMissingRoles}
import code.api.util.ApiRole
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json
import net.liftweb.json.JArray
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class DynamicResourceDocTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createDynamicResourceDoc))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateDynamicResourceDoc))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getDynamicResourceDoc))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getAllDynamicResourceDocs))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.deleteDynamicResourceDoc))

  feature("Test the DynamicResourceDoc endpoints") {
    scenario("We create my DynamicResourceDoc and get,update", ApiEndpoint1,ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetDynamicResourceDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetAllDynamicResourceDocs.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canUpdateDynamicResourceDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canDeleteDynamicResourceDoc.toString)

      val request = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)

      lazy val postDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(dynamicResourceDocId = None)

      val response = makePostRequest(request, write(postDynamicResourceDoc))
      Then("We should get a 201")
      response.code should equal(201)

      val dynamicResourceDoc = response.body.extract[JsonDynamicResourceDoc]

      dynamicResourceDoc.dynamicResourceDocId shouldNot be (null)
      dynamicResourceDoc.methodBody should be (postDynamicResourceDoc.methodBody)
      dynamicResourceDoc.partialFunctionName  should be (postDynamicResourceDoc.partialFunctionName)
      dynamicResourceDoc.requestVerb  should be (postDynamicResourceDoc.requestVerb)
      dynamicResourceDoc.requestUrl  should be (postDynamicResourceDoc.requestUrl)
      dynamicResourceDoc.summary  should be (postDynamicResourceDoc.summary)
      dynamicResourceDoc.description  should be (postDynamicResourceDoc.description)
      dynamicResourceDoc.errorResponseBodies should be (postDynamicResourceDoc.errorResponseBodies)
      dynamicResourceDoc.tags should be (postDynamicResourceDoc.tags)

      dynamicResourceDoc.exampleRequestBody should be(postDynamicResourceDoc.exampleRequestBody)
      dynamicResourceDoc.successResponseBody should be(postDynamicResourceDoc.successResponseBody)

      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "dynamic-resource-docs" / {dynamicResourceDoc.dynamicResourceDocId.getOrElse("")}).GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)

      val dynamicResourceDocJsonGet400 = responseGet.body.extract[JsonDynamicResourceDoc]

      dynamicResourceDoc.dynamicResourceDocId shouldNot be (postDynamicResourceDoc.dynamicResourceDocId)
      dynamicResourceDoc.methodBody should be (postDynamicResourceDoc.methodBody)
      dynamicResourceDoc.partialFunctionName  should be (postDynamicResourceDoc.partialFunctionName)
      dynamicResourceDoc.requestVerb  should be (postDynamicResourceDoc.requestVerb)
      dynamicResourceDoc.requestUrl  should be (postDynamicResourceDoc.requestUrl)
      dynamicResourceDoc.summary  should be (postDynamicResourceDoc.summary)
      dynamicResourceDoc.description  should be (postDynamicResourceDoc.description)
      dynamicResourceDoc.errorResponseBodies should be (postDynamicResourceDoc.errorResponseBodies)
      dynamicResourceDoc.tags should be (postDynamicResourceDoc.tags)

      dynamicResourceDoc.exampleRequestBody should be(postDynamicResourceDoc.exampleRequestBody)
      dynamicResourceDoc.successResponseBody should be(postDynamicResourceDoc.successResponseBody)

      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "dynamic-resource-docs").GET <@ (user1)


      val responseGetAll = makeGetRequest(requestGetAll)
      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val dynamicResourceDocsJsonGetAll = responseGetAll.body \ "dynamic-resource-docs"

      dynamicResourceDocsJsonGetAll shouldBe a [JArray]

      val dynamicResourceDocs = dynamicResourceDocsJsonGetAll(0)
      
      (dynamicResourceDocs \ "dynamic_resource_doc_id").values.toString should equal (dynamicResourceDoc.dynamicResourceDocId.get)
      (dynamicResourceDocs \ "partial_function_name").values.toString should equal (postDynamicResourceDoc.partialFunctionName)
      (dynamicResourceDocs \ "request_verb").values.toString should equal (postDynamicResourceDoc.requestVerb)
      (dynamicResourceDocs \ "request_url").values.toString should equal (postDynamicResourceDoc.requestUrl)
      (dynamicResourceDocs \ "summary").values.toString should equal (postDynamicResourceDoc.summary)
      (dynamicResourceDocs \ "description").values.toString should equal (postDynamicResourceDoc.description)
      (dynamicResourceDocs \ "example_request_body") should equal (postDynamicResourceDoc.exampleRequestBody.orNull)
      (dynamicResourceDocs \ "success_response_body") should equal (postDynamicResourceDoc.successResponseBody.orNull)
      (dynamicResourceDocs \ "error_response_bodies").values.toString should equal (postDynamicResourceDoc.errorResponseBodies)
      (dynamicResourceDocs \ "tags").values.toString should equal (postDynamicResourceDoc.tags)
      (dynamicResourceDocs \ "method_body").values.toString should equal (postDynamicResourceDoc.methodBody)


      Then(s"we test the $ApiEndpoint4")
      val requestUpdate = (v4_0_0_Request / "management" / "dynamic-resource-docs" / {dynamicResourceDoc.dynamicResourceDocId.getOrElse("")}).PUT <@ (user1)

      val postDynamicResourceDocBody = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(partialFunctionName="getAccount")

      val responseUpdate = makePutRequest(requestUpdate,write(postDynamicResourceDocBody))
      Then("We should get a 200")
      responseUpdate.code should equal(200)

      val responseGetAfterUpdated = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGetAfterUpdated.code should equal(200)

      val dynamicResourceDocJsonGetAfterUpdated = responseGetAfterUpdated.body.extract[JsonDynamicResourceDoc]

      dynamicResourceDocJsonGetAfterUpdated.partialFunctionName should be (postDynamicResourceDocBody.partialFunctionName)


      Then(s"we test the $ApiEndpoint5")
      val requestDelete = (v4_0_0_Request / "management" / "dynamic-resource-docs" / {dynamicResourceDoc.dynamicResourceDocId.getOrElse("")}).DELETE <@ (user1)

      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 204")
      responseDelete.code should equal(204)

      val responseGetAfterDeleted = makeGetRequest(requestGet)
      Then("We should get a 400")
      Then("We should get a 400")
      responseGetAfterDeleted.code should equal(400)
      responseGetAfterDeleted.body.extract[ErrorMessage].message contains(DynamicResourceDocNotFound) should be (true)
    }
  }

  feature("Test the DynamicResourceDoc endpoints error cases") {
    scenario("We create my DynamicResourceDoc -- duplicated DynamicResourceDoc Name", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)


      val request = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)

      lazy val postDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc

      val response = makePostRequest(request, write(postDynamicResourceDoc))
      Then("We should get a 201")
      response.code should equal(201)

      Then(s"we test the $ApiEndpoint1 with the same methodName")

      val response2 = makePostRequest(request, write(postDynamicResourceDoc))
      Then("We should get a 400")
      response2.code should equal(400)
      response2.body.extract[ErrorMessage].message contains(DynamicResourceDocAlreadyExists) should be (true)

    }

    scenario("We create/get/getAll/update my DynamicResourceDoc without our proper roles", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")

      val request = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      lazy val postDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc
      val response = makePostRequest(request, write(postDynamicResourceDoc))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanCreateDynamicResourceDoc}")

      Then(s"we test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "dynamic-resource-docs" / "xx").GET <@ (user1)


      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 403")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetDynamicResourceDoc}")


      Then(s"we test the $ApiEndpoint3")
      val requestGetAll = (v4_0_0_Request / "management" / "dynamic-resource-docs").GET <@ (user1)

      val responseGetAll = makeGetRequest(requestGetAll)
      responseGetAll.code should equal(403)
      responseGetAll.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanGetAllDynamicResourceDocs}")


      Then(s"we test the $ApiEndpoint4")

      val requestUpdate = (v4_0_0_Request / "management" / "dynamic-resource-docs" / "xx").PUT <@ (user1)
      val responseUpdate = makePutRequest(requestUpdate,write(postDynamicResourceDoc))

      responseUpdate.code should equal(403)
      responseUpdate.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanUpdateDynamicResourceDoc}")

      Then(s"we test the $ApiEndpoint5")

      val requestDelete = (v4_0_0_Request / "management" / "dynamic-resource-docs" / "xx").DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)

      responseDelete.code should equal(403)
      responseDelete.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles${CanDeleteDynamicResourceDoc}")
    }
  }


}
