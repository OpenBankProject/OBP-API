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

package code.dynamicEntity

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

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
    // Read before the save changes the row: the webhooks compare their field conditions against it.
    val fieldTypesBefore = existsDynamicEntity.map(code.dynamicentitywebhook.DynamicEntityWebhookConditions.fieldTypes)
    if (!wasRowLevel && dynamicEntity.useRowLevelAccess) {
      // Every record of the entity in scope, whatever its owner — switching row-level access on
      // affects them all.
      val existingRowCount = code.DynamicData.DynamicData
        .countInSpace(dynamicEntity.bankId, dynamicEntity.entityName)
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
          // Definition creator resolves to the on-behalf-of user (UserReference.DynamicEntity_UserId):
          // a consent user owns nothing durable. ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2.
          userId = code.users.Users.users.vend
            .attributedUserId(dynamicEntity.userId, code.users.UserReference.DynamicEntity_UserId)
            .openOr(dynamicEntity.userId),
          bankId = dynamicEntity.bankId,
          hasPersonalEntity = dynamicEntity.hasPersonalEntity,
          hasPublicAccess = dynamicEntity.hasPublicAccess,
          hasCommunityAccess = dynamicEntity.hasCommunityAccess,
          personalRequiresRole = dynamicEntity.personalRequiresRole,
          useRowLevelAccess = dynamicEntity.useRowLevelAccess,
          authMode = dynamicEntity.authMode)
        code.api.dynamic.entity.helper.DynamicEntityHelper.forgetDefinitions()
        fieldTypesBefore.foreach(code.dynamicentitywebhook.DynamicEntityWebhookDefinitionChanges.afterUpdate(saved, _))
        // DE_indexing: provision/refresh the projection for this definition's indexed scalar fields.
        // Guarded by projectionEnabled (default off); best-effort (a failure leaves the definition saved
        // and queries reporting pending, not a broken create). Fields passed explicitly because the new
        // definition isn't committed/visible in the definition map yet.
        if (code.api.dynamic.entity.projection.IndexingCapabilities.projectionEnabled) {
          try {
            val info = code.api.dynamic.entity.helper.DynamicEntityInfo(
              dynamicEntity.metadataJson, dynamicEntity.entityName, dynamicEntity.bankId,
              dynamicEntity.hasPersonalEntity, dynamicEntity.hasPublicAccess, dynamicEntity.hasCommunityAccess, dynamicEntity.personalRequiresRole, dynamicEntity.useRowLevelAccess, dynamicEntity.authMode)
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
    // The entity's webhooks go with it, whichever branch below deletes it.
    val dynamicEntityIds = dynamicEntity match {
      case v: DynamicEntity => v.dynamicEntityId.toList
      case v => DynamicEntity.findAllScopedByName(v.bankId, v.entityName)
        .flatMap(_.dynamicEntityId)
    }
    dynamicEntityIds.filter(_.nonEmpty).foreach(code.dynamicentitywebhook.DynamicEntityWebhookDefinitionChanges.beforeDelete)
    try dynamicEntity match {
      case v: DynamicEntity => DynamicEntity.deleteById(v.dynamicEntityId.getOrElse(""))
      // Anything that is not one of our own rows is matched by name, and a name identifies an entity
      // only within one space: two spaces may each hold one called country. Without the bank id this
      // deletes every space's copy. No caller reaches this branch today -- both pass a row this
      // provider just returned -- but the signature takes any DynamicEntityT, and DynamicEntityCommons
      // is what a caller naturally holds, so the branch is one call away from being reached.
      case v => DynamicEntity.deleteScopedByName(v.bankId, v.entityName)
    } finally code.api.dynamic.entity.helper.DynamicEntityHelper.forgetDefinitions()
  }

  private[this] def getByDynamicEntityId(dynamicEntityId: String): Box[DynamicEntity] =
    DynamicEntity.findById(dynamicEntityId)

}

