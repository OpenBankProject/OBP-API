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

import cats.data.NonEmptyList
import code.api.util.DoobieUtil
import code.users.{UserReference, Users}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/**
 * This object stores Files with Doobie: the metadata in File, FileAttachment and FileAccess,
 * and the bytes, through [[PostgresFileStore]], in FileContent. The four stores below replace the
 * Lift Mapper entities upstream wrote for these tables (file_t, fileattachment, fileaccess,
 * filecontent) and keep their behaviour.
 */
object MappedFilesProvider extends FilesProvider {

  /** Where the bytes go. Replaced by an object storage implementation when the volume calls for it. */
  val store: FileStore = PostgresFileStore

  /** The caller and, when an agent called for somebody, that somebody (empty otherwise), by the
   *  reference's attribution policy: these tables record both. */
  private def attributed(userId: String, ref: UserReference): Box[(String, String)] =
    Users.users.vend.attributionOf(userId, ref).map(a => (a.userId, if (a.isDelegated) a.onBehalfOfUserId else ""))

  override def createFile(bankId: String, sha256: String, mediaType: String, fileName: String, isPublic: Boolean,
                          userId: String, bytes: Array[Byte], isPersonal: Boolean, scanStatus: String): Box[FileT] = {
    val fileId = UUID.randomUUID().toString
    for {
      (callerId, onBehalfOfUserId) <- attributed(userId, UserReference.File_UserId)
      _ <- store.write(fileId, bytes)
      file <- tryo {
        File.insert(File(
          id = 0L,
          fileId = fileId,
          bankId = bankId,
          sha256 = sha256,
          sizeInBytes = bytes.length.toLong,
          mediaType = mediaType,
          fileName = fileName,
          userId = callerId,
          storedOnBehalfOfUserId = onBehalfOfUserId,
          isPublic = isPublic,
          createdAt = new Date(),
          isPersonal = isPersonal,
          storedScanStatus = scanStatus,
          // Mapper wrote the fields it was not given with their defaults: "" for a string, NULL for a date.
          storedScanStatusReason = "",
          scanStatusUpdatedAt = None,
          storedScanStatusUpdatedByUserId = ""))
      }
    } yield file
  }

  override def getFile(bankId: String, fileId: String): Box[FileT] =
    File.find(bankId, fileId)

  override def getContent(fileId: String): Box[Array[Byte]] = store.read(fileId)

  override def setPublic(bankId: String, fileId: String, isPublic: Boolean): Box[FileT] =
    File.find(bankId, fileId).flatMap(file => tryo(File.setPublic(file, isPublic)))

  override def getFiles(bankId: String, limit: Int, offset: Int, scanStatus: Option[String]): (List[FileT], Long) =
    (File.findPage(bankId, scanStatus, limit, offset), File.count(bankId, scanStatus))

  override def setScanStatus(bankId: String, fileId: String, scanStatus: String, reason: String, userId: String): Box[FileT] =
    File.find(bankId, fileId).flatMap { file =>
      tryo(File.setScanStatus(file, scanStatus, reason, new Date(), userId))
    }

  override def personalUsage(bankId: String, ownerUserId: String): (Int, Long) = {
    val owned = File.findPersonal(bankId).filter(_.ownerUserId == ownerUserId)
    (owned.size, owned.map(_.sizeInBytes).sum)
  }

  override def getFilesOwnedOrSharedWith(bankId: String, userId: String): List[FileT] = {
    val uploaded = File.findUploadedBy(bankId, userId)
    val uploadedFor = File.findUploadedFor(bankId, userId)
    val sharedFileIds = FileAccess.findByGrantee(userId).map(_.fileId).distinct
    val shared = File.findByFileIds(bankId, sharedFileIds)
    (uploaded ++ uploadedFor ++ shared).groupBy(_.fileId).values.map(_.head).toList.sortBy(_.createdAt.getTime)
  }

  override def createAttachment(file: FileT, recordType: String, recordId: String, userId: String): Box[FileAttachmentT] =
    attributed(userId, UserReference.FileAttachment_UserId).flatMap { case (callerId, onBehalfOfUserId) =>
      tryo {
        FileAttachment.insert(FileAttachment(
          id = 0L,
          // Mapper's defaultValue for FileAttachmentId.
          fileAttachmentId = UUID.randomUUID().toString,
          fileId = file.fileId,
          bankId = file.bankId,
          recordType = recordType,
          recordId = recordId,
          userId = callerId,
          storedOnBehalfOfUserId = onBehalfOfUserId,
          createdAt = new Date()))
      }
    }

