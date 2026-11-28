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

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/*
 * The tables of data retention. See RETENTION_PLAN.md at the repository root for the model.
 *
 * Each store below replaces the Lift Mapper entity upstream wrote for its table (retentionpolicy,
 * retentionarchive, retentionarchivecontent, retentionhold, retentionrun, retentionrunitem), and keeps
 * its behaviour: every column is read through Option, and a NULL comes back as the Mapper field gave
 * it (a string as null, or as the default its accessor applied; a flag as false; a number as the
 * field's default). An optional value is still written as the empty string (-1 for ArchiveDays), as
 * upstream wrote it, so the rows are the same rows.
 *
 * Naming: these are DB entities, so no class starts with `Mapped` (see MappedClassNameTest). None of
 * them adds a column to an existing table.
 */

/** Small helpers the six stores share. */
private[retention] object RetentionSql {
  def readDate(value: Option[java.sql.Timestamp]): Date = value.map(t => new Date(t.getTime)).orNull
  def readOptionalDate(value: Option[java.sql.Timestamp]): Option[Date] = value.map(t => new Date(t.getTime))
  def ts(date: Date): Option[java.sql.Timestamp] = Option(date).map(d => new java.sql.Timestamp(d.getTime))
  def where(conditions: List[Fragment]): Fragment =
    if (conditions.isEmpty) Fragment.empty else fr"WHERE" ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
  def first[A](rows: List[A]): Box[A] = rows.headOption match {
    case Some(row) => Full(row)
    case None => Empty
  }
}

import code.retention.RetentionSql._

/**
 * One retention policy: how long one kind of resource of one space (a bank, or SYS) stays live, and
 * what happens to it then.
 *
 * The archivedays column holds -1 when the policy has no archive stage, as upstream stored it
 * (Mapper had no nullable int); it is read back as None.
 *
 * `id` is -1 on a policy not yet saved, as Mapper's IdPK was.
 */
case class RetentionPolicy(
  id: Long,
  retentionPolicyId: String,
  bankId: String,
  name: String,
  description: String,
  resourceType: String,
  entityName: Option[String],
  dateBasis: String,
  condition: Option[RetentionCondition],
  retainDays: Int,
  archiveDays: Option[Int],
  action: String,
  cascade: Boolean,
  requiresApproval: Boolean,
  enabled: Boolean,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
)

object RetentionPolicy {

