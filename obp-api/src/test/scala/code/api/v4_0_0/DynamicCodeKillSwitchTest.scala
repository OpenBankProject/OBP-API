/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
 */
package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import org.json4s.jvalue2extractable
import org.json4s.jvalue2monadic
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.DynamicCodeExecutionDisabled
import code.api.util.{ApiRole, DynamicUtil}
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.entitlement.Entitlement
import code.setup.{EnvVarOverride, OBPReq}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Failure, Full}
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * Covers the RCE kill-switch (allow_user_generated_scala_code) added around
 * code.api.util.DynamicUtil.dynamicCodeExecutionEnabled. Exercises the predicate
 * directly and each of the three http4s chokepoints it guards (connector methods,
 * dynamic resource docs, ABAC rules), plus a regression check that Dynamic Entities
 * (which never execute user code) are unaffected when the switch is off.
 */
class DynamicCodeKillSwitchTest extends V400ServerSetup with EnvVarOverride {

  def v6_0_0_Request: OBPReq = baseRequest / "obp" / "v6.0.0"

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  override def beforeEach(): Unit = {
    super.beforeEach()
    setPropsValues("starConnector_supported_types" -> "mapped,internal")
    setPropsValues("connector" -> "star")
  }

  // The predicate defaults to false everywhere (including test/dev) with no run-mode
  // fallback — it is false unless allow_user_generated_scala_code is explicitly set. This
  // suite's baseline test.default.props sets it to true explicitly so the ON scenarios
  // below can compile/execute dynamic code; there is no way, within this harness, to
  // exercise the truly-absent case, since the base props file always supplies a value once
  // set. The "absent -> false" branch is what protects deployers who never set the prop at
  // all — it's covered by direct inspection of DynamicUtil.dynamicCodeExecutionEnabled's
  // match expression, not by an integration test.
  Feature("DynamicUtil.dynamicCodeExecutionEnabled predicate") {

    Scenario("Explicit prop=true (this suite's baseline) enables compilation", VersionOfApi) {
      Then("the predicate should be true given the explicit test-props value")
      DynamicUtil.dynamicCodeExecutionEnabled should be(true)

      And("compileScalaCode should compile and execute")
      val result = DynamicUtil.compileScalaCode[Int]("41 + 1")
      result should be(Full(42))
    }

    // run_tests_parallel.sh exports OBP_ALLOW_USER_GENERATED_SCALA_CODE=true for every shard
    // (mirroring CI's allow_user_generated_scala_code=true default), and that env var always
    // wins over setPropsValues (see APIUtil.getPropsValue). withEnvOverride forces the env var
    // out of the way for the scope of this scenario so the "false" prop actually takes effect.
    Scenario("Explicit prop=false disables compilation regardless of run mode", VersionOfApi) {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues("allow_user_generated_scala_code" -> "false")

        Then("the predicate should be false")
        DynamicUtil.dynamicCodeExecutionEnabled should be(false)

        And("compileScalaCode should refuse to compile/execute and return the kill-switch Failure")
        val result = DynamicUtil.compileScalaCode[Int]("41 + 1")
        result should be(Failure(DynamicCodeExecutionDisabled))
      }
    }

    Scenario("A later explicit prop=true re-enables after being forced off", VersionOfApi) {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues("allow_user_generated_scala_code" -> "false")
        DynamicUtil.dynamicCodeExecutionEnabled should be(false)
      }

      setPropsValues("allow_user_generated_scala_code" -> "true")

