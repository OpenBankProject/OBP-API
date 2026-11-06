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
package code.retention

import java.util.Date

import code.util.MappedUUID
import net.liftweb.mapper._

/*
 * The tables of data retention. See RETENTION_PLAN.md at the repository root for the model.
 *
 * Naming: these are DB entities, so no class starts with `Mapped` and no column object starts with
 * `m` + uppercase (see MappedClassNameTest). None of them adds a column to an existing table.
 */

/**
 * One retention policy: how long one kind of resource of one space (a bank, or SYS) stays live, and
 * what happens to it then.
 *
 * ArchiveDays holds -1 when the policy has no archive stage, because Mapper has no nullable int; the
 * readers below turn that back into None.
 */
class RetentionPolicy extends LongKeyedMapper[RetentionPolicy] with IdPK with CreatedUpdated {
  def getSingleton = RetentionPolicy

  object RetentionPolicyId extends MappedUUID(this)
  object BankId extends MappedString(this, 255)
  object Name extends MappedString(this, 255)
  object Description extends MappedText(this)
  object ResourceType extends MappedString(this, 64)
  object EntityName extends MappedString(this, 255)
  object DateBasis extends MappedString(this, 255)
  object ConditionField extends MappedString(this, 255)
  object ConditionValue extends MappedString(this, 2000)
  object RetainDays extends MappedInt(this)
  object ArchiveDays extends MappedInt(this) {
    override def defaultValue = -1
  }
  object Action extends MappedString(this, 32)
  object Cascade extends MappedBoolean(this)
  object RequiresApproval extends MappedBoolean(this)
  object Enabled extends MappedBoolean(this)
  object CreatedByUserId extends MappedString(this, 255)

  def retentionPolicyId: String = RetentionPolicyId.get
  def bankId: String = BankId.get
  def name: String = Option(Name.get).getOrElse("")
  def description: String = Option(Description.get).getOrElse("")
  def resourceType: String = ResourceType.get
  def entityName: Option[String] = Option(EntityName.get).filter(_.nonEmpty)
  def dateBasis: String = DateBasis.get
  def condition: Option[RetentionCondition] =
    Option(ConditionField.get).filter(_.nonEmpty).map(field => RetentionCondition(field, Option(ConditionValue.get).getOrElse("")))
  def retainDays: Int = RetainDays.get
  def archiveDays: Option[Int] = Option(ArchiveDays.get).filter(_ >= 0)
  def action: String = Action.get
  def cascade: Boolean = Cascade.get
  def requiresApproval: Boolean = RequiresApproval.get
  def enabled: Boolean = Enabled.get
  def createdByUserId: String = Option(CreatedByUserId.get).getOrElse("")
}

object RetentionPolicy extends RetentionPolicy with LongKeyedMetaMapper[RetentionPolicy] {
  override def dbIndexes: List[BaseIndex[RetentionPolicy]] =
    UniqueIndex(RetentionPolicyId) :: Index(BankId) :: super.dbIndexes
}

/** A simple condition on a resource: the field `field` must have the value `value`. */
case class RetentionCondition(field: String, value: String)

/**
 * One archived resource: a JSON copy of a record that has left the live tables. It is read-only and
 * reachable only through the retention archive endpoints. It stays here until DeleteAfter, and is
 * then deleted. A file's bytes are kept apart, in RetentionArchiveContent, so that listing the
 * archive never loads them.
 */
class RetentionArchive extends LongKeyedMapper[RetentionArchive] with IdPK {
  def getSingleton = RetentionArchive

  object RetentionArchiveId extends MappedUUID(this)
  object RetentionPolicyId extends MappedString(this, 36)
  object RetentionRunId extends MappedString(this, 36)
  object BankId extends MappedString(this, 255)
  object ResourceType extends MappedString(this, 64)
  object ResourceId extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  /** The owner of the archived resource, as it was stored on the resource. Copied, never the caller. */
  object OwnerUserId extends MappedString(this, 255)
  object ParentResourceType extends MappedString(this, 64)
  object ParentResourceId extends MappedString(this, 255)
  object JsonCopy extends MappedText(this)
  object HasContent extends MappedBoolean(this)
  object ContentSizeInBytes extends MappedLong(this)
  object ArchivedAt extends MappedDateTime(this)
  object DeleteAfter extends MappedDateTime(this)

