package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetEntitlementRequestsAtAnyBank}
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole}
import code.api.v3_0_0.Http4s300.Implementations3_0_0
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
      response300.body.toString contains AuthenticatedUserIsRequired should be (true)
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

  feature(s"Pagination and sorting for entitlement request endpoints") {

    scenario("Get my entitlement requests with limit parameter", VersionOfApi, ApiEndpoint5) {
      // Create 3 entitlement requests
      val roles = List("CanCreateBankLevelEndpointTag", "CanGetSystemLevelEndpointTag", "CanCreateBankLevelDynamicEndpoint")
      val bankIds = List(testBankId1.value, "", testBankId1.value)
      roles.zip(bankIds).foreach { case (role, bankId) =>
        val postJson = s"""{"bank_id":"$bankId", "role_name":"$role"}"""
        val request = (v3_0Request / "entitlement-requests").POST <@(user1)
        val response = makePostRequest(request, postJson)
        response.code should equal(201)
      }

      When("We request with limit=2")
      val requestWithLimit = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("limit", "2"))
      val responseWithLimit = makeGetRequest(requestWithLimit)
      Then("We should get a 200 and at most 2 results")
      responseWithLimit.code should equal(200)
      val resultWithLimit = responseWithLimit.body.extract[EntitlementRequestsJSON]
      resultWithLimit.entitlement_requests.length should be (2)

      When("We request with limit=1")
      val requestWithLimit1 = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("limit", "1"))
      val responseWithLimit1 = makeGetRequest(requestWithLimit1)
      Then("We should get a 200 and at most 1 result")
      responseWithLimit1.code should equal(200)
      val resultWithLimit1 = responseWithLimit1.body.extract[EntitlementRequestsJSON]
      resultWithLimit1.entitlement_requests.length should be (1)
    }

    scenario("Get my entitlement requests with offset parameter", VersionOfApi, ApiEndpoint5) {
      // Create 3 entitlement requests
      val roles = List("CanCreateBankLevelEndpointTag", "CanGetSystemLevelEndpointTag", "CanCreateBankLevelDynamicEndpoint")
      val bankIds = List(testBankId1.value, "", testBankId1.value)
      roles.zip(bankIds).foreach { case (role, bankId) =>
        val postJson = s"""{"bank_id":"$bankId", "role_name":"$role"}"""
        val request = (v3_0Request / "entitlement-requests").POST <@(user1)
        val response = makePostRequest(request, postJson)
        response.code should equal(201)
      }

      When("We request with offset=1")
      val requestWithOffset = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("offset", "1"))
      val responseWithOffset = makeGetRequest(requestWithOffset)
      Then("We should get a 200 and 2 results (3 total minus 1 offset)")
      responseWithOffset.code should equal(200)
      val resultWithOffset = responseWithOffset.body.extract[EntitlementRequestsJSON]
      resultWithOffset.entitlement_requests.length should be (2)

      When("We request with offset=2")
      val requestWithOffset2 = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("offset", "2"))
      val responseWithOffset2 = makeGetRequest(requestWithOffset2)
      Then("We should get a 200 and 1 result")
      responseWithOffset2.code should equal(200)
      val resultWithOffset2 = responseWithOffset2.body.extract[EntitlementRequestsJSON]
      resultWithOffset2.entitlement_requests.length should be (1)
    }

    scenario("Get my entitlement requests with sort_direction parameter", VersionOfApi, ApiEndpoint5) {
      // Create 2 entitlement requests
      val postJson1 = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request1 = (v3_0Request / "entitlement-requests").POST <@(user1)
      val response1 = makePostRequest(request1, postJson1)
      response1.code should equal(201)
      val result1 = response1.body.extract[EntitlementRequestJSON]

      val postJson2 = s"""{"bank_id":"", "role_name":"CanGetSystemLevelEndpointTag"}"""
      val response2 = makePostRequest(request1, postJson2)
      response2.code should equal(201)
      val result2 = response2.body.extract[EntitlementRequestJSON]

      When("We request with sort_direction=ASC")
      val requestAsc = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("sort_direction", "ASC"))
      val responseAsc = makeGetRequest(requestAsc)
      Then("We should get a 200")
      responseAsc.code should equal(200)
      val resultAsc = responseAsc.body.extract[EntitlementRequestsJSON]
      resultAsc.entitlement_requests.length should be (2)

      When("We request with sort_direction=DESC")
      val requestDesc = (v3_0Request / "my" / "entitlement-requests").GET <@(user1) <<? List(("sort_direction", "DESC"))
      val responseDesc = makeGetRequest(requestDesc)
      Then("We should get a 200")
      responseDesc.code should equal(200)
      val resultDesc = responseDesc.body.extract[EntitlementRequestsJSON]
      resultDesc.entitlement_requests.length should be (2)
    }

    scenario("Get all entitlement requests with pagination - default behavior still works", VersionOfApi, ApiEndpoint3) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetEntitlementRequestsAtAnyBank.toString)

      // Create 2 entitlement requests
      val postJson1 = s"""{"bank_id":"${testBankId1.value}", "role_name":"CanCreateBankLevelEndpointTag"}"""
      val request = (v3_0Request / "entitlement-requests").POST <@(user1)
      makePostRequest(request, postJson1).code should equal(201)

      val postJson2 = s"""{"bank_id":"", "role_name":"CanGetSystemLevelEndpointTag"}"""
      makePostRequest(request, postJson2).code should equal(201)

      When("We request without any pagination params")
      val getAll = (v3_0Request / "entitlement-requests").GET <@(user1)
      val responseGetAll = makeGetRequest(getAll)
      Then("We should get a 200 and all results")
      responseGetAll.code should equal(200)
      val resultGetAll = responseGetAll.body.extract[EntitlementRequestsJSON]
      resultGetAll.entitlement_requests.length should be (2)

      When("We request with limit=1")
      val getAllWithLimit = (v3_0Request / "entitlement-requests").GET <@(user1) <<? List(("limit", "1"))
      val responseGetAllWithLimit = makeGetRequest(getAllWithLimit)
      Then("We should get a 200 and 1 result")
      responseGetAllWithLimit.code should equal(200)
      val resultGetAllWithLimit = responseGetAllWithLimit.body.extract[EntitlementRequestsJSON]
      resultGetAllWithLimit.entitlement_requests.length should be (1)
    }

  }

}
