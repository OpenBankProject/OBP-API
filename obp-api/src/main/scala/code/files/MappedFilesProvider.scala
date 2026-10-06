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
package code.files

import java.util.{Date, UUID}

import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

/**
 * This object stores Files with Lift Mapper: the metadata in File, FileAttachment and FileAccess,
 * and the bytes, through [[PostgresFileStore]], in FileContent.
 */
object MappedFilesProvider extends FilesProvider {

  /** Where the bytes go. Replaced by an object storage implementation when the volume calls for it. */
  val store: FileStore = PostgresFileStore

  override def createFile(bankId: String, sha256: String, mediaType: String, fileName: String,
                          userId: String, onBehalfOfUserId: String, bytes: Array[Byte]): Box[FileT] = {
    val fileId = UUID.randomUUID().toString
    for {
      _ <- store.write(fileId, bytes)
      file <- tryo {
        File.create
          .FileId(fileId)
          .BankId(bankId)
          .Sha256(sha256)
          .SizeInBytes(bytes.length.toLong)
          .MediaType(mediaType)
          .FileName(fileName)
          .UserId(userId)
          .OnBehalfOfUserId(onBehalfOfUserId)
          .CreatedAt(new Date())
          .saveMe()
      }
    } yield file
  }

  override def getFile(bankId: String, fileId: String): Box[FileT] =
    File.find(By(File.BankId, bankId), By(File.FileId, fileId))

  override def getContent(fileId: String): Box[Array[Byte]] = store.read(fileId)

  override def createAttachment(file: FileT, recordType: String, recordId: String,
                                userId: String, onBehalfOfUserId: String): Box[FileAttachmentT] = tryo {
    FileAttachment.create
      .FileId(file.fileId)
      .BankId(file.bankId)
      .RecordType(recordType)
      .RecordId(recordId)
      .UserId(userId)
      .OnBehalfOfUserId(onBehalfOfUserId)
      .CreatedAt(new Date())
      .saveMe()
  }

  override def getAttachment(bankId: String, fileId: String, fileAttachmentId: String): Box[FileAttachmentT] =
    FileAttachment.find(By(FileAttachment.BankId, bankId), By(FileAttachment.FileId, fileId),
      By(FileAttachment.FileAttachmentId, fileAttachmentId))

  override def attachmentExists(fileId: String, recordType: String, recordId: String): Boolean =
    FileAttachment.find(By(FileAttachment.FileId, fileId), By(FileAttachment.RecordType, recordType),
      By(FileAttachment.RecordId, recordId)).isDefined

  override def deleteAttachment(fileAttachmentId: String): Box[Boolean] = tryo {
    FileAttachment.bulkDelete_!!(By(FileAttachment.FileAttachmentId, fileAttachmentId))
  }

  override def getFilesAttachedTo(bankId: String, recordType: String, recordId: String): List[FileT] = {
    val fileIds = FileAttachment.findAll(By(FileAttachment.BankId, bankId), By(FileAttachment.RecordType, recordType),
      By(FileAttachment.RecordId, recordId), OrderBy(FileAttachment.CreatedAt, Ascending)).map(_.FileId.get)
    if (fileIds.isEmpty) Nil
    else {
      val byId = File.findAll(By(File.BankId, bankId), ByList(File.FileId, fileIds.distinct)).map(f => f.fileId -> f).toMap
      fileIds.distinct.flatMap(byId.get)
    }
  }

  override def grantAccess(fileId: String, granteeUserId: String, grantedByUserId: String, onBehalfOfUserId: String): Box[FileAccessT] = tryo {
    FileAccess.create
      .FileId(fileId)
      .GranteeUserId(granteeUserId)
      .GrantedByUserId(grantedByUserId)
      .OnBehalfOfUserId(onBehalfOfUserId)
      .CreatedAt(new Date())
      .saveMe()
  }

  override def getAccess(fileId: String, granteeUserId: String): Box[FileAccessT] =
    FileAccess.find(By(FileAccess.FileId, fileId), By(FileAccess.GranteeUserId, granteeUserId))

  override def getAccessList(fileId: String): List[FileAccessT] =
    FileAccess.findAll(By(FileAccess.FileId, fileId), OrderBy(FileAccess.CreatedAt, Ascending))

  override def revokeAccess(fileId: String, granteeUserId: String): Box[Boolean] = tryo {
    FileAccess.bulkDelete_!!(By(FileAccess.FileId, fileId), By(FileAccess.GranteeUserId, granteeUserId))
  }
}

