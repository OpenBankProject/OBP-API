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

import java.util.Date

import code.files.{FileAccessT, FileAttachmentT, FileT}

/*
 * The JSON of the v7.0.0 file endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** One stored file: what it is and who owns it. Never the bytes, which Get File Content returns. */
case class FileJsonV700(
  file_id: String,
  bank_id: String,
  file_name: String,
  media_type: String,
  size_in_bytes: Long,
  sha256: String,
  is_public: Boolean,
  owner_user_id: String,
  user_id: String,
  on_behalf_of_user_id: String,
  created_at: Date
)

case class FilesJsonV700(files: List[FileJsonV700])

case class FilesPaginationJsonV700(total: Long, limit: Int, offset: Int)

/** One page of all the files of a space, for administrators. */
case class FilesPageJsonV700(files: List[FileJsonV700], pagination: FilesPaginationJsonV700)

/**
 * A public file as anyone may see it, without logging in: what it is, but not who uploaded it.
 */
case class PublicFileJsonV700(
  file_id: String,
  bank_id: String,
  file_name: String,
  media_type: String,
  size_in_bytes: Long,
  sha256: String,
  created_at: Date
)

case class PublicFilesJsonV700(files: List[PublicFileJsonV700])

/**
 * The body of Create File. The bytes travel base64 encoded. sha256, when given, is checked against the
 * decoded bytes; OBP computes and stores its own either way.
 */
case class PostFileJsonV700(
  file_name: String,
  media_type: String,
  content_base64: String,
  is_public: Option[Boolean],
  sha256: Option[String]
)

case class PutFileIsPublicJsonV700(is_public: Boolean)

case class PostFileAttachmentJsonV700(record_type: String, record_id: String)

case class FileAttachmentJsonV700(
  file_attachment_id: String,
  file_id: String,
  bank_id: String,
  record_type: String,
  record_id: String,
  user_id: String,
  on_behalf_of_user_id: String,
  created_at: Date
)

case class FileAttachmentsJsonV700(attachments: List[FileAttachmentJsonV700])

case class PostFileAccessJsonV700(user_id: String)

case class FileAccessJsonV700(
  file_id: String,
  user_id: String,
  granted_by_user_id: String,
  on_behalf_of_user_id: String,
  created_at: Date
)

case class FileAccessListJsonV700(access: List[FileAccessJsonV700])

object JSONFactory700Files {

  def createFileJson(file: FileT): FileJsonV700 =
    FileJsonV700(
      file_id = file.fileId,
      bank_id = file.bankId,
      file_name = file.fileName,
      media_type = file.mediaType,
      size_in_bytes = file.sizeInBytes,
      sha256 = file.sha256,
      is_public = file.isPublic,
      owner_user_id = file.ownerUserId,
      user_id = file.userId,
      on_behalf_of_user_id = file.onBehalfOfUserId,
      created_at = file.createdAt
    )

  def createFilesJson(files: List[FileT]): FilesJsonV700 = FilesJsonV700(files.map(createFileJson))

  def createFilesPageJson(files: List[FileT], total: Long, limit: Int, offset: Int): FilesPageJsonV700 =
    FilesPageJsonV700(files.map(createFileJson), FilesPaginationJsonV700(total, limit, offset))

  def createPublicFileJson(file: FileT): PublicFileJsonV700 =
    PublicFileJsonV700(
      file_id = file.fileId,
      bank_id = file.bankId,
      file_name = file.fileName,
      media_type = file.mediaType,
      size_in_bytes = file.sizeInBytes,
      sha256 = file.sha256,
      created_at = file.createdAt
    )

  def createPublicFilesJson(files: List[FileT]): PublicFilesJsonV700 = PublicFilesJsonV700(files.map(createPublicFileJson))

  def createFileAttachmentJson(attachment: FileAttachmentT): FileAttachmentJsonV700 =
    FileAttachmentJsonV700(
      file_attachment_id = attachment.fileAttachmentId,
      file_id = attachment.fileId,
      bank_id = attachment.bankId,
      record_type = attachment.recordType,
      record_id = attachment.recordId,
      user_id = attachment.userId,
      on_behalf_of_user_id = attachment.onBehalfOfUserId,
      created_at = attachment.createdAt
    )