  override def getAttachment(bankId: String, fileId: String, fileAttachmentId: String): Box[FileAttachmentT] =
    FileAttachment.find(bankId, fileId, fileAttachmentId)

  override def getAttachments(bankId: String, fileId: String): List[FileAttachmentT] =
    FileAttachment.findByFile(bankId, fileId)

  override def attachmentExists(fileId: String, recordType: String, recordId: String): Boolean =
    FileAttachment.exists(fileId, recordType, recordId)

  override def deleteAttachment(fileAttachmentId: String): Box[Boolean] = tryo {
    FileAttachment.deleteByFileAttachmentId(fileAttachmentId)
  }

  override def getFilesAttachedTo(bankId: String, recordType: String, recordId: String): List[FileT] = {
    val fileIds = FileAttachment.findByRecord(List(bankId), recordType, recordId).map(_.fileId)
    if (fileIds.isEmpty) Nil
    else {
      val byId = File.findByFileIds(bankId, fileIds.distinct).map(f => f.fileId -> f).toMap
      fileIds.distinct.flatMap(byId.get)
    }
  }

  // The grantee is an explicit target named in the request: the endpoint refuses a consent user rather
  // than redirecting, since access given to one would die with its Consent.
  override def grantAccess(fileId: String, granteeUserId: String, grantedByUserId: String): Box[FileAccessT] =
    attributed(grantedByUserId, UserReference.FileAccess_GrantedByUserId).flatMap { case (callerId, onBehalfOfUserId) =>
      tryo {
        FileAccess.insert(FileAccess(
          id = 0L,
          fileId = fileId,
          granteeUserId = granteeUserId,
          grantedByUserId = callerId,
          storedOnBehalfOfUserId = onBehalfOfUserId,
          createdAt = new Date()))
      }
    }

  override def getAccess(fileId: String, granteeUserId: String): Box[FileAccessT] =
    FileAccess.find(fileId, granteeUserId)

  override def getAccessList(fileId: String): List[FileAccessT] =
    FileAccess.findByFileId(fileId)

  override def revokeAccess(fileId: String, granteeUserId: String): Box[Boolean] = tryo {
    FileAccess.delete(fileId, granteeUserId)
  }
}

/**
 * This store keeps each file's bytes in the FileContent table. It suits files of a few megabytes
 * at modest volume; the cost is that every database backup carries every file. The table is kept
 * apart from File so that listings of File never load whole documents.
 */
object PostgresFileStore extends FileStore {
  override def write(fileId: String, bytes: Array[Byte]): Box[Unit] = tryo {
    FileContent.insert(fileId, bytes)
  }

  override def read(fileId: String): Box[Array[Byte]] =
    Box(FileContent.findContent(fileId))
}

/** Small helpers the four stores share. */
private[files] object FileStoreSql {
  def readDate(value: Option[java.sql.Timestamp]): Date = value.map(t => new Date(t.getTime)).orNull
  def ts(date: Date): Option[java.sql.Timestamp] = Option(date).map(d => new java.sql.Timestamp(d.getTime))
  def first[A](rows: List[A]): Box[A] = rows.headOption match {
    case Some(row) => Full(row)
    case None => Empty
  }
}

import FileStoreSql._

/**
 * One row of file_t. This replaces upstream's Mapper entity. The `stored*` fields hold the column as
 * it is, NULL included (read back as null, as Mapper's getters did); the FileT members read it the
 * way upstream's did.
 */
case class File(
  id: Long,
  fileId: String,
  bankId: String,
  sha256: String,
  sizeInBytes: Long,
  mediaType: String,
  fileName: String,
  userId: String,
  storedOnBehalfOfUserId: String,
  isPublic: Boolean,
  createdAt: Date,
  isPersonal: Boolean,
  // NULL on rows written before scanning existed, which count as clean.
  storedScanStatus: String,
  storedScanStatusReason: String,
  scanStatusUpdatedAt: Option[Date],
  storedScanStatusUpdatedByUserId: String
) extends FileT {
  override def onBehalfOfUserId: String = Option(storedOnBehalfOfUserId).getOrElse("")
  override def scanStatus: String = Option(storedScanStatus).filter(_.nonEmpty).getOrElse(code.files.ScanStatus.Clean)
  override def scanStatusReason: String = Option(storedScanStatusReason).getOrElse("")
  override def scanStatusUpdatedByUserId: String = Option(storedScanStatusUpdatedByUserId).getOrElse("")
}

