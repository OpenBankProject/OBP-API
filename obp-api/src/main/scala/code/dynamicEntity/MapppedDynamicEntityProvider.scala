package code.dynamicEntity

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedDynamicEntityProvider extends DynamicEntityProvider with CustomJsonFormats with MdcLoggable {

  override def getById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntityT] = {
    //If bankId is empty, we only return the system level entities
    DynamicEntity.findScopedById(bankId, dynamicEntityId)
  }

  override def getByEntityName(bankId: Option[String], entityName: String): Box[DynamicEntityT] =
    //If Bank id is empty, we only return  the system level entity
    DynamicEntity.findScopedByName(bankId, entityName)
      

  override def getDynamicEntities(bankId: Option[String], returnBothBankAndSystemLevel: Boolean): List[DynamicEntity] = {
    if(returnBothBankAndSystemLevel)
      DynamicEntity.findAll()
    else //If Bank id is empty, we only return  the system level entity
      DynamicEntity.findAllScoped(bankId)
  }

  override def getDynamicEntitiesByUserId(userId: String): List[DynamicEntity] = {
    DynamicEntity.findAllByUserId(userId)
  }

  override def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT] = {

    //to find exists dynamicEntity, if dynamicEntityId supplied, query by dynamicEntityId, or use entityName and dynamicEntityId to do query
    val existsDynamicEntity: Box[DynamicEntity] = dynamicEntity.dynamicEntityId match {
      case Some(id) if StringUtils.isNotBlank(id) => getByDynamicEntityId(id)
      case _ => Empty
    }

    // §8.4: switching useRowLevelAccess on for an entity that already has rows makes those
    // rows admin-only (no backfill). Warn so the operator grants access deliberately.
    val wasRowLevel = existsDynamicEntity.map(_.useRowLevelAccess).getOrElse(false)
    if (!wasRowLevel && dynamicEntity.useRowLevelAccess) {
      // Every record of the entity in scope, whatever its owner — switching row-level access on
      // affects them all.
      val existingRowCount = code.DynamicData.DynamicData
        .findAllCommunity(dynamicEntity.bankId, dynamicEntity.entityName).size
      if (existingRowCount > 0)
        logger.warn(s"createOrUpdate says: useRowLevelAccess switched on for entity '${dynamicEntity.entityName}' " +
          s"(bankId=${dynamicEntity.bankId.getOrElse("none")}) which already has $existingRowCount row(s); these are now " +
          s"admin-only until access is granted via the ACL (no backfill — see DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md §8.4).")
    }

    tryo{
      try {
        val saved = DynamicEntity.upsert(
          dynamicEntityId = existsDynamicEntity.toOption.flatMap(_.dynamicEntityId),
          entityName = dynamicEntity.entityName,
          metadataJson = dynamicEntity.metadataJson,
          userId = dynamicEntity.userId,
          bankId = dynamicEntity.bankId,
          hasPersonalEntity = dynamicEntity.hasPersonalEntity,
          hasPublicAccess = dynamicEntity.hasPublicAccess,
          hasCommunityAccess = dynamicEntity.hasCommunityAccess,
          personalRequiresRole = dynamicEntity.personalRequiresRole,
          useRowLevelAccess = dynamicEntity.useRowLevelAccess)
        // DE_indexing: provision/refresh the projection for this definition's indexed scalar fields.
        // Guarded by projectionEnabled (default off); best-effort (a failure leaves the definition saved
        // and queries reporting pending, not a broken create). Fields passed explicitly because the new
        // definition isn't committed/visible in the definition map yet.
        if (code.api.dynamic.entity.projection.IndexingCapabilities.projectionEnabled) {
          try {
            val info = code.api.dynamic.entity.helper.DynamicEntityInfo(
              dynamicEntity.metadataJson, dynamicEntity.entityName, dynamicEntity.bankId,
              dynamicEntity.hasPersonalEntity, dynamicEntity.hasPublicAccess, dynamicEntity.hasCommunityAccess, dynamicEntity.personalRequiresRole, dynamicEntity.useRowLevelAccess)
            val scalar = code.api.dynamic.entity.projection.ProjectionProvisioner.scalarFieldsOf(info.indexedFields)
            if (scalar.nonEmpty)
              code.api.dynamic.entity.projection.ProjectionProvisioner
                .ensureProvisionedFields(dynamicEntity.bankId, dynamicEntity.entityName, scalar)
                .unsafeRunSync()(cats.effect.unsafe.implicits.global)
          } catch {
            case e: Throwable => logger.error(s"DE projection provisioning failed for ${dynamicEntity.entityName} (definition saved; queries will report pending)", e)
          }
        }
        saved
      } catch {
        case e : Throwable =>
          logger.error("Create or Update DynamicEntity fail.", e)
          throw e
      }
    }
  }


  override def delete(dynamicEntity: DynamicEntityT): Box[Boolean] = Box.tryo{
    // A row we loaded is deleted by its own id; anything else only names an entity, so every row
    // with that name goes — the same two-branch behaviour Mapper had.
    dynamicEntity match {
      case v: DynamicEntity => DynamicEntity.deleteById(v.dynamicEntityId.getOrElse(""))
      case v => DynamicEntity.deleteByEntityName(v.entityName)
    }
  }

  private[this] def getByDynamicEntityId(dynamicEntityId: String): Box[DynamicEntity] =
    DynamicEntity.findById(dynamicEntityId)

}