  def createFileAttachmentsJson(attachments: List[FileAttachmentT]): FileAttachmentsJsonV700 =
    FileAttachmentsJsonV700(attachments.map(createFileAttachmentJson))

  def createFileAccessJson(access: FileAccessT): FileAccessJsonV700 =
    FileAccessJsonV700(
      file_id = access.fileId,
      user_id = access.granteeUserId,
      granted_by_user_id = access.grantedByUserId,
      on_behalf_of_user_id = access.onBehalfOfUserId,
      created_at = access.createdAt
    )

  def createFileAccessListJson(access: List[FileAccessT]): FileAccessListJsonV700 =
    FileAccessListJsonV700(access.map(createFileAccessJson))

  private val exampleDate = new Date(1792195200000L) // 2026-10-17T00:00:00Z

  val fileJsonExample: FileJsonV700 = FileJsonV700(
    file_id = "b3a2a1e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
    bank_id = "gh.29.uk",
    file_name = "site_survey.pdf",
    media_type = "application/pdf",
    size_in_bytes = 482113L,
    sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    is_public = false,
    owner_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    on_behalf_of_user_id = "",
    created_at = exampleDate
  )

  val filesJsonExample: FilesJsonV700 = FilesJsonV700(List(fileJsonExample))

  val filesPageJsonExample: FilesPageJsonV700 =
    FilesPageJsonV700(List(fileJsonExample), FilesPaginationJsonV700(total = 1, limit = 500, offset = 0))

  val publicFileJsonExample: PublicFileJsonV700 = PublicFileJsonV700(
    file_id = "c4b3b2f5-6d7e-4f80-9bac-1d2e3f4a5b6c",
    bank_id = "gh.29.uk",
    file_name = "site_photo.jpg",
    media_type = "image/jpeg",
    size_in_bytes = 1048576L,
    sha256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
    created_at = exampleDate
  )

  val publicFilesJsonExample: PublicFilesJsonV700 = PublicFilesJsonV700(List(publicFileJsonExample))

  val postFileJsonExample: PostFileJsonV700 = PostFileJsonV700(
    file_name = "site_survey.pdf",
    media_type = "application/pdf",
    content_base64 = "JVBERi0xLjcKJcfsj6IK",
    is_public = Some(false),
    sha256 = Some("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
  )

  val putFileIsPublicJsonExample: PutFileIsPublicJsonV700 = PutFileIsPublicJsonV700(is_public = true)

  val postFileAttachmentJsonExample: PostFileAttachmentJsonV700 =
    PostFileAttachmentJsonV700(record_type = "customer", record_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh")

  val fileAttachmentJsonExample: FileAttachmentJsonV700 = FileAttachmentJsonV700(
    file_attachment_id = "d5c4c3a6-7e8f-4091-acbd-2e3f4a5b6c7d",
    file_id = fileJsonExample.file_id,
    bank_id = "gh.29.uk",
    record_type = "customer",
    record_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
    user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    on_behalf_of_user_id = "",
    created_at = exampleDate
  )

  val fileAttachmentsJsonExample: FileAttachmentsJsonV700 = FileAttachmentsJsonV700(List(fileAttachmentJsonExample))

  val postFileAccessJsonExample: PostFileAccessJsonV700 = PostFileAccessJsonV700(user_id = "6f5e4d3c-2b1a-4098-8765-43210fedcba9")

  val fileAccessJsonExample: FileAccessJsonV700 = FileAccessJsonV700(
    file_id = fileJsonExample.file_id,
    user_id = "6f5e4d3c-2b1a-4098-8765-43210fedcba9",
    granted_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    on_behalf_of_user_id = "",
    created_at = exampleDate
  )

  val fileAccessListJsonExample: FileAccessListJsonV700 = FileAccessListJsonV700(List(fileAccessJsonExample))
}
