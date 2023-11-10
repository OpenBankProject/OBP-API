package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateEntitlementAtAnyBank, CanCreateEntitlementAtOneBank, CanGetAnyUser, CanGetMetricsAtOneBank}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v2_1_0.MetricsJson
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.UserJsonV400
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class JustInTimeEntitlementsTest extends V510ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getUserByUserId))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  feature(s"Assuring Just In Time Entitlements work as expected in case of system roles - $VersionOfApi") {
    scenario("Test absence of props create_just_in_time_entitlements", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
    scenario("Test create_just_in_time_entitlements=false", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      setPropsValues("create_just_in_time_entitlements" -> "false")
      val response = makeGetRequest(request)
      Then("error should be " + UserHasMissingRoles + CanGetAnyUser)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetAnyUser)
    }
    scenario("Test create_just_in_time_entitlements=true", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v5_1_0_Request / "users" / "user_id" / resourceUser3.userId).GET <@(user1)
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val response = makeGetRequest(request)
      Then("We get successful response")
      response.code should equal(200)
      response.body.extract[UserJsonV400].user_id should equal(resourceUser3.userId)
    }
  }
  
  
  feature(s"Assuring Just In Time Entitlements work as expected in case of bank roles - $VersionOfApi") {
    lazy val bankId = testBankId1.value
    def getMetrics(consumerAndToken: Option[(Consumer, Token)], bankId: String): APIResponse = {
      val request = v5_1_0_Request / "management" / "metrics" / "banks" / bankId <@(consumerAndToken)
      makeGetRequest(request)
    }
    scenario("Test absence of props create_just_in_time_entitlements", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateEntitlementAtOneBank.toString)
      val response = getMetrics(user1, bankId)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains (UserHasMissingRoles + CanGetMetricsAtOneBank) should be (true)
    }
    scenario("Test create_just_in_time_entitlements=false", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateEntitlementAtOneBank.toString)
      setPropsValues("create_just_in_time_entitlements" -> "false")
      val response = getMetrics(user1, bankId)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains (UserHasMissingRoles + CanGetMetricsAtOneBank) should be (true)
    }
    scenario("Test create_just_in_time_entitlements=true", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateEntitlementAtOneBank.toString)
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val response = getMetrics(user1, bankId)
      Then("We should get a 200")
      response.code should equal(200)
      response.body.extract[MetricsJsonV510]
    }
  }

}