object File {

  private val selectColumns =
    fr"""SELECT id, fileid, bankid, sha256, sizeinbytes, mediatype, filename, userid, onbehalfofuserid,
           ispublic, createdat, ispersonal, scanstatus, scanstatusreason, scanstatusupdatedat,
           scanstatusupdatedbyuserid
         FROM file_t"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[Long], Option[String],
    Option[String], Option[String], Option[String], Option[Boolean], Option[java.sql.Timestamp], Option[Boolean],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[String])

  // MappedLong read NULL as 0 and MappedBoolean as false (both declared defaults).
  private def fromRow(r: Row): File =
    File(r._1, r._2.orNull, r._3.orNull, r._4.orNull, r._5.getOrElse(0L), r._6.orNull, r._7.orNull, r._8.orNull,
      r._9.orNull, r._10.getOrElse(false), readDate(r._11), r._12.getOrElse(false), r._13.orNull, r._14.orNull,
      r._15.map(t => new Date(t.getTime)), r._16.orNull)

  private def query(condition: Fragment): List[File] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def bankAndStatus(bankId: String, scanStatus: Option[String]): Fragment =
    fr"WHERE bankid = ${Option(bankId)}" ++
      scanStatus.map(s => fr"AND scanstatus = ${Option(s)}").getOrElse(Fragment.empty)

  def find(bankId: String, fileId: String): Box[File] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND fileid = ${Option(fileId)} ORDER BY id ASC"))

  /** The file with this id, in whichever space it is. */
  def findByFileId(fileId: String): Box[File] =
    first(query(fr"WHERE fileid = ${Option(fileId)} ORDER BY id ASC"))

  /** One page of a space's files, newest first. */
  def findPage(bankId: String, scanStatus: Option[String], limit: Int, offset: Int): List[File] =
    query(bankAndStatus(bankId, scanStatus) ++ fr"ORDER BY createdat DESC, id DESC" ++ DoobieUtil.pagination(limit, offset))

  def count(bankId: String, scanStatus: Option[String]): Long =
    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM file_t" ++ bankAndStatus(bankId, scanStatus)).query[Long].unique)

  def countByFileId(fileId: String): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM file_t WHERE fileid = ${Option(fileId)}".query[Long].unique)

  def findPersonal(bankId: String): List[File] =
    query(fr"WHERE bankid = ${Option(bankId)} AND ispersonal = ${true} ORDER BY id ASC")

  /** Files the User uploaded for themselves: OnBehalfOfUserId is the empty string (not NULL), as upstream matched it. */
  def findUploadedBy(bankId: String, userId: String): List[File] =
    query(fr"WHERE bankid = ${Option(bankId)} AND userid = ${Option(userId)} AND onbehalfofuserid = ${""} ORDER BY id ASC")

  /** Files an agent uploaded for the User. */
  def findUploadedFor(bankId: String, userId: String): List[File] =
    query(fr"WHERE bankid = ${Option(bankId)} AND onbehalfofuserid = ${Option(userId)} ORDER BY id ASC")

  /** The files of a space with these ids. An empty list matches nothing, as Mapper's ByList did. */
  def findByFileIds(bankId: String, fileIds: List[String]): List[File] =
    fileIds.filter(_ != null).distinct match {
      case Nil => Nil
      case ids =>
        query(fr"WHERE bankid = ${Option(bankId)} AND" ++ Fragments.in(fr"fileid", NonEmptyList.fromListUnsafe(ids)) ++
          fr"ORDER BY id ASC")
    }

  /**
   * One batch of the files of these spaces created at or before `createdAtOrBefore`, in id order after
   * `afterId`: for walking the table by key. An empty list of spaces matches nothing.
   */
  def findBatch(bankIds: List[String], createdAtOrBefore: Date, afterId: Long, maxRows: Int): List[File] =
    bankIds.filter(_ != null).distinct match {
      case Nil => Nil
      case ids =>
        query(fr"WHERE" ++ Fragments.in(fr"bankid", NonEmptyList.fromListUnsafe(ids)) ++
          fr"AND createdat <= ${ts(createdAtOrBefore)} AND id > $afterId ORDER BY id ASC" ++
          DoobieUtil.pagination(maxRows, 0))
    }

  def insert(f: File): File = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO file_t (fileid, bankid, sha256, sizeinbytes, mediatype, filename, userid, onbehalfofuserid,
              ispublic, createdat, ispersonal, scanstatus, scanstatusreason, scanstatusupdatedat,
              scanstatusupdatedbyuserid)
            VALUES (${Option(f.fileId)}, ${Option(f.bankId)}, ${Option(f.sha256)}, ${f.sizeInBytes},
              ${Option(f.mediaType)}, ${Option(f.fileName)}, ${Option(f.userId)}, ${Option(f.storedOnBehalfOfUserId)},
              ${f.isPublic}, ${ts(f.createdAt)}, ${f.isPersonal}, ${Option(f.storedScanStatus)},
              ${Option(f.storedScanStatusReason)}, ${f.scanStatusUpdatedAt.flatMap(ts)},
              ${Option(f.storedScanStatusUpdatedByUserId)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    f.copy(id = id)
  }

  def setPublic(file: File, isPublic: Boolean): File = {
    DoobieUtil.runUpdate(sql"UPDATE file_t SET ispublic = $isPublic WHERE id = ${file.id}".update.run)
    file.copy(isPublic = isPublic)
  }

  def setScanStatus(file: File, scanStatus: String, reason: String, at: Date, userId: String): File = {
    DoobieUtil.runUpdate(
      sql"""UPDATE file_t SET scanstatus = ${Option(scanStatus)}, scanstatusreason = ${Option(reason)},
              scanstatusupdatedat = ${ts(at)}, scanstatusupdatedbyuserid = ${Option(userId)}
            WHERE id = ${file.id}""".update.run)
    file.copy(storedScanStatus = scanStatus, storedScanStatusReason = reason, scanStatusUpdatedAt = Option(at),
      storedScanStatusUpdatedByUserId = userId)
  }

  /** Deletes the one row, by its primary key, as Mapper's delete_! did. */
  def delete(file: File): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM file_t WHERE id = ${file.id}".update.run) > 0
}

/**
 * The bytes of one file. This replaces upstream's Mapper entity; there is no row class, since only
 * the bytes are ever read.
 */
object FileContent {

  def insert(fileId: String, bytes: Array[Byte]): Unit = {
    DoobieUtil.runUpdate(
      sql"INSERT INTO filecontent (fileid, content) VALUES (${Option(fileId)}, ${Option(bytes)})".update.run)
    ()
  }

  /** The bytes of the file, if it has a row and the row holds any (NULL reads as none, as `Box !!` did). */
  def findContent(fileId: String): Option[Array[Byte]] =
    DoobieUtil.runQuery(
      (fr"SELECT content FROM filecontent WHERE fileid = ${Option(fileId)} ORDER BY id ASC" ++ DoobieUtil.pagination(1, 0))
        .query[Option[Array[Byte]]].option).flatten

  def countByFileId(fileId: String): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM filecontent WHERE fileid = ${Option(fileId)}".query[Long].unique)

  /** Always true, like Mapper's bulkDelete_!!. */
  def deleteByFileId(fileId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM filecontent WHERE fileid = ${Option(fileId)}".update.run)
    true
  }
}

/** One row of fileattachment. This replaces upstream's Mapper entity. */
case class FileAttachment(
  id: Long,
  fileAttachmentId: String,
  fileId: String,
  bankId: String,
  recordType: String,
  recordId: String,
  userId: String,
  storedOnBehalfOfUserId: String,
  createdAt: Date
) extends FileAttachmentT {
  override def onBehalfOfUserId: String = Option(storedOnBehalfOfUserId).getOrElse("")
}

object FileAttachment {

  private val selectColumns =
    fr"""SELECT id, fileattachmentid, fileid, bankid, recordtype, recordid, userid, onbehalfofuserid, createdat
         FROM fileattachment"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp])

