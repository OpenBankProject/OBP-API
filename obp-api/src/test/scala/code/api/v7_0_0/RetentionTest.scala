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

import java.util.{Base64, Date}

import code.DynamicData.{DynamicData, DynamicDataAccess, DynamicDataProvider}
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.generateUUID
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.chat.{ChatMessage, ChatRoom, Reaction}
import code.customer.MappedCustomer
import code.customerattribute.MappedCustomerAttribute
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.entitlement.Entitlement
import code.files.{File, FileAttachment, FileContent, MappedFilesProvider}
import code.retention.{RetentionArchive, RetentionPolicy, RetentionProps, RetentionRun}
import code.scheduler.{JobScheduler, RetentionScheduler}
import code.usercustomerlinks.MappedUserCustomerLink
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/**
 * This suite checks the v7.0.0 data retention endpoints: the Roles at SYS and at a bank, retention policy
 * create, read, update and delete, the preview counts, the props that switch archiving and deleting off,
 * approval by a second user, legal holds, archiving and deleting dynamic entity records with the files
 * attached to them, the retention archive, the disposal register, anonymising a customer and deleting
 * chat messages.
 */
class RetentionTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object CreatePolicy extends Tag("createRetentionPolicy")
  object GetPolicies extends Tag("getRetentionPolicies")
  object GetPolicy extends Tag("getRetentionPolicy")
  object UpdatePolicy extends Tag("updateRetentionPolicy")
  object DeletePolicy extends Tag("deleteRetentionPolicy")
  object Preview extends Tag("getRetentionPolicyPreview")
  object CreateRun extends Tag("createRetentionRun")
  object GetRuns extends Tag("getRetentionRuns")
  object GetRun extends Tag("getRetentionRun")
  object Approval extends Tag("updateRetentionRunApproval")
  object RunItems extends Tag("getRetentionRunItems")
  object CreateHold extends Tag("createRetentionHold")
  object GetHolds extends Tag("getRetentionHolds")
  object DeleteHold extends Tag("deleteRetentionHold")
  object GetArchive extends Tag("getRetentionArchiveEntries")
  object GetArchiveEntry extends Tag("getRetentionArchiveEntry")

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  private def bankId = testBankId1.value

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def space(bankId: String) = v7 / "management" / "banks" / bankId
  private def policies(bankId: String) = space(bankId) / "retention-policies"
  private def runs(bankId: String) = space(bankId) / "retention-runs"
  private def holds(bankId: String) = space(bankId) / "retention-holds"
  private def archive(bankId: String) = space(bankId) / "retention-archive"

  private def message(response: code.setup.APIResponse) = response.body.extract[ErrorMessage].message
  private def str(json: JValue, field: String): String = (json \ field).extract[String]
  private def int(json: JValue, field: String): Int = (json \ field).extract[Int]

  private val allRoles: List[ApiRole] = List(canManageRetentionPolicy, canGetRetentionPolicies, canRunRetentionPolicy,
    canApproveRetentionRun, canGetRetentionRuns, canManageRetentionHold, canGetRetentionArchive)

  private def grant(bankId: String, userId: String, roles: ApiRole*): Unit =
    roles.foreach(role => Entitlement.entitlement.vend.addEntitlement(bankId, userId, role.toString))

  /** Most scenarios use short periods; the minimum retention period has scenarios of its own. */
  override def beforeEach(): Unit = {
    super.beforeEach()
    setPropsValues("retention.minimum_retain_days" -> "0")
  }

  private def minimumRetainDays(days: Int): Unit = {
    setPropsValues("retention.minimum_retain_days" -> days.toString)
  }

  private def yearsAgo(years: Int): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(years).toString

  private def allowArchiveAndDelete(archive: Boolean, delete: Boolean): Unit = {
    setPropsValues("retention.archive.enabled" -> archive.toString, "retention.delete.enabled" -> delete.toString)
  }

  /** A Dynamic Entity in the system space with a status and a date field. */
  private def entity(entityName: String): Unit =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
      entityName,
      s"""{"$entityName":{"properties":{
         |"status":{"type":"string","example":"OPEN"},
         |"closed_on":{"type":"string","example":"2020-01-31"}}}}""".stripMargin,
      None, "retention-test", None, hasPersonalEntity = false)).openOrThrowException("definition")

  /** A record of that entity in the system space; its id. */
  private def record(entityName: String, status: String, closedOn: String = "2020-01-31"): String = {
    val id = generateUUID()
    DynamicDataProvider.connectorMethodProvider.vend
      .save(None, entityName, (s"${entityName}_id" -> id) ~ ("status" -> status) ~ ("closed_on" -> closedOn), None, false)
      .openOrThrowException("record").dynamicDataId.get
  }

  private def recordExists(entityName: String, id: String): Boolean =
    DynamicData.count(By(DynamicData.BankId, SYS), By(DynamicData.DynamicEntityName, entityName), By(DynamicData.DynamicDataId, id)) > 0

  private def policyBody(resourceType: String, action: String, entityName: Option[String] = None, retainDays: Int = 0,
                         archiveDays: Option[Int] = None, condition: Option[(String, String)] = None,
                         dateBasis: Option[String] = None, cascade: Boolean = false, requiresApproval: Boolean = false,
                         enabled: Boolean = true): String =
    compact(render(
      ("name" -> s"$resourceType $action") ~
        ("resource_type" -> resourceType) ~
        ("entity_name" -> entityName) ~
        ("date_basis" -> dateBasis) ~
        ("condition" -> condition.map { case (f, v) => ("field" -> f) ~ ("value" -> v) }) ~
        ("retain_days" -> retainDays) ~
        ("archive_days" -> archiveDays) ~
        ("action" -> action) ~
        ("cascade" -> cascade) ~
        ("requires_approval" -> requiresApproval) ~
        ("enabled" -> enabled)))

  /** Create a policy as user1, who holds every Role at the space; its id. */
  private def createPolicy(bankId: String, body: String): String = {
    val created = makePostRequest(policies(bankId).POST <@ (user1), body)
    withClue(created.body) { created.code should equal(201) }
    str(created.body, "retention_policy_id")
  }

  private def runNow(bankId: String, policyId: String, mode: String = "execute") =
    makePostRequest((policies(bankId) / policyId / "runs").POST <@ (user1), compact(render("mode" -> mode)))

  private def runsOf(policyId: String): List[RetentionRun] =
    RetentionRun.findAll(By(RetentionRun.RetentionPolicyId, policyId))

  /** The scheduler runs every enabled policy in the database: switch off any left by other scenarios, and clear its lock. */
  private def schedulerSeesOnlyNewPolicies(): Unit = {
    RetentionPolicy.findAll(By(RetentionPolicy.Enabled, true)).foreach(_.Enabled(false).saveMe())
    JobScheduler.findAll(By(JobScheduler.Name, "RetentionScheduler")).foreach(_.delete_!)
  }

  private def items(bankId: String, runId: String): List[JValue] =
    makeGetRequest((runs(bankId) / runId / "items").GET <@ (user1)).body \ "retention_run_items" match {
      case JArray(values) => values
      case _ => Nil
    }

  feature("Retention policies") {

    scenario("the Roles are required, at the space named in the URL", CreatePolicy, GetPolicies, Preview, CreateRun, VersionOfApi) {
      val body = policyBody("chat_message", "delete")

      When("an anonymous caller creates a policy")
      makePostRequest(policies(SYS).POST, body).code should equal(401)

      When("user1 creates one without the Role")
      val refused = makePostRequest(policies(SYS).POST <@ (user1), body)
      refused.code should equal(403)
      message(refused) should include(UserHasMissingRoles)
      message(refused) should include("CanManageRetentionPolicy")

      When("user1 holds the Role at a bank but not at SYS")
      grant(bankId, resourceUser1.userId, canManageRetentionPolicy)
      makePostRequest(policies(SYS).POST <@ (user1), body).code should equal(403)

      Then("the Role at the bank is enough at that bank")
      makePostRequest(policies(bankId).POST <@ (user1), body).code should equal(201)

      And("reading, previewing and running need their own Roles")
      makeGetRequest(policies(SYS).GET <@ (user2)).code should equal(403)
      makeGetRequest((runs(SYS)).GET <@ (user2)).code should equal(403)
      makeGetRequest((holds(SYS)).GET <@ (user2)).code should equal(403)
      makeGetRequest((archive(SYS)).GET <@ (user2)).code should equal(403)
    }

    scenario("create, read, list, update and delete a policy at SYS, and refuse invalid ones",
      CreatePolicy, GetPolicies, GetPolicy, UpdatePolicy, DeletePolicy, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)

      When("a policy is created")
      val created = makePostRequest(policies(SYS).POST <@ (user1), policyBody("dynamic_entity_record", "archive",
        Some(entityName), retainDays = 730, archiveDays = Some(1825), condition = Some("status" -> "CLOSED"),
        dateBasis = Some("updated"), cascade = true, requiresApproval = true))
      withClue(created.body) { created.code should equal(201) }
      val policyId = str(created.body, "retention_policy_id")
      str(created.body, "bank_id") should equal(SYS)
      str(created.body, "resource_type") should equal("dynamic_entity_record")
      str(created.body, "entity_name") should equal(entityName)
      str(created.body, "date_basis") should equal("updated")
      (created.body \ "condition") should equal(("field" -> "status") ~ ("value" -> "CLOSED"))
      int(created.body, "retain_days") should equal(730)
      int(created.body, "archive_days") should equal(1825)
      str(created.body, "action") should equal("archive")
      (created.body \ "cascade").extract[Boolean] should equal(true)
      (created.body \ "requires_approval").extract[Boolean] should equal(true)
      (created.body \ "enabled").extract[Boolean] should equal(true)
      str(created.body, "created_by_user_id") should equal(resourceUser1.userId)

      Then("it is read and listed")
      makeGetRequest((policies(SYS) / policyId).GET <@ (user1)).code should equal(200)
      val listed = makeGetRequest(policies(SYS).GET <@ (user1))
      listed.code should equal(200)
      (listed.body \ "retention_policies").children.map(p => str(p, "retention_policy_id")) should equal(List(policyId))
      And("it is not found in another space")
      grant(bankId, resourceUser1.userId, allRoles: _*)
      makeGetRequest((policies(bankId) / policyId).GET <@ (user1)).code should equal(404)

      When("it is updated to delete, without an archive stage, and the condition is removed")
      val updated = makePutRequest((policies(SYS) / policyId).PUT <@ (user1),
        compact(render(("action" -> "delete") ~ ("retain_days" -> 365) ~ ("condition" -> (("field" -> "") ~ ("value" -> ""))))))
      withClue(updated.body) { updated.code should equal(200) }
      str(updated.body, "action") should equal("delete")
      int(updated.body, "retain_days") should equal(365)
      (updated.body \ "archive_days") should equal(JNothing)
      (updated.body \ "condition") should equal(JNothing)

      Then("invalid policies are refused")
      val noArchiveDays = makePostRequest(policies(SYS).POST <@ (user1), policyBody("chat_message", "archive"))
      noArchiveDays.code should equal(400)
      message(noArchiveDays) should include(InvalidRetentionPolicy)
      makePostRequest(policies(SYS).POST <@ (user1), policyBody("chat_message", "anonymise")).code should equal(400)
      makePostRequest(policies(SYS).POST <@ (user1), policyBody("unknown", "delete")).code should equal(400)
      makePostRequest(policies(SYS).POST <@ (user1), policyBody("dynamic_entity_record", "delete", Some("no_such_entity"))).code should equal(400)
      makePostRequest(policies(SYS).POST <@ (user1), policyBody("customer", "anonymise")).code should equal(400)

      When("it is deleted")
      makeDeleteRequest((policies(SYS) / policyId).DELETE <@ (user1)).code should equal(204)
      makeGetRequest((policies(SYS) / policyId).GET <@ (user1)).code should equal(404)
    }

    scenario("the preview counts what is due, what is held, and changes nothing", Preview, CreateHold, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = false)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val closed1 = record(entityName, "CLOSED", "2020-01-31")
      val closed2 = record(entityName, "CLOSED", "2021-06-30")
      val open = record(entityName, "OPEN", "2020-01-31")
      val recent = record(entityName, "CLOSED", "2999-01-01")

      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName),
        retainDays = 365, condition = Some("status" -> "CLOSED"), dateBasis = Some("field:closed_on")))

      When("a record is put on hold")
      val hold = makePostRequest(holds(SYS).POST <@ (user1), compact(render(
        ("resource_type" -> "dynamic_entity_record") ~ ("resource_id" -> closed2) ~ ("entity_name" -> entityName) ~ ("reason" -> "Open complaint."))))
      withClue(hold.body) { hold.code should equal(201) }

      val preview = makeGetRequest((policies(SYS) / policyId / "preview").GET <@ (user1))
      withClue(preview.body) { preview.code should equal(200) }
      Then("the two closed records past their date match, one of them on hold")
      int(preview.body, "matched_count") should equal(2)
      int(preview.body, "held_count") should equal(1)
      int(preview.body, "due_count") should equal(1)
      (preview.body \ "sample_resource_ids").extract[List[String]] should equal(List(closed1))
      str(preview.body, "oldest_trigger_date") should startWith("2020-01-31")
      (preview.body \ "can_execute").extract[Boolean] should equal(false)

      And("a preview run records the counts and changes nothing")
      val previewRun = runNow(SYS, policyId, "preview")
      withClue(previewRun.body) { previewRun.code should equal(201) }
      str(previewRun.body, "status") should equal("done")
      int(previewRun.body, "matched_count") should equal(2)
      List(closed1, closed2, open, recent).foreach(id => recordExists(entityName, id) should equal(true))

      And("a policy with a longer period matches nothing yet")
      val laterId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), retainDays = 36500))
      int(makeGetRequest((policies(SYS) / laterId / "preview").GET <@ (user1)).body, "matched_count") should equal(0)
    }

    scenario("a run is refused while the props switch archiving or deleting off", CreateRun, GetRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = false)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")

      val archivePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "archive", Some(entityName), archiveDays = Some(30)))
      val refusedArchive = runNow(SYS, archivePolicy)
      refusedArchive.code should equal(400)
      message(refusedArchive) should include(RetentionArchiveIsDisabled)

      val deletePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))
      val refusedDelete = runNow(SYS, deletePolicy)
      refusedDelete.code should equal(400)
      message(refusedDelete) should include(RetentionDeleteIsDisabled)

      Then("nothing was disposed of, and the refused runs are recorded")
      recordExists(entityName, id) should equal(true)
      val listed = makeGetRequest(runs(SYS).GET <@ (user1) <<? List("status" -> "refused"))
      listed.code should equal(200)
      (listed.body \ "retention_runs").children.size should equal(2)

      And("a preview still works")
      makeGetRequest((policies(SYS) / deletePolicy / "preview").GET <@ (user1)).code should equal(200)
    }

    scenario("a run that requires approval waits for a different user", CreateRun, Approval, GetRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), requiresApproval = true))

      When("user1 runs it")
      val pending = runNow(SYS, policyId)
      withClue(pending.body) { pending.code should equal(201) }
      str(pending.body, "status") should equal("pending")
      str(pending.body, "requested_by_user_id") should equal(resourceUser1.userId)
      val runId = str(pending.body, "retention_run_id")
      recordExists(entityName, id) should equal(true)

      val approve = compact(render(("decision" -> "approve") ~ ("comment" -> "Checked.")))
      Then("user1 may not approve their own run")
      val own = makePutRequest((runs(SYS) / runId / "approval").PUT <@ (user1), approve)
      own.code should equal(403)
      message(own) should include(RetentionRunApproverIsRequester)

      And("user2 needs the Role to approve it")
      makePutRequest((runs(SYS) / runId / "approval").PUT <@ (user2), approve).code should equal(403)
      grant(SYS, resourceUser2.userId, canApproveRetentionRun)
      val approved = makePutRequest((runs(SYS) / runId / "approval").PUT <@ (user2), approve)
      withClue(approved.body) { approved.code should equal(200) }
      str(approved.body, "status") should equal("done")
      str(approved.body, "approved_by_user_id") should equal(resourceUser2.userId)
      str(approved.body, "approval_decision") should equal("approved")
      int(approved.body, "deleted_count") should equal(1)
      recordExists(entityName, id) should equal(false)

      And("a run that is no longer pending cannot be decided again")
      makePutRequest((runs(SYS) / runId / "approval").PUT <@ (user2), approve).code should equal(400)

      And("a rejected run disposes of nothing")
      val other = record(entityName, "CLOSED")
      val second = str(runNow(SYS, policyId).body, "retention_run_id")
      val rejected = makePutRequest((runs(SYS) / second / "approval").PUT <@ (user2), compact(render("decision" -> "reject")))
      rejected.code should equal(200)
      str(rejected.body, "status") should equal("refused")
      str(rejected.body, "approval_decision") should equal("rejected")
      recordExists(entityName, other) should equal(true)
    }

    scenario("a record on hold is skipped until the hold is released", CreateHold, GetHolds, DeleteHold, CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val held = record(entityName, "CLOSED")
      val free = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))

      When("every record of the entity is on hold")
      val hold = makePostRequest(holds(SYS).POST <@ (user1), compact(render(
        ("resource_type" -> "dynamic_entity_record") ~ ("entity_name" -> entityName) ~ ("reason" -> "Audit in progress."))))
      withClue(hold.body) { hold.code should equal(201) }
      val holdId = str(hold.body, "retention_hold_id")
      val first = runNow(SYS, policyId)
      int(first.body, "held_count") should equal(2)
      int(first.body, "deleted_count") should equal(0)
      recordExists(entityName, held) should equal(true)
      recordExists(entityName, free) should equal(true)

      When("that hold is released and one record is held by id")
      makeDeleteRequest((holds(SYS) / holdId).DELETE <@ (user1)).code should equal(204)
      makeDeleteRequest((holds(SYS) / holdId).DELETE <@ (user1)).code should equal(400)
      makePostRequest(holds(SYS).POST <@ (user1), compact(render(
        ("resource_type" -> "dynamic_entity_record") ~ ("resource_id" -> held) ~ ("reason" -> "Open complaint.")))).code should equal(201)
      val active = makeGetRequest(holds(SYS).GET <@ (user1) <<? List("is_active" -> "true"))
      (active.body \ "retention_holds").children.size should equal(1)
      val released = makeGetRequest(holds(SYS).GET <@ (user1) <<? List("is_active" -> "false"))
      (released.body \ "retention_holds").children.map(h => str(h, "released_by_user_id")) should equal(List(resourceUser1.userId))

      Then("only the record still on hold stays")
      val second = runNow(SYS, policyId)
      int(second.body, "held_count") should equal(1)
      int(second.body, "deleted_count") should equal(1)
      recordExists(entityName, held) should equal(true)
      recordExists(entityName, free) should equal(false)
    }
  }

  feature("Disposal") {

    scenario("archive a dynamic entity record with its file, read the archive, then delete it from the archive",
      CreateRun, RunItems, GetArchive, GetArchiveEntry, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = true, delete = false)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      DynamicDataAccess.create.BankId(SYS).EntityName(entityName).DynamicDataId(id).UserId(resourceUser2.userId)
        .CanRead(true).GrantedBy(resourceUser1.userId).saveMe()
      // One byte: the Files table keeps its bytes in a MappedBinary, which Lift maps to a one-byte BINARY on H2.
      val bytes = Array[Byte](42)
      val file = MappedFilesProvider.createFile(SYS, "0" * 64, "application/pdf", "evidence.pdf", false, resourceUser1.userId, bytes)
        .openOrThrowException("file")
      MappedFilesProvider.createAttachment(file, entityName, id, resourceUser1.userId).openOrThrowException("attachment")

      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "archive", Some(entityName),
        archiveDays = Some(0), cascade = true))
      val run = runNow(SYS, policyId)
      withClue(run.body) { run.code should equal(201) }
      str(run.body, "status") should equal("done")
      int(run.body, "matched_count") should equal(1)
      int(run.body, "archived_count") should equal(2)
      val runId = str(run.body, "retention_run_id")

      Then("the record, its access list, the file and its bytes have left the live tables")
      recordExists(entityName, id) should equal(false)
      DynamicDataAccess.count(By(DynamicDataAccess.DynamicDataId, id)) should equal(0)
      File.count(By(File.FileId, file.fileId)) should equal(0)
      FileContent.count(By(FileContent.FileId, file.fileId)) should equal(0)
      FileAttachment.count(By(FileAttachment.FileId, file.fileId)) should equal(0)

      And("the disposal register names both, the file under its record")
      val register = items(SYS, runId)
      register.map(i => (str(i, "resource_type"), str(i, "action"))).toSet should equal(
        Set(("dynamic_entity_record", "archive"), ("file", "archive")))
      val fileItem = register.find(i => str(i, "resource_type") == "file").get
      str(fileItem, "resource_id") should equal(file.fileId)
      str(fileItem, "parent_resource_id") should equal(id)

      And("the archive holds both, and gives the copy and the bytes back")
      val listed = makeGetRequest(archive(SYS).GET <@ (user1))
      listed.code should equal(200)
      val entries = (listed.body \ "retention_archive_entries").children
      entries.size should equal(2)
      entries.foreach(e => (e \ "data") should equal(JNothing))
      val recordEntry = entries.find(e => str(e, "resource_type") == "dynamic_entity_record").get
      val one = makeGetRequest((archive(SYS) / str(recordEntry, "retention_archive_id")).GET <@ (user1))
      one.code should equal(200)
      str(one.body \ "data" \ "data", "status") should equal("CLOSED")
      str(one.body, "resource_id") should equal(id)
      val fileEntry = entries.find(e => str(e, "resource_type") == "file").get
      val withContent = makeGetRequest((archive(SYS) / str(fileEntry, "retention_archive_id")).GET <@ (user1) <<? List("include_content" -> "true"))
      Base64.getDecoder.decode(str(withContent.body, "content_base64")) should equal(bytes)

      When("deleting is switched on, the next run deletes what reached the end of its archive period")
      allowArchiveAndDelete(archive = true, delete = true)
      val purge = runNow(SYS, policyId)
      withClue(purge.body) { purge.code should equal(201) }
      int(purge.body, "deleted_count") should equal(2)
      RetentionArchive.count(By(RetentionArchive.BankId, SYS)) should equal(0)
      items(SYS, str(purge.body, "retention_run_id")).map(i => str(i, "action")).distinct should equal(List("delete_from_archive"))
    }

    scenario("delete a dynamic entity record and cascade to its file; without cascade the file stays", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val file = MappedFilesProvider.createFile(SYS, "1" * 64, "image/png", "photo.png", false, resourceUser1.userId, Array[Byte](1))
        .openOrThrowException("file")
      MappedFilesProvider.createAttachment(file, entityName, id, resourceUser1.userId).openOrThrowException("attachment")

      val other = s"other_${generateUUID().take(8).replace("-", "")}"
      entity(other)
      val otherId = record(other, "CLOSED")
      val kept = MappedFilesProvider.createFile(SYS, "2" * 64, "image/png", "kept.png", false, resourceUser1.userId, Array[Byte](4))
        .openOrThrowException("file")
      MappedFilesProvider.createAttachment(kept, other, otherId, resourceUser1.userId).openOrThrowException("attachment")

      val cascading = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), cascade = true))
      val run = runNow(SYS, cascading)
      int(run.body, "deleted_count") should equal(2)
      recordExists(entityName, id) should equal(false)
      File.count(By(File.FileId, file.fileId)) should equal(0)
      RetentionArchive.count() should equal(0)

      val notCascading = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(other)))
      int(runNow(SYS, notCascading).body, "deleted_count") should equal(1)
      recordExists(other, otherId) should equal(false)
      File.count(By(File.FileId, kept.fileId)) should equal(1)
      FileAttachment.count(By(FileAttachment.FileId, kept.fileId)) should equal(0)
    }

    scenario("anonymise a customer: personal fields cleared, links removed, the customer id kept", CreateRun, RunItems, VersionOfApi) {
      grant(bankId, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val customerId = generateUUID()
      MappedCustomer.create.mCustomerId(customerId).mBank(bankId).mNumber(customerId.take(10))
        .mLegalName("Example Person").mEmail("person@example.com").mMobileNumber("+10000000000")
        .mDateOfBirth(new Date(0L)).mTitle("Dr").saveMe()
      MappedCustomerAttribute.create.mBankId(bankId).mCustomerId(customerId).mCustomerAttributeId(generateUUID())
        .mName("nickname").mType("STRING").mValue("Example").saveMe()
      MappedUserCustomerLink.create.mUserCustomerLinkId(generateUUID()).mCustomerId(customerId)
        .mUserId(resourceUser2.userId).mDateInserted(new Date()).mIsActive(true).saveMe()

      val policyId = createPolicy(bankId, policyBody("customer", "anonymise"))
      val run = runNow(bankId, policyId)
      withClue(run.body) { run.code should equal(201) }
      int(run.body, "anonymised_count") should equal(1)

      val customer = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId)).openOrThrowException("customer")
      customer.legalName should equal("ANONYMISED")
      customer.email should equal("")
      customer.mobileNumber should equal("")
      customer.title should equal("")
      customer.mNumber.get should equal(customerId.take(10))
      MappedCustomerAttribute.count(By(MappedCustomerAttribute.mCustomerId, customerId)) should equal(0)
      MappedUserCustomerLink.count(By(MappedUserCustomerLink.mCustomerId, customerId)) should equal(0)

      val register = items(bankId, str(run.body, "retention_run_id"))
      register.map(i => (str(i, "resource_id"), str(i, "action"), str(i, "owner_user_id"))) should equal(
        List((customerId, "anonymise", resourceUser2.userId)))

      And("an anonymised customer is not anonymised again")
      int(runNow(bankId, policyId).body, "matched_count") should equal(0)
    }

    scenario("delete the chat messages of a bank, keeping the room", CreateRun, GetRuns, VersionOfApi) {
      grant(bankId, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val roomId = generateUUID()
      ChatRoom.create.ChatRoomId(roomId).BankId(bankId).Name("General").CreatedByUserId(resourceUser1.userId)
        .LastMessagePreview("second message").LastMessageAt(new Date()).saveMe()
      val messageIds = List("first message", "second message").map { content =>
        val messageId = generateUUID()
        ChatMessage.create.ChatMessageId(messageId).ChatRoomId(roomId).SenderUserId(resourceUser1.userId)
          .Content(content).MessageType("text").saveMe()
        messageId
      }
      Reaction.create.ReactionId(generateUUID()).ChatMessageId(messageIds.head).UserId(resourceUser2.userId).Emoji("+1").saveMe()
      val otherBankRoom = generateUUID()
      ChatRoom.create.ChatRoomId(otherBankRoom).BankId("another-bank").Name("Elsewhere").saveMe()
      ChatMessage.create.ChatMessageId(generateUUID()).ChatRoomId(otherBankRoom).Content("elsewhere").MessageType("text").saveMe()

      val policyId = createPolicy(bankId, policyBody("chat_message", "delete"))
      val run = runNow(bankId, policyId)
      withClue(run.body) { run.code should equal(201) }
      int(run.body, "deleted_count") should equal(2)

      ChatMessage.count(By(ChatMessage.ChatRoomId, roomId)) should equal(0)
      Reaction.count(By(Reaction.ChatMessageId, messageIds.head)) should equal(0)
      val room = ChatRoom.find(By(ChatRoom.ChatRoomId, roomId)).openOrThrowException("room")
      room.lastMessagePreview should equal("")
      ChatMessage.count(By(ChatMessage.ChatRoomId, otherBankRoom)) should equal(1)
      items(bankId, str(run.body, "retention_run_id")).map(i => str(i, "resource_id")).toSet should equal(messageIds.toSet)

      val listed = makeGetRequest(runs(bankId).GET <@ (user1) <<? List("retention_policy_id" -> policyId))
      (listed.body \ "retention_runs").children.map(r => str(r, "status")) should equal(List("done"))
      RetentionRun.count(By(RetentionRun.BankId, SYS)) should equal(0)
    }
  }

  feature("Retention runs only when it is switched on") {

    scenario("archiving, deleting and the scheduler are off by default", Preview, CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      RetentionProps.archiveEnabled should equal(false)
      RetentionProps.deleteEnabled should equal(false)
      RetentionProps.schedulerEnabled should equal(false)

      Then("the scheduler is not started")
      RetentionScheduler.startIfEnabled() should equal(false)

      And("a run is refused and the preview says it cannot execute")
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))
      (makeGetRequest((policies(SYS) / policyId / "preview").GET <@ (user1)).body \ "can_execute").extract[Boolean] should equal(false)
      runNow(SYS, policyId).code should equal(400)
      recordExists(entityName, id) should equal(true)
    }

    scenario("each prop switches on only its own actions", Preview, CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      grant(bankId, resourceUser1.userId, allRoles: _*)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val archivePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "archive", Some(entityName), archiveDays = Some(30)))
      val deletePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))
      val anonymisePolicy = createPolicy(bankId, policyBody("customer", "anonymise"))
      def canExecute(space: String, policyId: String) =
        (makeGetRequest((policies(space) / policyId / "preview").GET <@ (user1)).body \ "can_execute").extract[Boolean]

      When("only archiving is switched on")
      allowArchiveAndDelete(archive = true, delete = false)
      canExecute(SYS, archivePolicy) should equal(true)
      canExecute(SYS, deletePolicy) should equal(false)
      canExecute(bankId, anonymisePolicy) should equal(false)
      val kept = record(entityName, "CLOSED")
      val refusedDelete = runNow(SYS, deletePolicy)
      refusedDelete.code should equal(400)
      message(refusedDelete) should include(RetentionDeleteIsDisabled)
      val refusedAnonymise = runNow(bankId, anonymisePolicy)
      refusedAnonymise.code should equal(400)
      message(refusedAnonymise) should include(RetentionDeleteIsDisabled)
      recordExists(entityName, kept) should equal(true)
      Then("an archive run archives")
      val archived = runNow(SYS, archivePolicy)
      withClue(archived.body) { archived.code should equal(201) }
      int(archived.body, "archived_count") should equal(1)
      recordExists(entityName, kept) should equal(false)

      When("only deleting is switched on")
      allowArchiveAndDelete(archive = false, delete = true)
      canExecute(SYS, archivePolicy) should equal(false)
      canExecute(SYS, deletePolicy) should equal(true)
      canExecute(bankId, anonymisePolicy) should equal(true)
      val other = record(entityName, "CLOSED")
      val refusedArchive = runNow(SYS, archivePolicy)
      refusedArchive.code should equal(400)
      message(refusedArchive) should include(RetentionArchiveIsDisabled)
      recordExists(entityName, other) should equal(true)
      Then("a delete run deletes")
      int(runNow(SYS, deletePolicy).body, "deleted_count") should equal(1)
      recordExists(entityName, other) should equal(false)
    }

    scenario("an approved run is refused when the props were switched off after it was requested", CreateRun, Approval, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      grant(SYS, resourceUser2.userId, canApproveRetentionRun)
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), requiresApproval = true))
      val pending = runNow(SYS, policyId)
      str(pending.body, "status") should equal("pending")

      When("deleting is switched off before user2 approves")
      allowArchiveAndDelete(archive = false, delete = false)
      val runId = str(pending.body, "retention_run_id")
      val approved = makePutRequest((runs(SYS) / runId / "approval").PUT <@ (user2), compact(render("decision" -> "approve")))
      Then("the approval is refused, the run is recorded as refused, and the record stays")
      approved.code should equal(400)
      message(approved) should include(RetentionDeleteIsDisabled)
      val run = makeGetRequest((runs(SYS) / runId).GET <@ (user1))
      str(run.body, "status") should equal("refused")
      str(run.body, "approved_by_user_id") should equal(resourceUser2.userId)
      int(run.body, "deleted_count") should equal(0)
      recordExists(entityName, id) should equal(true)
    }

    scenario("archived resources past their archive period stay in the archive while deleting is off", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      schedulerSeesOnlyNewPolicies()
      allowArchiveAndDelete(archive = true, delete = false)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "archive", Some(entityName), archiveDays = Some(0)))
      int(runNow(SYS, policyId).body, "archived_count") should equal(1)
      val archivedCount = RetentionArchive.count(By(RetentionArchive.RetentionPolicyId, policyId))
      archivedCount should equal(1)

      When("the policy runs again, by hand and from the scheduler")
      val again = runNow(SYS, policyId)
      withClue(again.body) { again.code should equal(201) }
      int(again.body, "deleted_count") should equal(0)
      str(again.body, "message") should include("retention.delete.enabled is false")
      RetentionScheduler.runOnce() should equal(true)
      Then("the archive entry is still there")
      RetentionArchive.count(By(RetentionArchive.RetentionPolicyId, policyId)) should equal(archivedCount)
    }
  }

  feature("The minimum retention period") {

    scenario("is five years by default", Preview, VersionOfApi) {
      RetentionProps.MinimumRetainDaysDefault should equal(1825)
    }

    scenario("a policy shorter than the minimum is refused", CreatePolicy, UpdatePolicy, Preview, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      minimumRetainDays(1825)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)

      val tooShort = makePostRequest(policies(SYS).POST <@ (user1), policyBody("dynamic_entity_record", "delete", Some(entityName), retainDays = 1824))
      tooShort.code should equal(400)
      message(tooShort) should include(InvalidRetentionPolicy)
      message(tooShort) should include("retention.minimum_retain_days")
      makePostRequest(policies(SYS).POST <@ (user1), policyBody("dynamic_entity_record", "archive", Some(entityName),
        retainDays = 365, archiveDays = Some(3650))).code should equal(400)

      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), retainDays = 1825))
      val shortened = makePutRequest((policies(SYS) / policyId).PUT <@ (user1), compact(render("retain_days" -> 30)))
      shortened.code should equal(400)
      message(shortened) should include(InvalidRetentionPolicy)

      val preview = makeGetRequest((policies(SYS) / policyId / "preview").GET <@ (user1))
      int(preview.body, "minimum_retain_days") should equal(1825)
    }

    scenario("a policy from before the minimum was raised disposes of nothing younger than the minimum",
      Preview, CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val old = record(entityName, "CLOSED", yearsAgo(6))
      val young = record(entityName, "CLOSED", yearsAgo(3))
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName),
        retainDays = 365, dateBasis = Some("field:closed_on")))
      int(makeGetRequest((policies(SYS) / policyId / "preview").GET <@ (user1)).body, "matched_count") should equal(2)

      When("the minimum is raised to five years")
      minimumRetainDays(1825)
      val preview = makeGetRequest((policies(SYS) / policyId / "preview").GET <@ (user1))
      int(preview.body, "matched_count") should equal(1)
      (preview.body \ "sample_resource_ids").extract[List[String]] should equal(List(old))
      And("the policy cannot be changed until its retain_days meets the minimum")
      makePutRequest((policies(SYS) / policyId).PUT <@ (user1), compact(render("name" -> "renamed"))).code should equal(400)

      Then("a run, by hand or from the scheduler, keeps the record younger than five years")
      int(runNow(SYS, policyId).body, "deleted_count") should equal(1)
      schedulerSeesOnlyNewPolicies()
      RetentionPolicy.findAll(By(RetentionPolicy.RetentionPolicyId, policyId)).foreach(_.Enabled(true).saveMe())
      RetentionScheduler.runOnce() should equal(true)
      recordExists(entityName, old) should equal(false)
      recordExists(entityName, young) should equal(true)
    }
  }

  feature("The retention scheduler") {

    scenario("disposes of nothing, and records no runs, while the props are off", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      schedulerSeesOnlyNewPolicies()
      allowArchiveAndDelete(archive = false, delete = false)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val deletePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))
      val archivePolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "archive", Some(entityName), archiveDays = Some(30)))

      RetentionScheduler.runOnce() should equal(true)
      recordExists(entityName, id) should equal(true)
      runsOf(deletePolicy) should equal(Nil)
      runsOf(archivePolicy) should equal(Nil)
    }

    scenario("runs enabled policies only, as the system, and leaves disabled ones alone", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      schedulerSeesOnlyNewPolicies()
      allowArchiveAndDelete(archive = false, delete = true)
      val enabledEntity = s"application_${generateUUID().take(8).replace("-", "")}"
      val disabledEntity = s"other_${generateUUID().take(8).replace("-", "")}"
      entity(enabledEntity)
      entity(disabledEntity)
      val gone = record(enabledEntity, "CLOSED")
      val kept = record(disabledEntity, "CLOSED")
      val enabledPolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(enabledEntity)))
      val disabledPolicy = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(disabledEntity), enabled = false))

      RetentionScheduler.runOnce() should equal(true)
      recordExists(enabledEntity, gone) should equal(false)
      recordExists(disabledEntity, kept) should equal(true)
      runsOf(enabledPolicy).map(r => (r.status, r.requestedByUserId)) should equal(List(("done", "")))
      runsOf(disabledPolicy) should equal(Nil)

      And("a disabled policy can still be run by hand")
      int(runNow(SYS, disabledPolicy).body, "deleted_count") should equal(1)
      recordExists(disabledEntity, kept) should equal(false)
    }

    scenario("asks once for approval, rather than once per tick, and disposes of nothing until approved", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      schedulerSeesOnlyNewPolicies()
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName), requiresApproval = true))

      RetentionScheduler.runOnce() should equal(true)
      RetentionScheduler.runOnce() should equal(true)
      runsOf(policyId).map(_.status) should equal(List("pending"))
      recordExists(entityName, id) should equal(true)
    }

    scenario("skips a tick while another holds the lock", CreateRun, VersionOfApi) {
      grant(SYS, resourceUser1.userId, allRoles: _*)
      schedulerSeesOnlyNewPolicies()
      allowArchiveAndDelete(archive = false, delete = true)
      val entityName = s"application_${generateUUID().take(8).replace("-", "")}"
      entity(entityName)
      val id = record(entityName, "CLOSED")
      val policyId = createPolicy(SYS, policyBody("dynamic_entity_record", "delete", Some(entityName)))

      val lock = JobScheduler.create.JobId(generateUUID()).Name("RetentionScheduler").ApiInstanceId("another-node").saveMe()
      RetentionScheduler.runOnce() should equal(false)
      recordExists(entityName, id) should equal(true)
      runsOf(policyId) should equal(Nil)

      Then("the next tick, with the lock gone, runs")
      JobScheduler.delete_!(lock)
      RetentionScheduler.runOnce() should equal(true)
      recordExists(entityName, id) should equal(false)
    }
  }
}
