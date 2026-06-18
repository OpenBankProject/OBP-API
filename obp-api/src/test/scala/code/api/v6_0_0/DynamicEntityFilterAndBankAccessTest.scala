/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.entitlement.Entitlement
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.write
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.Tag

/**
 * Characterization tests for two areas of the dynamic-entity runtime CRUD that were
 * previously uncovered, written BEFORE the Lift -> http4s migration of `Http4sDynamicEntity`
 * so they lock in the current (Lift) behaviour and act as the migration regression gate:
 *
 *  - G1: GET-all query-parameter filtering (`filterDynamicObjects`, including the
 *        `locale` (PARAM_LOCALE) exclusion) on the generic, public and community endpoints.
 *        The migration rewrites this from Lift `req.params` to http4s `req.uri.query.multiParams`.
 *  - G2: bank-level `public` / `community` access (`/banks/BANK_ID/public/...`,
 *        `/banks/BANK_ID/community/...`), which the extractors support but no test exercised.
 */
class DynamicEntityFilterAndBankAccessTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  // ==================== Helper Methods ====================

  /** name (string) + number (integer) — two fields so we can assert multi-field AND filtering. */
  def twoFieldSchema: JValue = parse(
    """
      |{
      |    "description": "Filter/bank-access characterization entity.",
      |    "required": ["name"],
      |    "properties": {
      |        "name":   { "type": "string",  "maxLength": 40, "minLength": 1, "example": "Alice" },
      |        "number": { "type": "integer", "example": 1 }
      |    }
      |}
    """.stripMargin)

  def systemEntityJson(entityName: String, extraFlags: JObject = JObject(Nil)): JValue =
    (("entity_name" -> entityName) ~ ("has_personal_entity" -> true) ~ ("schema" -> twoFieldSchema)) merge extraFlags

  def bankEntityJson(entityName: String, extraFlags: JObject = JObject(Nil)): JValue =
    (("entity_name" -> entityName) ~ ("has_personal_entity" -> true) ~ ("schema" -> twoFieldSchema)) merge extraFlags

  def createSystemEntity(entityJson: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateSystemLevelDynamicEntity.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@(user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  def deleteSystemEntity(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteSystemLevelDynamicEntity.toString)
    val deleteRequest = (v6_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@(user1)
    makeDeleteRequest(deleteRequest)
  }

  def createBankEntity(bankId: String, entityJson: JValue): (Int, JValue) = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankLevelDynamicEntity.toString)
    val request = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities").POST <@(user1)
    val response = makePostRequest(request, write(entityJson))
    (response.code, response.body)
  }

  def deleteBankEntity(bankId: String, dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteBankLevelDynamicEntity.toString)
    val deleteRequest = (v6_0_0_Request / "management" / "banks" / bankId / "dynamic-entities" / dynamicEntityId).DELETE <@(user1)
    makeDeleteRequest(deleteRequest)
  }

  def listSize(body: JValue, listName: String): Int =
    (body \ listName).asInstanceOf[JArray].arr.size

  def record(name: String, number: Int): JValue = ("name" -> name) ~ ("number" -> number)

  // ==================== G1: GET-all query-parameter filtering ====================

  feature("G1 - GET-all query parameter filtering (filterDynamicObjects)") {

    scenario("Generic /my/ GET-all filters by field value, supports multi-field AND, and excludes locale", VersionOfApi) {
      val (code, body) = createSystemEntity(systemEntityJson("test_filter_my"))
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("user1 creates three personal records via /my/test_filter_my (no role required)")
        val create = (dynamicEntity_Request / "my" / "test_filter_my").POST <@(user1)
        makePostRequest(create, write(record("Alice", 1))).code should equal(201)
        makePostRequest(create, write(record("Bob",   2))).code should equal(201)
        makePostRequest(create, write(record("Alice", 3))).code should equal(201)

        val base = (dynamicEntity_Request / "my" / "test_filter_my").GET <@(user1)

        Then("no query params returns all three")
        val all = makeGetRequest(base)
        all.code should equal(200)
        listSize(all.body, "test_filter_my_list") should equal(3)

        Then("filtering by a single field returns only matching records")
        val byName = makeGetRequest(base <<? List(("name", "Alice")))
        byName.code should equal(200)
        listSize(byName.body, "test_filter_my_list") should equal(2)

        Then("an integer field filters too")
        val byNumber = makeGetRequest(base <<? List(("number", "1")))
        byNumber.code should equal(200)
        listSize(byNumber.body, "test_filter_my_list") should equal(1)

        Then("multiple fields are combined with AND")
        val byNameAndNumber = makeGetRequest(base <<? List(("name", "Alice"), ("number", "1")))
        byNameAndNumber.code should equal(200)
        listSize(byNameAndNumber.body, "test_filter_my_list") should equal(1)

        Then("a non-matching value returns an empty list")
        val noMatch = makeGetRequest(base <<? List(("name", "Nobody")))
        noMatch.code should equal(200)
        listSize(noMatch.body, "test_filter_my_list") should equal(0)

        Then("the locale param is excluded from filtering (returns all)")
        val localeOnly = makeGetRequest(base <<? List(("locale", "en")))
        localeOnly.code should equal(200)
        listSize(localeOnly.body, "test_filter_my_list") should equal(3)

        Then("locale alongside a real filter does not affect the real filter")
        val nameWithLocale = makeGetRequest(base <<? List(("name", "Alice"), ("locale", "en")))
        nameWithLocale.code should equal(200)
        listSize(nameWithLocale.body, "test_filter_my_list") should equal(2)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("Public /public/ GET-all filters by field value", VersionOfApi) {
      val (code, body) = createSystemEntity(systemEntityJson("test_filter_public", ("has_public_access" -> true)))
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("user1 creates two non-personal records via the system endpoint")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanCreateDynamicEntity_Systemtest_filter_public")
        val create = (dynamicEntity_Request / "test_filter_public").POST <@(user1)
        makePostRequest(create, write(record("Pub1", 10))).code should equal(201)
        makePostRequest(create, write(record("Pub2", 20))).code should equal(201)

        val base = (dynamicEntity_Request / "public" / "test_filter_public").GET

        Then("anonymous GET-all returns both")
        val all = makeGetRequest(base)
        all.code should equal(200)
        listSize(all.body, "test_filter_public_list") should equal(2)

        Then("anonymous GET-all filters by field value")
        val filtered = makeGetRequest(base <<? List(("name", "Pub1")))
        filtered.code should equal(200)
        listSize(filtered.body, "test_filter_public_list") should equal(1)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }

    scenario("Community /community/ GET-all filters by field value", VersionOfApi) {
      val (code, body) = createSystemEntity(systemEntityJson("test_filter_community", ("has_community_access" -> true)))
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("user1 creates two personal records via /my/")
        val create = (dynamicEntity_Request / "my" / "test_filter_community").POST <@(user1)
        makePostRequest(create, write(record("Com1", 100))).code should equal(201)
        makePostRequest(create, write(record("Com2", 200))).code should equal(201)

        And("user1 has the CanGet role for community access")
        Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, "CanGetDynamicEntity_Systemtest_filter_community")
        val base = (dynamicEntity_Request / "community" / "test_filter_community").GET <@(user1)

        Then("GET-all returns both")
        val all = makeGetRequest(base)
        all.code should equal(200)
        listSize(all.body, "test_filter_community_list") should be >= 2

        Then("GET-all filters by field value")
        val filtered = makeGetRequest(base <<? List(("name", "Com1")))
        filtered.code should equal(200)
        listSize(filtered.body, "test_filter_community_list") should equal(1)
      } finally {
        deleteSystemEntity(dynamicEntityId)
      }
    }
  }

  // ==================== G2: bank-level public / community access ====================

  feature("G2 - bank-level public and community access") {

    scenario("Bank-level /banks/BANK_ID/public/ GET works without authentication", VersionOfApi) {
      val bankId = testBankId1.value
      val (code, body) = createBankEntity(bankId, bankEntityJson("test_bank_public", ("has_public_access" -> true)))
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        When("user1 creates a non-personal bank-level record")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, "CanCreateDynamicEntity_test_bank_public")
        val create = (dynamicEntity_Request / "banks" / bankId / "test_bank_public").POST <@(user1)
        val createResponse = makePostRequest(create, write(record("BankPub", 1)))
        createResponse.code should equal(201)
        val recordId = (createResponse.body \ "test_bank_public" \ "test_bank_public_id").extract[String]

        Then("anonymous GET-all on the bank public path returns 200")
        val all = makeGetRequest((dynamicEntity_Request / "banks" / bankId / "public" / "test_bank_public").GET)
        all.code should equal(200)
        listSize(all.body, "test_bank_public_list") should be >= 1

        Then("anonymous GET single on the bank public path returns 200")
        val one = makeGetRequest((dynamicEntity_Request / "banks" / bankId / "public" / "test_bank_public" / recordId).GET)
        one.code should equal(200)
      } finally {
        deleteBankEntity(bankId, dynamicEntityId)
      }
    }

    scenario("Bank-level /banks/BANK_ID/community/ GET requires auth then CanGet role", VersionOfApi) {
      val bankId = testBankId1.value
      val (code, body) = createBankEntity(bankId, bankEntityJson("test_bank_community", ("has_community_access" -> true)))
      code should equal(201)
      val dynamicEntityId = (body \ "dynamic_entity_id").extract[String]

      try {
        val base = dynamicEntity_Request / "banks" / bankId / "community" / "test_bank_community"

        Then("anonymous GET returns 401")
        makeGetRequest(base.GET).code should equal(401)

        Then("authenticated without the CanGet role returns 403")
        makeGetRequest(base.GET <@(user2)).code should equal(403)

        When("user2 is granted the bank-level CanGet role")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, "CanGetDynamicEntity_test_bank_community")
        Then("the GET now returns 200")
        makeGetRequest(base.GET <@(user2)).code should equal(200)
      } finally {
        deleteBankEntity(bankId, dynamicEntityId)
      }
    }
  }
}
