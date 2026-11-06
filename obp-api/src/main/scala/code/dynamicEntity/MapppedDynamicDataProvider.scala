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

package code.DynamicData

import org.json4s._
import code.api.util.{CustomJsonFormats, DoobieUtil}
import code.api.util.ErrorMessages.DynamicDataNotFound
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import net.liftweb.common.{Box, Failure, Full, Empty}
import com.openbankproject.commons.util.json
import org.json4s.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import java.util.Date

/**
 * Note on IsPersonalEntity flag:
 * The IsPersonalEntity flag indicates HOW a record was created (via /my/ endpoint or not),
 * but is NOT used as a filter when querying personal data. The /my/ endpoints return all
 * records belonging to the current user (filtered by UserId), regardless of IsPersonalEntity value.
 * This provides a unified view of a user's data whether it was created via /my/ or non-/my/ endpoints.
 */
/*
 * Every write below also fires the entity's Dynamic Entity webhooks (DynamicEntityWebhookFiring), in the
 * write's own transaction. A write that is not made by a caller fires nothing without special handling:
 * a backup writes into a ZZ_BAK_ entity it has just created, which has no webhooks, and a cascade delete
 * deletes the definition in the same transaction as its records, which deletes the webhooks together
 * with the requests just queued for them.
 */
object MappedDynamicDataProvider extends DynamicDataProvider with CustomJsonFormats{

  /**
   * The user a row belongs to: the caller, or the user its consent names (attribution policy
   * UserReference.DynamicData_UserId). Applied on every entry point that takes a userId, for
   * reads as well as writes, so a consent user reads, updates and deletes the same rows it
   * writes. The resolver logs each redirect; a Failure (invariant broken) keeps the caller.
   * The endpoint decides who may reach this provider (a consent user needs the entity's role,
   * see Http4sDynamicEntity.personalRoleWaived). ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2.
   */
  private def ownerOf(userId: Option[String]): Option[String] =
    userId.map(id => code.users.Users.users.vend.attributedUserId(id, code.users.UserReference.DynamicData_UserId).openOr(id))

  /**
   * The wording of the not-found failure for an owner-scoped read, which varies with what the
   * caller named. The four spellings reach callers as the body of a not-found response, so they are
   * kept exactly, irregularities included: one renders the user id as an Option ("userId = Some(x)"),
   * and the bank id is preceded by a space that the community wording below does not have.
   */
  private def notFoundMessage(entityName: String, id: String, bankId: Option[String],
                              userId: Option[String], isPersonalEntity: Boolean): String = {
    val base = s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id"
    (bankId.isDefined, isPersonalEntity) match {
      case (false, false) => base
      case (false, true)  => s"$base, userId = $userId"
      case (true, false)  => s"$base, bankId= ${bankId.get}"
      case (true, true)   => s"$base, bankId= ${bankId.get}, userId = ${userId.getOrElse(null)}"
    }
  }

  /** As notFoundMessage, for the community reads, whose bank id carries no leading space. */
  private def notFoundCommunityMessage(entityName: String, id: String, bankId: Option[String]): String = {
    val base = s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id"
    bankId.map(theBank => s"$base, bankId=$theBank").getOrElse(base)
  }

