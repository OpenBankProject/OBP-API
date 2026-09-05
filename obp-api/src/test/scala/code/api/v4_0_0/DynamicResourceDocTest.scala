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

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, DynamicResourceDocAlreadyExists, DynamicResourceDocNotFound, UserHasMissingRoles}
import code.api.util.ApiRole
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.json
import org.json4s.JArray
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.net.{URLDecoder, URLEncoder}


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

  // End-to-end exercise of the NATIVE runtime-compiled dynamic-endpoint dispatch (Piece C):
  // Http4sDynamicEndpoint.pieceC -> DynamicEndpoints.findEndpoint -> ResourceDoc.authCheckIO ->
  // the compiled OBPEndpointIO handler -> Sandbox.runInSandboxIO -> OBPReturnType => IO[Response] implicit.
  // The metadata-CRUD scenarios above only prove the doc/template compiles; these prove it RUNS.
  feature("Native execution of runtime-compiled dynamic endpoints (Piece C)") {

    scenario("Call the always-available practise endpoint (anonymous) end-to-end", VersionOfApi) {
      When("We POST a valid body to /obp/dynamic-endpoint/test-dynamic-resource-doc/my_user/MY_USER_ID")
      val request = (dynamicEndpoint_Request / "test-dynamic-resource-doc" / "my_user" / "123").POST
      val response = makePostRequest(request, """{"name":"Jhon","age":12,"hobby":["coding"]}""")
      Then("We should get a 200 (the practise endpoint requires no auth) served natively by PractiseEndpoint")
      response.code should equal(200)
      And("the body is the banks JSON returned by the practise endpoint (createBanksJson)")
      json.compactRender(response.body) should include("banks")
    }

    scenario("Create a runtime-compiled dynamic resource doc (no roles) and call it end-to-end", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We create a dynamic resource doc with no roles (anonymous) and a unique URL")
      val createReq = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "nativePieceCTest",
        requestUrl = "/my_native_user/MY_USER_ID"
      )
      val createResp = makePostRequest(createReq, write(doc))
      Then("We should get a 201")
      createResp.code should equal(201)

      Then("calling the compiled endpoint with a valid body returns 200 and the computed response body")
      // The doc has no roles but its errorResponseBodies require an authenticated user, so call as user1.
      val callReq = (dynamicEndpoint_Request / "dynamic-resource-doc" / "my_native_user" / "user-xyz").POST <@ (user1)
      val callResp = makePostRequest(callReq, """{"name":"Jhon","age":12,"hobby":["coding"]}""")
      callResp.code should equal(200)
      val rendered = json.compactRender(callResp.body)
      rendered should include("user-xyz_from_path") // pathParam MY_USER_ID flowed into the response
      rendered should include("Jhon")               // request body parsed and echoed back

      Then("calling without a body returns 400 — the body's `return errorResponse(...)` is recovered from the sandbox (NonLocalReturn)")
      val callNoBodyReq = (dynamicEndpoint_Request / "dynamic-resource-doc" / "my_native_user" / "user-xyz").POST <@ (user1)
      val callNoBodyResp = makePostRequest(callNoBodyReq, "")
      callNoBodyResp.code should equal(400)
    }

    // Exercises ResourceDoc.authCheckIO's role-gated path (the native mirror of wrappedWithAuthCheck):
    // a runtime-compiled dynamic-resource-doc declaring a role must enforce 401 (no auth) / 403 (no role)
    // / 200 (role granted). The existing scenario above only covers the no-role (anonymous-ish) path.
    scenario("Create a role-gated runtime-compiled dynamic resource doc and verify 401 / 403 / 200", ApiEndpoint1, VersionOfApi) {
      val dynamicRole = "CanCallNativePieceCRoleTest" // becomes a system-level dynamic role (requiresBankId = false)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We create a dynamic resource doc gated by that role (system-level: URL has no BANK_ID)")
      val createReq = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = dynamicRole,
        partialFunctionName = "nativePieceCRoleTest",
        requestUrl = "/my_role_user/MY_USER_ID"
      )
      makePostRequest(createReq, write(doc)).code should equal(201)

      val callUrl = dynamicEndpoint_Request / "dynamic-resource-doc" / "my_role_user" / "user-1"
      val body = """{"name":"Jhon","age":12,"hobby":["coding"]}"""

      Then("calling without authentication returns 401")
      val resp401 = makePostRequest(callUrl.POST, body)
      resp401.code should equal(401)
      resp401.body.extract[ErrorMessage].message should include(AuthenticatedUserIsRequired)

      Then("calling authenticated but without the role returns 403")
      val resp403 = makePostRequest(callUrl.POST <@ (user1), body)
      resp403.code should equal(403)
      resp403.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

      Then("granting the role makes the call succeed (200)")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, dynamicRole)
      val resp200 = makePostRequest(callUrl.POST <@ (user1), body)
      resp200.code should equal(200)
      json.compactRender(resp200.body) should include("_from_path")
    }

    // Regression guard for DynamicEndpointCodeGenerator.buildTemplate: the template served by
    // POST /management/dynamic-resource-docs/endpoint-code must emit the NATIVE contract
    // (Request[IO] / IO[Response[IO]] / callContext.httpBody / errorResponse), so the documented
    // workflow — copy the generated process body into a dynamic resource doc's method_body —
    // yields code that compiles and serves. The template previously emitted the retired Lift
    // contract (Box[JsonResponse], request.json, errorJsonResponse), which no longer compiles.
    scenario("The generated endpoint-code template compiles and serves as a dynamic resource doc method body", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We generate the endpoint code template for a POST endpoint with example bodies")
      val fragment = SwaggerDefinitionsJSON.jsonResourceDocFragment.copy(
        requestVerb = "POST",
        requestUrl = "/template_gen_user/TEMPLATE_USER_ID"
      )
      val codeReq = (v4_0_0_Request / "management" / "dynamic-resource-docs" / "endpoint-code").POST <@ (user1)
      val codeResp = makePostRequest(codeReq, write(fragment))
      codeResp.code should equal(201)
      val template = URLDecoder.decode((codeResp.body \ "code").values.toString, "UTF-8")

      Then("the template declares the native process signature, not the retired Lift one")
      template should include("override protected def process(callContext: CallContext, request: Request[IO], pathParams: Map[String, String]): IO[Response[IO]]")
      template should include("callContext.httpBody")
      template should include("errorResponse(")
      template should not include "Box[JsonResponse]"
      template should not include "request.json"
      template should not include "errorJsonResponse"
      template should not include "getPathParams(callContext, request)"

      Then("the process body sliced from the template compiles as a dynamic resource doc method body (201)")
      val marker = "IO[Response[IO]] = {"
      val processBody = template.substring(template.indexOf(marker) + marker.length, template.lastIndexOf("}"))
      val createReq = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "generatedTemplateTest",
        requestUrl = "/template_gen_user/TEMPLATE_USER_ID",
        methodBody = URLEncoder.encode(processBody, "UTF-8"),
        exampleRequestBody = fragment.exampleRequestBody,
        successResponseBody = fragment.successResponseBody
      )
      makePostRequest(createReq, write(doc)).code should equal(201)

      Then("calling the served endpoint with a valid body returns 200 (the template's placeholder business logic)")
      val callReq = (dynamicEndpoint_Request / "dynamic-resource-doc" / "template_gen_user" / "user-1").POST <@ (user1)
      makePostRequest(callReq, """{"name":"Jhon","age":12,"hobby":["coding"]}""").code should equal(200)

      Then("calling without a body returns 400 via the template's errorResponse early-exit")
      makePostRequest(callReq, "").code should equal(400)
    }
  }

  // Provenance is captured server-side into the DB columns but intentionally NOT surfaced in the
  // v4.0.0 (STABLE) response JSON — the v4 shape is frozen, so we assert against the stored entity,
  // not the response. (Exposure of these fields is planned for a new, v7, endpoint version.)
  feature("Provenance is captured on runtime-compiled dynamic resource docs") {

    scenario("Create stores created_by_user_id + method_body hash; update records the updater and refreshes the hash", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canUpdateDynamicResourceDoc.toString)

      When("We create a dynamic resource doc")
      val createReq = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      val posted = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        partialFunctionName = "provenanceTest",
        requestUrl = "/provenance_test_user/MY_USER_ID"
      )
      val createResp = makePostRequest(createReq, write(posted))
      createResp.code should equal(201)
      val docId = (createResp.body \ "dynamic_resource_doc_id").values.toString

      Then("the stored row records the authenticated caller and the server-computed SHA-256 of the decoded body")
      def storedRow = code.dynamicResourceDoc.DynamicResourceDoc
        .find(net.liftweb.mapper.By(code.dynamicResourceDoc.DynamicResourceDoc.DynamicResourceDocId, docId))
        .openOrThrowException("stored dynamic resource doc not found")
      storedRow.CreatedByUserId.get should be(resourceUser1.userId)
      storedRow.MethodBodyHash.get should be(code.api.util.APIUtil.sha256Hex(posted.decodedMethodBody))

      When("We update the doc with a changed method body")
      val changedMethodBody = URLEncoder.encode(
        URLDecoder.decode(posted.methodBody, "UTF-8") + "\n    // a change\n", "UTF-8")
      val updateReq = (v4_0_0_Request / "management" / "dynamic-resource-docs" / docId).PUT <@ (user1)
      val updateResp = makePutRequest(updateReq,
        write(posted.copy(dynamicResourceDocId = Some(docId), methodBody = changedMethodBody)))
      updateResp.code should equal(200)

      Then("created_by_user_id is preserved, updated_by_user_id is recorded, and the hash reflects the new body")
      storedRow.CreatedByUserId.get should be(resourceUser1.userId)
      storedRow.UpdatedByUserId.get should be(resourceUser1.userId)
      storedRow.MethodBodyHash.get should be(code.api.util.APIUtil.sha256Hex(URLDecoder.decode(changedMethodBody, "UTF-8")))
    }
  }

}
