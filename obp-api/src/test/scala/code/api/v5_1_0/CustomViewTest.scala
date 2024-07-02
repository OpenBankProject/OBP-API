package code.api.v5_1_0

import code.api.Constant.{SYSTEM_AUDITOR_VIEW_ID, SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID, SYSTEM_OWNER_VIEW_ID, SYSTEM_STAGE_ONE_VIEW_ID}
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import net.liftweb.util.StringHelpers
import org.scalatest.Tag

import java.util.UUID

class CustomViewTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createCustomView))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.updateCustomView))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.getCustomView))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.deleteCustomView))

  
  val bankId = testBankId1.value
  val accountId = testAccountId1.value
  val ownerView = SYSTEM_OWNER_VIEW_ID
  val manageCustomView = SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID
  val targetViewId = "_test"
  
  val postCustomViewJsonWithFullPermissinos = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createCustomViewJson.copy(name=targetViewId)
  val postCustomViewJson = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createCustomViewJson.copy(
    name=targetViewId,
    metadata_view=targetViewId,
    allowed_permissions = List("can_see_transaction_start_date", "can_add_url")
  )
  val putCustomViewJsonWithFullPermissinos = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateCustomViewJson
  val putCustomViewJson = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateCustomViewJson.copy(
    description = "1",
    metadata_view = "2",
    is_public = false,
    which_alias_to_use = "",//TODO please check this field later.
    hide_metadata_if_alias_used = false,
    allowed_permissions = List("can_see_transaction_this_bank_account", "can_see_bank_account_owners")
  )
  
  feature(s"test Authorized access") {
    
    scenario(s"We will call the endpoint, $UserNotLoggedIn", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"target-views").POST
      val response510 = makePostRequest(request510, write(postCustomViewJson))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).PUT
        val response510 = makePutRequest(request510, write(putCustomViewJson))
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).GET
        val response510 = makeGetRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).DELETE
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
    }
    
    scenario(s"We will call the endpoint, $SourceViewHasLessPermission", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"target-views").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCustomViewJsonWithFullPermissinos))
      Then("We should get a 400")
      response510.code should equal(400)
      response510.body.extract[ErrorMessage].message contains(SourceViewHasLessPermission) shouldBe(true)

      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).PUT <@ (user1)
        val response510 = makePutRequest(request510, write(putCustomViewJsonWithFullPermissinos))
        Then("We should get a 400")
        response510.code should equal(400)
        response510.body.extract[ErrorMessage].message contains(SourceViewHasLessPermission) shouldBe(true)
      }
    }
    
    scenario(s"We will call the endpoint, $ViewDoesNotPermitAccess ", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"target-views").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCustomViewJson))
      Then("We should get a 400")
      response510.code should equal(400)
      response510.body.extract[ErrorMessage].message contains (ViewDoesNotPermitAccess) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains ("can_create_custom_view") shouldBe (true)
      
      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).PUT <@ (user1)
        val response510 = makePutRequest(request510, write(putCustomViewJson))
        Then("We should get a 400")
        response510.code should equal(400)
        response510.body.extract[ErrorMessage].message contains (ViewDoesNotPermitAccess) shouldBe (true)
        response510.body.extract[ErrorMessage].message contains ("can_update_custom_view") shouldBe (true)
      }
      
      {
        When("we need to prepare the custom view for get and delete")
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / manageCustomView / "target-views").POST <@ (user1)
        val response510 = makePostRequest(request510, write(postCustomViewJson))
        Then("We should get a 201")
        response510.code should equal(201)
      }
      {
        
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"target-views" / targetViewId ).GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 400")
        response510.code should equal(400)
        response510.body.extract[ErrorMessage].message contains (ViewDoesNotPermitAccess) shouldBe (true)
        response510.body.extract[ErrorMessage].message contains ("can_get_custom_view") shouldBe (true)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView / "target-views" / targetViewId).DELETE <@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 400")
        response510.code should equal(400)
        response510.body.extract[ErrorMessage].message contains (ViewDoesNotPermitAccess) shouldBe (true)
        response510.body.extract[ErrorMessage].message contains ("can_delete_custom_view") shouldBe (true)

      }
    }
    
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / manageCustomView /"target-views").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCustomViewJson))
      Then("We should get a 201")
      response510.code should equal(201)
      response510.body.extract[CustomViewJsonV510].name should equal(postCustomViewJson.name)
      response510.body.extract[CustomViewJsonV510].description should equal(postCustomViewJson.description)
      response510.body.extract[CustomViewJsonV510].metadata_view should equal(postCustomViewJson.metadata_view)
      response510.body.extract[CustomViewJsonV510].is_public should equal(postCustomViewJson.is_public)
      response510.body.extract[CustomViewJsonV510].alias should equal(postCustomViewJson.which_alias_to_use)
      response510.body.extract[CustomViewJsonV510].hide_metadata_if_alias_used should equal(postCustomViewJson.hide_metadata_if_alias_used)
      response510.body.extract[CustomViewJsonV510].allowed_permissions.sorted should equal(postCustomViewJson.allowed_permissions.sorted)

      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / manageCustomView / "target-views" / targetViewId).GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CustomViewJsonV510].name should equal(postCustomViewJson.name)
        response510.body.extract[CustomViewJsonV510].description should equal(postCustomViewJson.description)
        response510.body.extract[CustomViewJsonV510].metadata_view should equal(postCustomViewJson.metadata_view)
        response510.body.extract[CustomViewJsonV510].is_public should equal(postCustomViewJson.is_public)
        response510.body.extract[CustomViewJsonV510].alias should equal(postCustomViewJson.which_alias_to_use)
        response510.body.extract[CustomViewJsonV510].hide_metadata_if_alias_used should equal(postCustomViewJson.hide_metadata_if_alias_used)
        response510.body.extract[CustomViewJsonV510].allowed_permissions.sorted should equal(postCustomViewJson.allowed_permissions.sorted)

      }
      
      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / manageCustomView /"target-views" / targetViewId ).PUT <@ (user1)
        val response510 = makePutRequest(request510, write(putCustomViewJson))
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CustomViewJsonV510].description should equal(putCustomViewJson.description)
        response510.body.extract[CustomViewJsonV510].metadata_view should equal(putCustomViewJson.metadata_view)
        response510.body.extract[CustomViewJsonV510].is_public should equal(putCustomViewJson.is_public)
        response510.body.extract[CustomViewJsonV510].alias should equal(putCustomViewJson.which_alias_to_use)
        response510.body.extract[CustomViewJsonV510].hide_metadata_if_alias_used should equal(putCustomViewJson.hide_metadata_if_alias_used)
        response510.body.extract[CustomViewJsonV510].allowed_permissions.sorted should equal(putCustomViewJson.allowed_permissions.sorted)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / manageCustomView /"target-views" / targetViewId ).GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CustomViewJsonV510].description should equal(putCustomViewJson.description)
        response510.body.extract[CustomViewJsonV510].metadata_view should equal(putCustomViewJson.metadata_view)
        response510.body.extract[CustomViewJsonV510].is_public should equal(putCustomViewJson.is_public)
        response510.body.extract[CustomViewJsonV510].alias should equal(putCustomViewJson.which_alias_to_use)
        response510.body.extract[CustomViewJsonV510].hide_metadata_if_alias_used should equal(putCustomViewJson.hide_metadata_if_alias_used)
        response510.body.extract[CustomViewJsonV510].allowed_permissions.sorted should equal(putCustomViewJson.allowed_permissions.sorted)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / manageCustomView /"target-views" / targetViewId ).DELETE <@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 204")
        response510.code should equal(204)
      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / manageCustomView / "target-views" / targetViewId).GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 400")
        response510.code should equal(400)
        response510.body.extract[ErrorMessage].message contains (ViewNotFound) shouldBe(true)

      }
    }
    
  }
  
}
