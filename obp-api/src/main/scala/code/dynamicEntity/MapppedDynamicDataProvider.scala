package code.DynamicData

import org.json4s._
import code.api.util.{CustomJsonFormats, DoobieUtil}
import code.api.util.ErrorMessages.DynamicDataNotFound
import net.liftweb.common.{Box, Failure, Full, Empty}
import com.openbankproject.commons.util.json
import org.json4s.JObject
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL._
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

/**
 * Note on IsPersonalEntity flag:
 * The IsPersonalEntity flag indicates HOW a record was created (via /my/ endpoint or not),
 * but is NOT used as a filter when querying personal data. The /my/ endpoints return all
 * records belonging to the current user (filtered by UserId), regardless of IsPersonalEntity value.
 * This provides a unified view of a user's data whether it was created via /my/ or non-/my/ endpoints.
 */
object MappedDynamicDataProvider extends DynamicDataProvider with CustomJsonFormats{
  override def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val idName = getIdName(entityName)
    val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
    // Create inserts; it must not fall back to updating whatever row already holds this id. The
    // id can be supplied by the caller, so an upsert here would overwrite another user's record.
    writeRecord(bankId, entityName, requestBody, userId, isPersonalEntity, idValue,
      DynamicData.insert)
  }
  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    // get scopes by user and bank, so reaching the write below means the caller owns the row.
    val dynamicData = get(bankId, entityName, id, userId, isPersonalEntity).openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id").asInstanceOf[DynamicData]
    writeRecord(bankId, entityName, requestBody, userId, isPersonalEntity,
      dynamicData.dynamicDataId.getOrElse(""), DynamicData.updateById)
  }

  // Separate method for reference validation - only checks ID and entity name exist
  def existsById(entityName: String, id: String): Boolean = {
    println(s"========== Reference validation: checking if DynamicDataId='$id' exists for DynamicEntityName='$entityName' ==========")
    val exists = DynamicData.countByIdAndEntity(id, entityName) > 0
    println(s"========== Reference validation result: exists=$exists ==========")
    exists
  }

  override def get(bankId: Option[String],entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    // Four scopes, unchanged: (system|bank) x (impersonal|personal). The personal ones compare
    // userid with `= ?` even when userId is None, which renders `= NULL` and matches nothing.
    val found =
      if (isPersonalEntity) DynamicData.findPersonal(bankId, entityName, id, userId)
      else DynamicData.findImpersonal(bankId, entityName, id)
    found match {
      case Full(dynamicData) => Full(dynamicData)
      case _ =>
        //forced the empty also to a error here. this is get Dynamic by Id, if it return Empty, better show the error in this level.
        val scope = (bankId, isPersonalEntity) match {
          case (None, false) => ""
          case (None, true) => s", userId = $userId"
          case (Some(b), false) => s", bankId= $b"
          case (Some(b), true) => s", bankId= $b, userId = ${userId.getOrElse("")}"
        }
        Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id$scope")
    }
  }

  override def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject] = {
    getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    if (isPersonalEntity) DynamicData.findAllPersonal(bankId, entityName, userId)
    else DynamicData.findAllImpersonal(bankId, entityName)
  }

  override def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean) = {
    get(bankId, entityName, id, userId, isPersonalEntity).map { d =>
      val result = DynamicData.delete(d.asInstanceOf[DynamicData].dynamicDataId.getOrElse(""))
      // DE_indexing: remove the projection row in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
      result
    }
  }

  // Community access: return ALL records regardless of userId/IsPersonalEntity
  override def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT] = {
    DynamicData.findAllCommunity(bankId, entityName)
  }

  override def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject] = {
    getAllCommunity(bankId, entityName)
      .map(it => json.parse(it.dataJson))
      .map(_.asInstanceOf[JObject])
  }

  override def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT] = {
    DynamicData.findCommunity(bankId, entityName, id) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ =>
        val scope = bankId.map(b => s", bankId=$b").getOrElse("")
        Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id$scope")
    }
  }

  override def updateCommunity(bankId: Option[String], entityName: String, requestBody: JObject, id: String): Box[DynamicDataT] = {
    val dynamicData = getCommunity(bankId, entityName, id)
      .openOrThrowException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      .asInstanceOf[DynamicData]
    // Preserve the row's existing owner/personal flag — row-level access changes the data, not provenance.
    // getCommunity has already resolved the row within the bank scope, so this is an update.
    writeRecord(bankId, entityName, requestBody, dynamicData.userId, dynamicData.isPersonalEntity,
      dynamicData.dynamicDataId.getOrElse(""), DynamicData.updateById)
  }

  override def deleteCommunity(bankId: Option[String], entityName: String, id: String): Box[Boolean] = {
    getCommunity(bankId, entityName, id).map { d =>
      val result = DynamicData.delete(d.asInstanceOf[DynamicData].dynamicDataId.getOrElse(""))
      // DE_indexing: remove the projection row in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
      result
    }
  }

  override def existsData(bankId: Option[String], dynamicEntityName: String, userId: Option[String], isPersonalEntity: Boolean): Boolean = {
    if (isPersonalEntity) DynamicData.findAllPersonal(bankId, dynamicEntityName, userId).nonEmpty
    else DynamicData.findAllImpersonal(bankId, dynamicEntityName).nonEmpty
  }

  /** The shared half of create and update; `write` is what decides which of the two it is. */
  private def writeRecord(bankId: Option[String], entityName: String, requestBody: JObject,
                          userId: Option[String], isPersonalEntity: Boolean,
                          dynamicDataId: String,
                          write: (String, String, String, Option[String], Option[String], Boolean) => DynamicData): Box[DynamicData] =
    tryo {
      val dataStr = json.compactRender(requestBody)
      val saved = write(dynamicDataId, entityName, dataStr, bankId, userId, isPersonalEntity)
      // DE_indexing: keep the projection in sync in the same transaction (no-op unless projection enabled+ready).
      code.api.dynamic.entity.projection.ProjectionDualWrite.onSave(bankId, entityName,
        saved.dynamicDataId.getOrElse(""), requestBody)
      saved
    }

  private def getIdName(entityName: String) = {
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase
  }
}