  override def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    // Create inserts; it must not fall back to updating whatever row already holds this id. The
    // id can be supplied by the caller, so an upsert here would overwrite another user's record.
    writeRecord(bankId, entityName, requestBody, ownerOf(userId), isPersonalEntity, userId, idValue, existing = None)
  }
  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    // get scopes by space, entity and owner, so reaching the write below means the caller owns the row.
    // Resolve the owner once and use it for both the read and the write, so an agent updates the
    // same row it created rather than looking for one under its own id.
    val owner = ownerOf(userId)
    val dynamicData = get(bankId, entityName, id, owner, isPersonalEntity).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id").asInstanceOf[DynamicData]
    writeRecord(bankId, entityName, requestBody, owner, isPersonalEntity, userId,
      dynamicData.dynamicDataId.getOrElse(""), existing = Some(dynamicData))
  }

  /**
   * Does `entityName` hold a record with this id, in this space?
   *
   * Used to check a `reference:` field. The space matters because a record id is unique within one
   * space and one entity, not across the instance: two banks may each hold a record whose natural
   * key is the country code DE, and so may the system space. `bankId` is the space, and None is the
   * system space, stored as Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID.
   */
  def recordExists(bankId: Option[String], entityName: String, id: String): Boolean =
    DynamicData.find(bankId, entityName, id, None).isDefined

  override def get(bankId: Option[String], entityName: String, id: String, callerUserId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    // An Empty is turned into a Failure rather than passed up: this is get-by-id, and the reason
    // nothing came back is worth stating at this level.
    DynamicData.find(bankId, entityName, id, Some(DynamicData.Ownership(isPersonalEntity, userId))) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(notFoundMessage(entityName, id, bankId, userId, isPersonalEntity))
    }
  }

  override def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject] = {
    getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getAll(bankId: Option[String], entityName: String, callerUserId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    val userId = ownerOf(callerUserId)
    DynamicData.findAll(bankId, entityName, Some(DynamicData.Ownership(isPersonalEntity, userId)))
  }

  override def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean) = {
    get(bankId, entityName, id, userId, isPersonalEntity).map(_ => deleteRecord(bankId, entityName, id))
  }

  // Community access: return ALL records regardless of userId/IsPersonalEntity
  override def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT] =
    DynamicData.findAllCommunity(bankId, entityName)

  override def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject] = {
    getAllCommunity(bankId, entityName)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT] =
    DynamicData.find(bankId, entityName, id, None) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(notFoundCommunityMessage(entityName, id, bankId))
    }

  override def updateCommunity(bankId: Option[String], entityName: String, requestBody: JObject, id: String, callerUserId: Option[String]): Box[DynamicDataT] = {
    val dynamicData = getCommunity(bankId, entityName, id)
      .openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      .asInstanceOf[DynamicData]
    // Preserve the row's existing owner/personal flag — row-level access changes the data, not provenance.
    writeRecord(bankId, entityName, withRecordId(entityName, requestBody, id), dynamicData.userId,
      dynamicData.isPersonalEntity, callerUserId, dynamicData.dynamicDataId.getOrElse(""), existing = Some(dynamicData))
  }

  /**
   * The stored JSON of a record carries the record's own id under `<entity>_id`, which is how a read gives
   * it back and how a cascade delete finds the record. The regular write path adds it before the call
   * (NewStyle.invokeDynamicConnector). The row-level endpoints write straight through this provider, and a
   * PUT or PATCH body does not carry the id, so it is added here when it is missing.
   */
  private def withRecordId(entityName: String, requestBody: JObject, id: String): JObject = {
    val idName = getIdName(entityName)
    (requestBody \ idName) match {
      case JString(_) => requestBody
      case _          => JObject(requestBody.obj.filterNot(_._1 == idName) :+ JField(idName, JString(id)))
    }
  }

  override def getByIds(bankId: Option[String], entityName: String, ids: List[String]): List[DynamicDataT] =
    // Grouped so that a long list never becomes one very long IN clause.
    ids.distinct.grouped(1000).toList.flatMap(someIds => DynamicData.findAllByIds(bankId, entityName, someIds))

  override def deleteCommunity(bankId: Option[String], entityName: String, id: String): Box[Boolean] = {
    getCommunity(bankId, entityName, id).map(_ => deleteRecord(bankId, entityName, id))
  }

  private def deleteRecord(bankId: Option[String], entityName: String, id: String): Boolean = {
    val result = DynamicData.delete(bankId, entityName, id)
    // DE_indexing: remove the projection row in the same transaction (no-op unless projection enabled+ready).
    code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
    if (result) code.dynamicentitywebhook.DynamicEntityWebhookFiring.recordDeleted(bankId, entityName, id)
    result
  }

  override def existsData(bankId: Option[String], dynamicEntityName: String, callerUserId: Option[String], isPersonalEntity: Boolean): Boolean = {
    val userId = ownerOf(callerUserId)
    DynamicData.findAll(bankId, dynamicEntityName, Some(DynamicData.Ownership(isPersonalEntity, userId))).nonEmpty
  }

  /**
   * This works out the two user ids to record as the writer of a record, for a call made by
   * `callerUserId`: the caller itself, which is an agent's own id when an agent made the call, and the
   * user the call was made for. They are the same when nobody is delegating.
   *
   * Both are kept because a record is someone's data, and whether a person wrote it themselves or an
   * agent wrote it for them has to be answerable from the record. `ref` names the pair of columns
   * being filled, so the attribution log line names them. With no caller, both are null.
   */
  private def writersOf(callerUserId: Option[String], ref: code.users.UserReference): (String, String) =
    callerUserId.filter(_.nonEmpty) match {
      case None => (null, null)
      case Some(caller) =>
        code.users.Users.users.vend.attributionOf(caller, ref) match {
          case Full(attribution) => (attribution.userId, attribution.onBehalfOfUserId)
          case _                 => (caller, caller)
        }
    }

  /**
   * This saves a record: a new one when `existing` is None, otherwise that row. `userId` is the owner
   * stored in userid, already resolved. `callerUserId` is the user making the call, not resolved, from
   * which the writer columns are filled: the created pair only when the record is new, and the updated
   * pair on every save.
   */
  private def writeRecord(bankId: Option[String], entityName: String, requestBody: JObject,
                          userId: Option[String], isPersonalEntity: Boolean, callerUserId: Option[String],
                          dynamicDataId: String, existing: Option[DynamicData]): Box[DynamicData] =
    tryo {
      val dataStr = json.compactRender(requestBody)
      // The stored values before this write, which webhook field conditions compare against.
      val before = existing.map(row =>
        scala.util.Try(json.parse(row.dataJson).asInstanceOf[JObject]).toOption.getOrElse(JObject(Nil)))
      val (writerUserId, writerOnBehalfOfUserId) = writersOf(callerUserId,
        if (existing.isEmpty) code.users.UserReference.DynamicData_CreatedByUserId else code.users.UserReference.DynamicData_UpdatedByUserId)
      val saved = existing match {
        case None =>
          DynamicData.insert(dynamicDataId, entityName, dataStr, bankId, userId, isPersonalEntity,
            writerUserId, writerOnBehalfOfUserId)
        case Some(_) =>
          DynamicData.update(dynamicDataId, entityName, dataStr, bankId, userId, isPersonalEntity,
            writerUserId, writerOnBehalfOfUserId)
      }
      // DE_indexing: keep the projection in sync in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onSave(bankId, entityName,
        saved.dynamicDataId.getOrElse(""), requestBody)
      // Dynamic Entity webhooks: queued in this same transaction, so they go out only if the write commits.
      code.dynamicentitywebhook.DynamicEntityWebhookFiring.recordSaved(bankId, entityName,
        saved.dynamicDataId.getOrElse(""), before, requestBody, saved.updatedDate.getOrElse(new Date()))
      saved
    }

  private def getIdName(entityName: String) = {
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase
  }
}

