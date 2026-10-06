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

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

/**
 * This file holds the model for Files: documents such as PDFs and photographs that belong to a space
 * (a bank, or SYS for the system space) and can be attached to OBP records. See the Glossary item
 * "Files" and ideas/ogcr_file_storage.md.
 *
 * A file's bytes never change once stored. A new version of a document is a new file. What can
 * change is which records the file is attached to and which Users have been given access to it.
 *
 * The metadata (File, FileAttachment, FileAccess) is kept apart from the bytes, which are reached
 * only through a [[FileStore]]. Today that store is a Postgres table; it can be replaced by object
 * storage without changing anything above it.
 */
object Files extends SimpleInjector {
  val provider = new Inject(() => buildOne) {}
  def buildOne: FilesProvider = MappedFilesProvider
}

/** One stored file: what it is and who owns it. Never the bytes, which only the [[FileStore]] holds. */
trait FileT {
  def fileId: String
  def bankId: String
  /** Hex SHA-256 of the bytes, computed by OBP when the file was uploaded. */
  def sha256: String
  def sizeInBytes: Long
  def mediaType: String
  def fileName: String
  /** The User who uploaded the file: an agent's own id when an agent made the call. */
  def userId: String
  /** The User an agent uploaded the file for. Empty when nobody was delegating. */
  def onBehalfOfUserId: String
  def createdAt: Date

  /** The file's owner: the User an agent acted for, or else the User who uploaded it. */
  def ownerUserId: String = if (onBehalfOfUserId.nonEmpty) onBehalfOfUserId else userId
}

/** A link between a file and one record, such as a Customer or a Dynamic Entity record. */
trait FileAttachmentT {
  def fileAttachmentId: String
  def fileId: String
  def bankId: String
  def recordType: String
  def recordId: String
  def userId: String
  def onBehalfOfUserId: String
  def createdAt: Date
}

/** A User's read access to one file, given by the file's owner. */
trait FileAccessT {
  def fileId: String
  def granteeUserId: String
  def grantedByUserId: String
  def onBehalfOfUserId: String
  def createdAt: Date
}

/**
 * This trait is where a file's bytes are kept. It is deliberately small, so that the Postgres store
 * used today can be replaced by object storage (S3, MinIO, Azure Blob) later. Because the bytes never
 * change and are identified by their hash, such a move is a copy followed by a hash check.
 *
 * `write` must take part in the caller's database transaction where the store is the database, so
 * that a file's metadata and its bytes are saved together or not at all.
 */
trait FileStore {
  def write(fileId: String, bytes: Array[Byte]): Box[Unit]
  def read(fileId: String): Box[Array[Byte]]
}

trait FilesProvider {
  /** Store the bytes and the metadata of a new file, together. */
  def createFile(bankId: String, sha256: String, mediaType: String, fileName: String,
                 userId: String, onBehalfOfUserId: String, bytes: Array[Byte]): Box[FileT]
  def getFile(bankId: String, fileId: String): Box[FileT]
  def getContent(fileId: String): Box[Array[Byte]]

  def createAttachment(file: FileT, recordType: String, recordId: String,
                       userId: String, onBehalfOfUserId: String): Box[FileAttachmentT]
  def getAttachment(bankId: String, fileId: String, fileAttachmentId: String): Box[FileAttachmentT]
  def attachmentExists(fileId: String, recordType: String, recordId: String): Boolean
  def deleteAttachment(fileAttachmentId: String): Box[Boolean]
  /** The files attached to one record of a space. */
  def getFilesAttachedTo(bankId: String, recordType: String, recordId: String): List[FileT]

  def grantAccess(fileId: String, granteeUserId: String, grantedByUserId: String, onBehalfOfUserId: String): Box[FileAccessT]
  def getAccess(fileId: String, granteeUserId: String): Box[FileAccessT]
  def getAccessList(fileId: String): List[FileAccessT]
  def revokeAccess(fileId: String, granteeUserId: String): Box[Boolean]
}