  def retentionArchiveId: String = RetentionArchiveId.get
  def retentionPolicyId: Option[String] = Option(RetentionPolicyId.get).filter(_.nonEmpty)
  def retentionRunId: String = Option(RetentionRunId.get).getOrElse("")
  def bankId: String = BankId.get
  def resourceType: String = ResourceType.get
  def resourceId: String = ResourceId.get
  def entityName: Option[String] = Option(EntityName.get).filter(_.nonEmpty)
  def ownerUserId: Option[String] = Option(OwnerUserId.get).filter(_.nonEmpty)
  def parentResourceType: Option[String] = Option(ParentResourceType.get).filter(_.nonEmpty)
  def parentResourceId: Option[String] = Option(ParentResourceId.get).filter(_.nonEmpty)
  def jsonCopy: String = Option(JsonCopy.get).getOrElse("{}")
  def hasContent: Boolean = HasContent.get
  def contentSizeInBytes: Long = ContentSizeInBytes.get
  def archivedAt: Date = ArchivedAt.get
  def deleteAfter: Option[Date] = Option(DeleteAfter.get)
}

object RetentionArchive extends RetentionArchive with LongKeyedMetaMapper[RetentionArchive] {
  override def dbIndexes: List[BaseIndex[RetentionArchive]] =
    UniqueIndex(RetentionArchiveId) ::
    Index(BankId, ResourceType, ResourceId) ::
    Index(RetentionPolicyId) ::
    Index(DeleteAfter) ::
    super.dbIndexes
}

/**
 * The bytes of an archived file, one row per RetentionArchive row that has content. They are kept
 * Base64 encoded in a text column rather than in a MappedBinary: Lift maps a binary column to a
 * one-byte BINARY on H2, which the test databases use, while a text column holds any size on every
 * database OBP supports.
 */
class RetentionArchiveContent extends LongKeyedMapper[RetentionArchiveContent] with IdPK {
  def getSingleton = RetentionArchiveContent

  object RetentionArchiveId extends MappedString(this, 36)
  object ContentBase64 extends MappedText(this)

  def content: Option[Array[Byte]] =
    Option(ContentBase64.get).flatMap(text => scala.util.Try(java.util.Base64.getDecoder.decode(text)).toOption)
}

object RetentionArchiveContent extends RetentionArchiveContent with LongKeyedMetaMapper[RetentionArchiveContent] {
  override def dbIndexes: List[BaseIndex[RetentionArchiveContent]] = UniqueIndex(RetentionArchiveId) :: super.dbIndexes
}

/**
 * A legal hold: while it is active (ReleasedAt is empty) no retention run archives, anonymises or
 * deletes what it names. It names either one resource (ResourceId), or, for dynamic entity records,
 * every record of one dynamic entity of the space (EntityName with no ResourceId).
 */
class RetentionHold extends LongKeyedMapper[RetentionHold] with IdPK {
  def getSingleton = RetentionHold

  object RetentionHoldId extends MappedUUID(this)
  object BankId extends MappedString(this, 255)
  object ResourceType extends MappedString(this, 64)
  object ResourceId extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  object Reason extends MappedText(this)
  object PlacedByUserId extends MappedString(this, 255)
  object PlacedAt extends MappedDateTime(this)
  object ReleasedByUserId extends MappedString(this, 255)
  object ReleasedAt extends MappedDateTime(this)

  def retentionHoldId: String = RetentionHoldId.get
  def bankId: String = BankId.get
  def resourceType: String = ResourceType.get
  def resourceId: Option[String] = Option(ResourceId.get).filter(_.nonEmpty)
  def entityName: Option[String] = Option(EntityName.get).filter(_.nonEmpty)
  def reason: String = Option(Reason.get).getOrElse("")
  def placedByUserId: String = Option(PlacedByUserId.get).getOrElse("")
  def placedAt: Date = PlacedAt.get
  def releasedByUserId: Option[String] = Option(ReleasedByUserId.get).filter(_.nonEmpty)
  def releasedAt: Option[Date] = Option(ReleasedAt.get)
  def isActive: Boolean = releasedAt.isEmpty
}

object RetentionHold extends RetentionHold with LongKeyedMetaMapper[RetentionHold] {
  override def dbIndexes: List[BaseIndex[RetentionHold]] =
    UniqueIndex(RetentionHoldId) :: Index(BankId, ResourceType) :: super.dbIndexes
}

/**
 * One run of a policy: a preview (counts only) or an execution. An execution of a policy that
 * requires approval starts as pending and runs when a second, different user approves it.
 */
class RetentionRun extends LongKeyedMapper[RetentionRun] with IdPK {
  def getSingleton = RetentionRun

