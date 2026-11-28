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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canCreateDynamicEntityAuditChain, canDeleteCascadeDynamicEntityDefinition, canDeleteDynamicEntityDefinition, canGetDynamicEntityAuditChain, canGetDynamicEntityAuditRootChain, canSealDynamicEntityAuditRootChain}
import code.api.util.DoobieUtil
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.DynamicData.DynamicDataProvider
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.dynamicentityauditchain.{DynamicEntityAuditChain, DynamicEntityAuditChains, DynamicEntityAuditEntry}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import doobie.Fragment
import doobie.implicits._
import org.json4s.JsonAST._
import org.json4s.{jvalue2extractable, jvalue2monadic}
import org.scalatest.Tag

/**
 * This suite checks Dynamic Entity audit chains and the root audit chain that ties them together: starting one (the Roles, the STARTED and EXISTING entries, and that it starts only once), the entry each record write adds, that every hash can be worked out again from the entries' JSON alone by the rules in the Glossary, that the check finds records and entries changed behind OBP-API's back, that an entity with a chain cannot be deleted or renamed, that a chain is bound to its entity, and that the root chain finds a chain moved, rewound, deleted or added in the database.
 */
class DynamicEntityAuditChainsTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("startDynamicEntityAuditChain")
  object ApiEndpoint2 extends Tag("getDynamicEntityAuditChain")
  object ApiEndpoint3 extends Tag("getDynamicEntityAuditChainEntries")
  object ApiEndpoint4 extends Tag("checkDynamicEntityAuditChain")
  object ApiEndpoint5 extends Tag("getDynamicEntityAuditRootChain")
  object ApiEndpoint6 extends Tag("getDynamicEntityAuditRootChainEntries")
  object ApiEndpoint7 extends Tag("sealDynamicEntityAuditRootChain")
  object ApiEndpoint8 extends Tag("checkDynamicEntityAuditRootChain")

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  private val owner = "dynamic-entity-audit-chain-owner"
  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def chainOf(dynamicEntityId: String) = v7 / "management" / "banks" / SYS / "dynamic-entities" / dynamicEntityId / "audit-chain"

  private def root = v7 / "management" / "dynamic-entity-audit-chains" / "root"

  private def message(response: code.setup.APIResponse) = response.body.extract[ErrorMessage].message

  // Changes made behind OBP-API's back are written straight to the tables, as a person with access to the
  // database would.
  private def runSql(statement: Fragment): Unit = {
    DoobieUtil.runUpdate(statement.update.run)
    ()
  }

  /** The stored JSON of a record, read straight from the dynamicdata table. */
  private def recordJsonOf(entityName: String, recordId: String): String =
    DoobieUtil.runQuery(
      sql"""SELECT datajson FROM dynamicdata
            WHERE dynamicentityname = ${Option(entityName)} AND dynamicdataid = ${Option(recordId)}
            ORDER BY id ASC""".query[Option[String]].to[List])
      .headOption.getOrElse(throw new IllegalStateException("the record")).orNull

  private def setRecordJson(entityName: String, recordId: String, dataJson: String): Unit =
    runSql(sql"""UPDATE dynamicdata SET datajson = ${Option(dataJson)}
                 WHERE dynamicentityname = ${Option(entityName)} AND dynamicdataid = ${Option(recordId)}""")

  private def deleteRecord(entityName: String, recordId: String): Unit =
    runSql(sql"""DELETE FROM dynamicdata
                 WHERE dynamicentityname = ${Option(entityName)} AND dynamicdataid = ${Option(recordId)}""")

  private def entryOf(chainId: String, sequence: Long): DynamicEntityAuditEntry =
    DoobieUtil.runQuery(DynamicEntityAuditEntry.findIO(
      fr"WHERE chain_id = ${Option(chainId)} AND sequence = $sequence"))
      .headOption.getOrElse(throw new IllegalStateException(s"entry $sequence"))

  private def deleteEntry(chainId: String, sequence: Long): Unit =
    runSql(sql"DELETE FROM dynamic_entity_audit_entry WHERE chain_id = ${Option(chainId)} AND sequence = $sequence")

  private def grantChainRoles(): Unit =
    List(canCreateDynamicEntityAuditChain, canGetDynamicEntityAuditChain)
      .foreach(role => Entitlement.entitlement.vend.addEntitlement(SYS, resourceUser1.userId, role.toString))

  /** A system level Dynamic Entity with a string field and a number field; its id. */
  private def entityIn(entityName: String): String =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
      entityName,
      s"""{"$entityName":{"properties":{
         |"outcome":{"type":"string","example":"pass"},
         |"score":{"type":"number","example":"10.5"}}}}""".stripMargin,
      None, owner, None, hasPersonalEntity = false)).openOrThrowException("definition").dynamicEntityId.get

  private def record(entityName: String, recordId: String, outcome: String): JObject =
    JObject(List(JField(s"${entityName}_id", JString(recordId)), JField("outcome", JString(outcome)), JField("score", JDouble(10.5))))

  private def save(entityName: String, recordId: String, outcome: String): Unit =
    DynamicDataProvider.connectorMethodProvider.vend.save(None, entityName, record(entityName, recordId, outcome), Some(resourceUser1.userId), false)
      .openOrThrowException("save the record")

  private def update(entityName: String, recordId: String, outcome: String): Unit =
    DynamicDataProvider.connectorMethodProvider.vend.update(None, entityName, record(entityName, recordId, outcome), recordId, Some(resourceUser1.userId), false)
      .openOrThrowException("update the record")

  private def delete(entityName: String, recordId: String): Unit =
    DynamicDataProvider.connectorMethodProvider.vend.delete(None, entityName, recordId, Some(resourceUser1.userId), false)
      .openOrThrowException("delete the record") should equal(true)

  private def entriesOf(dynamicEntityId: String): List[JValue] = {
    val response = makeGetRequest((chainOf(dynamicEntityId) / "entries").GET <@ (user1))
    withClue(response.body) { response.code should equal(200) }
    (response.body \ "entries").children
  }

  private def check(dynamicEntityId: String): JValue = {
    val response = makeGetRequest((chainOf(dynamicEntityId) / "check").GET <@ (user1))
    withClue(response.body) { response.code should equal(200) }
    response.body
  }

  private def problemsIn(checked: JValue): List[String] = (checked \ "problems").children.map(problem => (problem \ "problem").extract[String])

  private def sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)).map(byte => f"$byte%02x").mkString

  /** An entry's hash worked out from its JSON alone, by the rules in the Glossary item "Dynamic Entity Audit Chains". */
  private def hashFromJson(chainId: String, entry: JValue): String = {
    def text(name: String) = (entry \ name).extract[String]
    sha256(List("OBP-DE-AUDIT-1", chainId, (entry \ "sequence").extract[Long].toString, text("operation"), text("record_id"),
      text("owner_user_id"), text("data_hash"), text("user_id"), text("on_behalf_of_user_id"),
      (entry \ "recorded_at_epoch_millis").extract[Long].toString, text("previous_hash")).mkString("\n"))
  }

  /** An entity with one record, and its chain started over the API; the entity's id and the chain's id. */
  private def auditedEntityWithOneRecord(entityName: String, recordId: String): (String, String) = {
    val entityId = entityIn(entityName)
    save(entityName, recordId, "pending")
    val started = makePostRequest(chainOf(entityId).POST <@ (user1), "")
    withClue(started.body) { started.code should equal(201) }
    (entityId, (started.body \ "chain_id").extract[String])
  }

  feature("Starting and reading a Dynamic Entity audit chain") {

    scenario("the Roles, the first entries, and starting only once", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      val entityName = s"assessment_$suffix"
      val entityId = entityIn(entityName)
      val recordId = java.util.UUID.randomUUID().toString
      save(entityName, recordId, "pending")

      When("an anonymous caller starts the chain")
      makePostRequest(chainOf(entityId).POST, "").code should equal(401)

      When("user1 starts it without the Role")
      val refused = makePostRequest(chainOf(entityId).POST <@ (user1), "")
      refused.code should equal(403)
      message(refused) should include(UserHasMissingRoles)

      grantChainRoles()

      Then("an entity without a chain has none to read")
      val missing = makeGetRequest(chainOf(entityId).GET <@ (user1))
      missing.code should equal(404)
      message(missing) should include(DynamicEntityAuditChainNotStarted)

      When("user1 starts it with the Role at SYS")
      val started = makePostRequest(chainOf(entityId).POST <@ (user1), "")
      withClue(started.body) { started.code should equal(201) }
      (started.body \ "entity_name").extract[String] should equal(entityName)
      (started.body \ "dynamic_entity_id").extract[String] should equal(entityId)
      (started.body \ "bank_id").extract[String] should equal(SYS)
      (started.body \ "hash_format").extract[String] should equal("OBP-DE-AUDIT-1")
      (started.body \ "started_by_user_id").extract[String] should equal(resourceUser1.userId)
      (started.body \ "last_sequence").extract[Long] should equal(1L)

      Then("the chain starts with STARTED, then the record that already existed")
      val entries = entriesOf(entityId)
      entries.map(entry => (entry \ "operation").extract[String]) should equal(List("STARTED", "EXISTING"))
      (entries(1) \ "record_id").extract[String] should equal(recordId)
      (entries(1) \ "data" \ "outcome").extract[String] should equal("pending")

      And("reading the chain gives the last entry's hash")
      val read = makeGetRequest(chainOf(entityId).GET <@ (user1))
      read.code should equal(200)
      (read.body \ "last_hash").extract[String] should equal((entries.last \ "hash").extract[String])

      When("it is started again")
      val again = makePostRequest(chainOf(entityId).POST <@ (user1), "")
      again.code should equal(409)
      message(again) should include(DynamicEntityAuditChainAlreadyStarted)
    }

    scenario("the query parameters of the entries are checked", ApiEndpoint3, VersionOfApi) {
      grantChainRoles()
      val (entityId, _) = auditedEntityWithOneRecord(s"paged_$suffix", java.util.UUID.randomUUID().toString)
      makeGetRequest((chainOf(entityId) / "entries").GET <@ (user1) <<? List("limit" -> "0")).code should equal(400)
      makeGetRequest((chainOf(entityId) / "entries").GET <@ (user1) <<? List("from_sequence" -> "-1")).code should equal(400)
      val page = makeGetRequest((chainOf(entityId) / "entries").GET <@ (user1) <<? List("from_sequence" -> "1", "limit" -> "1"))
      withClue(page.body) { page.code should equal(200) }
      (page.body \ "entries").children.map(entry => (entry \ "sequence").extract[Long]) should equal(List(1L))
    }
  }

  feature("Record writes add entries, and the hashes can be checked without OBP") {

    scenario("create, update and delete each add one entry, linked by hash", ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      grantChainRoles()
      val entityName = s"decision_$suffix"
      val firstId = java.util.UUID.randomUUID().toString
      val (entityId, chainId) = auditedEntityWithOneRecord(entityName, firstId)
      val secondId = java.util.UUID.randomUUID().toString

      When("records are created, updated and deleted through the provider")
      save(entityName, secondId, "pending")
      update(entityName, secondId, "pass")
      delete(entityName, firstId)

      Then("each change has its own entry, in order")
      val entries = entriesOf(entityId)
      entries.map(entry => (entry \ "operation").extract[String]) should equal(List("STARTED", "EXISTING", "CREATED", "UPDATED", "DELETED"))
      entries.map(entry => (entry \ "sequence").extract[Long]) should equal(List(0L, 1L, 2L, 3L, 4L))
      (entries(3) \ "data" \ "outcome").extract[String] should equal("pass")
      (entries(3) \ "user_id").extract[String] should equal(resourceUser1.userId)
      (entries(4) \ "data") should equal(JNothing)

      And("every hash can be worked out from the JSON alone, and each entry points back to the one before")
      (entries.head \ "previous_hash").extract[String] should equal("0" * 64)
      entries.foreach { entry =>
        (entry \ "hash").extract[String] should equal(hashFromJson(chainId, entry))
        (entry \ "data_json").extractOpt[String].foreach(stored => (entry \ "data_hash").extract[String] should equal(sha256(stored)))
      }
      entries.sliding(2).foreach { case List(before, after) =>
        (after \ "previous_hash").extract[String] should equal((before \ "hash").extract[String])
      }

      And("one record's history can be read on its own")
      val history = makeGetRequest((chainOf(entityId) / "entries").GET <@ (user1) <<? List("record_id" -> secondId))
      (history.body \ "entries").children.map(entry => (entry \ "operation").extract[String]) should equal(List("CREATED", "UPDATED"))

      And("the check finds nothing wrong")
      val checked = check(entityId)
      withClue(checked) { (checked \ "is_intact").extract[Boolean] should equal(true) }
      (checked \ "entries_checked").extract[Long] should equal(5L)
      (checked \ "records_checked").extract[Long] should equal(1L)
      (checked \ "last_hash").extract[String] should equal((entries.last \ "hash").extract[String])
    }

    scenario("an entity without a chain gets no entries") {
      val entityName = s"plain_$suffix"
      entityIn(entityName)
      val before = DynamicEntityAuditEntry.count
      save(entityName, java.util.UUID.randomUUID().toString, "pending")
      DynamicEntityAuditEntry.count should equal(before)
    }
  }

  feature("The check finds changes made behind OBP-API's back") {

    scenario("a record changed, added or deleted in the database", ApiEndpoint4, VersionOfApi) {
      grantChainRoles()
      val entityName = s"tampered_record_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, _) = auditedEntityWithOneRecord(entityName, recordId)
      val recordJson = recordJsonOf(entityName, recordId)

      When("the record's data is changed directly")
      setRecordJson(entityName, recordId, recordJson.replace("pending", "pass"))
      Then("the check says it was changed without going through OBP-API")
      val changed = check(entityId)
      (changed \ "is_intact").extract[Boolean] should equal(false)
      problemsIn(changed).exists(_.contains("does not match its latest entry")) should equal(true)

      When("a record is added directly")
      runSql(sql"""INSERT INTO dynamicdata (dynamicdataid, dynamicentityname, bankid, datajson, ispersonalentity)
                   VALUES (${Option("smuggled")}, ${Option(entityName)}, ${Option(SYS)}, ${Option("{}")}, ${false})""")
      problemsIn(check(entityId)).exists(_.contains("has no entry in the chain")) should equal(true)

      When("the original record is deleted directly")
      deleteRecord(entityName, recordId)
      problemsIn(check(entityId)).exists(_.contains("it was deleted without going through OBP-API")) should equal(true)
    }

    scenario("an entry changed or removed in the database", ApiEndpoint4, VersionOfApi) {
      grantChainRoles()
      val entityName = s"tampered_entry_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, chainId) = auditedEntityWithOneRecord(entityName, recordId)
      update(entityName, recordId, "pass")
      update(entityName, recordId, "fail")
      (check(entityId) \ "is_intact").extract[Boolean] should equal(true)

      def entry(sequence: Long) = entryOf(chainId, sequence)

      When("an entry's data is changed directly")
      val second = entry(2)
      val original = second.dataJson.get
      val changedJson = original.replace("pass", "fail")
      runSql(sql"""UPDATE dynamic_entity_audit_entry SET data_json = ${Option(changedJson)}
                   WHERE chain_id = ${Option(chainId)} AND sequence = ${second.sequence}""")
      val dataChanged = check(entityId)
      (dataChanged \ "is_intact").extract[Boolean] should equal(false)
      problemsIn(dataChanged).exists(_.contains("Its data does not match its data_hash")) should equal(true)

      When("its data hash is changed to match, too")
      runSql(sql"""UPDATE dynamic_entity_audit_entry SET data_hash = ${Option(sha256(changedJson))}
                   WHERE chain_id = ${Option(chainId)} AND sequence = ${second.sequence}""")
      problemsIn(check(entityId)).exists(_.contains("Its hash is not the hash of its contents")) should equal(true)

      When("the entry is removed instead")
      deleteEntry(chainId, second.sequence)
      problemsIn(check(entityId)).exists(_.contains("an entry is missing or out of place")) should equal(true)
    }
  }

  feature("A chain cannot be switched off") {

    scenario("an entity with a chain cannot be deleted or renamed", VersionOfApi) {
      grantChainRoles()
      List(canDeleteDynamicEntityDefinition, canDeleteCascadeDynamicEntityDefinition)
        .foreach(role => Entitlement.entitlement.vend.addEntitlement(SYS, resourceUser1.userId, role.toString))
      val entityName = s"kept_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, _) = auditedEntityWithOneRecord(entityName, recordId)
      delete(entityName, recordId)

      When("its definition is deleted over the API")
      val deleted = makeDeleteRequest((v7 / "management" / "banks" / SYS / "dynamic-entities" / entityId).DELETE <@ (user1))
      deleted.code should equal(400)
      message(deleted) should include(DynamicEntityHasAuditChain)

      When("it is deleted with the cascade")
      val cascaded = makeDeleteRequest((v7 / "management" / "banks" / SYS / "dynamic-entities" / "cascade" / entityId).DELETE <@ (user1))
      cascaded.code should equal(400)
      message(cascaded) should include(DynamicEntityHasAuditChain)

      When("the provider is asked to delete it")
      val entity = DynamicEntityProvider.connectorMethodProvider.vend.getById(None, entityId).openOrThrowException("the entity")
      DynamicEntityProvider.connectorMethodProvider.vend.delete(entity).isDefined should equal(false)

      When("it is renamed")
      val renamed = DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
        s"renamed_$suffix", entity.metadataJson.replace(entityName, s"renamed_$suffix"), Some(entityId), owner, None, hasPersonalEntity = false))
      renamed.isDefined should equal(false)

      Then("the entity and its chain are still there, and the chain is intact")
      DynamicEntityProvider.connectorMethodProvider.vend.getById(None, entityId).isDefined should equal(true)
      (check(entityId) \ "is_intact").extract[Boolean] should equal(true)
      DynamicEntityAuditChains.hasChain(entityId) should equal(true)
    }
  }

  private def grantRootRoles(): Unit =
    List(canGetDynamicEntityAuditRootChain, canSealDynamicEntityAuditRootChain)
      .foreach(role => Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString))

  private def chainRow(chainId: String): DynamicEntityAuditChain =
    DoobieUtil.runQuery(DynamicEntityAuditChain.findOneIO(fr"WHERE chain_id = ${Option(chainId)}"))
      .getOrElse(throw new IllegalStateException(s"the chain $chainId"))

  private def checkRoot(includeChains: Boolean = false): JValue = {
    val response = makeGetRequest((root / "check").GET <@ (user1) <<? List("include_chains" -> includeChains.toString))
    withClue(response.body) { response.code should equal(200) }
    response.body
  }

  /** The problems a root check found about one entity chain. */
  private def problemsAbout(checked: JValue, chainId: String): List[String] =
    (checked \ "problems").children.filter { problem =>
      (problem \ "record_id").extractOpt[String].contains(chainId) || (problem \ "chain_id").extractOpt[String].contains(chainId)
    }.map(problem => (problem \ "problem").extract[String])

  private def seal(): JValue = {
    val sealResponse = makePostRequest((root / "seal").POST <@ (user1), "")
    withClue(sealResponse.body) { sealResponse.code should equal(201) }
    sealResponse.body
  }

  feature("Each chain is bound to its entity, and the root chain ties the chains together") {

    scenario("the root chain's Roles, its entries, and sealing", ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      grantChainRoles()
      makeGetRequest(root.GET).code should equal(401)
      val refused = makeGetRequest(root.GET <@ (user1))
      refused.code should equal(403)
      message(refused) should include(UserHasMissingRoles)
      makePostRequest((root / "seal").POST <@ (user1), "").code should equal(403)
      grantRootRoles()

      val entityName = s"rooted_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, chainId) = auditedEntityWithOneRecord(entityName, recordId)

      Then("the chain's first entry names its entity")
      (entriesOf(entityId).head \ "data") should equal(JObject(List(
        JField("bank_id", JString(SYS)), JField("dynamic_entity_id", JString(entityId)), JField("entity_name", JString(entityName)))))

      And("the root chain registered it, with its first entry's hash and its last entry")
      val rootChain = makeGetRequest(root.GET <@ (user1))
      withClue(rootChain.body) { rootChain.code should equal(200) }
      (rootChain.body \ "chain_id").extract[String] should equal("root")
      val registered = makeGetRequest((root / "entries").GET <@ (user1) <<? List("chain_id" -> chainId))
      val registration = (registered.body \ "entries").children
      registration.map(entry => (entry \ "operation").extract[String]) should equal(List("CHAIN_STARTED"))
      (registration.head \ "data" \ "entity_name").extract[String] should equal(entityName)
      (registration.head \ "data" \ "last_sequence").extract[Long] should equal(1L)
      (registration.head \ "hash").extract[String] should equal(hashFromJson("root", registration.head))

      When("a record changes and the root chain is sealed")
      update(entityName, recordId, "pass")
      val sealResponse = seal()
      (sealResponse \ "chains_sealed").extract[Int] should be >= 1
      (sealResponse \ "root_chain" \ "last_hash").extract[String] should equal(
        (makeGetRequest(root.GET <@ (user1)).body \ "last_hash").extract[String])
      Then("the root chain records the chain's new last entry")
      val heads = (makeGetRequest((root / "entries").GET <@ (user1) <<? List("chain_id" -> chainId)).body \ "entries").children
      heads.map(entry => (entry \ "operation").extract[String]) should equal(List("CHAIN_STARTED", "CHAIN_HEAD"))
      (heads.last \ "data" \ "last_sequence").extract[Long] should equal(2L)

      When("it is sealed again with nothing changed")
      (seal() \ "chains_sealed").extract[Int] should equal(0)

      And("the root chain finds nothing wrong with this chain")
      problemsAbout(checkRoot(includeChains = true), chainId) should equal(Nil)
    }

    scenario("a chain moved to another entity in the database", ApiEndpoint4, ApiEndpoint8, VersionOfApi) {
      grantChainRoles()
      grantRootRoles()
      val (_, chainId) = auditedEntityWithOneRecord(s"moved_from_$suffix", java.util.UUID.randomUUID().toString)
      val otherName = s"moved_to_$suffix"
      val otherId = entityIn(otherName)

      When("the chain's row is pointed at the other entity")
      runSql(sql"""UPDATE dynamic_entity_audit_chain SET dynamic_entity_id = ${Option(otherId)}, entity_name = ${Option(otherName)}
                   WHERE chain_id = ${Option(chainRow(chainId).chainId)}""")

      Then("the other entity's check says the chain does not name it")
      problemsIn(check(otherId)).exists(_.contains("The first entry does not name this chain's entity")) should equal(true)
      And("the root chain says the chain now names another entity")
      problemsAbout(checkRoot(), chainId).exists(_.contains("now names another entity")) should equal(true)
    }

    scenario("a chain rewound consistently in the database", ApiEndpoint8, VersionOfApi) {
      grantChainRoles()
      grantRootRoles()
      val entityName = s"rewound_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, chainId) = auditedEntityWithOneRecord(entityName, recordId)
      update(entityName, recordId, "pass")
      seal()

      When("the last entry is removed, and the chain's row and the record are put back to match the entry before")
      val last = entryOf(chainId, 2L)
      deleteEntry(chainId, last.sequence)
      val before = entryOf(chainId, 1L)
      runSql(sql"""UPDATE dynamic_entity_audit_chain SET last_sequence = ${1L}, last_hash = ${Option(before.hash)}
                   WHERE chain_id = ${Option(chainRow(chainId).chainId)}""")
      setRecordJson(entityName, recordId, before.dataJson.get)

      Then("the entity's own check passes, because the rewind is consistent")
      withClue(check(entityId)) { (check(entityId) \ "is_intact").extract[Boolean] should equal(true) }
      And("the root chain finds it, because it recorded the entry that is gone")
      problemsAbout(checkRoot(), chainId).exists(_.contains("the chain was rewound or rewritten")) should equal(true)
    }

    scenario("a whole chain deleted, and a chain added, in the database", ApiEndpoint8, VersionOfApi) {
      grantChainRoles()
      grantRootRoles()
      val entityName = s"vanished_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (entityId, chainId) = auditedEntityWithOneRecord(entityName, recordId)

      When("the entity, its record, its chain and its entries are all deleted directly")
      runSql(sql"DELETE FROM dynamic_entity_audit_entry WHERE chain_id = ${Option(chainId)}")
      runSql(sql"DELETE FROM dynamic_entity_audit_chain WHERE chain_id = ${Option(chainRow(chainId).chainId)}")
      runSql(sql"DELETE FROM dynamicdata WHERE dynamicentityname = ${Option(entityName)}")
      code.dynamicEntity.DynamicEntity.deleteById(entityId)
      Then("the root chain says the chain it registered no longer exists")
      problemsAbout(checkRoot(), chainId).exists(_.contains("it no longer exists")) should equal(true)

      When("a chain is added directly, without being registered")
      val addedName = s"smuggled_$suffix"
      val addedId = entityIn(addedName)
      val added = DoobieUtil.runUpdate(DynamicEntityAuditChain.insertIO(java.util.UUID.randomUUID().toString, SYS,
        addedId, addedName, ("", "")))
      Then("the root chain says it is not registered")
      problemsAbout(checkRoot(), added.chainId).exists(_.contains("is not registered in the root chain")) should equal(true)
    }

    scenario("checking the root chain with include_chains finds problems inside an entity's chain", ApiEndpoint8, VersionOfApi) {
      grantChainRoles()
      grantRootRoles()
      val entityName = s"deep_$suffix"
      val recordId = java.util.UUID.randomUUID().toString
      val (_, chainId) = auditedEntityWithOneRecord(entityName, recordId)
      setRecordJson(entityName, recordId, recordJsonOf(entityName, recordId).replace("pending", "pass"))

      problemsAbout(checkRoot(includeChains = false), chainId) should equal(Nil)
      val deep = problemsAbout(checkRoot(includeChains = true), chainId)
      deep.exists(_.contains(s"In the chain of $entityName: This record does not match its latest entry")) should equal(true)
      makeGetRequest((root / "check").GET <@ (user1) <<? List("include_chains" -> "maybe")).code should equal(400)
    }
  }
}