  private def fromRow(r: Row): FileAttachment =
    FileAttachment(r._1, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, r._6.orNull, r._7.orNull, r._8.orNull,
      readDate(r._9))

  private def query(condition: Fragment): List[FileAttachment] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(bankId: String, fileId: String, fileAttachmentId: String): Box[FileAttachment] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND fileid = ${Option(fileId)} AND fileattachmentid = ${Option(fileAttachmentId)} ORDER BY id ASC"))

  /** The attachments of one file of a space, oldest first. */
  def findByFile(bankId: String, fileId: String): List[FileAttachment] =
    query(fr"WHERE bankid = ${Option(bankId)} AND fileid = ${Option(fileId)} ORDER BY createdat ASC, id ASC")

  /** The attachments of one file, in whichever space. */
  def findByFileId(fileId: String): List[FileAttachment] =
    query(fr"WHERE fileid = ${Option(fileId)} ORDER BY id ASC")

  /** The attachments of one record, oldest first, in any of these spaces. An empty list matches nothing. */
  def findByRecord(bankIds: List[String], recordType: String, recordId: String): List[FileAttachment] =
    bankIds.filter(_ != null).distinct match {
      case Nil => Nil
      case ids =>
        query(fr"WHERE" ++ Fragments.in(fr"bankid", NonEmptyList.fromListUnsafe(ids)) ++
          fr"AND recordtype = ${Option(recordType)} AND recordid = ${Option(recordId)} ORDER BY createdat ASC, id ASC")
    }