/**
 * One record of a runtime-defined entity type. The record is JSON in `dataJson`; the other columns
 * are only scoping keys.
 *
 * `bankId` and `userId` both hold NULL but are NOT read the same way, and the difference is
 * invisible without reading both: bankId uses `IS NULL` for the system-level case, while userId is
 * compared with `= ?` even when the caller passes None. Lift rendered that second case as
 * `= NULL`, which matches nothing — so a personal-entity query with no user id has always returned
 * no rows rather than every row. Preserved literally.
 */
case class DynamicData(
  private val dynamicDataIdRaw: String,
  dynamicEntityName: String,
  dataJson: String,
  private val bankIdRaw: Option[String],
  private val userIdRaw: Option[String],
  isPersonalEntity: Boolean
) extends DynamicDataT {
  override def dynamicDataId: Option[String] = Option(dynamicDataIdRaw)
  override def bankId: Option[String] = bankIdRaw
  override def userId: Option[String] = userIdRaw
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

  private val selectColumns =
    fr"""SELECT dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity
         FROM dynamicdata"""

  // The name and payload columns are bound through Option on insert, so they are read as Option
  // too - a bare String mapping throws NonNullableColumnRead on a NULL and fails the whole query.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean])

  private def fromRow(row: Row): DynamicData = row match {
    case (dynamicDataId, dynamicEntityName, dataJson, bankId, userId, isPersonalEntity) =>
      DynamicData(dynamicDataId.orNull, dynamicEntityName.orNull, dataJson.orNull, bankId, userId,
        isPersonalEntity.getOrElse(false))
  }

  private def query(condition: Fragment): List[DynamicData] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicData] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** `None` means the column must be NULL — Lift's NullRef, not "do not filter". */
  private def scopedBank(bankId: Option[String]): Fragment =
    bankId.map(b => fr"bankid = $b").getOrElse(fr"bankid IS NULL")

  /**
   * `= ?` even for None, which renders `= NULL` and matches nothing. That is what
   * `By(UserId, userId.getOrElse(null))` did, and callers depend on the empty result.
   */
  private def scopedUser(userId: Option[String]): Fragment = fr"userid = $userId"

  def countByIdAndEntity(dynamicDataId: String, entityName: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM dynamicdata
            WHERE dynamicdataid = $dynamicDataId AND dynamicentityname = $entityName"""
        .query[Long].unique)

  /** Impersonal record count for one entity in one scope — the dynamic-entity listing shows it. */
  def countImpersonal(bankId: Option[String], entityName: String): Long =
    DoobieUtil.runQuery(
      (fr"""SELECT COUNT(*) FROM dynamicdata
            WHERE dynamicentityname = $entityName AND ispersonalentity = false AND """ ++
        scopedBank(bankId)).query[Long].unique)

  def findAll(): List[DynamicData] = query(fr"ORDER BY id ASC")

  def findImpersonal(bankId: Option[String], entityName: String, id: String): Box[DynamicData] =
    one(fr"""WHERE dynamicdataid = $id AND dynamicentityname = $entityName
             AND ispersonalentity = false AND """ ++ scopedBank(bankId))

  def findPersonal(bankId: Option[String], entityName: String, id: String,
                   userId: Option[String]): Box[DynamicData] =
    one(fr"WHERE dynamicdataid = $id AND dynamicentityname = $entityName AND " ++
      scopedUser(userId) ++ fr"AND " ++ scopedBank(bankId))

  def findAllImpersonal(bankId: Option[String], entityName: String): List[DynamicData] =
    query(fr"WHERE dynamicentityname = $entityName AND ispersonalentity = false AND " ++
      scopedBank(bankId) ++ fr"ORDER BY id ASC")

  def findAllPersonal(bankId: Option[String], entityName: String,
                      userId: Option[String]): List[DynamicData] =
    query(fr"WHERE dynamicentityname = $entityName AND " ++ scopedUser(userId) ++
      fr"AND " ++ scopedBank(bankId) ++ fr"ORDER BY id ASC")

  /** Community access: every record of the entity in scope, whatever its owner or personal flag. */
  def findAllCommunity(bankId: Option[String], entityName: String): List[DynamicData] =
    query(fr"WHERE dynamicentityname = $entityName AND " ++ scopedBank(bankId) ++
      fr"ORDER BY id ASC")

  def findCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicData] =
    one(fr"WHERE dynamicdataid = $id AND dynamicentityname = $entityName AND " ++
      scopedBank(bankId))

  /**
   * Creates a record. INSERT only - never an upsert.
   *
   * The record id can come from the request body, and it is unique across the whole table rather
   * than per entity, per bank or per user. An id-keyed upsert on this path would therefore let a
   * caller who names an existing id overwrite somebody else's row - including its userid and
   * bankid, which is to say re-own it. Mapper's create path was `DynamicData.create...saveMe()`,
   * an INSERT, so a duplicate id hit DYNAMICDATA_DYNAMICDATAID and surfaced as a Failure with the
   * existing row untouched. That is the behaviour kept here: the unique index is the check, and
   * the caller's `tryo` turns the violation back into a Failure.
   */
  def insert(dynamicDataId: String, entityName: String, dataJson: String, bankId: Option[String],
             userId: Option[String], isPersonalEntity: Boolean): DynamicData = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicdata
            (dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity)
            VALUES ($dynamicDataId, ${Option(entityName)}, ${Option(dataJson)}, $bankId, $userId,
             $isPersonalEntity)"""
        .update.run)
    one(fr"WHERE dynamicdataid = $dynamicDataId")
      .openOrThrowException("the dynamic data just written must be readable")
  }

  /**
   * Rewrites an existing record, addressed by id.
   *
   * Only the update path may use this, and only after it has resolved the record through `get`,
   * which scopes the lookup by user and bank - so by the time this runs the caller's right to the
   * row has been established. It is deliberately not reachable from create.
   */
  def updateById(dynamicDataId: String, entityName: String, dataJson: String,
                 bankId: Option[String], userId: Option[String],
                 isPersonalEntity: Boolean): DynamicData = {
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicdata SET dynamicentityname = ${Option(entityName)},
              datajson = ${Option(dataJson)}, bankid = $bankId, userid = $userId,
              ispersonalentity = $isPersonalEntity
            WHERE dynamicdataid = $dynamicDataId""".update.run)
    one(fr"WHERE dynamicdataid = $dynamicDataId")
      .openOrThrowException("the dynamic data just written must be readable")
  }

  def delete(dynamicDataId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicdata WHERE dynamicdataid = $dynamicDataId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdata".update.run)
    ()
  }
}
