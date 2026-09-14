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

package code.api.v5_1_0

import org.json4s._
import _root_.org.json4s.native.Serialization.write
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class SystemViewsPermissionsTests extends V510ServerSetup {
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag("addSystemViewPermission")
  object ApiEndpoint2 extends Tag("deleteSystemViewPermission")

  def postSystemViewPermission(viewId: String, body: CreateViewPermissionJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v5_1_0_Request / "system-views" / viewId / "permissions").POST <@(consumerAndToken)
    makePostRequest(request, write(body))
  }

  def deleteSystemViewPermission(viewId: String, permissionName: String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v5_1_0_Request / "system-views" / viewId / "permissions" / permissionName).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }

  def createSystemView(viewId: String): Boolean = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateSystemView")
    val postBody = createSystemViewJsonV500.copy(name = viewId).copy(metadata_view = viewId).toCreateViewJson
    val response = {
      val request = (v5_1_0_Request / "system-views").POST <@(user1)
      makePostRequest(request, write(postBody))
    }
    response.code == 201
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Add Permission to a System View") {
    scenario("Unauthorized access", ApiEndpoint1, VersionOfApi) {
      val response = postSystemViewPermission("some-id", CreateViewPermissionJson("can_grant_access_to_views", None), None)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("Authorized without role", ApiEndpoint1, VersionOfApi) {
      val response = postSystemViewPermission("some-id", CreateViewPermissionJson("can_grant_access_to_views", None), user1)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains(UserHasMissingRoles + "CanCreateSystemViewPermission") shouldBe (true)
    }

    scenario("Authorized with proper Role", ApiEndpoint1, VersionOfApi) {
      val viewId = APIUtil.generateUUID()
      createSystemView(viewId)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateSystemViewPermission")
      val permissionJson = CreateViewPermissionJson("can_grant_access_to_views", None)
      val response = postSystemViewPermission(viewId, permissionJson, user1)
      response.code should equal(201)
      response.body.extract[ViewPermissionJson]
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Delete Permission from a System View") {
    scenario("Unauthorized access", ApiEndpoint2, VersionOfApi) {
      val response = deleteSystemViewPermission("some-id", "can_grant_access_to_views", None)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message contains(AuthenticatedUserIsRequired)  shouldBe (true)
    }

    scenario("Authorized without role", ApiEndpoint2, VersionOfApi) {
      val response = deleteSystemViewPermission("some-id", "can_grant_access_to_views", user1)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains(UserHasMissingRoles + "CanDeleteSystemViewPermission")  shouldBe (true)
    }

    scenario("Authorized with proper Role", ApiEndpoint2, VersionOfApi) {
      val viewId = APIUtil.generateUUID()
      createSystemView(viewId)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateSystemViewPermission")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanDeleteSystemViewPermission")

      val permissionJson = CreateViewPermissionJson("can_grant_access_to_views", None)
      val createResp = postSystemViewPermission(viewId, permissionJson, user1)
      createResp.code should equal(201)

      val deleteResp = deleteSystemViewPermission(viewId, "can_grant_access_to_views", user1)
      deleteResp.code should equal(204)
    }
    scenario("Authorized with proper Role with extra_data", ApiEndpoint2, VersionOfApi) {
      val viewId = APIUtil.generateUUID()
      createSystemView(viewId)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateSystemViewPermission")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanDeleteSystemViewPermission")

      val permissionJson = CreateViewPermissionJson("can_grant_access_to_views", Some(List("owner")))
      val createResp = postSystemViewPermission(viewId, permissionJson, user1)
      createResp.code should equal(201)
      createResp.body.extract[CreateViewPermissionJson].permission_name should equal("can_grant_access_to_views")
      createResp.body.extract[CreateViewPermissionJson].extra_data should equal (Some(List("owner")))

      val deleteResp = deleteSystemViewPermission(viewId, "can_grant_access_to_views", user1)
      deleteResp.code should equal(204)
    }
  }
}
