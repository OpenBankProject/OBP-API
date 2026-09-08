package code.api.v7_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ErrorMessages._
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.dynamicResourceDoc.DynamicResourceDoc
import code.dynamicchangerequest.MakerChecker
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiShortVersions, ApiVersion}
import net.liftweb.mapper.By
import org.json4s.JsonAST.{JArray, JObject}
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.net.URLDecoder

/**
 * Maker/checker for dynamic code (MAKER_CHECKER_DYNAMIC_CODE_DESIGN.md), phase 1, exercised
 * end-to-end over HTTP: a v4.0.0 write is intercepted into a DynamicChangeRequest (202), a second
 * user approves it by hash, the artefact then exists and executes; plus rejection, withdrawal,
 * deactivation, the execution guard against direct DB tampering, and the role/auth surface.
 */
class DynamicChangeRequestTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations7_0_0.createDynamicChangeRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations7_0_0.getDynamicChangeRequests))
  object ApiEndpoint3 extends Tag(nameOf(Implementations7_0_0.getDynamicChangeRequest))
  object ApiEndpoint4 extends Tag(nameOf(Implementations7_0_0.approveDynamicChangeRequest))
  object ApiEndpoint5 extends Tag(nameOf(Implementations7_0_0.rejectDynamicChangeRequest))
  object ApiEndpoint6 extends Tag(nameOf(Implementations7_0_0.withdrawDynamicChangeRequest))
  object ApiEndpoint7 extends Tag(nameOf(Implementations7_0_0.getMyDynamicChangeRequests))
  object ApiEndpoint8 extends Tag(nameOf(Implementations7_0_0.deactivateDynamicResourceDoc))

  implicit class ResponseWithClue(r: code.setup.APIResponse) {
    /** assert the status code, showing the response body on failure */
    def codeIs(expected: Int): Unit = withClue(s"response body: ${r.body}") { r.code should equal(expected) }
  }

  def v4 = baseRequest / "obp" / "v4.0.0"
  def v7 = baseRequest / "obp" / "v7.0.0"
  def dynamicEndpoint = baseRequest / "obp" / ApiShortVersions.`dynamic-endpoint`.toString

  // The local test.default.props may not enable dynamic code (CI does); these scenarios need it on.
  // Braced body on purpose: .github/scripts/check_test_isolation.py only recognises `def name {` as a helper.
  private def dynamicCodeOn(): Unit = {
    setPropsValues("allow_user_generated_scala_code" -> "true")
  }

  private def enableMakerChecker(): Unit = {
    dynamicCodeOn()
    setPropsValues("dynamic_code_requires_approval" -> "true")
  }

  private def grant(userId: String, roles: ApiRole*): Unit =
    roles.foreach(r => Entitlement.entitlement.vend.addEntitlement("", userId, r.toString))

  private def makerRoles(): Unit =
    grant(resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc, ApiRole.canUpdateDynamicResourceDoc, ApiRole.canDeleteDynamicResourceDoc)

  private def checkerRoles(): Unit =
    grant(resourceUser2.userId, ApiRole.canApproveDynamicChangeRequest, ApiRole.canGetDynamicChangeRequests)

  private def newDoc(suffix: String) = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
    dynamicResourceDocId = None,
    bankId = None,
    roles = "",
    partialFunctionName = s"makerChecker$suffix",
    requestUrl = s"/mc_native_user_$suffix/MY_USER_ID"
  )

  private def storedDoc(url: String): Option[DynamicResourceDoc] =
    DynamicResourceDoc.find(By(DynamicResourceDoc.RequestUrl, url), By(DynamicResourceDoc.RequestVerb, "POST")).toOption

  private def callDynamicEndpoint(suffix: String) = {
    val req = (dynamicEndpoint / "dynamic-resource-doc" / s"mc_native_user_$suffix" / "user-xyz").POST <@ (user1)
    makePostRequest(req, """{"name":"Jhon","age":12,"hobby":["coding"]}""")
  }

  private def str(json: org.json4s.JValue, field: String): String = (json \ field).values.toString

  feature("Maker/checker disabled: today's behaviour is unchanged") {
    scenario("a v4 create is applied directly, the row is active with no approved hash, and the endpoint runs", VersionOfApi) {
      dynamicCodeOn(); makerRoles()
      val doc = newDoc("off")
      val resp = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      resp.codeIs(201)
      val row = storedDoc(doc.requestUrl).getOrElse(fail("doc not stored"))
      row.IsActive.get should be(true)
      Option(row.ApprovedHash.get).getOrElse("") should be("")
      callDynamicEndpoint("off").codeIs(200)
    }
  }

  feature("Maker/checker enabled: v4 writes are queued and applied only after a second user approves") {

    scenario("create is intercepted (202), nothing is stored, and the same user cannot approve", ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      enableMakerChecker(); makerRoles(); checkerRoles()
      grant(resourceUser1.userId, ApiRole.canApproveDynamicChangeRequest, ApiRole.canGetDynamicChangeRequests)
      val doc = newDoc("same")

      When("the maker POSTs the dynamic resource doc via v4.0.0")
      val resp = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      Then("the write is queued, not applied")
      resp.codeIs(202)
      str(resp.body, "status") should equal("INITIATED")
      str(resp.body, "target_type") should equal("DYNAMIC_RESOURCE_DOC")
      str(resp.body, "operation") should equal("CREATE")
      str(resp.body, "requestor_user_id") should equal(resourceUser1.userId)
      str(resp.body, "request_path") should include("/management/dynamic-resource-docs")
      val hash = str(resp.body, "payload_hash")
      hash.length should equal(64)
      hash should equal(MakerChecker.payloadHash(write(doc)))
      storedDoc(doc.requestUrl) should be(None)
      callDynamicEndpoint("same").codeIs(404)

      val id = str(resp.body, "dynamic_change_request_id")
      When("the maker reads it back")
      val get = makeGetRequest((v7 / "management" / "dynamic-change-requests" / id).GET <@ (user1))
      get.codeIs(200)
      str(get.body, "payload_hash") should equal(hash)

      When("the same user tries to approve")
      val approve = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user1),
        s"""{"payload_hash":"$hash","checker_comment":"self"}""")
      Then("maker/checker separation is enforced")
      approve.codeIs(400)
      approve.body.toString should include(MakerCheckerSameUser.split(":").head)
      storedDoc(doc.requestUrl) should be(None)
    }

    scenario("a second user approves by hash: wrong hash fails, right hash applies, the endpoint runs, a second approval fails", ApiEndpoint4, VersionOfApi) {
      enableMakerChecker(); makerRoles(); checkerRoles()
      val doc = newDoc("ok")
      val resp = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      resp.codeIs(202)
      val id = str(resp.body, "dynamic_change_request_id")
      val hash = str(resp.body, "payload_hash")

      When("the checker sends a different hash")
      val bad = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user2),
        """{"payload_hash":"0000000000000000000000000000000000000000000000000000000000000000"}""")
      bad.codeIs(400)
      bad.body.toString should include(DynamicChangeRequestHashMismatch.split(":").head)
      storedDoc(doc.requestUrl) should be(None)

      When("the checker approves the exact hash (sha256: prefix accepted)")
      val ok = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user2),
        s"""{"payload_hash":"sha256:$hash","checker_comment":"reviewed"}""")
      Then("the request is APPROVED and the doc exists with its body hash approved")
      ok.codeIs(200)
      str(ok.body, "status") should equal("APPROVED")
      str(ok.body, "checker_user_id") should equal(resourceUser2.userId)
      str(ok.body, "checker_comment") should equal("reviewed")
      (ok.body \ "target_id").values.toString.nonEmpty should be(true)
      val row = storedDoc(doc.requestUrl).getOrElse(fail("doc not applied"))
      row.CreatedByUserId.get should be(resourceUser1.userId)
      row.MethodBodyHash.get should be(APIUtil.sha256Hex(URLDecoder.decode(doc.methodBody, "UTF-8")))
      row.ApprovedHash.get should be(row.MethodBodyHash.get)
      row.IsActive.get should be(true)
      MakerChecker.isExecutableDynamicResourceDoc(row.DynamicResourceDocId.get) should be(true)

      Then("the compiled endpoint is served")
      val call = callDynamicEndpoint("ok")
      call.codeIs(200)
      call.body.toString should include("user-xyz_from_path")

      When("someone approves again")
      val again = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user2),
        s"""{"payload_hash":"$hash"}""")
      again.codeIs(400)
      again.body.toString should include(DynamicChangeRequestNotInitiated.split(":").head)

      Then("the v7 provenance view shows the approval")
      grant(resourceUser2.userId, ApiRole.canGetDynamicResourceDoc)
      val prov = makeGetRequest((v7 / "management" / "dynamic-resource-docs" / row.DynamicResourceDocId.get).GET <@ (user2))
      prov.codeIs(200)
      str(prov.body \ "provenance", "approved_hash") should equal(row.ApprovedHash.get)
      (prov.body \ "provenance" \ "is_active").values should equal(true)
    }

    scenario("the execution guard holds against direct database edits and deactivation is a single-approver action", ApiEndpoint8, VersionOfApi) {
      enableMakerChecker(); makerRoles(); checkerRoles()
      val doc = newDoc("guard")
      val resp = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      val id = str(resp.body, "dynamic_change_request_id")
      makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user2),
        s"""{"payload_hash":"${str(resp.body, "payload_hash")}"}""").codeIs(200)
      callDynamicEndpoint("guard").codeIs(200)
      val row = storedDoc(doc.requestUrl).getOrElse(fail("doc not applied"))

      When("the body hash is changed behind the API's back")
      row.MethodBodyHash("tampered").save
      Then("the endpoint is no longer served")
      MakerChecker.isExecutableDynamicResourceDoc(row.DynamicResourceDocId.get) should be(false)
      callDynamicEndpoint("guard").codeIs(404)
      row.MethodBodyHash(row.ApprovedHash.get).save
      callDynamicEndpoint("guard").codeIs(200)

      When("a maker without the approver role tries to deactivate")
      val forbidden = makePostRequest((v7 / "management" / "dynamic-resource-docs" / row.DynamicResourceDocId.get / "deactivation").POST <@ (user1), """{"comment":"x"}""")
      forbidden.codeIs(403)

      When("the approver deactivates directly")
      val off = makePostRequest((v7 / "management" / "dynamic-resource-docs" / row.DynamicResourceDocId.get / "deactivation").POST <@ (user2), """{"comment":"suspected leak"}""")
      Then("it is audited as an APPROVED DEACTIVATE row and the endpoint stops")
      off.codeIs(200)
      str(off.body, "operation") should equal("DEACTIVATE")
      str(off.body, "status") should equal("APPROVED")
      str(off.body, "requestor_user_id") should equal(resourceUser2.userId)
      storedDoc(doc.requestUrl).get.IsActive.get should be(false)
      callDynamicEndpoint("guard").codeIs(404)

      When("the maker asks to re-activate via an explicit change request and the approver approves it")
      val activate = makePostRequest((v7 / "management" / "dynamic-change-requests").POST <@ (user1),
        s"""{"target_type":"DYNAMIC_RESOURCE_DOC","operation":"ACTIVATE","target_id":"${row.DynamicResourceDocId.get}","proposed_payload":{},"business_justification":"reviewed, false alarm"}""")
      activate.codeIs(201)
      makePostRequest((v7 / "management" / "dynamic-change-requests" / str(activate.body, "dynamic_change_request_id") / "approval").POST <@ (user2),
        s"""{"payload_hash":"${str(activate.body, "payload_hash")}"}""").codeIs(200)
      storedDoc(doc.requestUrl).get.IsActive.get should be(true)
      callDynamicEndpoint("guard").codeIs(200)
    }

    scenario("seeding the approved hash of pre-existing rows runs once per database, not at every boot", VersionOfApi) {
      makerRoles(); checkerRoles()
      val logProvider = code.migration.MigrationScriptLogProvider.migrationScriptLogProvider.vend
      code.migration.MigrationScriptLog.findAll(By(code.migration.MigrationScriptLog.Name, MakerChecker.seedMigrationName)).foreach(_.delete_!)
      def approvedHashOf(url: String): String = Option(storedDoc(url).getOrElse(fail("doc not created")).ApprovedHash.get).getOrElse("")

      Given("a row created before approval was required, so it has no approved hash")
      dynamicCodeOn(); setPropsValues("dynamic_code_requires_approval" -> "false")
      val legacy = newDoc("legacy")
      makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(legacy)).codeIs(201)
      approvedHashOf(legacy.requestUrl) should equal("")
      setPropsValues("dynamic_code_requires_approval" -> "true")
      callDynamicEndpoint("legacy").codeIs(404)

      When("the instance boots with approval required for the first time")
      MakerChecker.seedApprovedHashesIfEnabled()
      Then("the row's current body is treated as approved and the seed is logged")
      approvedHashOf(legacy.requestUrl) should equal(APIUtil.sha256Hex(URLDecoder.decode(legacy.methodBody, "UTF-8")))
      callDynamicEndpoint("legacy").codeIs(200)
      logProvider.isExecuted(MakerChecker.seedMigrationName) should be(true)

      When("a row with no approved hash appears after the seed, e.g. inserted while approval was switched off")
      setPropsValues("dynamic_code_requires_approval" -> "false")
      val late = newDoc("late")
      makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(late)).codeIs(201)
      setPropsValues("dynamic_code_requires_approval" -> "true")
      MakerChecker.seedApprovedHashesIfEnabled()
      Then("a later boot does not bless it: it stays unexecutable until a checker approves it")
      approvedHashOf(late.requestUrl) should equal("")
      MakerChecker.isExecutableDynamicResourceDoc(storedDoc(late.requestUrl).get.DynamicResourceDocId.get) should be(false)
      callDynamicEndpoint("late").codeIs(404)
    }

    scenario("an update is queued with the live hash; rejection needs a comment and leaves the target untouched", ApiEndpoint5, VersionOfApi) {
      enableMakerChecker(); makerRoles(); checkerRoles()
      val doc = newDoc("upd")
      val created = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      makePostRequest((v7 / "management" / "dynamic-change-requests" / str(created.body, "dynamic_change_request_id") / "approval").POST <@ (user2),
        s"""{"payload_hash":"${str(created.body, "payload_hash")}"}""").codeIs(200)
      val row = storedDoc(doc.requestUrl).getOrElse(fail("doc not applied"))
      val docId = row.DynamicResourceDocId.get

      When("the maker PUTs a changed body")
      val changed = doc.copy(dynamicResourceDocId = Some(docId), summary = "changed summary")
      val put = makePutRequest((v4 / "management" / "dynamic-resource-docs" / docId).PUT <@ (user1), write(changed))
      put.codeIs(202)
      str(put.body, "operation") should equal("UPDATE")
      str(put.body, "target_id") should equal(docId)
      str(put.body, "current_payload_hash") should equal(row.MethodBodyHash.get)
      (put.body \ "current_payload" \ "summary").values.toString should equal(doc.summary)
      (put.body \ "proposed_payload" \ "summary").values.toString should equal("changed summary")
      storedDoc(doc.requestUrl).get.Summary.get should equal(doc.summary)
      val id = str(put.body, "dynamic_change_request_id")

      When("the checker rejects without a comment")
      makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "rejection").POST <@ (user2), """{"comment":"  "}""").codeIs(400)
      When("the checker rejects with a comment")
      val rej = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "rejection").POST <@ (user2), """{"comment":"not now"}""")
      rej.codeIs(200)
      str(rej.body, "status") should equal("REJECTED")
      str(rej.body, "checker_comment") should equal("not now")
      storedDoc(doc.requestUrl).get.Summary.get should equal(doc.summary)

      When("a delete is requested and approved")
      val del = makeDeleteRequest((v4 / "management" / "dynamic-resource-docs" / docId).DELETE <@ (user1))
      del.codeIs(202)
      str(del.body, "operation") should equal("DELETE")
      makePostRequest((v7 / "management" / "dynamic-change-requests" / str(del.body, "dynamic_change_request_id") / "approval").POST <@ (user2),
        s"""{"payload_hash":"${str(del.body, "payload_hash")}"}""").codeIs(200)
      storedDoc(doc.requestUrl) should be(None)
    }

    scenario("only the requestor can withdraw; listings and /my work; roles and auth are enforced", ApiEndpoint1, ApiEndpoint2, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      enableMakerChecker(); makerRoles(); checkerRoles()
      val doc = newDoc("wd")
      val resp = makePostRequest((v4 / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
      val id = str(resp.body, "dynamic_change_request_id")

      When("another user tries to withdraw")
      val other = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "withdrawal").POST <@ (user2), """{"comment":"mine now"}""")
      other.codeIs(400)
      other.body.toString should include(DynamicChangeRequestNotRequestor.split(":").head)
      When("the requestor withdraws")
      val wd = makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "withdrawal").POST <@ (user1), """{"comment":"superseded"}""")
      wd.codeIs(200)
      str(wd.body, "status") should equal("WITHDRAWN")

      Then("/my lists it for the maker and the management listing filters by status")
      val mine = makeGetRequest((v7 / "my" / "dynamic-change-requests").GET <@ (user1))
      mine.codeIs(200)
      (mine.body \ "dynamic_change_requests").asInstanceOf[JArray].arr.exists(r => str(r, "dynamic_change_request_id") == id) should be(true)
      val listed = makeGetRequest((v7 / "management" / "dynamic-change-requests").GET <@ (user2) <<? Map("status" -> "WITHDRAWN"))
      listed.codeIs(200)
      (listed.body \ "dynamic_change_requests").asInstanceOf[JArray].arr.forall(r => str(r, "status") == "WITHDRAWN") should be(true)
      (listed.body \ "dynamic_change_requests").asInstanceOf[JArray].arr.exists(r => str(r, "dynamic_change_request_id") == id) should be(true)

      Then("the listing needs the role and authentication")
      makeGetRequest((v7 / "management" / "dynamic-change-requests").GET <@ (user3)).codeIs(403)
      makeGetRequest((v7 / "management" / "dynamic-change-requests").GET).codeIs(401)
      makePostRequest((v7 / "management" / "dynamic-change-requests" / id / "approval").POST <@ (user3), """{"payload_hash":"x"}""").codeIs(403)

      When("an explicit submission is made without the maker role for that target type")
      val noRole = makePostRequest((v7 / "management" / "dynamic-change-requests").POST <@ (user3),
        s"""{"target_type":"DYNAMIC_RESOURCE_DOC","operation":"CREATE","proposed_payload":${write(newDoc("explicit"))},"business_justification":"j"}""")
      noRole.codeIs(403)
      When("an explicit submission is made by the maker and approved")
      val explicitDoc = newDoc("explicit")
      val explicit = makePostRequest((v7 / "management" / "dynamic-change-requests").POST <@ (user1),
        s"""{"target_type":"DYNAMIC_RESOURCE_DOC","operation":"CREATE","proposed_payload":${write(explicitDoc)},"business_justification":"needed by mobile"}""")
      explicit.codeIs(201)
      str(explicit.body, "business_justification") should equal("needed by mobile")
      str(explicit.body, "request_path") should equal("/obp/v4.0.0/management/dynamic-resource-docs")
      makePostRequest((v7 / "management" / "dynamic-change-requests" / str(explicit.body, "dynamic_change_request_id") / "approval").POST <@ (user2),
        s"""{"payload_hash":"${str(explicit.body, "payload_hash")}"}""").codeIs(200)
      storedDoc(explicitDoc.requestUrl).isDefined should be(true)
      callDynamicEndpoint("explicit").codeIs(200)
    }
  }
}