      Then("the predicate should be true again")
      DynamicUtil.dynamicCodeExecutionEnabled should be(true)
    }
  }

  Feature("Connector Methods endpoint respects the kill-switch") {

    Scenario("OFF: create connector method returns 400 with the kill-switch error, nothing persisted", VersionOfApi) {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues("allow_user_generated_scala_code" -> "false")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateConnectorMethod.toString)

        val countBefore = ConnectorMethodProvider.provider.vend.getAll().size

        val request = (v4_0_0_Request / "management" / "connector-methods").POST <@ (user1)
        lazy val postConnectorMethod = SwaggerDefinitionsJSON.jsonScalaConnectorMethod

        val response = makePostRequest(request, write(postConnectorMethod))

        Then("We should get a 400, not a 500 and not a 201")
        response.code should equal(400)
        response.body.extract[ErrorMessage].message should equal(DynamicCodeExecutionDisabled)

        And("nothing should have been persisted")
        ConnectorMethodProvider.provider.vend.getAll().size should equal(countBefore)
      }
    }

    Scenario("ON: create connector method returns 201 and is persisted", VersionOfApi) {
      setPropsValues("allow_user_generated_scala_code" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateConnectorMethod.toString)

      val countBefore = ConnectorMethodProvider.provider.vend.getAll().size

      val request = (v4_0_0_Request / "management" / "connector-methods").POST <@ (user1)
      lazy val postConnectorMethod = SwaggerDefinitionsJSON.jsonScalaConnectorMethod

      val response = makePostRequest(request, write(postConnectorMethod))

      Then("We should get a 201")
      response.code should equal(201)
      val connectorMethod = response.body.extract[JsonConnectorMethod]
      connectorMethod.connectorMethodId shouldNot be(null)

      And("it should be persisted")
      ConnectorMethodProvider.provider.vend.getAll().size should equal(countBefore + 1)
    }
  }

  Feature("Dynamic Resource Doc endpoint respects the kill-switch") {

    Scenario("OFF: create dynamic resource doc returns 400 with the kill-switch error", VersionOfApi) {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues("allow_user_generated_scala_code" -> "false")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

        val request = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
        lazy val postDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(dynamicResourceDocId = None)

        val response = makePostRequest(request, write(postDynamicResourceDoc))

        Then("We should get a 400, not a 500 and not a 201")
        response.code should equal(400)
        response.body.extract[ErrorMessage].message should equal(DynamicCodeExecutionDisabled)
      }
    }

    Scenario("ON: create dynamic resource doc returns 201", VersionOfApi) {
      setPropsValues("allow_user_generated_scala_code" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val request = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      lazy val postDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(dynamicResourceDocId = None)

      val response = makePostRequest(request, write(postDynamicResourceDoc))

      Then("We should get a 201")
      response.code should equal(201)
      val dynamicResourceDoc = response.body.extract[JsonDynamicResourceDoc]
      dynamicResourceDoc.dynamicResourceDocId shouldNot be(null)
    }
  }

  Feature("ABAC Rule endpoint respects the kill-switch") {

    Scenario("OFF: create ABAC rule returns 400 with the kill-switch error", VersionOfApi) {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues("allow_user_generated_scala_code" -> "false")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)

        val createJson = code.api.v6_0_0.CreateAbacRuleJsonV600(
          rule_name = "kill-switch-off-test",
          rule_code = "true",
          description = "should not compile",
          policy = "account-access",
          is_active = true
        )
        val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
        val response = makePostRequest(request, write(createJson))

        Then("We should get a 400, not a 500 and not a 201")
        response.code should equal(400)
        response.body.extract[ErrorMessage].message should equal(DynamicCodeExecutionDisabled)
      }
    }

    Scenario("ON: create ABAC rule returns 201", VersionOfApi) {
      setPropsValues("allow_user_generated_scala_code" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateAbacRule.toString)

      // "false" (deny-all) is used rather than "true" because the ABAC engine rejects
      // "true" as a tautology (see AbacRuleTests "Tautology Detection" scenarios) —
      // that would fail this scenario for a reason unrelated to the kill-switch.
      val createJson = code.api.v6_0_0.CreateAbacRuleJsonV600(
        rule_name = "kill-switch-on-test",
        rule_code = "false",
        description = "should compile",
        policy = "account-access",
        is_active = true
      )
      val request = (v6_0_0_Request / "management" / "abac-rules").POST <@ (user1)
      val response = makePostRequest(request, write(createJson))

      Then("We should get a 201")
      response.code should equal(201)
      (response.body \ "abac_rule_id").extract[String] shouldNot be("")
    }
  }

  Feature("Dynamic Entities are unaffected by the kill-switch (no over-reach)") {

    Scenario("OFF: create Dynamic Entity still succeeds because it never compiles user code", VersionOfApi) {
      setPropsValues("allow_user_generated_scala_code" -> "false")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)

      val entityJson = org.json4s.native.JsonMethods.parse(
        """
          |{
          |    "entity_name": "kill_switch_regression_entity",
          |    "has_personal_entity": true,
          |    "schema": {
          |       "description": "regression entity for the dynamic code kill-switch",
          |        "required": ["name"],
          |        "properties": {
          |            "name": {
          |                "type": "string",
          |                "example": "James Brown"
          |            }
          |        }
          |    }
          |}
          |""".stripMargin)

      val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)
      val response = makePostRequest(request, write(entityJson))

      Then("We should still get a 201 — Dynamic Entities do not execute user code")
      response.code should equal(201)

      val dynamicEntityId = (response.body \ "dynamic_entity_id").extract[String]

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
      val deleteRequest = (v4_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@ (user1)
      makeDeleteRequest(deleteRequest)
    }
  }

}
