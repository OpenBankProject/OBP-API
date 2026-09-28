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

package code.api.v7_0_0

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.{ApiRole, DiagnosticDynamicEntityCheck}
import code.api.util.ErrorMessages._
import code.dynamicEntity.DynamicEntityProvider
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

/**
 * The v7.0.0 endpoints that manage Dynamic Entity definitions, at `/management/banks/BANK_ID/dynamic-entities`.
 *
 * The point of these endpoints is that one set of URLs serves every space, with the system space named
 * by its bank id, SYS. So the scenarios check the three things that makes true: SYS gets through to
 * the handler here while it still gets 404 wherever a real bank is needed; a Definition Role is checked
 * at the BANK_ID in the URL, so a grant at one space never covers another; and every response names
 * its space, SYS included. They also hold down three defects the older system endpoints had once
 * records moved to SYS: record counts of zero, a backup refused for want of a Role nobody could hold,
 * and every system record reported as orphaned. See DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md, phase 6.
 */
class DynamicEntityDefinitionTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.getDynamicEntityDefinitions))
  object ApiEndpoint2 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.createDynamicEntityDefinition))
  object ApiEndpoint3 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.updateDynamicEntityDefinition))
  object ApiEndpoint4 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.deleteDynamicEntityDefinition))
  object ApiEndpoint5 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.backupDynamicEntityDefinition))
  object ApiEndpoint6 extends Tag(nameOf(Http4s700DynamicEntityDefinitions.deleteDynamicEntityDefinitionCascade))

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  def v7 = baseRequest / "obp" / "v7.0.0"
  def dynamicEntityData = baseRequest / "obp" / "dynamic-entity"

  private def definitionsAt(bankId: String) = v7 / "management" / "banks" / bankId / "dynamic-entities"

  private def newEntityName(): String = "test_definition_" + UUID.randomUUID().toString.take(8).replace("-", "")

  private def definition(entityName: String): JValue =
    ("entity_name" -> entityName) ~
    ("has_personal_entity" -> false) ~
    ("schema" -> parse(
      """{"description": "Entity for the v7.0.0 definition tests.", "required": ["name"],
        | "properties": {"name": {"type": "string", "maxLength": 40, "minLength": 1, "example": "Test"}}}""".stripMargin))

  private def grant(bankId: String, role: ApiRole): Unit =
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role.toString)

  private def errorOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  /** Create a definition as user1 at `bankId`, granting the Role there first; returns its id. */
  private def createdAt(bankId: String, entityName: String): String = {
    grant(bankId, canCreateDynamicEntityDefinition)
    val response = makePostRequest(definitionsAt(bankId).POST <@ (user1), write(definition(entityName)))
    response.code should equal(201)
    (response.body \ "dynamic_entity_id").extract[String]
  }

  private def cascadeDelete(bankId: String, dynamicEntityId: String): Unit = {
    grant(bankId, canDeleteCascadeDynamicEntityDefinition)
    makeDeleteRequest((definitionsAt(bankId) / "cascade" / dynamicEntityId).DELETE <@ (user1))
  }

  feature("Dynamic Entity definitions in the system space, at BANK_ID = SYS") {

    scenario("SYS reaches the handler, and the Role is required", ApiEndpoint2, VersionOfApi) {
      When("no user is given")
      val anonymous = makePostRequest(definitionsAt(SYS).POST, write(definition(newEntityName())))
      Then("the call is unauthorised, not a 404 for an unknown bank")
      anonymous.code should equal(401)

      When("a user without the Role calls")
      val refused = makePostRequest(definitionsAt(SYS).POST <@ (user1), write(definition(newEntityName())))
      Then("the call is refused for the missing Role")
      refused.code should equal(403)
      errorOf(refused) should include(UserHasMissingRoles)
      errorOf(refused) should include(CanCreateDynamicEntityDefinition.toString)
    }

    scenario("a Role granted at a bank does not cover the system space", ApiEndpoint2, VersionOfApi) {
      Given("user2 holds the Role at a bank, and nowhere else")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser2.userId, CanCreateDynamicEntityDefinition.toString)
      When("user2 creates a definition at SYS")
      val response = makePostRequest(definitionsAt(SYS).POST <@ (user2), write(definition(newEntityName())))
      Then("the call is refused: the Role is checked at SYS, where user2 holds nothing")
      response.code should equal(403)
      errorOf(response) should include(CanCreateDynamicEntityDefinition.toString)
    }

    scenario("create, list, update, back up and delete a system level definition", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3,
      ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      val entityName = newEntityName()

      When("a definition is created at SYS")
      grant(SYS, canCreateDynamicEntityDefinition)
      val created = makePostRequest(definitionsAt(SYS).POST <@ (user1), write(definition(entityName)))
      Then("it is created, and the response names the system space")
      created.code should equal(201)
      (created.body \ "bank_id").extract[String] should equal(SYS)
      val dynamicEntityId = (created.body \ "dynamic_entity_id").extract[String]

      try {
        And("the creator can write a record, because the Record Roles were granted at SYS")
        val record = makePostRequest((dynamicEntityData / entityName).POST <@ (user1), write(("name" -> "one record"): JValue))
        record.code should equal(201)

        When("the definitions at SYS are listed")
        grant(SYS, canGetDynamicEntityDefinitions)
        val listed = makeGetRequest(definitionsAt(SYS).GET <@ (user1))
        listed.code should equal(200)
        val entry = (listed.body \ "dynamic_entities").extract[List[JObject]]
          .find(e => (e \ "entity_name").extract[String] == entityName)
          .getOrElse(fail(s"$entityName should be listed at SYS"))
        Then("the entry names the system space and counts the record stored at SYS")
        (entry \ "bank_id").extract[String] should equal(SYS)
        (entry \ "record_count").extract[Long] should equal(1L)

        And("the record is not reported as orphaned by the diagnostics")
        val definitions = DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(None, true)
        DiagnosticDynamicEntityCheck.checkOrphanedRecords(definitions).map(_.entityName) should not contain entityName

        When("the definition is updated")
        grant(SYS, canUpdateDynamicEntityDefinition)
        val updated = makePutRequest((definitionsAt(SYS) / dynamicEntityId).PUT <@ (user1), write(definition(entityName)))
        updated.code should equal(200)
        (updated.body \ "bank_id").extract[String] should equal(SYS)

        When("it is backed up")
        grant(SYS, canBackupDynamicEntityDefinition)
        val backup = makePostRequest((definitionsAt(SYS) / dynamicEntityId / "backup").POST <@ (user1), "")
        Then("the backup is made in the system space: the Record Role it needs is read at SYS")
        backup.code should equal(201)
        (backup.body \ "bank_id").extract[String] should equal(SYS)
        (backup.body \ "entity_name").extract[String] should equal(s"${entityName}_BAK")
        val backupId = (backup.body \ "dynamic_entity_id").extract[String]
        cascadeDelete(SYS, backupId)

        When("the definition is deleted while it still has a record")
        grant(SYS, canDeleteDynamicEntityDefinition)
        val refused = makeDeleteRequest((definitionsAt(SYS) / dynamicEntityId).DELETE <@ (user1))
        Then("the plain delete is refused")
        refused.code should equal(400)
        errorOf(refused) should include(DynamicEntityOperationNotAllowed)
      } finally {
        cascadeDelete(SYS, dynamicEntityId)
      }

      Then("the cascade delete removed it")
      val afterwards = makeGetRequest(definitionsAt(SYS).GET <@ (user1))
      (afterwards.body \ "dynamic_entities").extract[List[JObject]]
        .map(e => (e \ "entity_name").extract[String]) should not contain entityName
    }

    scenario("a definition in another space is not found by id", ApiEndpoint3, VersionOfApi) {
      val entityName = newEntityName()
      val dynamicEntityId = createdAt(testBankId1.value, entityName)
      try {
        grant(SYS, canUpdateDynamicEntityDefinition)
        val response = makePutRequest((definitionsAt(SYS) / dynamicEntityId).PUT <@ (user1), write(definition(entityName)))
        response.code should equal(404)
        errorOf(response) should include(DynamicEntityNotFoundByDynamicEntityId)
      } finally cascadeDelete(testBankId1.value, dynamicEntityId)
    }
  }

  feature("Dynamic Entity definitions at a bank") {

    scenario("a bank level definition names its bank, and an unknown bank is still a 404", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      val entityName = newEntityName()
      grant(testBankId1.value, canCreateDynamicEntityDefinition)
      val created = makePostRequest(definitionsAt(testBankId1.value).POST <@ (user1), write(definition(entityName)))
      created.code should equal(201)
      (created.body \ "bank_id").extract[String] should equal(testBankId1.value)
      cascadeDelete(testBankId1.value, (created.body \ "dynamic_entity_id").extract[String])

      val unknown = makePostRequest(definitionsAt("no-such-bank-" + UUID.randomUUID().toString.take(8)).POST <@ (user1),
        write(definition(newEntityName())))
      unknown.code should equal(404)
      errorOf(unknown) should include(BankNotFound)
    }
  }

  private def dataAt(bankId: String) = v7 / "banks" / bankId / "dynamic-entities"

  private def flaggedDefinition(entityName: String, flags: (String, Boolean)*): JValue =
    flags.foldLeft(definition(entityName).asInstanceOf[JObject]) { case (json, (flag, value)) =>
      JObject(json.obj.filterNot(_._1 == flag) :+ JField(flag, JBool(value)))
    }

  /** Create a definition with the given flags as user1 at `bankId`; returns its id. */
  private def createdWithFlags(bankId: String, entityName: String, flags: (String, Boolean)*): String = {
    grant(bankId, canCreateDynamicEntityDefinition)
    val response = makePostRequest(definitionsAt(bankId).POST <@ (user1), write(flaggedDefinition(entityName, flags: _*)))
    response.code should equal(201)
    (response.body \ "dynamic_entity_id").extract[String]
  }

  private def idOf(response: code.setup.APIResponse, entityName: String): String =
    (response.body \ entityName \ s"${entityName}_id").extract[String]

  feature("Dynamic Entity records at /obp/v7.0.0/banks/BANK_ID/dynamic-entities/...") {

    scenario("every URL form works in the system space, and every response names SYS", VersionOfApi) {
      val entityName = newEntityName()
      val dynamicEntityId = createdWithFlags(SYS, entityName,
        "has_personal_entity" -> true, "has_public_access" -> true, "has_community_access" -> true)
      try {
        When("a record is created, read, replaced, patched and deleted at SYS")
        val created = makePostRequest((dataAt(SYS) / entityName).POST <@ (user1), write(("name" -> "first"): JValue))
        created.code should equal(201)
        (created.body \ "bank_id").extract[String] should equal(SYS)
        val recordId = idOf(created, entityName)

        val listed = makeGetRequest((dataAt(SYS) / entityName).GET <@ (user1))
        listed.code should equal(200)
        (listed.body \ "bank_id").extract[String] should equal(SYS)
        (listed.body \ s"${entityName}_list").extract[List[JObject]].map(o => (o \ s"${entityName}_id").extract[String]) should contain(recordId)

        val one = makeGetRequest((dataAt(SYS) / entityName / recordId).GET <@ (user1))
        one.code should equal(200)
        (one.body \ "bank_id").extract[String] should equal(SYS)

        val replaced = makePutRequest((dataAt(SYS) / entityName / recordId).PUT <@ (user1), write(("name" -> "second"): JValue))
        replaced.code should equal(200)
        (replaced.body \ entityName \ "name").extract[String] should equal("second")

        val patched = makePatchRequest((dataAt(SYS) / entityName / recordId).PATCH <@ (user1), write(("name" -> "third"): JValue))
        patched.code should equal(200)
        (patched.body \ entityName \ "name").extract[String] should equal("third")

        Then("the same record is served at the unversioned URL, which still omits bank_id for the system space")
        val unversioned = makeGetRequest((dynamicEntityData / entityName / recordId).GET <@ (user1))
        unversioned.code should equal(200)
        (unversioned.body \ "bank_id") should equal(JNothing)
        (unversioned.body \ entityName \ "name").extract[String] should equal("third")

        When("the public, community and personal forms are used")
        val public = makeGetRequest((dataAt(SYS) / "public" / entityName).GET)
        public.code should equal(200)
        (public.body \ "bank_id").extract[String] should equal(SYS)

        val community = makeGetRequest((dataAt(SYS) / "community" / entityName).GET <@ (user1))
        community.code should equal(200)
        (community.body \ "bank_id").extract[String] should equal(SYS)

        val personal = makePostRequest((dataAt(SYS) / "my" / entityName).POST <@ (user1), write(("name" -> "mine"): JValue))
        personal.code should equal(201)
        (personal.body \ "bank_id").extract[String] should equal(SYS)
        val myList = makeGetRequest((dataAt(SYS) / "my" / entityName).GET <@ (user1))
        myList.code should equal(200)
        (myList.body \ s"${entityName}_list").extract[List[JObject]].map(o => (o \ s"${entityName}_id").extract[String]) should contain(idOf(personal, entityName))
        makeDeleteRequest((dataAt(SYS) / "my" / entityName / idOf(personal, entityName)).DELETE <@ (user1)).code should equal(200)

        Then("the record can be deleted at SYS")
        makeDeleteRequest((dataAt(SYS) / entityName / recordId).DELETE <@ (user1)).code should equal(200)
        makeGetRequest((dataAt(SYS) / entityName / recordId).GET <@ (user1)).code should equal(404)
      } finally cascadeDelete(SYS, dynamicEntityId)
    }

    scenario("the definition's links point at the v7.0.0 data URLs", ApiEndpoint2, VersionOfApi) {
      val entityName = newEntityName()
      grant(SYS, canCreateDynamicEntityDefinition)
      val created = makePostRequest(definitionsAt(SYS).POST <@ (user1),
        write(flaggedDefinition(entityName, "has_public_access" -> true)))
      created.code should equal(201)
      try {
        val hrefs = (created.body \ "_links" \ "related").extract[List[JObject]].map(l => (l \ "href").extract[String])
        hrefs should not be empty
        all(hrefs) should startWith(s"/obp/v7.0.0/banks/$SYS/dynamic-entities/")
      } finally cascadeDelete(SYS, (created.body \ "dynamic_entity_id").extract[String])
    }

    scenario("records of a bank level entity are served at that bank and name it", VersionOfApi) {
      val entityName = newEntityName()
      val bankId = testBankId1.value
      val dynamicEntityId = createdWithFlags(bankId, entityName)
      try {
        val created = makePostRequest((dataAt(bankId) / entityName).POST <@ (user1), write(("name" -> "at a bank"): JValue))
        created.code should equal(201)
        (created.body \ "bank_id").extract[String] should equal(bankId)

        And("the entity is not found in the system space")
        makeGetRequest((dataAt(SYS) / entityName).GET <@ (user1)).code should equal(404)
        makeDeleteRequest((dataAt(bankId) / entityName / idOf(created, entityName)).DELETE <@ (user1)).code should equal(200)
      } finally cascadeDelete(bankId, dynamicEntityId)
    }

    scenario("the row-level access list is served at SYS", VersionOfApi) {
      val entityName = newEntityName()
      val dynamicEntityId = createdWithFlags(SYS, entityName, "use_row_level_access" -> true)
      try {
        val created = makePostRequest((dataAt(SYS) / entityName).POST <@ (user1), write(("name" -> "shared"): JValue))
        created.code should equal(201)
        val access = makeGetRequest((dataAt(SYS) / entityName / idOf(created, entityName) / "access").GET <@ (user1))
        access.code should equal(200)
      } finally cascadeDelete(SYS, dynamicEntityId)
    }

    scenario("an entity that does not exist is a 404", VersionOfApi) {
      makeGetRequest((dataAt(SYS) / ("no_such_entity_" + UUID.randomUUID().toString.take(8).replace("-", ""))).GET <@ (user1)).code should equal(404)
    }
  }

  feature("SYS is only a space where an endpoint says so") {

    scenario("an endpoint that needs a real bank still answers 404 for SYS", VersionOfApi) {
      grant(SYS, canGetDynamicEntityDefinitions)
      When("the v4.0.0 bank level endpoint, which has not opted in, is called at SYS")
      val response = makeGetRequest((baseRequest / "obp" / "v4.0.0" / "management" / "banks" / SYS / "dynamic-entities").GET <@ (user1))
      Then("SYS is looked up as a bank and not found")
      response.code should equal(404)
      errorOf(response) should include(BankNotFound)
    }
  }

  feature("v7.0.0 resource docs of a Dynamic Entity's endpoints") {

    /** The OBPv7.0.0 listing as the API Explorer asks for it, narrowed to the given partial functions. */
    def v7Docs(functions: List[String]): List[JValue] = {
      val response = makeGetRequest((v7 / "resource-docs" / "OBPv7.0.0" / "obp") <<? List(("functions", functions.mkString(","))))
      response.code should equal(200)
      (response.body \ "resource_docs").children
    }

    def v4Docs(functions: List[String]): List[JValue] = {
      val response = makeGetRequest((v7 / "resource-docs" / "OBPv6.0.0" / "obp") <<? List(("functions", functions.mkString(","))))
      response.code should equal(200)
      (response.body \ "resource_docs").children
    }

    def operationIds(docs: List[JValue]): List[String] = docs.map(d => (d \ "operation_id").extract[String])

    scenario("the OBPv7.0.0 listing documents records at their v7.0.0 URLs, in the system space and at a bank", VersionOfApi) {
      val entityName = newEntityName()
      val bankId = testBankId1.value
      val systemEntityId = createdWithFlags(SYS, entityName, "use_row_level_access" -> true)
      val bankEntityId = createdWithFlags(bankId, entityName)
      try {
        val functions = List(
          s"dynamicEntity_create${entityName}_",
          s"dynamicEntity_get${entityName}List_$bankId",
          s"dynamicEntity_grant${entityName}RowAccess_")
        val docs = v7Docs(functions)
        def docWithId(id: String): JValue =
          docs.find(d => (d \ "operation_id").extract[String] == id).getOrElse(fail(s"no resource doc $id in ${operationIds(docs)}"))

        Then("a system entity's docs have v7.0.0 ids and name SYS in the URL and in the example response")
        val create = docWithId(s"OBPv7.0.0-dynamicEntity_create${entityName}_")
        (create \ "request_verb").extract[String] should equal("POST")
        (create \ "request_url").extract[String] should equal(s"/banks/$SYS/dynamic-entities/$entityName")
        (create \ "specified_url").extract[String] should equal(s"/obp/v7.0.0/banks/$SYS/dynamic-entities/$entityName")
        (create \ "implemented_by" \ "version").extract[String] should equal("OBPv7.0.0")
        (create \ "success_response_body" \ "bank_id").extract[String] should equal(SYS)

        And("the row-level access grant is documented as the PUT it is served as")
        val grantAccess = docWithId(s"OBPv7.0.0-dynamicEntity_grant${entityName}RowAccess_")
        (grantAccess \ "request_verb").extract[String] should equal("PUT")
        (grantAccess \ "request_url").extract[String] should equal(s"/banks/$SYS/dynamic-entities/$entityName/${entityName.toUpperCase}_ID/access")

        And("a bank level entity's docs name its bank")
        val list = docWithId(s"OBPv7.0.0-dynamicEntity_get${entityName}List_$bankId")
        (list \ "request_url").extract[String] should equal(s"/banks/$bankId/dynamic-entities/$entityName")
        (list \ "success_response_body" \ "bank_id").extract[String] should equal(bankId)

        And("the v7.0.0 listing does not also carry the unversioned docs")
        operationIds(docs).filter(_.startsWith("OBPv4.0.0-dynamicEntity_")) shouldBe empty

        Then("an older version's listing keeps the unversioned docs and their v4.0.0 ids")
        val olderDocs = v4Docs(functions)
        operationIds(olderDocs) should contain(s"OBPv4.0.0-dynamicEntity_create${entityName}_")
        operationIds(olderDocs).filter(_.startsWith("OBPv7.0.0-")) shouldBe empty
        val olderCreate = olderDocs.find(d => (d \ "operation_id").extract[String] == s"OBPv4.0.0-dynamicEntity_create${entityName}_").get
        (olderCreate \ "specified_url").extract[String] should equal(s"/obp/dynamic-entity/$entityName")
        (olderCreate \ "success_response_body" \ "bank_id") should equal(JNothing)

        And("an older listing's bank level example names the entity's bank as bank_id, as the response does")
        val olderList = olderDocs.find(d => (d \ "operation_id").extract[String] == s"OBPv4.0.0-dynamicEntity_get${entityName}List_$bankId")
          .getOrElse(fail(s"no v4.0.0 list doc in ${operationIds(olderDocs)}"))
        (olderList \ "success_response_body" \ "bank_id").extract[String] should equal(bankId)
        (olderList \ "success_response_body" \ "bank-id") should equal(JNothing)
      } finally {
        cascadeDelete(SYS, systemEntityId)
        cascadeDelete(bankId, bankEntityId)
      }
    }
  }
}