  private val selectColumns =
    fr"""SELECT id, retentionpolicyid, bankid, name, description, resourcetype, entityname, datebasis,
                conditionfield, conditionvalue, retaindays, archivedays, action, cascade_c, requiresapproval,
                enabled, createdbyuserid, createdat, updatedat
         FROM retentionpolicy"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Int], Option[Int], Option[String],
    Option[Boolean], Option[Boolean], Option[Boolean], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): RetentionPolicy = row match {
    case (id, retentionPolicyId, bankId, name, description, resourceType, entityName, dateBasis, conditionField,
          conditionValue, retainDays, archiveDays, action, cascade, requiresApproval, enabled, createdByUserId,
          createdAt, updatedAt) =>
      RetentionPolicy(id, retentionPolicyId.orNull, bankId.orNull, name.getOrElse(""), description.getOrElse(""),
        resourceType.orNull, entityName.filter(_.nonEmpty), dateBasis.orNull,
        conditionField.filter(_.nonEmpty).map(field => RetentionCondition(field, conditionValue.getOrElse(""))),
        retainDays.getOrElse(0), Some(archiveDays.getOrElse(-1)).filter(_ >= 0), action.orNull,
        cascade.getOrElse(false), requiresApproval.getOrElse(false), enabled.getOrElse(false),
        createdByUserId.getOrElse(""), readDate(createdAt), readDate(updatedAt))
  }

  private def query(condition: Fragment): List[RetentionPolicy] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** A policy not yet saved, with its id, space and creator set; `save` inserts it. */
  def newPolicy(retentionPolicyId: String, bankId: String, createdByUserId: String): RetentionPolicy =
    RetentionPolicy(-1L, retentionPolicyId, bankId, "", "", "", None, "", None, 0, None, "", false, false, false,
      createdByUserId, null, null)

  /** The policy with this id in this space. An id of another space is not found. */
  def find(bankId: String, retentionPolicyId: String): Box[RetentionPolicy] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND retentionpolicyid = ${Option(retentionPolicyId)} ORDER BY id ASC"))

  /** The policies of a space, oldest first. */
  def findAllByBankId(bankId: String): List[RetentionPolicy] =
    query(fr"WHERE bankid = ${Option(bankId)} ORDER BY id ASC")

  def findAllEnabled(): List[RetentionPolicy] =
    query(fr"WHERE enabled = ${true} ORDER BY id ASC")

  /** The policy with this id, in whichever space it is. */
  def findAllByRetentionPolicyId(retentionPolicyId: String): List[RetentionPolicy] =
    query(fr"WHERE retentionpolicyid = ${Option(retentionPolicyId)} ORDER BY id ASC")

  /**
   * Insert a policy not yet saved, or write every field of a saved one. Both stamp updatedat, and an
   * insert createdat too, as Mapper's CreatedUpdated did.
   */
  def save(p: RetentionPolicy): RetentionPolicy = {
    val stamp = new Date()
    if (p.id < 0) {
      val id = DoobieUtil.runUpdate(
        sql"""INSERT INTO retentionpolicy (retentionpolicyid, bankid, name, description, resourcetype, entityname,
                datebasis, conditionfield, conditionvalue, retaindays, archivedays, action, cascade_c,
                requiresapproval, enabled, createdbyuserid, createdat, updatedat)
              VALUES (${Option(p.retentionPolicyId)}, ${Option(p.bankId)}, ${Option(p.name)}, ${Option(p.description)},
                ${Option(p.resourceType)}, ${p.entityName.getOrElse("")}, ${Option(p.dateBasis)},
                ${p.condition.map(_.field).getOrElse("")}, ${p.condition.map(_.value).getOrElse("")}, ${p.retainDays},
                ${p.archiveDays.getOrElse(-1)}, ${Option(p.action)}, ${p.cascade}, ${p.requiresApproval}, ${p.enabled},
                ${Option(p.createdByUserId)}, ${ts(stamp)}, ${ts(stamp)})"""
          .update.withUniqueGeneratedKeys[Long]("id"))
      p.copy(id = id, createdAt = stamp, updatedAt = stamp)
    } else {
      DoobieUtil.runUpdate(
        sql"""UPDATE retentionpolicy SET retentionpolicyid = ${Option(p.retentionPolicyId)}, bankid = ${Option(p.bankId)},
                name = ${Option(p.name)}, description = ${Option(p.description)},
                resourcetype = ${Option(p.resourceType)}, entityname = ${p.entityName.getOrElse("")},
                datebasis = ${Option(p.dateBasis)}, conditionfield = ${p.condition.map(_.field).getOrElse("")},
                conditionvalue = ${p.condition.map(_.value).getOrElse("")}, retaindays = ${p.retainDays},
                archivedays = ${p.archiveDays.getOrElse(-1)}, action = ${Option(p.action)}, cascade_c = ${p.cascade},
                requiresapproval = ${p.requiresApproval}, enabled = ${p.enabled},
                createdbyuserid = ${Option(p.createdByUserId)}, updatedat = ${ts(stamp)}
              WHERE id = ${p.id}""".update.run)
      p.copy(updatedAt = stamp)
    }
  }

  /** Deletes the one row, by its primary key, as Mapper's delete_! did. */
  def delete(p: RetentionPolicy): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM retentionpolicy WHERE id = ${p.id}".update.run) > 0
}

/** A simple condition on a resource: the field `field` must have the value `value`. */
case class RetentionCondition(field: String, value: String)

/**
 * One archived resource: a JSON copy of a record that has left the live tables. It is read-only and
 * reachable only through the retention archive endpoints. It stays here until DeleteAfter, and is
 * then deleted. A file's bytes are kept apart, in RetentionArchiveContent, so that listing the
 * archive never loads them.
 *
 * `ownerUserId` is the owner of the archived resource, as it was stored on the resource. Copied, never
 * the caller.
 */
case class RetentionArchive(
  id: Long,
  retentionArchiveId: String,
  retentionPolicyId: Option[String],
  retentionRunId: String,
  bankId: String,
  resourceType: String,
  resourceId: String,
  entityName: Option[String],
  ownerUserId: Option[String],
  parentResourceType: Option[String],
  parentResourceId: Option[String],
  jsonCopy: String,
  hasContent: Boolean,
  contentSizeInBytes: Long,
  archivedAt: Date,
  deleteAfter: Option[Date]
)

object RetentionArchive {

  private val selectColumns =
    fr"""SELECT id, retentionarchiveid, retentionpolicyid, retentionrunid, bankid, resourcetype, resourceid,
                entityname, owneruserid, parentresourcetype, parentresourceid, jsoncopy, hascontent,
                contentsizeinbytes, archivedat, deleteafter
         FROM retentionarchive"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[Long], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def fromRow(row: Row): RetentionArchive = row match {
    case (id, retentionArchiveId, retentionPolicyId, retentionRunId, bankId, resourceType, resourceId, entityName,
          ownerUserId, parentResourceType, parentResourceId, jsonCopy, hasContent, contentSizeInBytes, archivedAt,
          deleteAfter) =>
      RetentionArchive(id, retentionArchiveId.orNull, retentionPolicyId.filter(_.nonEmpty), retentionRunId.getOrElse(""),
        bankId.orNull, resourceType.orNull, resourceId.orNull, entityName.filter(_.nonEmpty),
        ownerUserId.filter(_.nonEmpty), parentResourceType.filter(_.nonEmpty), parentResourceId.filter(_.nonEmpty),
        jsonCopy.getOrElse("{}"), hasContent.getOrElse(false), contentSizeInBytes.getOrElse(0L), readDate(archivedAt),
        readOptionalDate(deleteAfter))
  }

  private def query(condition: Fragment): List[RetentionArchive] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def due(now: Date): Fragment = fr"deleteafter <= ${ts(now)}"

  def insert(a: RetentionArchive): RetentionArchive = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO retentionarchive (retentionarchiveid, retentionpolicyid, retentionrunid, bankid, resourcetype,
              resourceid, entityname, owneruserid, parentresourcetype, parentresourceid, jsoncopy, hascontent,
              contentsizeinbytes, archivedat, deleteafter)
            VALUES (${Option(a.retentionArchiveId)}, ${a.retentionPolicyId.getOrElse("")}, ${Option(a.retentionRunId)},
              ${Option(a.bankId)}, ${Option(a.resourceType)}, ${Option(a.resourceId)}, ${a.entityName.getOrElse("")},
              ${a.ownerUserId.getOrElse("")}, ${a.parentResourceType.getOrElse("")}, ${a.parentResourceId.getOrElse("")},
              ${Option(a.jsonCopy)}, ${a.hasContent}, ${a.contentSizeInBytes}, ${ts(a.archivedAt)},
              ${a.deleteAfter.flatMap(ts)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    a.copy(id = id)
  }

  /** The entry with this id in this space. An id of another space is not found. */
  def find(bankId: String, retentionArchiveId: String): Box[RetentionArchive] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND retentionarchiveid = ${Option(retentionArchiveId)} ORDER BY id ASC"))

  /** One page of a space's archive, newest first; each filter given matches its column exactly. */
  def findPage(bankId: String, resourceType: Option[String], resourceId: Option[String], entityName: Option[String],
               ownerUserId: Option[String], retentionPolicyId: Option[String], retentionRunId: Option[String],
               limit: Int, offset: Int): List[RetentionArchive] =
    query(where(List(
      Some(fr"bankid = ${Option(bankId)}"),
      resourceType.map(v => fr"resourcetype = ${Option(v)}"),
      resourceId.map(v => fr"resourceid = ${Option(v)}"),
      entityName.map(v => fr"entityname = ${Option(v)}"),
      ownerUserId.map(v => fr"owneruserid = ${Option(v)}"),
      retentionPolicyId.map(v => fr"retentionpolicyid = ${Option(v)}"),
      retentionRunId.map(v => fr"retentionrunid = ${Option(v)}")
    ).flatten) ++ fr"ORDER BY id DESC" ++ DoobieUtil.pagination(limit, offset))

  /** How many entries this policy archived whose archive period has ended by `now`. */
  def countDueOfPolicy(retentionPolicyId: String, now: Date): Long =
    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM retentionarchive" ++
      where(List(fr"retentionpolicyid = ${Option(retentionPolicyId)}", due(now)))).query[Long].unique)

  /** Up to `limit` entries this policy archived whose archive period has ended, the earliest due first. */
  def findDueOfPolicy(retentionPolicyId: String, now: Date, limit: Int): List[RetentionArchive] =
    query(where(List(fr"retentionpolicyid = ${Option(retentionPolicyId)}", due(now))) ++
      fr"ORDER BY deleteafter ASC, id ASC" ++ DoobieUtil.pagination(limit, 0))

  /** Up to `limit` entries of a space whose archive period has ended, the earliest due first. */
  def findDueOfBank(bankId: String, now: Date, limit: Int): List[RetentionArchive] =
    query(where(List(fr"bankid = ${Option(bankId)}", due(now))) ++
      fr"ORDER BY deleteafter ASC, id ASC" ++ DoobieUtil.pagination(limit, 0))

  /** The spaces of up to `limit` entries whose archive period has ended, each space once. */
  def findDueBankIds(now: Date, limit: Int): List[String] =
    DoobieUtil.runQuery((fr"SELECT bankid FROM retentionarchive" ++ where(List(due(now))) ++
      fr"ORDER BY id ASC" ++ DoobieUtil.pagination(limit, 0)).query[Option[String]].to[List]).map(_.orNull).distinct

  def countAll(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM retentionarchive".query[Long].unique)

  def countByBankId(bankId: String): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM retentionarchive WHERE bankid = ${Option(bankId)}".query[Long].unique)

  def countByRetentionPolicyId(retentionPolicyId: String): Long =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM retentionarchive WHERE retentionpolicyid = ${Option(retentionPolicyId)}".query[Long].unique)

  /** Deletes the one row, by its primary key, as Mapper's delete_! did. */
  def delete(a: RetentionArchive): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM retentionarchive WHERE id = ${a.id}".update.run) > 0
}

