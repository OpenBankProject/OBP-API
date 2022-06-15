package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanCreateAnyTransactionRequest, CanCreateScopeAtAnyBank, CanDeleteScopeAtAnyBank, CanGetAnyUser, CanGetEntitlementsForAnyUserAtAnyBank}
import code.api.util.ErrorMessages._
import code.api.v3_0_0.{CreateScopeJson, ScopeJsons}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.scope.Scope
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ScopesTest extends V400ServerSetup {
  override def beforeEach() = {
    // Default props values 
    setPropsValues("require_scopes_for_all_roles"-> "false")
    setPropsValues("allow_entitlements_or_scopes"-> "false")
    setPropsValues("require_scopes_for_listed_roles"-> "")
  }
  
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getUsers))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.addScope))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getScopes))

  def addScope(consumerId: String, json: CreateScopeJson): APIResponse = {
    // When("We try to add a scope v4.0.0")
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateScopeAtAnyBank.toString)
    val request400 = (v4_0_0_Request / "consumers" / consumerId / "scopes").POST <@ (user1)
    val response400 = makePostRequest(request400, write(json))
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response400
    //Then("We should get a 201")
    //response400.code should equal(201)
    //val scope = response400.body.extract[ScopeJson]
    //scope
  }  
  def getScopes(consumerId: String): APIResponse = {
    // When("We try to add a scope v4.0.0")
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetEntitlementsForAnyUserAtAnyBank.toString)
    val request400 = (v4_0_0_Request / "consumers" / consumerId / "scopes").GET <@ (user1)
    val response400 = makeGetRequest(request400)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response400
  }
  

  /**
   * Those tests needs to check the app behaviour regarding next properties:
   * - require_scopes_for_all_roles=false
   * - require_scopes_for_listed_roles=CanCreateUserAuthContext,CanGetCustomer
   * - allow_entitlements_or_scopes=false
   * 
   */
  feature(s"test $ApiEndpoint1 version $VersionOfApi") {

    // Consumer AND User has the Role
    // require_scopes_for_all_roles=true
    scenario("We will call the endpoint with require_scopes_for_all_roles=true", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_all_roles"-> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
    scenario("We will call the endpoint with require_scopes_for_all_roles=true but without user entitlement", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_all_roles"-> "true")
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
    scenario("We will call the endpoint with require_scopes_for_all_roles=true but without scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_all_roles"-> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
    scenario("We will call the endpoint with require_scopes_for_all_roles=true but without entitlement and scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_all_roles"-> "true")
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
    
    
    // Consumer AND User has the Role
    // require_scopes_for_listed_roles=CanGetAnyUser
    scenario("We will call the endpoint with require_scopes_for_listed_roles=CanGetAnyUser", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_listed_roles"-> "CanGetAnyUser")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
    scenario("We will call the endpoint with require_scopes_for_listed_roles=CanGetAnyUser but without user entitlement", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_listed_roles"-> "CanGetAnyUser")
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
    scenario("We will call the endpoint with require_scopes_for_listed_roles=CanGetAnyUser but without scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_listed_roles"-> "CanGetAnyUser")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
    scenario("We will call the endpoint with require_scopes_for_listed_roles=CanGetAnyUser but without entitlement and scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("require_scopes_for_listed_roles"-> "CanGetAnyUser")
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }

    
    // Consumer OR User has the Role
    scenario("We will call the endpoint with allow_entitlements_or_scopes=true and scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("allow_entitlements_or_scopes"-> "true")
      //Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
    scenario("We will call the endpoint with allow_entitlements_or_scopes=true and user entitlement", ApiEndpoint1, VersionOfApi) {
      setPropsValues("allow_entitlements_or_scopes"-> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(200)
      response400.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
    scenario("We will call the endpoint with allow_entitlements_or_scopes=true but without entitlement or scope", ApiEndpoint1, VersionOfApi) {
      setPropsValues("allow_entitlements_or_scopes"-> "true")
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      // Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }

    // Consumer has he Scope but this is not enough
    scenario("We will call the endpoint without user entitlement but with scope", ApiEndpoint1, VersionOfApi) {
      // Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAnyUser.toString)
      Scope.scope.vend.addScope("", testConsumer.id.get.toString, ApiRole.CanGetAnyUser.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("We get successful response")
      response400.code should equal(403)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi") {
    scenario("We will try to add scope to a consumer which does not exist", ApiEndpoint2, VersionOfApi) {
      val result = addScope("testConsumer.consumerId.get", SwaggerDefinitionsJSON.createScopeJson)
      result.code should equal(404)
    }
    scenario("We will try to add scope to a consumer which exists", ApiEndpoint2, VersionOfApi) {
      val result = addScope(
        testConsumer.consumerId.get, 
        SwaggerDefinitionsJSON.createScopeJson.copy(bank_id = "", role_name = CanDeleteScopeAtAnyBank.toString())
      )
      result.code should equal(201)
      
      val scopes = getScopes(testConsumer.consumerId.get)
      scopes.code should equal(200)
      scopes.body.extract[ScopeJsons].list.exists(_.role_name == CanDeleteScopeAtAnyBank.toString())
    }
    scenario("We will try to add scope to a consumer which exists but with incorrect role name", ApiEndpoint2, VersionOfApi) {
      val result = addScope(
        testConsumer.consumerId.get, 
        SwaggerDefinitionsJSON.createScopeJson.copy(bank_id = "", role_name = "IncorrectRoleName")
      )
      result.code should equal(400)
      val errorMessage = result.body.extract[ErrorMessage].message
      errorMessage contains IncorrectRoleName should be (true)
    }
    scenario("We will try to add scope to a consumer which exists but with incorrect bank id", ApiEndpoint2, VersionOfApi) {
      val result = addScope(
        testConsumer.consumerId.get, 
        SwaggerDefinitionsJSON.createScopeJson.copy(bank_id = "InvalidBankId", role_name = CanCreateAnyTransactionRequest.toString())
      )
      result.code should equal(400)
      val errorMessage = result.body.extract[ErrorMessage].message
      errorMessage contains BankNotFound should be (true)
    }
  }
  
}