  object RetentionRunId extends MappedUUID(this)
  object RetentionPolicyId extends MappedString(this, 36)
  object BankId extends MappedString(this, 255)
  object Mode extends MappedString(this, 16)
  object Status extends MappedString(this, 16)
  object RequestedByUserId extends MappedString(this, 255)
  object ApprovedByUserId extends MappedString(this, 255)
  object ApprovalDecision extends MappedString(this, 16)
  object ApprovalComment extends MappedText(this)
  object MatchedCount extends MappedInt(this)
  object ArchivedCount extends MappedInt(this)
  object AnonymisedCount extends MappedInt(this)
  object DeletedCount extends MappedInt(this)
  object HeldCount extends MappedInt(this)
  object FailedCount extends MappedInt(this)
  object RequestedAt extends MappedDateTime(this)
  object StartedAt extends MappedDateTime(this)
  object EndedAt extends MappedDateTime(this)
  object Message extends MappedText(this)

  def retentionRunId: String = RetentionRunId.get
  def retentionPolicyId: Option[String] = Option(RetentionPolicyId.get).filter(_.nonEmpty)
  def bankId: String = BankId.get
  def mode: String = Mode.get
  def status: String = Status.get
  def requestedByUserId: String = Option(RequestedByUserId.get).getOrElse("")
  def approvedByUserId: Option[String] = Option(ApprovedByUserId.get).filter(_.nonEmpty)
  def approvalDecision: Option[String] = Option(ApprovalDecision.get).filter(_.nonEmpty)
  def approvalComment: Option[String] = Option(ApprovalComment.get).filter(_.nonEmpty)
  def requestedAt: Date = RequestedAt.get
  def startedAt: Option[Date] = Option(StartedAt.get)
  def endedAt: Option[Date] = Option(EndedAt.get)
  def message: String = Option(Message.get).getOrElse("")
}

object RetentionRun extends RetentionRun with LongKeyedMetaMapper[RetentionRun] {
  override def dbIndexes: List[BaseIndex[RetentionRun]] =
    UniqueIndex(RetentionRunId) :: Index(BankId, RequestedAt) :: Index(RetentionPolicyId, Status) :: super.dbIndexes
}

/**
 * The disposal register: one row per resource a run archived, anonymised or deleted, and per
 * archived resource later deleted from the archive. It holds identifiers only, never content, so it
 * can be kept after the resource itself is gone.
 */
class RetentionRunItem extends LongKeyedMapper[RetentionRunItem] with IdPK {
  def getSingleton = RetentionRunItem

  object RetentionRunItemId extends MappedUUID(this)
  object RetentionRunId extends MappedString(this, 36)
  object RetentionPolicyId extends MappedString(this, 36)
  object BankId extends MappedString(this, 255)
  object ResourceType extends MappedString(this, 64)
  object ResourceId extends MappedString(this, 255)
  object EntityName extends MappedString(this, 255)
  /** The owner of the disposed resource, as it was stored on the resource. Copied, never the caller. */
  object OwnerUserId extends MappedString(this, 255)
  object Action extends MappedString(this, 32)
  object ParentResourceType extends MappedString(this, 64)
  object ParentResourceId extends MappedString(this, 255)
  object RetentionArchiveId extends MappedString(this, 36)
  object DisposedAt extends MappedDateTime(this)

  def retentionRunItemId: String = RetentionRunItemId.get
  def retentionRunId: String = RetentionRunId.get
  def retentionPolicyId: Option[String] = Option(RetentionPolicyId.get).filter(_.nonEmpty)
  def bankId: String = BankId.get
  def resourceType: String = ResourceType.get
  def resourceId: String = ResourceId.get
  def entityName: Option[String] = Option(EntityName.get).filter(_.nonEmpty)
  def ownerUserId: Option[String] = Option(OwnerUserId.get).filter(_.nonEmpty)
  def action: String = Action.get
  def parentResourceType: Option[String] = Option(ParentResourceType.get).filter(_.nonEmpty)
  def parentResourceId: Option[String] = Option(ParentResourceId.get).filter(_.nonEmpty)
  def retentionArchiveId: Option[String] = Option(RetentionArchiveId.get).filter(_.nonEmpty)
  def disposedAt: Date = DisposedAt.get
}

object RetentionRunItem extends RetentionRunItem with LongKeyedMetaMapper[RetentionRunItem] {
  override def dbIndexes: List[BaseIndex[RetentionRunItem]] =
    UniqueIndex(RetentionRunItemId) :: Index(RetentionRunId) :: Index(BankId, ResourceType, ResourceId) :: super.dbIndexes
}