/**
 * The bytes of an archived file, one row per RetentionArchive row that has content. They are kept
 * Base64 encoded in a text column rather than in a binary one, as upstream chose: Lift maps a binary
 * column to a one-byte BINARY on H2, which the test databases use, while a text column holds any size
 * on every database OBP supports.
 */
case class RetentionArchiveContent(id: Long, retentionArchiveId: String, contentBase64: String) {
  def content: Option[Array[Byte]] =
    Option(contentBase64).flatMap(text => scala.util.Try(java.util.Base64.getDecoder.decode(text)).toOption)
}

object RetentionArchiveContent {

  def insert(retentionArchiveId: String, bytes: Array[Byte]): RetentionArchiveContent = {
    val base64 = java.util.Base64.getEncoder.encodeToString(bytes)
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO retentionarchivecontent (retentionarchiveid, contentbase64)
            VALUES (${Option(retentionArchiveId)}, ${Option(base64)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    RetentionArchiveContent(id, retentionArchiveId, base64)
  }

  def findByRetentionArchiveId(retentionArchiveId: String): Box[RetentionArchiveContent] =
    first(DoobieUtil.runQuery(
      sql"""SELECT id, retentionarchiveid, contentbase64 FROM retentionarchivecontent
            WHERE retentionarchiveid = ${Option(retentionArchiveId)} ORDER BY id ASC"""
        .query[(Long, Option[String], Option[String])].to[List])
      .map { case (id, archiveId, content) => RetentionArchiveContent(id, archiveId.orNull, content.orNull) })

  /** Always true, like Mapper's bulkDelete_!!. */
  def deleteByRetentionArchiveId(retentionArchiveId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM retentionarchivecontent WHERE retentionarchiveid = ${Option(retentionArchiveId)}".update.run)
    true
  }
}

/**
 * A legal hold: while it is active (ReleasedAt is empty) no retention run archives, anonymises or
 * deletes what it names. It names either one resource (ResourceId), or, for dynamic entity records,
 * every record of one dynamic entity of the space (EntityName with no ResourceId).
 */
case class RetentionHold(
  id: Long,
  retentionHoldId: String,
  bankId: String,
  resourceType: String,
  resourceId: Option[String],
  entityName: Option[String],
  reason: String,
  placedByUserId: String,
  placedAt: Date,
  releasedByUserId: Option[String],
  releasedAt: Option[Date]
) {
  def isActive: Boolean = releasedAt.isEmpty
}

object RetentionHold {

  private val selectColumns =
    fr"""SELECT id, retentionholdid, bankid, resourcetype, resourceid, entityname, reason, placedbyuserid, placedat,
                releasedbyuserid, releasedat
         FROM retentionhold"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[String], Option[java.sql.Timestamp])

  private def fromRow(row: Row): RetentionHold = row match {
    case (id, retentionHoldId, bankId, resourceType, resourceId, entityName, reason, placedByUserId, placedAt,
          releasedByUserId, releasedAt) =>
      RetentionHold(id, retentionHoldId.orNull, bankId.orNull, resourceType.orNull, resourceId.filter(_.nonEmpty),
        entityName.filter(_.nonEmpty), reason.getOrElse(""), placedByUserId.getOrElse(""), readDate(placedAt),
        releasedByUserId.filter(_.nonEmpty), readOptionalDate(releasedAt))
  }

  private def query(condition: Fragment): List[RetentionHold] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(h: RetentionHold): RetentionHold = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO retentionhold (retentionholdid, bankid, resourcetype, resourceid, entityname, reason,
              placedbyuserid, placedat, releasedbyuserid, releasedat)
            VALUES (${Option(h.retentionHoldId)}, ${Option(h.bankId)}, ${Option(h.resourceType)},
              ${h.resourceId.getOrElse("")}, ${h.entityName.getOrElse("")}, ${Option(h.reason)},
              ${Option(h.placedByUserId)}, ${ts(h.placedAt)}, ${h.releasedByUserId.getOrElse("")},
              ${h.releasedAt.flatMap(ts)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    h.copy(id = id)
  }

  /** The hold with this id in this space. An id of another space is not found. */
  def find(bankId: String, retentionHoldId: String): Box[RetentionHold] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND retentionholdid = ${Option(retentionHoldId)} ORDER BY id ASC"))

  /** The holds of a space that have not been released. */
  def findAllActive(bankId: String): List[RetentionHold] =
    query(fr"WHERE bankid = ${Option(bankId)} AND releasedat IS NULL ORDER BY id ASC")

  /** One page of a space's holds, newest first, released ones included unless `isActive` says otherwise. */
  def findPage(bankId: String, resourceType: Option[String], resourceId: Option[String], isActive: Option[Boolean],
               limit: Int, offset: Int): List[RetentionHold] =
    query(where(List(
      Some(fr"bankid = ${Option(bankId)}"),
      resourceType.map(v => fr"resourcetype = ${Option(v)}"),
      resourceId.map(v => fr"resourceid = ${Option(v)}"),
      isActive.map(active => if (active) fr"releasedat IS NULL" else fr"releasedat IS NOT NULL")
    ).flatten) ++ fr"ORDER BY id DESC" ++ DoobieUtil.pagination(limit, offset))

  /** Release the hold: it is kept, with who released it and when. */
  def release(h: RetentionHold, releasedByUserId: String, releasedAt: Date): RetentionHold = {
    DoobieUtil.runUpdate(
      sql"""UPDATE retentionhold SET releasedbyuserid = ${Option(releasedByUserId)}, releasedat = ${ts(releasedAt)}
            WHERE id = ${h.id}""".update.run)
    h.copy(releasedByUserId = Option(releasedByUserId).filter(_.nonEmpty), releasedAt = Option(releasedAt))
  }
}

/**
 * One run of a policy: a preview (counts only) or an execution. An execution of a policy that
 * requires approval starts as pending and runs when a second, different user approves it.
 *
 * `id` is -1 on a run not yet inserted, as Mapper's IdPK was.
 */
case class RetentionRun(
  id: Long,
  retentionRunId: String,
  retentionPolicyId: Option[String],
  bankId: String,
  mode: String,
  status: String,
  requestedByUserId: String,
  approvedByUserId: Option[String],
  approvalDecision: Option[String],
  approvalComment: Option[String],
  matchedCount: Int,
  archivedCount: Int,
  anonymisedCount: Int,
  deletedCount: Int,
  heldCount: Int,
  failedCount: Int,
  requestedAt: Date,
  startedAt: Option[Date],
  endedAt: Option[Date],
  message: String
)

object RetentionRun {

  private val selectColumns =
    fr"""SELECT id, retentionrunid, retentionpolicyid, bankid, mode_c, status, requestedbyuserid, approvedbyuserid,
                approvaldecision, approvalcomment, matchedcount, archivedcount, anonymisedcount, deletedcount,
                heldcount, failedcount, requestedat, startedat, endedat, message
         FROM retentionrun"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Int], Option[Int], Option[Int],
    Option[Int], Option[Int], Option[Int], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[String])

  private def fromRow(row: Row): RetentionRun = row match {
    case (id, retentionRunId, retentionPolicyId, bankId, mode, status, requestedByUserId, approvedByUserId,
          approvalDecision, approvalComment, matchedCount, archivedCount, anonymisedCount, deletedCount, heldCount,
          failedCount, requestedAt, startedAt, endedAt, message) =>
      RetentionRun(id, retentionRunId.orNull, retentionPolicyId.filter(_.nonEmpty), bankId.orNull, mode.orNull,
        status.orNull, requestedByUserId.getOrElse(""), approvedByUserId.filter(_.nonEmpty),
        approvalDecision.filter(_.nonEmpty), approvalComment.filter(_.nonEmpty), matchedCount.getOrElse(0),
        archivedCount.getOrElse(0), anonymisedCount.getOrElse(0), deletedCount.getOrElse(0), heldCount.getOrElse(0),
        failedCount.getOrElse(0), readDate(requestedAt), readOptionalDate(startedAt), readOptionalDate(endedAt),
        message.getOrElse(""))
  }

  private def query(condition: Fragment): List[RetentionRun] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(r: RetentionRun): RetentionRun = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO retentionrun (retentionrunid, retentionpolicyid, bankid, mode_c, status, requestedbyuserid,
              approvedbyuserid, approvaldecision, approvalcomment, matchedcount, archivedcount, anonymisedcount,
              deletedcount, heldcount, failedcount, requestedat, startedat, endedat, message)
            VALUES (${Option(r.retentionRunId)}, ${r.retentionPolicyId.getOrElse("")}, ${Option(r.bankId)},
              ${Option(r.mode)}, ${Option(r.status)}, ${Option(r.requestedByUserId)}, ${r.approvedByUserId.getOrElse("")},
              ${r.approvalDecision.getOrElse("")}, ${r.approvalComment.getOrElse("")}, ${r.matchedCount},
              ${r.archivedCount}, ${r.anonymisedCount}, ${r.deletedCount}, ${r.heldCount}, ${r.failedCount},
              ${ts(r.requestedAt)}, ${r.startedAt.flatMap(ts)}, ${r.endedAt.flatMap(ts)}, ${Option(r.message)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    r.copy(id = id)
  }

  /** Write every field of a run already inserted. */
  def save(r: RetentionRun): RetentionRun = {
    DoobieUtil.runUpdate(
      sql"""UPDATE retentionrun SET retentionrunid = ${Option(r.retentionRunId)},
              retentionpolicyid = ${r.retentionPolicyId.getOrElse("")}, bankid = ${Option(r.bankId)},
              mode_c = ${Option(r.mode)}, status = ${Option(r.status)},
              requestedbyuserid = ${Option(r.requestedByUserId)}, approvedbyuserid = ${r.approvedByUserId.getOrElse("")},
              approvaldecision = ${r.approvalDecision.getOrElse("")}, approvalcomment = ${r.approvalComment.getOrElse("")},
              matchedcount = ${r.matchedCount}, archivedcount = ${r.archivedCount},
              anonymisedcount = ${r.anonymisedCount}, deletedcount = ${r.deletedCount}, heldcount = ${r.heldCount},
              failedcount = ${r.failedCount}, requestedat = ${ts(r.requestedAt)}, startedat = ${r.startedAt.flatMap(ts)},
              endedat = ${r.endedAt.flatMap(ts)}, message = ${Option(r.message)}
            WHERE id = ${r.id}""".update.run)
    r
  }

  /** The run with this id in this space. An id of another space is not found. */
  def find(bankId: String, retentionRunId: String): Box[RetentionRun] =
    first(query(fr"WHERE bankid = ${Option(bankId)} AND retentionrunid = ${Option(retentionRunId)} ORDER BY id ASC"))

  /** One page of a space's runs, newest first. */
  def findPage(bankId: String, retentionPolicyId: Option[String], status: Option[String], limit: Int,
               offset: Int): List[RetentionRun] =
    query(where(List(
      Some(fr"bankid = ${Option(bankId)}"),
      retentionPolicyId.map(v => fr"retentionpolicyid = ${Option(v)}"),
      status.map(v => fr"status = ${Option(v)}")
    ).flatten) ++ fr"ORDER BY id DESC" ++ DoobieUtil.pagination(limit, offset))

  def findAllByRetentionPolicyId(retentionPolicyId: String): List[RetentionRun] =
    query(fr"WHERE retentionpolicyid = ${Option(retentionPolicyId)} ORDER BY id ASC")

  def findAllByRetentionPolicyIdAndStatus(retentionPolicyId: String, status: String): List[RetentionRun] =
    query(fr"WHERE retentionpolicyid = ${Option(retentionPolicyId)} AND status = ${Option(status)} ORDER BY id ASC")

  def existsByRetentionPolicyIdAndStatus(retentionPolicyId: String, status: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM retentionrun
            WHERE retentionpolicyid = ${Option(retentionPolicyId)} AND status = ${Option(status)}"""
        .query[Long].unique) > 0

  /** Is there a run of this policy with this status that started after `startedAfter`? */
  def existsStartedAfter(retentionPolicyId: String, status: String, startedAfter: Date): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM retentionrun
            WHERE retentionpolicyid = ${Option(retentionPolicyId)} AND status = ${Option(status)}
              AND startedat > ${ts(startedAfter)}"""
        .query[Long].unique) > 0

  def countByBankId(bankId: String): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM retentionrun WHERE bankid = ${Option(bankId)}".query[Long].unique)
}

/**
 * The disposal register: one row per resource a run archived, anonymised or deleted, and per
 * archived resource later deleted from the archive. It holds identifiers only, never content, so it
 * can be kept after the resource itself is gone.
 *
 * `ownerUserId` is the owner of the disposed resource, as it was stored on the resource. Copied, never
 * the caller.
 */
case class RetentionRunItem(
  id: Long,
  retentionRunItemId: String,
  retentionRunId: String,
  retentionPolicyId: Option[String],
  bankId: String,
  resourceType: String,
  resourceId: String,
  entityName: Option[String],
  ownerUserId: Option[String],
  action: String,
  parentResourceType: Option[String],
  parentResourceId: Option[String],
  retentionArchiveId: Option[String],
  disposedAt: Date
)

object RetentionRunItem {

  private val selectColumns =
    fr"""SELECT id, retentionrunitemid, retentionrunid, retentionpolicyid, bankid, resourcetype, resourceid,
                entityname, owneruserid, action, parentresourcetype, parentresourceid, retentionarchiveid, disposedat
         FROM retentionrunitem"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): RetentionRunItem = row match {
    case (id, retentionRunItemId, retentionRunId, retentionPolicyId, bankId, resourceType, resourceId, entityName,
          ownerUserId, action, parentResourceType, parentResourceId, retentionArchiveId, disposedAt) =>
      RetentionRunItem(id, retentionRunItemId.orNull, retentionRunId.orNull, retentionPolicyId.filter(_.nonEmpty),
        bankId.orNull, resourceType.orNull, resourceId.orNull, entityName.filter(_.nonEmpty),
        ownerUserId.filter(_.nonEmpty), action.orNull, parentResourceType.filter(_.nonEmpty),
        parentResourceId.filter(_.nonEmpty), retentionArchiveId.filter(_.nonEmpty), readDate(disposedAt))
  }

  def insert(i: RetentionRunItem): RetentionRunItem = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO retentionrunitem (retentionrunitemid, retentionrunid, retentionpolicyid, bankid, resourcetype,
              resourceid, entityname, owneruserid, action, parentresourcetype, parentresourceid, retentionarchiveid,
              disposedat)
            VALUES (${Option(i.retentionRunItemId)}, ${Option(i.retentionRunId)}, ${i.retentionPolicyId.getOrElse("")},
              ${Option(i.bankId)}, ${Option(i.resourceType)}, ${Option(i.resourceId)}, ${i.entityName.getOrElse("")},
              ${i.ownerUserId.getOrElse("")}, ${Option(i.action)}, ${i.parentResourceType.getOrElse("")},
              ${i.parentResourceId.getOrElse("")}, ${i.retentionArchiveId.getOrElse("")}, ${ts(i.disposedAt)})"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    i.copy(id = id)
  }

  /** One page of one run's register, in the order the resources were disposed of. */
  def findPageByRetentionRunId(retentionRunId: String, limit: Int, offset: Int): List[RetentionRunItem] =
    DoobieUtil.runQuery((selectColumns ++ fr"WHERE retentionrunid = ${Option(retentionRunId)} ORDER BY id ASC" ++
      DoobieUtil.pagination(limit, offset)).query[Row].to[List]).map(fromRow)
}