/**
 * One runtime-defined entity type.
 *
 * `bankId` genuinely holds NULL for system-level entities. Unlike the message-doc and
 * resource-doc providers, whose reads leave bankid unconstrained when no bank is supplied, every
 * read here uses `IS NULL` for the system-level case — so a system-level lookup does not see
 * bank-level entities. The three providers differ and each keeps its own behaviour.
 */
case class DynamicEntity(
  private val dynamicEntityIdRaw: String,
  entityName: String,
  metadataJson: String,
  userId: String,
  private val bankIdRaw: Option[String],
  hasPersonalEntity: Boolean,
  hasPublicAccess: Boolean,
  hasCommunityAccess: Boolean,
  personalRequiresRole: Boolean,
  useRowLevelAccess: Boolean
) extends DynamicEntityT {
  override def dynamicEntityId: Option[String] = Option(dynamicEntityIdRaw)
  override def bankId: Option[String] = bankIdRaw.filter(b => b != null && b.nonEmpty)
}

object DynamicEntity {

  private val selectColumns =
    fr"""SELECT dynamicentityid, entityname, metadatajson, userid, bankid, haspersonalentity,
                haspublicaccess, hascommunityaccess, personalrequiresrole, userowlevelaccess
         FROM dynamicentity"""

  // Option wherever the insert binds Option, and for the flags too: Mapper's MappedBoolean read a
  // NULL column as false rather than throwing, and older rows predate these columns.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean], Option[Boolean], Option[Boolean], Option[Boolean],
    Option[Boolean])

  private def fromRow(row: Row): DynamicEntity = row match {
    case (dynamicEntityId, entityName, metadataJson, userId, bankId, hasPersonalEntity,
          hasPublicAccess, hasCommunityAccess, personalRequiresRole, useRowLevelAccess) =>
      DynamicEntity(dynamicEntityId.orNull, entityName.orNull, metadataJson.orNull, userId.orNull,
        bankId, hasPersonalEntity.getOrElse(false), hasPublicAccess.getOrElse(false),
        hasCommunityAccess.getOrElse(false), personalRequiresRole.getOrElse(false),
        useRowLevelAccess.getOrElse(false))
  }

  private def query(condition: Fragment): List[DynamicEntity] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicEntity] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** `None` means the column must be NULL, matching Lift's NullRef — not "do not filter". */
  private def scopedBank(bankId: Option[String]): Fragment =
    bankId.map(b => fr"bankid = $b").getOrElse(fr"bankid IS NULL")

  def findScopedById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntity] =
    one(fr"WHERE dynamicentityid = $dynamicEntityId AND " ++ scopedBank(bankId))

  def findScopedByName(bankId: Option[String], entityName: String): Box[DynamicEntity] =
    one(fr"WHERE entityname = $entityName AND " ++ scopedBank(bankId))

  /** Ignores scope entirely — used only for the by-id lookup inside createOrUpdate. */
  def findById(dynamicEntityId: String): Box[DynamicEntity] =
    one(fr"WHERE dynamicentityid = $dynamicEntityId")

  def findAllScoped(bankId: Option[String]): List[DynamicEntity] =
    query(fr"WHERE " ++ scopedBank(bankId) ++ fr"ORDER BY id ASC")

  def findAll(): List[DynamicEntity] = query(fr"ORDER BY id ASC")

  def findAllByUserId(userId: String): List[DynamicEntity] =
    query(fr"WHERE userid = $userId ORDER BY id ASC")

  def upsert(dynamicEntityId: Option[String], entityName: String, metadataJson: String,
             userId: String, bankId: Option[String], hasPersonalEntity: Boolean,
             hasPublicAccess: Boolean, hasCommunityAccess: Boolean, personalRequiresRole: Boolean,
             useRowLevelAccess: Boolean): DynamicEntity = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = dynamicEntityId.getOrElse(APIUtil.generateUUID())
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE dynamicentity SET entityname = ${Option(entityName)},
              metadatajson = ${Option(metadataJson)}, userid = ${Option(userId)}, bankid = $bankId,
              haspersonalentity = $hasPersonalEntity, haspublicaccess = $hasPublicAccess,
              hascommunityaccess = $hasCommunityAccess,
              personalrequiresrole = $personalRequiresRole,
              userowlevelaccess = $useRowLevelAccess, updatedat = $now
            WHERE dynamicentityid = $id""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO dynamicentity
              (dynamicentityid, entityname, metadatajson, userid, bankid, haspersonalentity,
               haspublicaccess, hascommunityaccess, personalrequiresrole, userowlevelaccess,
               createdat, updatedat)
              VALUES ($id, ${Option(entityName)}, ${Option(metadataJson)}, ${Option(userId)},
               $bankId, $hasPersonalEntity, $hasPublicAccess, $hasCommunityAccess,
               $personalRequiresRole, $useRowLevelAccess, $now, $now)"""
          .update.run)
    }
    findById(id).openOrThrowException("the dynamic entity just written must be readable")
  }

  def deleteById(dynamicEntityId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicentity WHERE dynamicentityid = $dynamicEntityId".update.run) > 0

  def deleteByEntityName(entityName: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicentity WHERE entityname = $entityName".update.run)
    true
  }

  def countRowsForEntity(entityName: String, bankId: Option[String]): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM dynamicentity WHERE entityname = $entityName AND " ++
        scopedBank(bankId)).query[Long].unique)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentity".update.run)
    ()
  }
}
