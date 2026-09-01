package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canCreateAbacRule, canExecuteAbacRule}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class AbacRuleTests extends V600ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Force creation of multiple users so the statistical permissiveness check
    // has a meaningful sample (>1 user). Without this, a rule matching only
    // resourceUser1's email looks "100% permissive" in an otherwise empty DB.
    val _ = (resourceUser1, resourceUser2, resourceUser3, resourceUser4)
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.executeAbacRule))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.executeAbacPolicy))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.createAbacRule))

  /**
   * Helper to create an ABAC rule via the API and return its ID.
   */
  private def createAbacRuleViaApi(
    ruleName: String,
    ruleCode: String,
    policy: String = "account-access",
    isActive: Boolean = true,
    description: String = "Test rule"
  ): String = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
    val createJson = CreateAbacRuleJsonV600(
      rule_name = ruleName,
      rule_code = ruleCode,
      description = description,
      policy = policy,
      is_active = isActive
    )
    val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
    val response = makePostRequest(request, write(createJson))
    response.code should equal(201)
    (response.body \ "abac_rule_id").extract[String]
  }

  // ==================== executeAbacRule Tests ====================

  feature(s"Assuring that endpoint executeAbacRule works as expected - $VersionOfApi") {

    scenario("Anonymous access should be rejected", ApiEndpoint1, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "abac-rules" / "some-rule-id" / "execute").POST
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without CanExecuteAbacRule role should be rejected", ApiEndpoint1, VersionOfApi) {
      When("We make the request without the required role")
      val request = (v6_0_0_Request / "management" / "abac-rules" / "some-rule-id" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + canExecuteAbacRule)
    }

    scenario("Execute a non-existent rule should return error", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)
      When("We execute a rule that does not exist")
      val request = (v6_0_0_Request / "management" / "abac-rules" / "non-existent-id" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 404")
      response.code should equal(404)
    }

    scenario("Execute an allow-all rule should return true", ApiEndpoint1, VersionOfApi) {
      val ruleId = createAbacRuleViaApi("allow-all-test", s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the allow-all rule")
      val request = (v6_0_0_Request / "management" / "abac-rules" / ruleId / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = true")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(true)
    }

    scenario("Execute a deny-all rule should return false", ApiEndpoint1, VersionOfApi) {
      val ruleId = createAbacRuleViaApi("deny-all-test", "false")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the deny-all rule")
      val request = (v6_0_0_Request / "management" / "abac-rules" / ruleId / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = false")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(false)
    }

    scenario("Execute rule with explicit authenticated_user_id", ApiEndpoint1, VersionOfApi) {
      val ruleId = createAbacRuleViaApi("auth-user-test", s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the rule providing an explicit authenticated_user_id")
      val request = (v6_0_0_Request / "management" / "abac-rules" / ruleId / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(
        authenticated_user_id = Some(resourceUser1.userId),
        on_behalf_of_user_id = None,
        user_id = None,
        bank_id = None,
        account_id = None,
        view_id = None,
        transaction_request_id = None,
        transaction_id = None,
        customer_id = None
      )
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = true")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(true)
    }

    scenario("Execute an inactive rule should return error", ApiEndpoint1, VersionOfApi) {
      val ruleId = createAbacRuleViaApi("inactive-test", s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""", isActive = false)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the inactive rule")
      val request = (v6_0_0_Request / "management" / "abac-rules" / ruleId / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get result = false (inactive rule fails)")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(false)
    }
  }

  // ==================== executeAbacPolicy Tests ====================

  feature(s"Assuring that endpoint executeAbacPolicy works as expected - $VersionOfApi") {

    scenario("Anonymous access should be rejected", ApiEndpoint2, VersionOfApi) {
      When("We make the request without authentication")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("Authenticated user without CanExecuteAbacRule role should be rejected", ApiEndpoint2, VersionOfApi) {
      When("We make the request without the required role")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + canExecuteAbacRule)
    }

    scenario("Execute policy with invalid policy name should return error", ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)
      When("We execute a non-existent policy")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "non-existent-policy" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))
      Then("We should get a 404")
      response.code should equal(404)
    }

    scenario("Execute policy with no rules should default to deny (false)", ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the account-access policy with no rules configured")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = false (default deny when no rules)")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(false)
    }

    scenario("Execute policy with one allow-all rule should return true", ApiEndpoint2, VersionOfApi) {
      createAbacRuleViaApi("policy-allow-test", s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""", policy = "account-access")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the account-access policy")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = true")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(true)
    }

    scenario("Execute policy with only deny-all rules should return false", ApiEndpoint2, VersionOfApi) {
      createAbacRuleViaApi("policy-deny-test", "false", policy = "account-access")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the account-access policy with only deny rules")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = false")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(false)
    }

    scenario("Execute policy with mixed rules - OR logic means at least one must pass", ApiEndpoint2, VersionOfApi) {
      // Create one allow rule and one deny rule for the same policy
      createAbacRuleViaApi("policy-mixed-allow", s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""", policy = "account-access")
      createAbacRuleViaApi("policy-mixed-deny", "false", policy = "account-access")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canExecuteAbacRule.toString)

      When("We execute the account-access policy with mixed rules")
      val request = (v6_0_0_Request / "management" / "abac-policies" / "account-access" / "execute").POST <@ (user1)
      val execJson = ExecuteAbacRuleJsonV600(None, None, None, None, None, None, None, None, None)
      val response = makePostRequest(request, write(execJson))

      Then("We should get a 200 with result = true (OR logic - at least one rule passes)")
      response.code should equal(200)
      val result = response.body.extract[AbacRuleResultJsonV600]
      result.result should equal(true)
    }
  }

  // ==================== Tautology Detection Tests ====================

  feature(s"Assuring that tautological ABAC rules are rejected - $VersionOfApi") {

    scenario("Creating a rule with bare 'true' should be rejected", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val createJson = CreateAbacRuleJsonV600(
        rule_name = "tautology-true",
        rule_code = "true",
        description = "Should be rejected",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))
      Then("We should get a 400")
      response.code should equal(400)
    }

    scenario("Creating a rule with '1==1' should be rejected", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val createJson = CreateAbacRuleJsonV600(
        rule_name = "tautology-numeric",
        rule_code = "1==1",
        description = "Should be rejected",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))
      Then("We should get a 400")
      response.code should equal(400)
    }

    scenario("Creating a rule with 'false' should be allowed (deny-all is fine)", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val createJson = CreateAbacRuleJsonV600(
        rule_name = "deny-all-ok",
        rule_code = "false",
        description = "Deny all is allowed",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))
      Then("We should get a 201")
      response.code should equal(201)
    }

    scenario("Validating a rule with 'true' should return PermissivenessError", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val validateJson = ValidateAbacRuleJsonV600(rule_code = "true")
      val request = (v6_0_0_Request / "management" / "abac-rules" / "validate").POST <@ (user1)
      val response = makePostRequest(request, write(validateJson))
      Then("We should get a 200 with valid=false and PermissivenessError type")
      response.code should equal(200)
      (response.body \ "valid").extract[Boolean] should equal(false)
      (response.body \ "details" \ "error_type").extract[String] should equal("PermissivenessError")
    }
  }

  // ==================== Statistical Permissiveness Detection Tests ====================

  feature(s"Assuring that statistically too permissive ABAC rules are rejected - $VersionOfApi") {

    scenario("Creating a rule with 'emailAddress.length >= 0' should be rejected as statistically too permissive", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val createJson = CreateAbacRuleJsonV600(
        rule_name = "statistical-tautology",
        rule_code = "authenticatedUser.emailAddress.length >= 0",
        description = "Should be rejected - statistically too permissive",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))
      Then("We should get a 400")
      response.code should equal(400)
    }

    scenario("Validating a statistically too permissive rule should return PermissivenessError", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val validateJson = ValidateAbacRuleJsonV600(rule_code = "authenticatedUser.emailAddress.length >= 0")
      val request = (v6_0_0_Request / "management" / "abac-rules" / "validate").POST <@ (user1)
      val response = makePostRequest(request, write(validateJson))
      Then("We should get a 200 with valid=false and PermissivenessError type")
      response.code should equal(200)
      (response.body \ "valid").extract[Boolean] should equal(false)
      (response.body \ "details" \ "error_type").extract[String] should equal("PermissivenessError")
    }

    scenario("Creating a selective attribute-checking rule should succeed", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)
      val createJson = CreateAbacRuleJsonV600(
        rule_name = "selective-rule",
        rule_code = s"""authenticatedUser.emailAddress == "${resourceUser1.emailAddress}"""",
        description = "Selective rule that only matches one specific user",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))
      Then("We should get a 201")
      response.code should equal(201)
    }
  }
}
