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

import cats.effect.IO
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, UserXORApplication, unboxFullOrFail}
import code.api.util.ApiRole
import code.api.util.ApiRole.{canCreateFileAtOneBank, canCreatePublicFileAtOneBank, canGetFileAtOneBank}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers.BytesResponse
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, Glossary}
import code.files.{FileT, Files}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.util.Helpers.tryo
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Try

/**
 * This object holds the v7.0.0 file endpoints: documents such as PDFs and photographs that belong to a
 * space (a bank, or SYS) and can be attached to records. See [[code.files.Files]] and the Glossary item
 * "Files".
 *
 * Uploading needs CanCreateFileAtOneBank at the space, and making a file public also needs
 * CanCreatePublicFileAtOneBank. There are no AtAnyBank Roles: a Role at SYS covers the system space.
 * A logged-in caller may read a file they own, one they have been given access to, or any file of a
 * space where they hold CanGetFileAtOneBank. Public files are served by separate endpoints, without
 * logging in, which serve nothing else.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Files {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  /** The most this instance may ever allow. The props can narrow this list, never extend it. */
  val MaxAllowedMediaTypes: List[String] = List("image/jpeg", "image/png", "image/webp", "application/pdf")

  /** files.allowed_media_types, kept to the types in MaxAllowedMediaTypes; all of them when it is not set. */
  def allowedMediaTypes: List[String] =
    APIUtil.getPropsValue("files.allowed_media_types").map(_.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList)
      .map(configured => MaxAllowedMediaTypes.filter(configured.contains))
      .openOr(MaxAllowedMediaTypes)

  val DefaultMaxSizeInBytes: Long = 10L * 1024 * 1024

  def maxSizeInBytes: Long = APIUtil.getPropsAsLongValue("files.max_size_in_bytes").openOr(DefaultMaxSizeInBytes)

  val MaxNameLength = 255

  /** Whether the bytes start the way a file of this media type does, so a file cannot claim to be what it is not. */
  private def contentMatches(mediaType: String, bytes: Array[Byte]): Boolean = {
    def startsWith(signature: Int*): Boolean =
      bytes.length >= signature.length && signature.zipWithIndex.forall { case (b, i) => (bytes(i) & 0xff) == b }
    mediaType match {
      case "image/jpeg" => startsWith(0xff, 0xd8, 0xff)
      case "image/png" => startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
      case "image/webp" => bytes.length >= 12 && startsWith(0x52, 0x49, 0x46, 0x46) &&
        new String(bytes.slice(8, 12), "US-ASCII") == "WEBP"
      case "application/pdf" => startsWith(0x25, 0x50, 0x44, 0x46, 0x2d) // %PDF-
      case _ => false
    }
  }

  private def sha256Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  /** The ids the caller acts as: their own, and the User an agent acts for. */
  private def actingUserIds(user: User, cc: CallContext): Set[String] =
    Set(user.userId, cc.onBehalfOfUserId).filter(_.nonEmpty)

  private def holdsRole(bankId: String, user: User, cc: CallContext, role: ApiRole): Boolean =
    APIUtil.handleAccessControlWithAuthMode(bankId, user.userId, APIUtil.getConsumerPrimaryKey(Some(cc)), List(role), UserXORApplication)

  private def isOwner(file: FileT, user: User, cc: CallContext): Boolean = actingUserIds(user, cc).contains(file.ownerUserId)

  private def canRead(file: FileT, user: User, cc: CallContext): Boolean =
    isOwner(file, user, cc) ||
      actingUserIds(user, cc).exists(id => Files.provider.vend.getAccess(file.fileId, id).isDefined) ||
      holdsRole(file.bankId, user, cc, canGetFileAtOneBank)

  private def readDeniedMessage(bankId: String): String =
    s"$UserHasMissingRoles $canGetFileAtOneBank at bank_id $bankId. Or be the file's owner, or be given access to it."

  /** The file, if the caller may read it: 404 when there is no such file, 403 when it is not theirs to read. */
  private def readableFile(bankId: String, fileId: String, user: User, cc: CallContext): Future[FileT] =
    for {
      file <- Future(Files.provider.vend.getFile(bankId, fileId))
        .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
      _ <- Helper.booleanToFuture(readDeniedMessage(bankId), 403, Some(cc))(canRead(file, user, cc))
    } yield file

  private def publicFile(bankId: String, fileId: String, cc: CallContext): Future[FileT] =
    Future(Files.provider.vend.getFile(bankId, fileId).filter(_.isPublic))
      .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))

  private def contentOf(file: FileT, inline: Boolean, cacheControl: String, cc: CallContext): Future[BytesResponse] =
    Future(Files.provider.vend.getContent(file.fileId))
      .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound The content of file ${file.fileId} is missing.", 404))
      .map(bytes => BytesResponse(
        bytes = bytes,
        mediaType = MediaType.parse(file.mediaType).getOrElse(MediaType.application.`octet-stream`),
        fileName = file.fileName,
        inline = inline,
        etag = file.sha256,
        cacheControl = cacheControl,
        metricSummary = Map("file_id" -> file.fileId, "media_type" -> file.mediaType, "size_in_bytes" -> bytes.length)))

  private def inlineRequested(req: Request[IO]): Boolean =
    req.uri.query.params.get("inline").exists(_.trim.equalsIgnoreCase("true"))

  /** record_type and record_id, both required. */
  private def recordOf(req: Request[IO], cc: CallContext): Future[(String, String)] = {
    val parameters = req.uri.query.params
    val recordType = parameters.get("record_type").map(_.trim).getOrElse("")
    val recordId = parameters.get("record_id").map(_.trim).getOrElse("")
    Helper.booleanToFuture(s"$InvalidFileQueryParameter record_type and record_id are both required.", 400, Some(cc)) {
      recordType.nonEmpty && recordId.nonEmpty
    }.map(_ => (recordType, recordId))
  }

  private val whoMayRead =
    s"""**Who may call it.** The file's owner, a User the owner has given access to, or a User with the Role
       |CanGetFileAtOneBank at BANK_ID (or an application acting as itself whose Consumer holds it as a Scope).""".stripMargin

  // Route: POST /obp/v7.0.0/banks/BANK_ID/files (201)
  lazy val createFile: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "files" =>
      EndpointHelpers.withUserAndBodyCreated[PostFileJsonV700, FileJsonV700](req) { (user, body, cc) =>
        val fileName = Option(body.file_name).map(_.trim).getOrElse("")
        val mediaType = Option(body.media_type).map(_.trim.toLowerCase).getOrElse("")
        val isPublic = body.is_public.getOrElse(false)
        val allowed = allowedMediaTypes
        val maxSize = maxSizeInBytes
        for {
          _ <- Helper.booleanToFuture(s"$UserHasMissingRoles $canCreatePublicFileAtOneBank at bank_id $bankIdInUrl. It is needed to create a file with is_public true.", 403, Some(cc)) {
            !isPublic || holdsRole(bankIdInUrl, user, cc, canCreatePublicFileAtOneBank)
          }
          _ <- Helper.booleanToFuture(s"$InvalidFile file_name must be 1 to $MaxNameLength characters.", 400, Some(cc)) {
            fileName.nonEmpty && fileName.length <= MaxNameLength
          }
          _ <- Helper.booleanToFuture(s"$FileMediaTypeNotAllowed media_type must be one of ${allowed.mkString(", ")}. Current media_type is $mediaType.", 400, Some(cc)) {
            allowed.contains(mediaType)
          }
          bytes <- Future(tryo(Base64.getMimeDecoder.decode(Option(body.content_base64).getOrElse(""))))
            .map(unboxFullOrFail(_, Some(cc), s"$InvalidFile content_base64 is not valid base64.", 400))
          _ <- Helper.booleanToFuture(s"$InvalidFile The file is empty.", 400, Some(cc))(bytes.nonEmpty)
          _ <- Helper.booleanToFuture(s"$FileTooLarge The most allowed is $maxSize bytes. The file has ${bytes.length}.", 400, Some(cc)) {
            bytes.length <= maxSize
          }
          _ <- Helper.booleanToFuture(s"$InvalidFile The content is not a file of media_type $mediaType.", 400, Some(cc)) {
            contentMatches(mediaType, bytes)
          }
          sha256 = sha256Hex(bytes)
          _ <- Helper.booleanToFuture(s"$FileSha256Mismatch The content's sha256 is $sha256.", 400, Some(cc)) {
            body.sha256.map(_.trim).filter(_.nonEmpty).forall(_.equalsIgnoreCase(sha256))
          }
          file <- Future(Files.provider.vend.createFile(bankIdInUrl, sha256, mediaType, fileName, isPublic,
            user.userId, bytes))
            .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not store the file.", 400))
        } yield JSONFactory700Files.createFileJson(file)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFile),
    "POST",
    "/banks/BANK_ID/files",
    "Create File",
    s"""Stores a file, such as a PDF or a photograph, in a space: a bank, or `SYS` for the system space.
       |The caller becomes its owner (the User an agent acts for, when one does). A file's content never
       |changes once stored: a new version of a document is a new file.
       |
       |The fields:
       |
       |- `file_name`: 1 to $MaxNameLength characters.
       |- `media_type`: one of the types this instance allows. At most these are ever allowed:
       |  ${MaxAllowedMediaTypes.mkString(", ")}; the prop `files.allowed_media_types` can narrow the list. The
       |  content must really be of that type.
       |- `content_base64`: the file, base64 encoded. Its size is limited by the prop `files.max_size_in_bytes`
       |  (default $DefaultMaxSizeInBytes bytes).
       |- `sha256`: optional, the hex SHA-256 of the content. When given, it must match. OBP computes and stores
       |  its own either way.
       |- `is_public`: optional, default false. A public file can be read by anyone, without logging in, through
       |  Get Public File and Get Public File Content. Making a file public also needs the Role
       |  CanCreatePublicFileAtOneBank at BANK_ID.
       |
       |**Who may call it.** A User with the Role CanCreateFileAtOneBank at BANK_ID (`SYS` for the system space),
       |or an application acting as itself whose Consumer holds it as a Scope.
       |
       |See ${Glossary.getGlossaryItemLink("Files")}.
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700Files.postFileJsonExample,
    JSONFactory700Files.fileJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidFile,
      FileMediaTypeNotAllowed, FileTooLarge, FileSha256Mismatch, UnknownError),
    List(apiTagFile),
    Some(List(canCreateFileAtOneBank)),
    authMode = UserXORApplication,
    http4sPartialFunction = Some(createFile)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/files?record_type=&record_id=
  lazy val getFilesAttachedToRecord: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "files" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          (recordType, recordId) <- recordOf(req, cc)
          files <- Future(Files.provider.vend.getFilesAttachedTo(bankIdInUrl, recordType, recordId))
        } yield JSONFactory700Files.createFilesJson(files.filter(canRead(_, user, cc)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilesAttachedToRecord),
    "GET",
    "/banks/BANK_ID/files",
    "Get Files Attached to a Record",
    s"""Lists the files attached to one record, oldest attachment first. Both query parameters are required:
       |
       |- `record_type`: the kind of record, as given when the file was attached, e.g. `customer`
       |- `record_id`: the record's id
       |
       |Only the files the caller may read are listed: those they own, those they have been given access to, or
       |all of them with the Role CanGetFileAtOneBank at BANK_ID.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.filesJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, InvalidFileQueryParameter, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFilesAttachedToRecord)
  ).allowSystemSpace()

  /** The most files one page of Get Files of a Space returns, and the default page size. */
  val MaxPageSize = 500

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/files
  lazy val getFilesOfSpace: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "files" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val parameters = req.uri.query.params
        def number(name: String, default: Int): Int =
          parameters.get(name).map(_.trim).filter(_.nonEmpty).map(value => Try(value.toInt).getOrElse(-1)).getOrElse(default)
        val limit = number("limit", MaxPageSize)
        val offset = number("offset", 0)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidFileQueryParameter limit must be a whole number from 1 to $MaxPageSize.", 400, Some(cc)) {
            limit >= 1 && limit <= MaxPageSize
          }
          _ <- Helper.booleanToFuture(s"$InvalidFileQueryParameter offset must be a whole number, 0 or more.", 400, Some(cc)) {
            offset >= 0
          }
          (files, total) <- Future(Files.provider.vend.getFiles(bankIdInUrl, limit, offset))
        } yield JSONFactory700Files.createFilesPageJson(files, total, limit, offset)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilesOfSpace),
    "GET",
    "/management/banks/BANK_ID/files",
    "Get Files of a Space",
    s"""Lists all the files of a space (a bank, or `SYS` for the system space), newest first, whoever owns them.
       |
       |Query parameters, both optional:
       |
       |- `limit`: the most files to return, from 1 to $MaxPageSize (default $MaxPageSize)
       |- `offset`: how many files to skip (default 0)
       |
       |`pagination.total` is the number of files in the space.
       |
       |**Who may call it.** A User with the Role CanGetFileAtOneBank at BANK_ID, or an application acting as itself
       |whose Consumer holds it as a Scope.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.filesPageJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidFileQueryParameter, UnknownError),
    List(apiTagFile),
    Some(List(canGetFileAtOneBank)),
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFilesOfSpace)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/my/files
  lazy val getMyFiles: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "my" / "files" =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        Future(actingUserIds(user, cc).toList.flatMap(Files.provider.vend.getFilesOwnedOrSharedWith(bankIdInUrl, _)))
          .map(files => JSONFactory700Files.createFilesJson(
            files.groupBy(_.fileId).values.map(_.head).toList.sortBy(_.createdAt.getTime)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getMyFiles),
    "GET",
    "/banks/BANK_ID/my/files",
    "Get My Files",
    s"""Lists the files of the space that the caller owns or has been given access to, oldest first.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.filesJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getMyFiles)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/files/FILE_ID
  lazy val getFile: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId if fileId.nonEmpty =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        readableFile(bankIdInUrl, fileId, user, cc).map(JSONFactory700Files.createFileJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFile),
    "GET",
    "/banks/BANK_ID/files/FILE_ID",
    "Get File",
    s"""Returns what is known about a file: its name, media type, size, SHA-256, whether it is public, its owner
       |and when it was stored. Get File Content returns the file itself.
       |
       |$whoMayRead
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.fileJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, UserHasMissingRoles, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFile)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/content
  lazy val getFileContent: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "content" if fileId.nonEmpty =>
      EndpointHelpers.withUserBytes(req) { (user, cc) =>
        readableFile(bankIdInUrl, fileId, user, cc).flatMap(contentOf(_, inlineRequested(req), "private, no-store", cc))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFileContent),
    "GET",
    "/banks/BANK_ID/files/FILE_ID/content",
    "Get File Content",
    s"""Returns the file itself, not JSON: the body is the file, with the file's own `Content-Type`, its SHA-256 as
       |the `ETag`, and `Content-Disposition` naming the file. Add `?inline=true` to have a browser show it rather
       |than save it. Errors are JSON, as everywhere else.
       |
       |$whoMayRead
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, UserHasMissingRoles, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFileContent)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/is-public
  lazy val updateFileIsPublic: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "is-public" if fileId.nonEmpty =>
      EndpointHelpers.withUserAndBody[PutFileIsPublicJsonV700, FileJsonV700](req) { (_, body, cc) =>
        Future(Files.provider.vend.getFile(bankIdInUrl, fileId))
          .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
          .map(_ => Files.provider.vend.setPublic(bankIdInUrl, fileId, body.is_public))
          .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not update the file.", 400))
          .map(JSONFactory700Files.createFileJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateFileIsPublic),
    "PUT",
    "/banks/BANK_ID/files/FILE_ID/is-public",
    "Update File Is Public",
    s"""Makes a file public, or withdraws it, for example one published by mistake. Its content does not change.
       |
       |Get Public File Content lets clients cache a public file for up to an hour, so a copy may still be served
       |from a cache for that long after the file is withdrawn.
       |
       |**Who may call it.** A User with the Role CanCreatePublicFileAtOneBank at BANK_ID, or an application acting
       |as itself whose Consumer holds it as a Scope.
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700Files.putFileIsPublicJsonExample,
    JSONFactory700Files.fileJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, FileNotFound, UnknownError),
    List(apiTagFile),
    Some(List(canCreatePublicFileAtOneBank)),
    authMode = UserXORApplication,
    http4sPartialFunction = Some(updateFileIsPublic)
  ).allowSystemSpace()

  private def manageDeniedMessage(bankId: String): String =
    s"$UserHasMissingRoles $canCreateFileAtOneBank at bank_id $bankId. Or be the file's owner."

  // Route: POST /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/attachments (201)
  lazy val createFileAttachment: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "attachments" if fileId.nonEmpty =>
      EndpointHelpers.withUserAndBodyCreated[PostFileAttachmentJsonV700, FileAttachmentJsonV700](req) { (user, body, cc) =>
        val recordType = Option(body.record_type).map(_.trim).getOrElse("")
        val recordId = Option(body.record_id).map(_.trim).getOrElse("")
        for {
          file <- Future(Files.provider.vend.getFile(bankIdInUrl, fileId))
            .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
          _ <- Helper.booleanToFuture(manageDeniedMessage(bankIdInUrl), 403, Some(cc)) {
            isOwner(file, user, cc) || holdsRole(bankIdInUrl, user, cc, canCreateFileAtOneBank)
          }
          _ <- Helper.booleanToFuture(s"$InvalidFile record_type and record_id must each be 1 to 255 characters.", 400, Some(cc)) {
            recordType.nonEmpty && recordType.length <= 255 && recordId.nonEmpty && recordId.length <= 255
          }
          _ <- Helper.booleanToFuture(s"$FileAttachmentAlreadyExists Current record is $recordType $recordId.", 409, Some(cc)) {
            !Files.provider.vend.attachmentExists(fileId, recordType, recordId)
          }
          attachment <- Future(Files.provider.vend.createAttachment(file, recordType, recordId, user.userId))
            .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not attach the file.", 400))
        } yield JSONFactory700Files.createFileAttachmentJson(attachment)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFileAttachment),
    "POST",
    "/banks/BANK_ID/files/FILE_ID/attachments",
    "Create File Attachment",
    s"""Attaches a file to a record, such as a Customer or a Dynamic Entity record. `record_type` names the kind
       |of record (e.g. `customer`, or a Dynamic Entity's name) and `record_id` its id; OBP does not look the
       |record up. A file is attached to a record at most once.
       |
       |**Who may call it.** The file's owner, or a User with the Role CanCreateFileAtOneBank at BANK_ID (or an
       |application acting as itself whose Consumer holds it as a Scope).
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700Files.postFileAttachmentJsonExample,
    JSONFactory700Files.fileAttachmentJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, InvalidJsonFormat, FileNotFound, UserHasMissingRoles,
      InvalidFile, FileAttachmentAlreadyExists, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(createFileAttachment)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/attachments
  lazy val getFileAttachments: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "attachments" if fileId.nonEmpty =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        readableFile(bankIdInUrl, fileId, user, cc)
          .map(_ => JSONFactory700Files.createFileAttachmentsJson(Files.provider.vend.getAttachments(bankIdInUrl, fileId)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFileAttachments),
    "GET",
    "/banks/BANK_ID/files/FILE_ID/attachments",
    "Get File Attachments",
    s"""Lists the records a file is attached to, oldest first.
       |
       |$whoMayRead
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.fileAttachmentsJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, UserHasMissingRoles, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFileAttachments)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/attachments/FILE_ATTACHMENT_ID (204)
  lazy val deleteFileAttachment: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "attachments" / fileAttachmentId
      if fileId.nonEmpty && fileAttachmentId.nonEmpty =>
      EndpointHelpers.withUserDelete(req) { (user, cc) =>
        for {
          file <- Future(Files.provider.vend.getFile(bankIdInUrl, fileId))
            .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
          _ <- Helper.booleanToFuture(manageDeniedMessage(bankIdInUrl), 403, Some(cc)) {
            isOwner(file, user, cc) || holdsRole(bankIdInUrl, user, cc, canCreateFileAtOneBank)
          }
          attachment <- Future(Files.provider.vend.getAttachment(bankIdInUrl, fileId, fileAttachmentId))
            .map(unboxFullOrFail(_, Some(cc), s"$FileAttachmentNotFound Current FILE_ATTACHMENT_ID is $fileAttachmentId", 404))
          deleted <- Future(Files.provider.vend.deleteAttachment(attachment.fileAttachmentId))
            .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not detach the file.", 400))
        } yield deleted
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteFileAttachment),
    "DELETE",
    "/banks/BANK_ID/files/FILE_ID/attachments/FILE_ATTACHMENT_ID",
    "Delete File Attachment",
    s"""Detaches a file from a record. The file itself is kept.
       |
       |**Who may call it.** The file's owner, or a User with the Role CanCreateFileAtOneBank at BANK_ID (or an
       |application acting as itself whose Consumer holds it as a Scope).
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, UserHasMissingRoles, FileAttachmentNotFound, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(deleteFileAttachment)
  ).allowSystemSpace()

  private def ownedFile(bankId: String, fileId: String, user: User, cc: CallContext): Future[FileT] =
    for {
      file <- Future(Files.provider.vend.getFile(bankId, fileId))
        .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
      _ <- Helper.booleanToFuture(s"$FileOwnerRequired Only the owner may change who has access to a file.", 403, Some(cc)) {
        isOwner(file, user, cc)
      }
    } yield file

  // Route: POST /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/access (201)
  lazy val createFileAccess: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "access" if fileId.nonEmpty =>
      EndpointHelpers.withUserAndBodyCreated[PostFileAccessJsonV700, FileAccessJsonV700](req) { (user, body, cc) =>
        val granteeUserId = Option(body.user_id).map(_.trim).getOrElse("")
        for {
          file <- ownedFile(bankIdInUrl, fileId, user, cc)
          grantee <- Future(code.users.Users.users.vend.getUserByUserId(granteeUserId))
            .map(unboxFullOrFail(_, Some(cc), s"$UserNotFoundByUserId Current user_id is $granteeUserId", 404))
          // Explicit target: fail loud rather than redirect (see the entitlement endpoints).
          _ <- Helper.booleanToFuture(
            s"$InvalidUserId user_id names a consent user (an agent identity minted by a Consent). Access to a file is given to humans - use the granting user's USER_ID.",
            400, Some(cc))(!grantee.isConsentUser)
          _ <- Helper.booleanToFuture(s"$FileAccessAlreadyExists Current user_id is $granteeUserId.", 409, Some(cc)) {
            Files.provider.vend.getAccess(file.fileId, granteeUserId).isEmpty
          }
          access <- Future(Files.provider.vend.grantAccess(file.fileId, granteeUserId, user.userId))
            .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not give access to the file.", 400))
        } yield JSONFactory700Files.createFileAccessJson(access)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFileAccess),
    "POST",
    "/banks/BANK_ID/files/FILE_ID/access",
    "Create File Access",
    s"""Gives a User read access to a file: they can then see it with Get File, Get File Content, Get File
       |Attachments and Get My Files. The User must be a person: a consent user is refused, since access given to
       |one would end with its Consent.
       |
       |**Who may call it.** The file's owner only.
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700Files.postFileAccessJsonExample,
    JSONFactory700Files.fileAccessJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, InvalidJsonFormat, FileNotFound, FileOwnerRequired,
      UserNotFoundByUserId, InvalidUserId, FileAccessAlreadyExists, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(createFileAccess)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/access
  lazy val getFileAccess: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "access" if fileId.nonEmpty =>
      EndpointHelpers.withUser(req) { (user, cc) =>
        for {
          file <- Future(Files.provider.vend.getFile(bankIdInUrl, fileId))
            .map(unboxFullOrFail(_, Some(cc), s"$FileNotFound Current FILE_ID is $fileId", 404))
          _ <- Helper.booleanToFuture(s"$UserHasMissingRoles $canGetFileAtOneBank at bank_id $bankIdInUrl. Or be the file's owner.", 403, Some(cc)) {
            isOwner(file, user, cc) || holdsRole(bankIdInUrl, user, cc, canGetFileAtOneBank)
          }
        } yield JSONFactory700Files.createFileAccessListJson(Files.provider.vend.getAccessList(file.fileId))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFileAccess),
    "GET",
    "/banks/BANK_ID/files/FILE_ID/access",
    "Get File Access",
    s"""Lists the Users who have been given access to a file, and who gave it.
       |
       |**Who may call it.** The file's owner, or a User with the Role CanGetFileAtOneBank at BANK_ID (or an
       |application acting as itself whose Consumer holds it as a Scope).
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.fileAccessListJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, UserHasMissingRoles, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(getFileAccess)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/banks/BANK_ID/files/FILE_ID/access/USER_ID (204)
  lazy val deleteFileAccess: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "banks" / bankIdInUrl / "files" / fileId / "access" / granteeUserId
      if fileId.nonEmpty && granteeUserId.nonEmpty =>
      EndpointHelpers.withUserDelete(req) { (user, cc) =>
        for {
          file <- ownedFile(bankIdInUrl, fileId, user, cc)
          _ <- Future(Files.provider.vend.getAccess(file.fileId, granteeUserId))
            .map(unboxFullOrFail(_, Some(cc), s"$FileAccessNotFound Current USER_ID is $granteeUserId", 404))
          revoked <- Future(Files.provider.vend.revokeAccess(file.fileId, granteeUserId))
            .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Could not remove access to the file.", 400))
        } yield revoked
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteFileAccess),
    "DELETE",
    "/banks/BANK_ID/files/FILE_ID/access/USER_ID",
    "Delete File Access",
    s"""Takes away a User's access to a file.
       |
       |**Who may call it.** The file's owner only.
       |
       |Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, FileNotFound, FileOwnerRequired, FileAccessNotFound, UnknownError),
    List(apiTagFile),
    None,
    authMode = UserXORApplication,
    http4sPartialFunction = Some(deleteFileAccess)
  ).allowSystemSpace()

  // ─── Public files: no authentication, and only files that are public ───

  /** A public file's content may be cached, but only for an hour, since a file can be withdrawn. */
  private val PublicCacheControl = "public, max-age=3600"

  // Route: GET /obp/v7.0.0/banks/BANK_ID/public-files?record_type=&record_id=
  lazy val getPublicFilesAttachedToRecord: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "public-files" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        for {
          (recordType, recordId) <- recordOf(req, cc)
          files <- Future(Files.provider.vend.getFilesAttachedTo(bankIdInUrl, recordType, recordId))
        } yield JSONFactory700Files.createPublicFilesJson(files.filter(_.isPublic))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPublicFilesAttachedToRecord),
    "GET",
    "/banks/BANK_ID/public-files",
    "Get Public Files Attached to a Record",
    s"""Lists the public files attached to one record, oldest attachment first. Files that are not public are
       |left out. Both query parameters are required: `record_type` and `record_id`.
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.publicFilesJsonExample,
    List($BankNotFound, InvalidFileQueryParameter, UnknownError),
    List(apiTagFile),
    None,
    http4sPartialFunction = Some(getPublicFilesAttachedToRecord)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/public-files/FILE_ID
  lazy val getPublicFile: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "public-files" / fileId if fileId.nonEmpty =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        publicFile(bankIdInUrl, fileId, cc).map(JSONFactory700Files.createPublicFileJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPublicFile),
    "GET",
    "/banks/BANK_ID/public-files/FILE_ID",
    "Get Public File",
    s"""Returns what is known about a public file: its name, media type, size, SHA-256 and when it was stored,
       |but not who stored it. A file that is not public is not found here.
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Files.publicFileJsonExample,
    List($BankNotFound, FileNotFound, UnknownError),
    List(apiTagFile),
    None,
    http4sPartialFunction = Some(getPublicFile)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/banks/BANK_ID/public-files/FILE_ID/content
  lazy val getPublicFileContent: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / bankIdInUrl / "public-files" / fileId / "content" if fileId.nonEmpty =>
      EndpointHelpers.executeAndRespondBytes(req) { cc =>
        publicFile(bankIdInUrl, fileId, cc).flatMap(contentOf(_, inlineRequested(req), PublicCacheControl, cc))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPublicFileContent),
    "GET",
    "/banks/BANK_ID/public-files/FILE_ID/content",
    "Get Public File Content",
    s"""Returns a public file itself, not JSON: the body is the file, with the file's own `Content-Type`, its
       |SHA-256 as the `ETag`, and `Content-Disposition` naming the file. Add `?inline=true` to have a browser show
       |it rather than save it. A file that is not public is not found here. Errors are JSON.
       |
       |The response may be cached for up to an hour (`Cache-Control: $PublicCacheControl`).
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, FileNotFound, UnknownError),
    List(apiTagFile),
    None,
    http4sPartialFunction = Some(getPublicFileContent)
  ).allowSystemSpace()
}
