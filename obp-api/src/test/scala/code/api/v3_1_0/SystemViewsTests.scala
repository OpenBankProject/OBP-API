/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.v3_1_0

import _root_.net.liftweb.json.Serialization.write
import com.openbankproject.commons.model.ErrorMessage
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateSystemView, CanDeleteSystemView, CanGetSystemView, CanUpdateSystemView}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.APIUtil
import code.api.v1_2_1.APIInfoJSON
import code.api.v3_0_0.ViewJsonV300
import code.api.v3_1_0.APIMethods310.Implementations3_1_0
import code.entitlement.Entitlement
import code.setup.APIResponse
import code.views.MapperViews
import code.views.system.AccountAccess
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{CreateViewJson, UpdateViewJSON}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.scalatest.Tag

class SystemViewsTests extends V310ServerSetup {
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
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getSystemView))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.createSystemView))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.updateSystemView))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.deleteSystemView))
  
  // Custom view, name starts from `_`
  // System view, owner
  val randomSystemViewId = APIUtil.generateUUID()
  val postBodySystemViewJson = createSystemViewJson.copy(name=randomSystemViewId).copy(metadata_view = randomSystemViewId)
  val systemViewId = MapperViews.getNewViewPermalink(postBodySystemViewJson.name)
  
  def getSystemView(viewId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_1_0_Request / "system-views" / viewId <@(consumerAndToken)
    makeGetRequest(request)
  }
  def postSystemView(view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_1_0_Request / "system-views").POST <@(consumerAndToken)
    makePostRequest(request, write(view))
  }
  def putSystemView(viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_1_0_Request / "system-views" / viewId).PUT <@(consumerAndToken)
    makePutRequest(request, write(view))
  }
  def deleteSystemView(viewId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_1_0_Request / "system-views" / viewId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  def createSystemView(viewId: String): Boolean = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
    val postBody = postBodySystemViewJson.copy(name=viewId).copy(metadata_view = viewId)
    val response400 = postSystemView(postBody, user1)
    response400.code == 201
  }
  
  
  
  /************************ the tests ************************/
  feature("/root"){
    scenario("The root of the API") {
      Given("Nothing, this one always is working ")
      val httpResponse = getAPIInfo
      Then("we should get a 200 ok code")
      httpResponse.code should equal (200)
      val apiInfo = httpResponse.body.extract[APIInfoJSON]
      apiInfo.version should equal ("v3.1.0")
    }
  }

  
  
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint2")
      val response400 = postSystemView(postBodySystemViewJson, None)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint2")
      val response400 = postSystemView(postBodySystemViewJson, user1)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateSystemView)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint2")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      val response400 = postSystemView(postBodySystemViewJson, user1)
      Then("We should get a 201")
      response400.code should equal(201)
      response400.body.extract[ViewJsonV300]
    }
  }
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getSystemView("", None)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val response400 = getSystemView("", user1)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetSystemView)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val viewId =  APIUtil.generateUUID()
      createSystemView(viewId)
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemView.toString)
      val response400 = getSystemView(viewId, user1)
      Then("We should get a 200")
      response400.code should equal(200)
      response400.body.extract[ViewJsonV300]
    }
  }


  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $ApiEndpoint3")
      val response400 = getSystemView("", None)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When(s"We make a request $ApiEndpoint3")
      val response400 = getSystemView("", user1)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetSystemView)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access with proper Role") {
    scenario("we will update a view on a bank account", ApiEndpoint3, VersionOfApi) {
      val updatedViewDescription = "aloha"
      val updatedAliasToUse = "public"
      val allowedActions = List("can_see_images", "can_delete_comment")

      def viewUpdateJson(originalView : ViewJsonV300) = {
        //it's not perfect, assumes too much about originalView (i.e. randomView(true, ""))
        UpdateViewJSON(
          description = updatedViewDescription,
          metadata_view = originalView.metadata_view,
          is_public = originalView.is_public,
          is_firehose = Some(true),
          which_alias_to_use = updatedAliasToUse,
          hide_metadata_if_alias_used = !originalView.hide_metadata_if_alias_used,
          allowed_actions = allowedActions
        )
      }
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemView.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateSystemView.toString)

      Given("A view exists")
      val creationReply = postSystemView(postBodySystemViewJson, user1)
      creationReply.code should equal (201)
      val createdView : ViewJsonV300 = creationReply.body.extract[ViewJsonV300]
      createdView.id should not startWith("_")
      createdView.can_see_images should equal(true)
      createdView.can_delete_comment should equal(true)
      createdView.can_delete_physical_location should equal(true)
      createdView.can_edit_owner_comment should equal(true)
      createdView.description should not equal(updatedViewDescription)
      createdView.hide_metadata_if_alias_used should equal(false)

      When("We use a valid access token and valid put json")
      val reply = putSystemView(createdView.id, viewUpdateJson(createdView), user1)
      Then("We should get back the updated view")
      reply.code should equal (200)
      val updatedView = reply.body.extract[ViewJsonV300]
      updatedView.can_see_images should equal(true)
      updatedView.can_delete_comment should equal(true)
      updatedView.can_delete_physical_location should equal(false)
      updatedView.can_edit_owner_comment should equal(false)
      updatedView.description should equal(updatedViewDescription)
      updatedView.hide_metadata_if_alias_used should equal(true)
      updatedView.is_firehose should equal(Some(true))
    }
  }


  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When(s"We make a request $ApiEndpoint4")
      val response400 = deleteSystemView("", None)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When(s"We make a request $ApiEndpoint4")
      val response400 = deleteSystemView("", user1)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteSystemView)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access with proper Role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      val viewId = APIUtil.generateUUID()
      createSystemView(viewId)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemView.toString)
      When(s"We make a request $ApiEndpoint4")
      val response400 = deleteSystemView(viewId, user1)
      Then("We should get a 200")
      response400.code should equal(200)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access with proper Role in order to delete owner view") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemView.toString)
      When(s"We make a request $ApiEndpoint4")
      AccountAccess.findAll(
        By(AccountAccess.view_id, SYSTEM_OWNER_VIEW_ID),
        By(AccountAccess.user_fk, resourceUser1.id.get)
      ).forall(_.delete_!) // Remove all rows assigned to the system owner view in order to delete it
      val response400 = deleteSystemView(SYSTEM_OWNER_VIEW_ID, user1)
      Then("We should get a 200")
      response400.code should equal(200)
    }
  }
}
