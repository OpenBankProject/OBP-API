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

import java.util.Base64

import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.generateUUID
import code.api.util.ApiRole.{CanCreateFileAtOneBank, CanGetFileAtOneBank, CanUpdateFileScanStatusAtOneBank, canCreateDynamicEntityDefinition}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.setup.{APIResponse, OBPReq}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.json4s.native.Serialization.write
import org.json4s.{jvalue2extractable, jvalue2monadic}
import org.scalatest.Tag

/**
 * This suite checks personal files and scan status: who may store a file in their own name without a Role and
 * under which limits, that it goes only on the owner's own personal Dynamic Entity records, and that a file's
 * content is served only once it is clean (to its owner and the scanner before that, to nobody when rejected).
 */
class PersonalFilesTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object PersonalFiles extends Tag("personalFiles")

  private lazy val bankId = testBankId1.value
  // Set by defineEntities, which each scenario calls: dynamic entities do not outlive a scenario.
  private var personalEntity = ""
  private var sharedEntity = ""

  private val pdfBytes: Array[Byte] = "%PDF-1.7\n% a pitch deck\n".getBytes("UTF-8")
  private val webpBytes: Array[Byte] = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes("ISO-8859-1")

  /**
   * The test Users are local Users with no login of their own, so their email addresses count as not validated. Each
   * is given a validated login here, as a User who signed up and confirmed their address would have.
   */
  private val loginsMadeHere = scala.collection.mutable.Set[Long]()

  private def setValidated(user: ResourceUser, validated: Boolean): Unit = {
    val login = AuthUser.findByResourceUserPrimaryKey(user.userPrimaryKey.value).openOr {
      // AuthUser.save copies username into the User's name and provider id, so it must be the provider id it has.
      val made = AuthUser(user = user.userPrimaryKey.value, username = user.idGivenByProvider, email = user.emailAddress,
        provider = code.api.Constant.localIdentityProvider).withPassword("Aa1!" + generateUUID()).saveMe()
      loginsMadeHere += made.id
      made
    }
    login.setValidated(validated).saveMe()
  }

  /**
   * The logins made here go, so later suites see the test Users as they were. AuthUser.delete removes the login rows
   * only: AuthUser.delete_! would also delete the Users themselves.
   */
  override def afterAll(): Unit = {
    loginsMadeHere.foreach(id => AuthUser.delete(id))
    super.afterAll()
  }

  /** Personal files on in the test bank, for Users whose email addresses are validated. */
  private def enable(extra: (String, String)*): Unit = {
    setPropsValues((List("files.personal.enabled_bank_ids" -> bankId) ++ extra): _*)
    List(resourceUser1, resourceUser2, resourceUser3).foreach(setValidated(_, validated = true))
  }

  private def withEntitlements[T](grants: (String, String, String)*)(f: => T): T = {
    val added = grants.map { case (bank, userId, role) => Entitlement.entitlement.vend.addEntitlement(bank, userId, role) }
    try f finally added.foreach(Entitlement.entitlement.vend.deleteEntitlement)
  }

  /** A personal and a shared dynamic entity, with fresh names, for this scenario. */
  private def defineEntities(): Unit = withEntitlements((bankId, resourceUser1.userId, canCreateDynamicEntityDefinition.toString)) {
    val suffix = generateUUID().take(8)
    personalEntity = s"pf_application_$suffix"
    sharedEntity = s"pf_shared_$suffix"
    for ((name, personal) <- List(personalEntity -> true, sharedEntity -> false)) {
      val definition = compact(render(("entity_name" -> name) ~ ("has_personal_entity" -> personal) ~
        ("schema" -> (("description" -> "A test record files are attached to.") ~ ("required" -> List("title")) ~
          ("properties" -> ("title" -> (("type" -> "string") ~ ("example" -> "a title"))))))))
      val response = makePostRequest((v7 / "management" / "banks" / bankId / "dynamic-entities").POST <@ (user1), definition)
      withClue(response.body) { response.code should equal(201) }
    }
  }

  /** A personal record of `personalEntity` owned by whoever calls. */
  private def personalRecord(caller: Option[(Consumer, Token)]): String = {
    val response = makePostRequest((v7 / "banks" / bankId / "dynamic-entities" / "my" / personalEntity).POST <@ (caller),
      compact(render("title" -> "my application")))
    withClue(response.body) { response.code should equal(201) }
    (response.body \ personalEntity \ s"${personalEntity}_id").extract[String]
  }

  private def myFiles = v7 / "banks" / bankId / "my" / "files"
  private def file(id: String) = v7 / "banks" / bankId / "files" / id

  private def body(recordId: String, recordType: String = personalEntity, mediaType: String = "application/pdf",
                   bytes: Array[Byte] = pdfBytes): String =
    write(PostMyFileJsonV700("pitch_deck.pdf", mediaType, Base64.getEncoder.encodeToString(bytes), None, recordType, recordId))

  private def message(response: APIResponse): String = response.body.extract[ErrorMessage].message

  /** The status of a content request. A served file is the file itself, which the JSON client cannot read. */
  private def contentStatus(req: OBPReq): Int = {
    val response = OBPReq.client.newCall(req.GET.toOkHttpRequest).execute()
    try response.code() finally response.close()
  }

  private def uploaded(recordId: String): FileJsonV700 = {
    val response = makePostRequest(myFiles.POST <@ (user1), body(recordId))
    withClue(response.body) { response.code should equal(201) }
    response.body.extract[FileJsonV700]
  }

  private def setStatus(fileId: String, status: String): APIResponse =
    withEntitlements((bankId, resourceUser2.userId, CanUpdateFileScanStatusAtOneBank.toString)) {
      makePutRequest((file(fileId) / "scan-status").PUT <@ (user2), write(PutFileScanStatusJsonV700(status, Some("test"))))
    }

  feature(s"Create My File - POST /banks/BANK_ID/my/files - $VersionOfApi") {

    scenario("Personal files are off unless the space is listed", PersonalFiles, VersionOfApi) {
      defineEntities()
      setPropsValues("files.personal.enabled_bank_ids" -> "some-other-bank")
      val response = makePostRequest(myFiles.POST <@ (user1), body(personalRecord(user1)))
      response.code should equal(403)
      message(response) should include(PersonalFilesNotEnabled)
    }

    scenario("The owner's email address must be validated", PersonalFiles, VersionOfApi) {
      defineEntities()
      val recordId = personalRecord(user1)
      enable()
      setValidated(resourceUser1, validated = false)
      val response = try makePostRequest(myFiles.POST <@ (user1), body(recordId)) finally setValidated(resourceUser1, validated = true)
      response.code should equal(403)
      message(response) should include(PersonalFileEmailNotValidated)
    }

    scenario("A User stores a pending, private, personal file on their own record, without a Role", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val recordId = personalRecord(user1)
      val stored = uploaded(recordId)
      stored.is_personal shouldBe true
      stored.is_public shouldBe false
      stored.scan_status should equal("pending")
      stored.owner_user_id should equal(resourceUser1.userId)

      And("it is attached to the record")
      val attachments = makeGetRequest((file(stored.file_id) / "attachments").GET <@ (user1))
      attachments.body.extract[FileAttachmentsJsonV700].attachments.map(a => (a.record_type, a.record_id)) should
        equal(List((personalEntity, recordId)))
    }

    scenario("Only the owner's own personal records take a personal file", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val someoneElses = personalRecord(user2)
      val notMine = makePostRequest(myFiles.POST <@ (user1), body(someoneElses))
      notMine.code should equal(403)
      message(notMine) should include(PersonalFileRecordNotOwned)
      val notPersonal = makePostRequest(myFiles.POST <@ (user1), body("any-id", recordType = sharedEntity))
      notPersonal.code should equal(403)

      And("the owner cannot attach it to another record later, but a holder of the Role can")
      val stored = uploaded(personalRecord(user1))
      val attach = write(PostFileAttachmentJsonV700(personalEntity, someoneElses))
      makePostRequest((file(stored.file_id) / "attachments").POST <@ (user1), attach).code should equal(403)
      withEntitlements((bankId, resourceUser1.userId, CanCreateFileAtOneBank.toString)) {
        makePostRequest((file(stored.file_id) / "attachments").POST <@ (user1), attach).code should equal(201)
      }
    }

    scenario("The personal media types and quota are enforced", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable("files.personal.max_files_per_user" -> "1")
      val recordId = personalRecord(user1)
      val webp = makePostRequest(myFiles.POST <@ (user1), body(recordId, mediaType = "image/webp", bytes = webpBytes))
      webp.code should equal(400)
      message(webp) should include(FileMediaTypeNotAllowed)
      // user3 has no personal files yet in this suite, so the quota of one is theirs to use.
      val record3 = personalRecord(user3)
      makePostRequest(myFiles.POST <@ (user3), body(record3)).code should equal(201)
      val second = makePostRequest(myFiles.POST <@ (user3), body(record3))
      second.code should equal(400)
      message(second) should include(PersonalFileQuotaExceeded)
    }
  }

  feature(s"Scan status - PUT /banks/BANK_ID/files/FILE_ID/scan-status - $VersionOfApi") {

    scenario("A pending file's content is served to its owner only, until it is clean", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val stored = uploaded(personalRecord(user1))
      val content = file(stored.file_id) / "content"
      contentStatus(content <@ (user1)) should equal(200)
      withEntitlements((bankId, resourceUser2.userId, CanGetFileAtOneBank.toString)) {
        val pending = makeGetRequest(content.GET <@ (user2))
        pending.code should equal(403)
        message(pending) should include(FileNotYetScanned)

        When("the scanner marks it clean")
        val cleaned = setStatus(stored.file_id, "clean")
        withClue(cleaned.body) { cleaned.code should equal(200) }
        cleaned.body.extract[FileJsonV700].scan_status should equal("clean")
        Then("staff who may read files get its content")
        contentStatus(content <@ (user2)) should equal(200)
      }
    }

    scenario("A rejected file's content is served to nobody, not even its owner", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val stored = uploaded(personalRecord(user1))
      setStatus(stored.file_id, "rejected").code should equal(200)
      val response = makeGetRequest((file(stored.file_id) / "content").GET <@ (user1))
      response.code should equal(403)
      message(response) should include(FileScanRejected)
    }

    scenario("Setting the status needs the Role and a valid status", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val stored = uploaded(personalRecord(user1))
      makePutRequest((file(stored.file_id) / "scan-status").PUT <@ (user2), write(PutFileScanStatusJsonV700("clean", None)))
        .code should equal(403)
      val invalid = setStatus(stored.file_id, "infected")
      invalid.code should equal(400)
      message(invalid) should include(InvalidFileScanStatus)
    }

    scenario("A scanner lists the pending files of a space, and only those without CanGetFileAtOneBank", PersonalFiles, VersionOfApi) {
      defineEntities()
      enable()
      val stored = uploaded(personalRecord(user1))
      val space = v7 / "management" / "banks" / bankId / "files"
      withEntitlements((bankId, resourceUser2.userId, CanUpdateFileScanStatusAtOneBank.toString)) {
        val pending = makeGetRequest((space <<? List("scan_status" -> "pending")).GET <@ (user2))
        withClue(pending.body) { pending.code should equal(200) }
        pending.body.extract[FilesPageJsonV700].files.map(_.file_id) should contain(stored.file_id)
        pending.body.extract[FilesPageJsonV700].files.map(_.scan_status).distinct should equal(List("pending"))
        makeGetRequest(space.GET <@ (user2)).code should equal(403)
        And("the scanner may read a pending file's content to scan it")
        contentStatus(file(stored.file_id) / "content" <@ (user2)) should equal(200)
      }
    }
  }
}
