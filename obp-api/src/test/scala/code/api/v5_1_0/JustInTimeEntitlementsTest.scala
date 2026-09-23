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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.UserOnly
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanCreateEntitlementAtAnyBank, CanCreateEntitlementAtOneBank, CanGetAnyUser, CanGetMetricsAtOneBank}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v2_1_0.MetricsJson
import code.api.v4_0_0.Http4s400.Implementations4_0_0
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


  feature(s"Just In Time Entitlements are never granted in the system space - $VersionOfApi") {
    // The system space is the space whose bank id is the literal SYS. A Role there is granted by
    // hand by someone holding the system space granting Role, never automatically, so that reaching
    // the instance wide space stays a deliberate act. Held at the function rather than over HTTP
    // because no endpoint resolves SYS as a bank id yet; see DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md.
    scenario("The per bank granting Role held at SYS grants nothing there", VersionOfApi) {
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val wantedRole = ApiRole.canGetMetricsAtOneBank
      Entitlement.entitlement.vend.addEntitlement(
        DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, CanCreateEntitlementAtOneBank.toString)

      When("access control is asked about a Role in the system space")
      val allowedInSystemSpace = APIUtil.handleAccessControlWithAuthMode(
        DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, "", List(wantedRole), UserOnly)

      Then("it refuses")
      allowedInSystemSpace should equal(false)

      And("no Entitlement was written in the system space")
      Entitlement.entitlement.vend.getEntitlement(
        DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser2.userId, wantedRole.toString).isDefined should equal(false)
    }

    scenario("The same caller at an ordinary bank is still granted just in time", VersionOfApi) {
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val wantedRole = ApiRole.canGetMetricsAtOneBank
      val bankId = testBankId1.value
      Entitlement.entitlement.vend.addEntitlement(
        bankId, resourceUser2.userId, CanCreateEntitlementAtOneBank.toString)

      When("access control is asked about the same Role at a real bank")
      val allowedAtBank = APIUtil.handleAccessControlWithAuthMode(
        bankId, resourceUser2.userId, "", List(wantedRole), UserOnly)

      Then("it allows, which is the behaviour the system space rule must not have broken")
      allowedAtBank should equal(true)

      And("the Entitlement was written at that bank")
      Entitlement.entitlement.vend.getEntitlement(
        bankId, resourceUser2.userId, wantedRole.toString).isDefined should equal(true)
    }
  }


  feature(s"Just In Time Entitlements only grant what the caller could have granted by hand - $VersionOfApi") {
    // Granting an Entitlement by hand goes through Add Entitlement, which refuses a Role whose
    // scope does not match the bank id it is given: a Role declared with requiresBankId = false
    // lives at the system scope and cannot be attached to a bank, and the caller needs a granting
    // Role that reaches that scope. Just in Time granting is documented as doing automatically what
    // that manual process would have allowed anyway, so it has to obey the same two rules. These
    // scenarios sit at the function rather than over HTTP so that the scope of the row that gets
    // written is visible, which is the part a response code cannot show.

    scenario("A system scoped Role is not granted from per bank granting rights", VersionOfApi) {
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val bankId = testBankId1.value
      val systemScopedRole = ApiRole.canSeeAccountAccessForAnyUser
      systemScopedRole.requiresBankId should equal(false)

      Given("a caller who may grant Entitlements at one bank and nowhere else")
      Entitlement.entitlement.vend.addEntitlement(
        bankId, resourceUser3.userId, CanCreateEntitlementAtOneBank.toString)
      withClue("the caller must really hold the granting Role, or this scenario proves nothing: ") {
        APIUtil.hasEntitlement(bankId, resourceUser3.userId, ApiRole.canCreateEntitlementAtOneBank) should equal(true)
      }

      When("access control is asked about a system scoped Role on an endpoint that carries that bank id")
      val allowed = APIUtil.handleAccessControlWithAuthMode(
        bankId, resourceUser3.userId, "", List(systemScopedRole), UserOnly)

      Then("it refuses, because Add Entitlement would have refused the same grant")
      allowed should equal(false)

      And("no Entitlement was written, at either scope")
      Entitlement.entitlement.vend.getEntitlement(
        bankId, resourceUser3.userId, systemScopedRole.toString).isDefined should equal(false)
      Entitlement.entitlement.vend.getEntitlement(
        "", resourceUser3.userId, systemScopedRole.toString).isDefined should equal(false)
    }

    scenario("A system scoped Role granted to an any bank granter is written at the system scope", VersionOfApi) {
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val bankId = testBankId1.value
      val systemScopedRole = ApiRole.canSeeAccountAccessForAnyUser

      Given("a caller who may grant Entitlements anywhere")
      Entitlement.entitlement.vend.addEntitlement(
        "", resourceUser1.userId, CanCreateEntitlementAtAnyBank.toString)

      When("access control is asked about a system scoped Role on an endpoint that carries a bank id")
      val allowed = APIUtil.handleAccessControlWithAuthMode(
        bankId, resourceUser1.userId, "", List(systemScopedRole), UserOnly)

      Then("it allows")
      allowed should equal(true)

      And("the Entitlement was written at the system scope, which is the only scope this Role is ever read at")
      Entitlement.entitlement.vend.getEntitlement(
        "", resourceUser1.userId, systemScopedRole.toString).isDefined should equal(true)

      And("nothing was written at the bank, where no check would ever have read it")
      Entitlement.entitlement.vend.getEntitlement(
        bankId, resourceUser1.userId, systemScopedRole.toString).isDefined should equal(false)
    }

    scenario("The granting Roles themselves are never granted this way", VersionOfApi) {
      setPropsValues("create_just_in_time_entitlements" -> "true")
      val bankId = testBankId1.value

      Given("a caller who may grant Entitlements at one bank")
      Entitlement.entitlement.vend.addEntitlement(
        bankId, resourceUser3.userId, CanCreateEntitlementAtOneBank.toString)
      withClue("the caller must really hold the granting Role, or this scenario proves nothing: ") {
        APIUtil.hasEntitlement(bankId, resourceUser3.userId, ApiRole.canCreateEntitlementAtOneBank) should equal(true)
      }

      When("access control is asked about the Role that grants Entitlements everywhere")
      val allowed = APIUtil.handleAccessControlWithAuthMode(
        bankId, resourceUser3.userId, "", List(ApiRole.canCreateEntitlementAtAnyBank), UserOnly)

      Then("it refuses: the granting Roles are excluded from this automation, as the Glossary and the props template both say")
      allowed should equal(false)

      And("no Entitlement was written, at either scope")
      Entitlement.entitlement.vend.getEntitlement(
        bankId, resourceUser3.userId, CanCreateEntitlementAtAnyBank.toString).isDefined should equal(false)
      Entitlement.entitlement.vend.getEntitlement(
        "", resourceUser3.userId, CanCreateEntitlementAtAnyBank.toString).isDefined should equal(false)
    }
  }

}