/**
 * One record of a runtime-defined entity type. The record is JSON in `dataJson`; the other columns
 * are scoping keys and the record's metadata.
 *
 * A system level record stores the SYS sentinel (Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) in
 * bankid rather than a SQL NULL; it is filtered back out of `bankId`, so every caller still sees
 * None for such a record. The record id is unique within one space and one entity, which is the
 * unique index on (bankid, dynamicentityname, dynamicdataid), so every write and delete addresses a
 * record by all three.
 *
 * createdat and updatedat were added after records already existed, so a record saved before then
 * holds NULL in both until it is next updated, and only updatedat is filled at that point: its
 * creation time was never recorded and is not guessed. The four writer columns follow the same rule:
 * the created pair is set when the record is first saved, the updated pair on every save. Each pair
 * holds the user who made the call and the user it was made for, which differ only when an agent
 * acted for somebody. They are separate from userid, which is the owner of a personal record.
 */
case class DynamicData(
  private val dynamicDataIdRaw: String,
  dynamicEntityName: String,
  dataJson: String,
  private val bankIdRaw: Option[String],
  private val userIdRaw: Option[String],
  isPersonalEntity: Boolean,
  override val createdDate: Option[Date] = None,
  override val updatedDate: Option[Date] = None,
  private val createdByUserIdRaw: Option[String] = None,
  private val createdByOnBehalfOfUserIdRaw: Option[String] = None,
  private val updatedByUserIdRaw: Option[String] = None,
  private val updatedByOnBehalfOfUserIdRaw: Option[String] = None
) extends DynamicDataT {
  override def dynamicDataId: Option[String] = Option(dynamicDataIdRaw)
  override def bankId: Option[String] =
    bankIdRaw.filter(b => b.nonEmpty && b != DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
  override def userId: Option[String] = userIdRaw
  override def createdByUserId: Option[String] = createdByUserIdRaw.filter(_.nonEmpty)
  override def createdByOnBehalfOfUserId: Option[String] = createdByOnBehalfOfUserIdRaw.filter(_.nonEmpty)
  override def updatedByUserId: Option[String] = updatedByUserIdRaw.filter(_.nonEmpty)
  override def updatedByOnBehalfOfUserId: Option[String] = updatedByOnBehalfOfUserIdRaw.filter(_.nonEmpty)
}

object DynamicData {

  // ProjectionStore builds raw SQL against this table and used to read these names off the Lift
  // metadata. They live here so the DDL and that SQL cannot drift.
  val tableName: String = "dynamicdata"
  val idColumnName: String = "dynamicdataid"
  val jsonColumnName: String = "datajson"
  val entityNameColumnName: String = "dynamicentityname"
  val bankIdColumnName: String = "bankid"
  val userIdColumnName: String = "userid"
  val personalColumnName: String = "ispersonalentity"
  /** The width of dynamicdataid (db.changelog-develop-merge-3.yaml widens it to 255). */
  val idColumnWidth: Int = 255

  /**
   * Whose records of an entity the caller is asking about. Each record of a personal entity belongs
   * to one user and is selected by naming that user; records of any other entity are selected by the
   * flag alone. The user is compared with `= ?` even when it is None, which renders `= NULL` and
   * matches nothing, so a personal read with no user returns no rows rather than every row.
   */
  case class Ownership(isPersonalEntity: Boolean, userId: Option[String])

  private val selectColumns =
    fr"""SELECT dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity,
                createdat, updatedat, createdbyuserid, createdbyonbehalfofuserid, updatedbyuserid,
                updatedbyonbehalfofuserid
         FROM dynamicdata"""

  // Every column is read through Option: a bare mapping throws NonNullableColumnRead on a NULL and
  // fails the whole query, and the metadata columns are NULL on every record that predates them.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[String], Option[String], Option[String], Option[String])

  private def readDate(value: Option[java.sql.Timestamp]): Option[Date] = value.map(t => new Date(t.getTime))

  private def fromRow(row: Row): DynamicData = row match {
    case (dynamicDataId, dynamicEntityName, dataJson, bankId, userId, isPersonalEntity, createdAt,
          updatedAt, createdBy, createdByOnBehalfOf, updatedBy, updatedByOnBehalfOf) =>
      DynamicData(dynamicDataId.orNull, dynamicEntityName.orNull, dataJson.orNull, bankId, userId,
        isPersonalEntity.getOrElse(false), readDate(createdAt), readDate(updatedAt), createdBy,
        createdByOnBehalfOf, updatedBy, updatedByOnBehalfOf)
  }

  private def query(condition: Fragment): List[DynamicData] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicData] =
    query(condition ++ fr"ORDER BY id ASC" ++ DoobieUtil.pagination(1, 0)).headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** The value bankid holds for a space: the bank's id, or SYS for the system space (None). */
  def storedBankId(bankId: Option[String]): String =
    bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /**
   * The bank and the entity a lookup is confined to. Every query here starts from these two, so a
   * record held in one space can never be found, updated or deleted through a request naming another.
   */
  private def inSpace(bankId: Option[String], entityName: String): Fragment =
    fr"WHERE bankid = ${storedBankId(bankId)} AND dynamicentityname = $entityName"

  private def owned(ownership: Option[Ownership]): Fragment = ownership match {
    case Some(Ownership(true, userId)) => fr"AND userid = $userId"
    case Some(Ownership(false, _))     => fr"AND ispersonalentity = false"
    case None                          => Fragment.empty
  }

  def findAll(): List[DynamicData] = query(fr"ORDER BY id ASC")

  def find(bankId: Option[String], entityName: String, id: String, ownership: Option[Ownership]): Box[DynamicData] =
    one(inSpace(bankId, entityName) ++ fr"AND dynamicdataid = $id" ++ owned(ownership))

  def findAll(bankId: Option[String], entityName: String, ownership: Option[Ownership]): List[DynamicData] =
    query(inSpace(bankId, entityName) ++ owned(ownership) ++ fr"ORDER BY id ASC")

  /** Community access: every record of the entity in the space, whatever its owner or personal flag. */
  def findAllCommunity(bankId: Option[String], entityName: String): List[DynamicData] =
    findAll(bankId, entityName, None)

  def findAllByIds(bankId: Option[String], entityName: String, ids: List[String]): List[DynamicData] =
    if (ids.isEmpty) Nil
    else query(inSpace(bankId, entityName) ++ fr"AND" ++
      Fragments.in(fr"dynamicdataid", cats.data.NonEmptyList.fromListUnsafe(ids.distinct)) ++ fr"ORDER BY id ASC")

  /** How many records an entity holds in a space, whoever owns them. */
  def countInSpace(bankId: Option[String], entityName: String): Long =
    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM dynamicdata" ++ inSpace(bankId, entityName)).query[Long].unique)

  /** Impersonal record count for one entity in one space — the dynamic-entity listing shows it. */
  def countImpersonal(bankId: Option[String], entityName: String): Long =
    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM dynamicdata" ++ inSpace(bankId, entityName) ++
      fr"AND ispersonalentity = false").query[Long].unique)

  /**
   * Creates a record. INSERT only - never an upsert.
   *
   * The record id can come from the request body. An id-keyed upsert on this path would let a caller
   * who names an existing id overwrite somebody else's row, which is to say re-own it. A duplicate id
   * within the space hits the unique index instead and surfaces as a Failure through the caller's tryo,
   * with the existing row untouched, which is what Mapper's create path did.
   */
  def insert(dynamicDataId: String, entityName: String, dataJson: String, bankId: Option[String],
             userId: Option[String], isPersonalEntity: Boolean, writerUserId: String,
             writerOnBehalfOfUserId: String): DynamicData = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicdata
            (dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity, createdat,
             updatedat, createdbyuserid, createdbyonbehalfofuserid, updatedbyuserid,
             updatedbyonbehalfofuserid)
            VALUES ($dynamicDataId, ${Option(entityName)}, ${Option(dataJson)}, ${storedBankId(bankId)},
             $userId, $isPersonalEntity, $now, $now, ${Option(writerUserId)},
             ${Option(writerOnBehalfOfUserId)}, ${Option(writerUserId)}, ${Option(writerOnBehalfOfUserId)})"""
        .update.run)
    find(bankId, entityName, dynamicDataId, None)
      .openOrThrowException("the dynamic data just written must be readable")
  }

  /**
   * Rewrites an existing record. Only the update paths use this, and only after they have resolved
   * the record through a scoped read, so by the time this runs the caller's right to the row has been
   * established. The created columns are left as they were.
   */
  def update(dynamicDataId: String, entityName: String, dataJson: String, bankId: Option[String],
             userId: Option[String], isPersonalEntity: Boolean, writerUserId: String,
             writerOnBehalfOfUserId: String): DynamicData = {
    DoobieUtil.runUpdate(
      (fr"""UPDATE dynamicdata SET datajson = ${Option(dataJson)}, userid = $userId,
              ispersonalentity = $isPersonalEntity,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())},
              updatedbyuserid = ${Option(writerUserId)},
              updatedbyonbehalfofuserid = ${Option(writerOnBehalfOfUserId)}""" ++
        inSpace(bankId, entityName) ++ fr"AND dynamicdataid = $dynamicDataId").update.run)
    find(bankId, entityName, dynamicDataId, None)
      .openOrThrowException("the dynamic data just written must be readable")
  }

  def delete(bankId: Option[String], entityName: String, dynamicDataId: String): Boolean =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicdata" ++ inSpace(bankId, entityName) ++ fr"AND dynamicdataid = $dynamicDataId")
        .update.run) > 0

  /** Every record held at one bank, whatever its entity. Never the system space: SYS is not a bank. */
  def deleteAllAtBank(bankId: String): Int =
    if (bankId == null || bankId.isEmpty || bankId == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) 0
    else DoobieUtil.runUpdate(sql"DELETE FROM dynamicdata WHERE bankid = $bankId".update.run)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdata".update.run)
    ()
  }
}