  def exists(fileId: String, recordType: String, recordId: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM fileattachment
            WHERE fileid = ${Option(fileId)} AND recordtype = ${Option(recordType)} AND recordid = ${Option(recordId)}"""
        .query[Long].unique) > 0

  def countByFileId(fileId: String): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM fileattachment WHERE fileid = ${Option(fileId)}".query[Long].unique)

  def insert(a: FileAttachment): FileAttachment = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO fileattachment (fileattachmentid, fileid, bankid, recordtype, recordid, userid,
              onbehalfofuserid, createdat)
            VALUES (${Option(a.fileAttachmentId)}, ${Option(a.fileId)}, ${Option(a.bankId)}, ${Option(a.recordType)},
              ${Option(a.recordId)}, ${Option(a.userId)}, ${Option(a.storedOnBehalfOfUserId)}, ${ts(a.createdAt)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    a.copy(id = id)
  }

  /** Always true, like Mapper's bulkDelete_!!. */
  def deleteByFileAttachmentId(fileAttachmentId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM fileattachment WHERE fileattachmentid = ${Option(fileAttachmentId)}".update.run)
    true
  }

  /** Always true, like Mapper's bulkDelete_!!. */
  def deleteByFileId(fileId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM fileattachment WHERE fileid = ${Option(fileId)}".update.run)
    true
  }

  /** Deletes the one row, by its primary key, as Mapper's delete_! did. */
  def delete(attachment: FileAttachment): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM fileattachment WHERE id = ${attachment.id}".update.run) > 0
}

/** One row of fileaccess. This replaces upstream's Mapper entity. */
case class FileAccess(
  id: Long,
  fileId: String,
  granteeUserId: String,
  grantedByUserId: String,
  storedOnBehalfOfUserId: String,
  createdAt: Date
) extends FileAccessT {
  override def onBehalfOfUserId: String = Option(storedOnBehalfOfUserId).getOrElse("")
}

object FileAccess {

  private val selectColumns =
    fr"SELECT id, fileid, granteeuserid, grantedbyuserid, onbehalfofuserid, createdat FROM fileaccess"

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[java.sql.Timestamp])

  private def fromRow(r: Row): FileAccess =
    FileAccess(r._1, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, readDate(r._6))

  private def query(condition: Fragment): List[FileAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def find(fileId: String, granteeUserId: String): Box[FileAccess] =
    first(query(fr"WHERE fileid = ${Option(fileId)} AND granteeuserid = ${Option(granteeUserId)} ORDER BY id ASC"))

  /** The access list of one file, oldest first. */
  def findByFileId(fileId: String): List[FileAccess] =
    query(fr"WHERE fileid = ${Option(fileId)} ORDER BY createdat ASC, id ASC")

  /** The files shared with the User. */
  def findByGrantee(granteeUserId: String): List[FileAccess] =
    query(fr"WHERE granteeuserid = ${Option(granteeUserId)} ORDER BY id ASC")

  def insert(a: FileAccess): FileAccess = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO fileaccess (fileid, granteeuserid, grantedbyuserid, onbehalfofuserid, createdat)
            VALUES (${Option(a.fileId)}, ${Option(a.granteeUserId)}, ${Option(a.grantedByUserId)},
              ${Option(a.storedOnBehalfOfUserId)}, ${ts(a.createdAt)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    a.copy(id = id)
  }

  /** Always true, like Mapper's bulkDelete_!!. */
  def delete(fileId: String, granteeUserId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM fileaccess WHERE fileid = ${Option(fileId)} AND granteeuserid = ${Option(granteeUserId)}".update.run)
    true
  }

  /** Always true, like Mapper's bulkDelete_!!. */
  def deleteByFileId(fileId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM fileaccess WHERE fileid = ${Option(fileId)}".update.run)
    true
  }
}
