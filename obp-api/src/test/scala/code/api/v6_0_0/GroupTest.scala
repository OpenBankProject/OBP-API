package code.api.v6_0_0

import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.JSONFactory600.{GroupJsonV600, GroupsJsonV600, PostGroupJsonV600, PutGroupJsonV600}
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class GroupTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.createGroup))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.getGroup))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.getGroups))
  object ApiEndpoint4 extends Tag(nameOf(Implementations6_0_0.updateGroup))
  object ApiEndpoint5 extends Tag(nameOf(Implementations6_0_0.deleteGroup))

  def postJson(bankId: Option[String] = None, name: String = "group-a") =
    PostGroupJsonV600(bankId, name, "a description", List("CanGetCustomer", "CanGetAccount"), true)

  Feature("Create Group v6.0.0") {

    Scenario("Fail without authentication", VersionOfApi, ApiEndpoint1) {
      val request = (v6_0_0_Request / "management" / "groups").POST
      val response = makePostRequest(request, write(postJson()))
      response.code should equal(401)
    }

    Scenario("Fail without CanCreateGroupAtAllBanks role for a system-level group", VersionOfApi, ApiEndpoint1) {
      val request = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val response = makePostRequest(request, write(postJson()))
      response.code should equal(403)
      val error = response.body.extract[ErrorMessage]
      error.message should include(UserHasMissingRoles)
    }

    Scenario("Fail with an empty group_name", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      val request = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val response = makePostRequest(request, write(postJson().copy(group_name = "")))
      response.code should equal(400)
    }

    Scenario("Succeed creating a system-level group with CanCreateGroupAtAllBanks", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      val request = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val response = makePostRequest(request, write(postJson()))
      response.code should equal(201)
      val group = response.body.extract[GroupJsonV600]
      group.group_name should equal("group-a")
      group.bank_id should equal(None)
      group.list_of_roles should equal(List("CanGetCustomer", "CanGetAccount"))
      group.is_enabled should equal(true)
      group.group_id.nonEmpty should equal(true)
    }

    Scenario("Succeed creating a bank-scoped group with CanCreateGroupAtOneBank", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateGroupAtOneBank.toString)
      val request = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val response = makePostRequest(request, write(postJson(Some(testBankId1.value), "bank-group")))
      response.code should equal(201)
      val group = response.body.extract[GroupJsonV600]
      group.bank_id should equal(Some(testBankId1.value))
    }
  }

  Feature("Get Group / Get Groups v6.0.0") {

    Scenario("Get a single group successfully", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetGroupsAtAllBanks.toString)
      val createRequest = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val createResponse = makePostRequest(createRequest, write(postJson(name = "group-to-get")))
      createResponse.code should equal(201)
      val groupId = createResponse.body.extract[GroupJsonV600].group_id

      val getRequest = (v6_0_0_Request / "management" / "groups" / groupId).GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)
      getResponse.code should equal(200)
      getResponse.body.extract[GroupJsonV600].group_id should equal(groupId)
    }

    Scenario("Get a non-existent group returns 404", VersionOfApi, ApiEndpoint2) {
      val getRequest = (v6_0_0_Request / "management" / "groups" / "does-not-exist").GET <@ (user1)
      val getResponse = makeGetRequest(getRequest)
      getResponse.code should equal(404)
    }

    Scenario("List all groups", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetGroupsAtAllBanks.toString)
      val createRequest = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      makePostRequest(createRequest, write(postJson(name = "group-list-1"))).code should equal(201)
      makePostRequest(createRequest, write(postJson(name = "group-list-2"))).code should equal(201)

      val listRequest = (v6_0_0_Request / "management" / "groups").GET <@ (user1)
      val listResponse = makeGetRequest(listRequest)
      listResponse.code should equal(200)
      val groups = listResponse.body.extract[GroupsJsonV600].groups
      groups.map(_.group_name) should contain allOf ("group-list-1", "group-list-2")
    }

    Scenario("List groups filtered by bank_id", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateGroupAtOneBank.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetGroupsAtOneBank.toString)
      val createRequest = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      makePostRequest(createRequest, write(postJson(Some(testBankId1.value), "group-filtered"))).code should equal(201)

      val listRequest = (v6_0_0_Request / "management" / "groups").GET.addQueryParameter("bank_id", testBankId1.value) <@ (user1)
      val listResponse = makeGetRequest(listRequest)
      listResponse.code should equal(200)
      val groups = listResponse.body.extract[GroupsJsonV600].groups
      groups.forall(_.bank_id == Some(testBankId1.value)) should equal(true)
      groups.map(_.group_name) should contain("group-filtered")
    }
  }

  Feature("Update Group v6.0.0") {

    Scenario("Succeed updating a group's fields", VersionOfApi, ApiEndpoint4) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateGroupAtAllBanks.toString)
      val createRequest = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val createResponse = makePostRequest(createRequest, write(postJson(name = "group-to-update")))
      val groupId = createResponse.body.extract[GroupJsonV600].group_id

      val putRequest = (v6_0_0_Request / "management" / "groups" / groupId).PUT <@ (user1)
      val putBody = PutGroupJsonV600(Some("renamed-group"), Some("new description"), Some(List("CanGetAnyUser")), Some(false))
      val putResponse = makePutRequest(putRequest, write(putBody))
      putResponse.code should equal(200)
      val updated = putResponse.body.extract[GroupJsonV600]
      updated.group_name should equal("renamed-group")
      updated.group_description should equal("new description")
      updated.list_of_roles should equal(List("CanGetAnyUser"))
      updated.is_enabled should equal(false)
    }

    Scenario("Updating a non-existent group returns 404", VersionOfApi, ApiEndpoint4) {
      val putRequest = (v6_0_0_Request / "management" / "groups" / "does-not-exist").PUT <@ (user1)
      val putResponse = makePutRequest(putRequest, write(PutGroupJsonV600(Some("x"), None, None, None)))
      putResponse.code should equal(404)
    }
  }

  Feature("Delete Group v6.0.0") {

    Scenario("Succeed deleting a group, then get returns 404", VersionOfApi, ApiEndpoint5) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateGroupAtAllBanks.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteGroupAtAllBanks.toString)
      val createRequest = (v6_0_0_Request / "management" / "groups").POST <@ (user1)
      val createResponse = makePostRequest(createRequest, write(postJson(name = "group-to-delete")))
      val groupId = createResponse.body.extract[GroupJsonV600].group_id

      val deleteRequest = (v6_0_0_Request / "management" / "groups" / groupId).DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)
      deleteResponse.code should equal(200)

      val getRequest = (v6_0_0_Request / "management" / "groups" / groupId).GET <@ (user1)
      makeGetRequest(getRequest).code should equal(404)
    }

    Scenario("Deleting a non-existent group returns 404", VersionOfApi, ApiEndpoint5) {
      val deleteRequest = (v6_0_0_Request / "management" / "groups" / "does-not-exist").DELETE <@ (user1)
      val deleteResponse = makeDeleteRequest(deleteRequest)
      deleteResponse.code should equal(404)
    }
  }

}
