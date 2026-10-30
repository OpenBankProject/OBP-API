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

import java.security.MessageDigest
import java.util.Base64

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateFileAtOneBank, CanCreatePublicFileAtOneBank, CanGetFileAtOneBank}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.entitlement.Entitlement
import code.scope.Scope
import code.setup.{APIResponse, OBPReq}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite checks the v7.0.0 file endpoints: who may upload, read, attach and share files, the checks
 * on an upload (media type, content, size, sha256), the bytes and headers of the content endpoints, and
 * that the public endpoints serve public files only, without logging in.
 */
class FilesTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object Files extends Tag("files")

  private lazy val bankId = testBankId1.value
  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  private def files(bank: String = bankId) = v7_0_0_Request / "banks" / bank / "files"
  private def publicFiles(bank: String = bankId) = v7_0_0_Request / "banks" / bank / "public-files"

  // The smallest content that passes the type check: the type's signature followed by some bytes.
  private val pngBytes: Array[Byte] = Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte) ++ "a test image".getBytes("UTF-8")
  private val pdfBytes: Array[Byte] = "%PDF-1.7\n% a test document\n".getBytes("UTF-8")

  private def sha256Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private def body(fileName: String = "photo.png", mediaType: String = "image/png", bytes: Array[Byte] = pngBytes,
                   isPublic: Option[Boolean] = None, sha256: Option[String] = None): String =
    write(PostFileJsonV700(fileName, mediaType, Base64.getEncoder.encodeToString(bytes), isPublic, sha256))

  private def withEntitlements[T](grants: (String, String, String)*)(f: => T): T = {
    val added = grants.map { case (bank, userId, role) => Entitlement.entitlement.vend.addEntitlement(bank, userId, role) }
    try f finally added.foreach(Entitlement.entitlement.vend.deleteEntitlement)
  }

  private def canCreate(userId: String = resourceUser1.userId, bank: String = bankId) =
    (bank, userId, CanCreateFileAtOneBank.toString)

  /** user1 uploads a file and the test gets its id. */
  private def uploaded(isPublic: Boolean = false, bytes: Array[Byte] = pngBytes, mediaType: String = "image/png",
                       bank: String = bankId): FileJsonV700 = {
    val grants = canCreate(bank = bank) +: (if (isPublic) Seq((bank, resourceUser1.userId, CanCreatePublicFileAtOneBank.toString)) else Nil)
    withEntitlements(grants: _*) {
      val response = makePostRequest(files(bank).POST <@ (user1), body(bytes = bytes, mediaType = mediaType, isPublic = Some(isPublic)))
      withClue(response.body) { response.code should equal(201) }
      response.body.extract[FileJsonV700]
    }
  }

  /** The raw response: the test client otherwise reads every body as text, which would spoil the bytes. */
  private def rawGet(req: OBPReq): (Int, Array[Byte], okhttp3.Headers) = {
    val response = OBPReq.client.newCall(req.GET.toOkHttpRequest).execute()
    try (response.code(), Option(response.body()).map(_.bytes()).getOrElse(Array.emptyByteArray), response.headers())
    finally response.close()
  }

  private def message(response: APIResponse): String = response.body.extract[ErrorMessage].message

  feature(s"Create File - POST /banks/BANK_ID/files - $VersionOfApi") {

    scenario("Anonymous access fails with 401", Files, VersionOfApi) {
      makePostRequest(files().POST, body()).code should equal(401)
    }

    scenario("A user without CanCreateFileAtOneBank gets 403", Files, VersionOfApi) {
      val response = makePostRequest(files().POST <@ (user2), body())
      response.code should equal(403)
      message(response) should include(CanCreateFileAtOneBank.toString)
    }

    scenario("A user with CanCreateFileAtOneBank stores a file and owns it", Files, VersionOfApi) {
      val file = uploaded()
      file.bank_id should equal(bankId)
      file.file_name should equal("photo.png")
      file.media_type should equal("image/png")
      file.size_in_bytes should equal(pngBytes.length.toLong)
      file.sha256 should equal(sha256Hex(pngBytes))
      file.is_public should equal(false)
      file.owner_user_id should equal(resourceUser1.userId)
    }

    scenario("The Role at another bank does not cover this bank, and there is no any-bank Role", Files, VersionOfApi) {
      withEntitlements(canCreate(bank = SYS)) {
        makePostRequest(files().POST <@ (user1), body()).code should equal(403)
      }
    }

    scenario("The Role at SYS lets a user store a file in the system space", Files, VersionOfApi) {
      uploaded(bank = SYS).bank_id should equal(SYS)
    }

    scenario("is_public needs CanCreatePublicFileAtOneBank as well", Files, VersionOfApi) {
      withEntitlements(canCreate()) {
        val response = makePostRequest(files().POST <@ (user1), body(isPublic = Some(true)))
        response.code should equal(403)
        message(response) should include(CanCreatePublicFileAtOneBank.toString)
      }
      uploaded(isPublic = true).is_public should equal(true)
    }

    scenario("Uploads that fail the checks get 400", Files, VersionOfApi) {
      withEntitlements(canCreate()) {
        def post(json: String) = makePostRequest(files().POST <@ (user1), json)

        Then("a media type outside the allowed list")
        message(post(body(fileName = "page.html", mediaType = "text/html", bytes = "<script>x</script>".getBytes("UTF-8")))) should include(FileMediaTypeNotAllowed)

        And("content that is not of the media type it claims")
        message(post(body(mediaType = "image/png", bytes = pdfBytes))) should include(InvalidFile)

        And("content that is not base64")
        message(post(write(PostFileJsonV700("photo.png", "image/png", "not base64 !!", None, None)))) should include(InvalidFile)

        And("a sha256 that does not match the content")
        message(post(body(sha256 = Some(sha256Hex(pdfBytes))))) should include(FileSha256Mismatch)

        And("a matching sha256 is accepted, in any letter case")
        post(body(sha256 = Some(sha256Hex(pngBytes).toUpperCase))).code should equal(201)
      }
    }

    scenario("A file larger than files.max_size_in_bytes gets 400", Files, VersionOfApi) {
      setPropsValues("files.max_size_in_bytes" -> "10")
      withEntitlements(canCreate()) {
        val response = makePostRequest(files().POST <@ (user1), body())
        response.code should equal(400)
        message(response) should include(FileTooLarge)
      }
    }

    scenario("files.allowed_media_types can narrow the list but never extend it", Files, VersionOfApi) {
      setPropsValues("files.allowed_media_types" -> "application/pdf,text/html")
      withEntitlements(canCreate()) {
        message(makePostRequest(files().POST <@ (user1), body())) should include(FileMediaTypeNotAllowed)
        message(makePostRequest(files().POST <@ (user1),
          body(fileName = "page.html", mediaType = "text/html", bytes = "<html></html>".getBytes("UTF-8")))) should include(FileMediaTypeNotAllowed)
        makePostRequest(files().POST <@ (user1), body(fileName = "doc.pdf", mediaType = "application/pdf", bytes = pdfBytes)).code should equal(201)
      }
    }

    scenario("A Consumer holding the Role as a Scope stores a file as itself, owned by its own User", Files, VersionOfApi) {
      val scope = Scope.scope.vend.addScope(bankId, testConsumer2.id.get.toString, CanCreateFileAtOneBank.toString)
      try {
        makePostRequest(files().POST <@ (user2), body()).code should equal(403)
        val response = makePostRequest(files().POST <@ (application2), body())
        response.code should equal(201)
        val ownUser = code.model.UserX.findByProviderId(code.api.OAuth2Login.OBPOIDC.clientCredentialsProvider, testConsumer2.key.get)
          .map(_.userId).openOrThrowException("own User")
        response.body.extract[FileJsonV700].owner_user_id should equal(ownUser)
      } finally Scope.scope.vend.deleteScope(scope)
    }
  }

  feature(s"Reading a file - Get File, Get File Content, Get My Files - $VersionOfApi") {

    scenario("The owner reads it; another user only with CanGetFileAtOneBank or access", Files, VersionOfApi) {
      val file = uploaded()
      val one = files() / file.file_id

      makeGetRequest(one.GET).code should equal(401)
      makeGetRequest(one.GET <@ (user1)).body.extract[FileJsonV700].file_id should equal(file.file_id)

      val refused = makeGetRequest(one.GET <@ (user2))
      refused.code should equal(403)
      message(refused) should include(CanGetFileAtOneBank.toString)

      withEntitlements((bankId, resourceUser2.userId, CanGetFileAtOneBank.toString)) {
        makeGetRequest(one.GET <@ (user2)).code should equal(200)
      }

      makePostRequest((one / "access").POST <@ (user1), write(PostFileAccessJsonV700(resourceUser2.userId))).code should equal(201)
      makeGetRequest(one.GET <@ (user2)).code should equal(200)
      makeGetRequest((v7_0_0_Request / "banks" / bankId / "my" / "files").GET <@ (user2))
        .body.extract[FilesJsonV700].files.map(_.file_id) should contain(file.file_id)

      makeDeleteRequest((one / "access" / resourceUser2.userId).DELETE <@ (user1)).code should equal(204)
      makeGetRequest(one.GET <@ (user2)).code should equal(403)
    }

    scenario("A file of another space is not found", Files, VersionOfApi) {
      val file = uploaded()
      makeGetRequest((files(SYS) / file.file_id).GET <@ (user1)).code should equal(404)
      message(makeGetRequest((files() / "no-such-file").GET <@ (user1))) should include(FileNotFound)
    }

    scenario("Get File Content returns the bytes with the file's headers", Files, VersionOfApi) {
      val file = uploaded()
      val (code, bytes, headers) = rawGet(files() / file.file_id / "content" <@ (user1))
      code should equal(200)
      bytes.toSeq should equal(pngBytes.toSeq)
      headers.get("Content-Type") should startWith("image/png")
      headers.get("ETag") should equal("\"" + sha256Hex(pngBytes) + "\"")
      headers.get("X-Content-Type-Options") should equal("nosniff")
      headers.get("Content-Security-Policy") should include("sandbox")
      headers.get("Cache-Control") should equal("private, no-store")
      headers.get("Content-Disposition") should equal("attachment; filename=\"photo.png\"")

      rawGet((files() / file.file_id / "content" <@ (user1)) <<? List("inline" -> "true"))._3
        .get("Content-Disposition") should startWith("inline")

      rawGet(files() / file.file_id / "content" <@ (user2))._1 should equal(403)
    }

    scenario("Get My Files lists the files a user owns", Files, VersionOfApi) {
      val file = uploaded()
      makeGetRequest((v7_0_0_Request / "banks" / bankId / "my" / "files").GET <@ (user1))
        .body.extract[FilesJsonV700].files.map(_.file_id) should contain(file.file_id)
    }
  }

  feature(s"Get Files of a Space - GET /management/banks/BANK_ID/files - $VersionOfApi") {

    scenario("Lists every file of the space, newest first, for a holder of CanGetFileAtOneBank only", Files, VersionOfApi) {
      val older = uploaded()
      val newer = uploaded()
      val request = v7_0_0_Request / "management" / "banks" / bankId / "files"

      makeGetRequest(request.GET).code should equal(401)
      val refused = makeGetRequest(request.GET <@ (user2))
      refused.code should equal(403)
      message(refused) should include(CanGetFileAtOneBank.toString)

      withEntitlements((bankId, resourceUser2.userId, CanGetFileAtOneBank.toString)) {
        val page = makeGetRequest(request.GET <@ (user2)).body.extract[FilesPageJsonV700]
        val ids = page.files.map(_.file_id)
        ids should contain allOf (older.file_id, newer.file_id)
        ids.indexOf(newer.file_id) should be < ids.indexOf(older.file_id)
        page.pagination.total should be >= 2L

        val first = makeGetRequest(request.GET <@ (user2) <<? List("limit" -> "1")).body.extract[FilesPageJsonV700]
        first.files.size should equal(1)
        first.pagination.limit should equal(1)

        message(makeGetRequest(request.GET <@ (user2) <<? List("limit" -> "0"))) should include(InvalidFileQueryParameter)
      }
    }
  }

  feature(s"Public files - $VersionOfApi") {

    scenario("A public file is served without logging in, and says nothing of who stored it", Files, VersionOfApi) {
      val file = uploaded(isPublic = true, bytes = pdfBytes, mediaType = "application/pdf")

      val metadata = makeGetRequest((publicFiles() / file.file_id).GET)
      metadata.code should equal(200)
      metadata.body.extract[PublicFileJsonV700].sha256 should equal(sha256Hex(pdfBytes))
      (metadata.body \ "owner_user_id").toOption should equal(None)

      val (code, bytes, headers) = rawGet(publicFiles() / file.file_id / "content")
      code should equal(200)
      bytes.toSeq should equal(pdfBytes.toSeq)
      headers.get("Content-Type") should startWith("application/pdf")
      headers.get("Cache-Control") should equal("public, max-age=3600")
    }

    scenario("A file that is not public is not found on the public endpoints, even for its owner", Files, VersionOfApi) {
      val file = uploaded()
      message(makeGetRequest((publicFiles() / file.file_id).GET)) should include(FileNotFound)
      makeGetRequest((publicFiles() / file.file_id).GET <@ (user1)).code should equal(404)
      rawGet(publicFiles() / file.file_id / "content")._1 should equal(404)
    }

    scenario("Update File Is Public needs CanCreatePublicFileAtOneBank, and withdraws a file from the public endpoints", Files, VersionOfApi) {
      val file = uploaded(isPublic = true)
      val isPublic = files() / file.file_id / "is-public"

      val refused = makePutRequest(isPublic.PUT <@ (user1), write(PutFileIsPublicJsonV700(false)))
      refused.code should equal(403)
      message(refused) should include(CanCreatePublicFileAtOneBank.toString)

      withEntitlements((bankId, resourceUser1.userId, CanCreatePublicFileAtOneBank.toString)) {
        val withdrawn = makePutRequest(isPublic.PUT <@ (user1), write(PutFileIsPublicJsonV700(false)))
        withdrawn.code should equal(200)
        withdrawn.body.extract[FileJsonV700].is_public should equal(false)
      }
      makeGetRequest((publicFiles() / file.file_id).GET).code should equal(404)
    }
  }

  feature(s"Attachments and access - $VersionOfApi") {

    scenario("The owner attaches a file to a record once; readers see it on the record", Files, VersionOfApi) {
      val privateFile = uploaded()
      val publicFile = uploaded(isPublic = true)
      val record = PostFileAttachmentJsonV700("customer", s"customer-${java.util.UUID.randomUUID()}")

      makePostRequest((files() / privateFile.file_id / "attachments").POST <@ (user2), write(record)).code should equal(403)

      val attached = makePostRequest((files() / privateFile.file_id / "attachments").POST <@ (user1), write(record))
      attached.code should equal(201)
      val attachment = attached.body.extract[FileAttachmentJsonV700]
      message(makePostRequest((files() / privateFile.file_id / "attachments").POST <@ (user1), write(record))) should include(FileAttachmentAlreadyExists)
      makePostRequest((files() / publicFile.file_id / "attachments").POST <@ (user1), write(record)).code should equal(201)

      val onRecord = List("record_type" -> record.record_type, "record_id" -> record.record_id)
      makeGetRequest(files().GET <@ (user1) <<? onRecord).body.extract[FilesJsonV700].files.map(_.file_id) should
        contain theSameElementsAs List(privateFile.file_id, publicFile.file_id)
      makeGetRequest(files().GET <@ (user2) <<? onRecord).body.extract[FilesJsonV700].files should be(empty)
      makeGetRequest(publicFiles().GET <<? onRecord).body.extract[PublicFilesJsonV700].files.map(_.file_id) should
        equal(List(publicFile.file_id))
      message(makeGetRequest(files().GET <@ (user1))) should include(InvalidFileQueryParameter)

      makeGetRequest((files() / privateFile.file_id / "attachments").GET <@ (user1))
        .body.extract[FileAttachmentsJsonV700].attachments.map(_.file_attachment_id) should equal(List(attachment.file_attachment_id))

      val detach = files() / privateFile.file_id / "attachments" / attachment.file_attachment_id
      makeDeleteRequest(detach.DELETE <@ (user2)).code should equal(403)
      makeDeleteRequest(detach.DELETE <@ (user1)).code should equal(204)
      message(makeDeleteRequest(detach.DELETE <@ (user1))) should include(FileAttachmentNotFound)
    }

    scenario("Only the owner gives and takes access", Files, VersionOfApi) {
      val file = uploaded()
      val access = files() / file.file_id / "access"

      message(makePostRequest(access.POST <@ (user2), write(PostFileAccessJsonV700(resourceUser2.userId)))) should include(FileOwnerRequired)
      message(makePostRequest(access.POST <@ (user1), write(PostFileAccessJsonV700("no-such-user")))) should include(UserNotFoundByUserId)

      And("a consent user is refused: access given to one would end with its Consent")
      resourceUser4.isConsentUser should equal(true)
      val consentUser = makePostRequest(access.POST <@ (user1), write(PostFileAccessJsonV700(resourceUser4.userId)))
      consentUser.code should equal(400)
      message(consentUser) should include(InvalidUserId)

      makePostRequest(access.POST <@ (user1), write(PostFileAccessJsonV700(resourceUser2.userId))).code should equal(201)
      message(makePostRequest(access.POST <@ (user1), write(PostFileAccessJsonV700(resourceUser2.userId)))) should include(FileAccessAlreadyExists)

      makeGetRequest(access.GET <@ (user1)).body.extract[FileAccessListJsonV700].access.map(_.user_id) should equal(List(resourceUser2.userId))
      makeGetRequest(access.GET <@ (user2)).code should equal(403)

      makeDeleteRequest((access / resourceUser2.userId).DELETE <@ (user2)).code should equal(403)
      makeDeleteRequest((access / resourceUser2.userId).DELETE <@ (user1)).code should equal(204)
      message(makeDeleteRequest((access / resourceUser2.userId).DELETE <@ (user1))) should include(FileAccessNotFound)
    }
  }
}