/**
 * One runtime-defined entity type.
 *
 * A system level definition stores the SYS sentinel (Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
 * in bankid rather than a SQL NULL, as the data tables do: one convention for the whole feature,
 * and a value a unique index can compare. Every read names the space exactly, so a system-level
 * lookup does not see bank-level entities.
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
  useRowLevelAccess: Boolean,
  // Upstream commit c583fe421. Null / empty on rows created before the column existed, which
  // DynamicEntityAuthMode.normalise reads back as the UserOnly default.
  authMode: String = DynamicEntityAuthMode.default
) extends DynamicEntityT {
  override def dynamicEntityId: Option[String] = Option(dynamicEntityIdRaw)
  // The sentinel is filtered back out here so every reader still sees None for the system space.
  // Empty and null are read as the system space too: rows written before the sentinel existed.
  override def bankId: Option[String] =
    bankIdRaw.filter(b => b != null && b.nonEmpty && b != code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
}

object DynamicEntity {

  private val selectColumns =
    fr"""SELECT dynamicentityid, entityname, metadatajson, userid, bankid, haspersonalentity,
                haspublicaccess, hascommunityaccess, personalrequiresrole, userowlevelaccess,
                authmode
         FROM dynamicentity"""

  // Option wherever the insert binds Option, and for the flags too: Mapper's MappedBoolean read a
  // NULL column as false rather than throwing, and older rows predate these columns.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[Boolean], Option[Boolean], Option[Boolean], Option[Boolean],
    Option[Boolean], Option[String])

  private def fromRow(row: Row): DynamicEntity = row match {
    case (dynamicEntityId, entityName, metadataJson, userId, bankId, hasPersonalEntity,
          hasPublicAccess, hasCommunityAccess, personalRequiresRole, useRowLevelAccess,
          authMode) =>
      DynamicEntity(dynamicEntityId.orNull, entityName.orNull, metadataJson.orNull, userId.orNull,
        bankId, hasPersonalEntity.getOrElse(false), hasPublicAccess.getOrElse(false),
        hasCommunityAccess.getOrElse(false), personalRequiresRole.getOrElse(false),
        useRowLevelAccess.getOrElse(false),
        DynamicEntityAuthMode.normalise(authMode.orNull))
  }

  private def query(condition: Fragment): List[DynamicEntity] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicEntity] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /** The value bankid holds for a space: the bank's id, or SYS for the system space (None). */
  private def storedBankId(bankId: Option[String]): String =
    code.api.dynamic.entity.helper.DynamicEntitySpace.bankIdOrSystem(bankId)

  /** Exactly one space - `None` is the system space, not "do not filter". */
  private def scopedBank(bankId: Option[String]): Fragment =
    fr"bankid = ${storedBankId(bankId)}"

  def findScopedById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntity] =
    one(fr"WHERE dynamicentityid = $dynamicEntityId AND " ++ scopedBank(bankId))

  def findScopedByName(bankId: Option[String], entityName: String): Box[DynamicEntity] =
    one(fr"WHERE entityname = $entityName AND " ++ scopedBank(bankId))

  /** Ignores scope entirely — used only for the by-id lookup inside createOrUpdate. */
  def findById(dynamicEntityId: String): Box[DynamicEntity] =
    one(fr"WHERE dynamicentityid = $dynamicEntityId")

  def findAllScoped(bankId: Option[String]): List[DynamicEntity] =
    query(fr"WHERE " ++ scopedBank(bankId) ++ fr"ORDER BY id ASC")

  def findAllScopedByName(bankId: Option[String], entityName: String): List[DynamicEntity] =
    query(fr"WHERE entityname = $entityName AND " ++ scopedBank(bankId) ++ fr"ORDER BY id ASC")

  def findAll(): List[DynamicEntity] = query(fr"ORDER BY id ASC")

  def findAllByUserId(userId: String): List[DynamicEntity] =
    query(fr"WHERE userid = $userId ORDER BY id ASC")

  def upsert(dynamicEntityId: Option[String], entityName: String, metadataJson: String,
             userId: String, bankId: Option[String], hasPersonalEntity: Boolean,
             hasPublicAccess: Boolean, hasCommunityAccess: Boolean, personalRequiresRole: Boolean,
             useRowLevelAccess: Boolean, authMode: String): DynamicEntity = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = dynamicEntityId.getOrElse(APIUtil.generateUUID())
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE dynamicentity SET entityname = ${Option(entityName)},
              metadatajson = ${Option(metadataJson)}, userid = ${Option(userId)},
              bankid = ${storedBankId(bankId)},
              haspersonalentity = $hasPersonalEntity, haspublicaccess = $hasPublicAccess,
              hascommunityaccess = $hasCommunityAccess,
              personalrequiresrole = $personalRequiresRole,
              userowlevelaccess = $useRowLevelAccess,
              authmode = ${Option(DynamicEntityAuthMode.normalise(authMode))}, updatedat = $now
            WHERE dynamicentityid = $id""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO dynamicentity
              (dynamicentityid, entityname, metadatajson, userid, bankid, haspersonalentity,
               haspublicaccess, hascommunityaccess, personalrequiresrole, userowlevelaccess,
               authmode, createdat, updatedat)
              VALUES ($id, ${Option(entityName)}, ${Option(metadataJson)}, ${Option(userId)},
               ${storedBankId(bankId)}, $hasPersonalEntity, $hasPublicAccess, $hasCommunityAccess,
               $personalRequiresRole, $useRowLevelAccess,
               ${Option(DynamicEntityAuthMode.normalise(authMode))}, $now, $now)"""
          .update.run)
    }
    findById(id).openOrThrowException("the dynamic entity just written must be readable")
  }

  def deleteById(dynamicEntityId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicentity WHERE dynamicentityid = $dynamicEntityId".update.run) > 0

  /**
   * A name identifies an entity only within one space: two spaces may each hold one called country,
   * so a delete by name names the space as well.
   */
  def deleteScopedByName(bankId: Option[String], entityName: String): Boolean = {
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicentity WHERE entityname = $entityName AND " ++ scopedBank(bankId)).update.run)
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
