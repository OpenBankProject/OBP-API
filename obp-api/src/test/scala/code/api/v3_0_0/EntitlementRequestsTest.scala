package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetEntitlementRequestsAtAnyBank}
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/*
Note This does not test retrieval from a backend.
We mock the backend so get test the API
 */
class EntitlementRequestsTest extends V300ServerSetup with DefaultUsers {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.addEntitlementRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.deleteEntitlementRequest))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_0_0.getAllEntitlementRequests))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_0_0.getEntitlementRequests))
  object ApiEndpoint5 extends Tag(nameOf(Implementations3_0_0.getEntitlementRequestsForCurrentUser))

  feature(s"The CURD endpoints") {

    scenario("create entitlement request - anonymous user.", VersionOfApi, ApiEndpoint1) {

      When("We make a request v3.0.0")
      val postJson = """{"bank_id":"xxx", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 401 and error message")
      response300.code should equal(401)
      response300.body.toString contains UserNotLoggedIn should be (true)
    }

    scenario("create entitlement request - non existing bank", VersionOfApi, ApiEndpoint1) {

      When("We make a request v3.0.0")
      val postJson = """{"bank_id":"xxx", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 404 and error message")
      response300.code should equal(404)
       response300.body.toString contains BankNotFound should be (true)
    }
    
    scenario("create entitlement request- non existing role name", VersionOfApi, ApiEndpoint1) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTagXXXX"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 400 and error message")
      response300.code should equal(400)
      response300.body.toString contains IncorrectRoleName should be (true)
    }
    
    
    scenario("create entitlement request- bank level role- but not bank_id", VersionOfApi, ApiEndpoint1) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 400 and error message")
      response300.code should equal(400)
      response300.body.toString contains EntitlementIsBankRole should be (true)
    }
    
    
    scenario("create entitlement request- system level role- but has bank_id", VersionOfApi, ApiEndpoint1) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanGetSystemLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 400 and error message")
      response300.code should equal(400)
      response300.body.toString contains EntitlementIsSystemRole should be (true)
    }
    
    scenario("create entitlement request- successfully", VersionOfApi, ApiEndpoint1) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 201 and correct response json format")
      response300.code should equal(201)
      val result = response300.body.extract[EntitlementRequestJSON]
      result.bank_id should be (testBankId1.value)
    }

    scenario("create entitlement request- create same entity twice", VersionOfApi, ApiEndpoint1) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 201 and correct response json format")
      response300.code should equal(201)


      val response3002rd = makePostRequest(request300, postJson)
      Then("We should get a 200 and correct response json format")
      response3002rd.code should equal(400)
      response3002rd.body.toString contains EntitlementRequestAlreadyExists should be (true)
    }
    
    scenario("CUR entitlement request- ", VersionOfApi, 
      ApiEndpoint1, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 201 and correct response json format")
      response300.code should equal(201)
      val result = response300.body.extract[EntitlementRequestJSON]
      result.bank_id should be (testBankId1.value)
      
      val postJson2 = s"""{"bank_id":"", "role_name":"CanGetSystemLevelEndpointTag"}"""
      val response2rd300 = makePostRequest(request300, postJson2)
      Then("We should get a 201 and correct response json format")
      response2rd300.code should equal(201)
      val result2rd = response2rd300.body.extract[EntitlementRequestJSON]
      result2rd.bank_id should be ("")


      val getALl = (v3_0Request / "entitlement-requests").GET <@(user1)
      val responseGetAll300 = makeGetRequest(getALl)
      Then("We should get a 200 and correct response json format")
      responseGetAll300.code should equal(403)
      responseGetAll300.body.toString contains CanGetEntitlementRequestsAtAnyBank.toString should be (true)
      
      val getOneUserALl = (v3_0Request /"users"/resourceUser1.userId/ "entitlement-requests").GET <@(user1)
      val responseGetOneUserALl = makeGetRequest(getOneUserALl)
      Then("We should get a 200 and correct response json format")
      responseGetOneUserALl.code should equal(403)
      responseGetAll300.body.toString contains CanGetEntitlementRequestsAtAnyBank.toString should be (true)
      
      val getMyUserALl = (v3_0Request /"my"/ "entitlement-requests").GET <@(user1)
      val responseGetMyUserALl = makeGetRequest(getMyUserALl)
      Then("We should get a 200 and correct response json format")
      responseGetMyUserALl.code should equal(200)
      val resultGetMyUserALl = responseGetMyUserALl.body.extract[EntitlementRequestsJSON]
      resultGetMyUserALl.entitlement_requests.length should be (2)
      
      {
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementRequestsAtAnyBank.toString)
        val getALl = (v3_0Request / "entitlement-requests").GET <@(user1)
        val responseGetAll300 = makeGetRequest(getALl)
        Then("We should get a 200 and correct response json format")
        responseGetAll300.code should equal(200)
        val resultGetAll = responseGetAll300.body.extract[EntitlementRequestsJSON]
        resultGetAll.entitlement_requests.length should be (2)

        val getOneUserALl = (v3_0Request /"users"/resourceUser1.userId/ "entitlement-requests").GET <@(user1)
        val responseGetOneUserALl = makeGetRequest(getOneUserALl)
        Then("We should get a 200 and correct response json format")
        responseGetOneUserALl.code should equal(200)
        val resultGetOneUserALl = responseGetOneUserALl.body.extract[EntitlementRequestsJSON]
        resultGetOneUserALl.entitlement_requests.length should be (2)

        val getMyUserALl = (v3_0Request /"my"/ "entitlement-requests").GET <@(user1)
        val responseGetMyUserALl = makeGetRequest(getMyUserALl)
        Then("We should get a 200 and correct response json format")
        responseGetMyUserALl.code should equal(200)
        val resultGetMyUserALl = responseGetMyUserALl.body.extract[EntitlementRequestsJSON]
        resultGetMyUserALl.entitlement_requests.length should be (2)
      }
     
    }
    

    scenario("create entitlement request- delete entity -missing role ", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 201 and correct response json format")
      response300.code should equal(201)
      val result = response300.body.extract[EntitlementRequestJSON]
      result.bank_id should be (testBankId1.value)
      
      val entitlementRequestId = result.entitlement_request_id
      val deleteRequest300 = (v3_0Request / "entitlement-requests"/ entitlementRequestId ).DELETE <@(user1)
      val deleteResponse = makeDeleteRequest(deleteRequest300)
      Then("We should get a 200 and correct response json format")
      deleteResponse.code should equal(403)
      deleteResponse.body.toString contains UserHasMissingRoles should be (true)
    }

    scenario("create entitlement request- delete entity -with role ", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteEntitlementRequestsAtAnyBank.toString)
      
      When("We make a request v3.0.0")
      val postJson = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request300 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response300 = makePostRequest(request300, postJson)
      Then("We should get a 201 and correct response json format")
      response300.code should equal(201)
      val result = response300.body.extract[EntitlementRequestJSON]
      result.bank_id should be (testBankId1.value)
      
      {
        val getMyUserALl = (v3_0Request /"my"/ "entitlement-requests").GET <@(user1)
        val responseGetMyUserALl = makeGetRequest(getMyUserALl)
        Then("We should get a 200 and correct response json format")
        responseGetMyUserALl.code should equal(200)
        val resultGetMyUserALl = responseGetMyUserALl.body.extract[EntitlementRequestsJSON]
        resultGetMyUserALl.entitlement_requests.length should be (1)
      }
      
      val entitlementRequestId = result.entitlement_request_id
      val deleteRequest300 = (v3_0Request / "entitlement-requests"/ entitlementRequestId ).DELETE <@(user1)
      val deleteResponse = makeDeleteRequest(deleteRequest300)
      Then("We should get a 200 and correct response json format")
      deleteResponse.code should equal(200)

      val getMyUserALl = (v3_0Request /"my"/ "entitlement-requests").GET <@(user1)
      val responseGetMyUserALl = makeGetRequest(getMyUserALl)
      Then("We should get a 200 and correct response json format")
      responseGetMyUserALl.code should equal(200)
      val resultGetMyUserALl = responseGetMyUserALl.body.extract[EntitlementRequestsJSON]
      resultGetMyUserALl.entitlement_requests.length should be (0)
    }

  }

}