/**
 * This store keeps each file's bytes in the FileContent table. It suits files of a few megabytes
 * at modest volume; the cost is that every database backup carries every file. The table is kept
 * apart from File because Mapper reads every column of a row, so listings of File would otherwise
 * load whole documents.
 */
object PostgresFileStore extends FileStore {
  override def write(fileId: String, bytes: Array[Byte]): Box[Unit] = tryo {
    FileContent.create.FileId(fileId).Content(bytes).saveMe()
    ()
  }

  override def read(fileId: String): Box[Array[Byte]] =
    FileContent.find(By(FileContent.FileId, fileId)).flatMap(row => Box !! row.Content.get)
}

class File extends FileT with LongKeyedMapper[File] with IdPK {
  override def getSingleton = File

  object FileId extends MappedString(this, 36)
  object BankId extends MappedString(this, 255)
  object Sha256 extends MappedString(this, 64)
  object SizeInBytes extends MappedLong(this)
  object MediaType extends MappedString(this, 100)
  object FileName extends MappedString(this, 255)
  object UserId extends MappedString(this, 255)
  object OnBehalfOfUserId extends MappedString(this, 255)
  object CreatedAt extends MappedDateTime(this)

  override def fileId: String = FileId.get
  override def bankId: String = BankId.get
  override def sha256: String = Sha256.get
  override def sizeInBytes: Long = SizeInBytes.get
  override def mediaType: String = MediaType.get
  override def fileName: String = FileName.get
  override def userId: String = UserId.get
  override def onBehalfOfUserId: String = Option(OnBehalfOfUserId.get).getOrElse("")
  override def createdAt: Date = CreatedAt.get
}

object File extends File with LongKeyedMetaMapper[File] {
  override def dbIndexes = UniqueIndex(FileId) :: Index(BankId, FileId) :: super.dbIndexes
}

class FileContent extends LongKeyedMapper[FileContent] with IdPK {
  override def getSingleton = FileContent

  object FileId extends MappedString(this, 36)
  object Content extends MappedBinary(this)
}

object FileContent extends FileContent with LongKeyedMetaMapper[FileContent] {
  override def dbIndexes = UniqueIndex(FileId) :: super.dbIndexes
}

class FileAttachment extends FileAttachmentT with LongKeyedMapper[FileAttachment] with IdPK {
  override def getSingleton = FileAttachment

  object FileAttachmentId extends MappedString(this, 36) {
    override def defaultValue = UUID.randomUUID().toString
  }
  object FileId extends MappedString(this, 36)
  object BankId extends MappedString(this, 255)
  object RecordType extends MappedString(this, 255)
  object RecordId extends MappedString(this, 255)
  object UserId extends MappedString(this, 255)
  object OnBehalfOfUserId extends MappedString(this, 255)
  object CreatedAt extends MappedDateTime(this)

  override def fileAttachmentId: String = FileAttachmentId.get
  override def fileId: String = FileId.get
  override def bankId: String = BankId.get
  override def recordType: String = RecordType.get
  override def recordId: String = RecordId.get
  override def userId: String = UserId.get
  override def onBehalfOfUserId: String = Option(OnBehalfOfUserId.get).getOrElse("")
  override def createdAt: Date = CreatedAt.get
}

object FileAttachment extends FileAttachment with LongKeyedMetaMapper[FileAttachment] {
  override def dbIndexes =
    UniqueIndex(FileAttachmentId) ::
    // A file is attached to a record at most once.
    UniqueIndex(FileId, RecordType, RecordId) ::
    // "Which files belong to this record?"
    Index(BankId, RecordType, RecordId) ::
    super.dbIndexes
}

class FileAccess extends FileAccessT with LongKeyedMapper[FileAccess] with IdPK {
  override def getSingleton = FileAccess

  object FileId extends MappedString(this, 36)
  object GranteeUserId extends MappedString(this, 255)
  object GrantedByUserId extends MappedString(this, 255)
  object OnBehalfOfUserId extends MappedString(this, 255)
  object CreatedAt extends MappedDateTime(this)

  override def fileId: String = FileId.get
  override def granteeUserId: String = GranteeUserId.get
  override def grantedByUserId: String = GrantedByUserId.get
  override def onBehalfOfUserId: String = Option(OnBehalfOfUserId.get).getOrElse("")
  override def createdAt: Date = CreatedAt.get
}

object FileAccess extends FileAccess with LongKeyedMetaMapper[FileAccess] {
  override def dbIndexes =
    // One row per User per file.
    UniqueIndex(FileId, GranteeUserId) ::
    // "Which files have been shared with me?"
    Index(GranteeUserId) ::
    super.dbIndexes
}
