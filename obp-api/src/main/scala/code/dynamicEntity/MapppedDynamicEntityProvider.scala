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

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

object MappedDynamicEntityProvider extends DynamicEntityProvider with CustomJsonFormats with MdcLoggable {

  /**
   * The value the bank id column holds for a given space.
   *
   * A definition belonging to a bank stores that bank's id. A definition belonging to the system
   * space stores Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID rather than a SQL NULL, which is the
   * convention the Dynamic Entity data tables already follow. Storing a real value rather than NULL
   * means a query can compare the column like any other, and a unique index over it means what it
   * says; a NULL compares equal to nothing, including itself. Readers still see None for a system
   * level definition, because `bankId` filters the sentinel back out.
   */
  private def storedBankId(bankId: Option[String]): String =
    bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)


  override def getById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntityT] =
    DynamicEntity.find(
      By(DynamicEntity.BankId, storedBankId(bankId)),
      By(DynamicEntity.DynamicEntityId, dynamicEntityId))

  override def getByEntityName(bankId: Option[String], entityName: String): Box[DynamicEntityT] =
    DynamicEntity.find(
      By(DynamicEntity.BankId, storedBankId(bankId)),
      By(DynamicEntity.EntityName, entityName)
    )
      

  override def getDynamicEntities(bankId: Option[String], returnBothBankAndSystemLevel: Boolean): List[DynamicEntity] = {
    if(returnBothBankAndSystemLevel)
      DynamicEntity.findAll()
    else
      DynamicEntity.findAll(By(DynamicEntity.BankId, storedBankId(bankId)))
  }

  override def getDynamicEntitiesByUserId(userId: String): List[DynamicEntity] = {
    DynamicEntity.findAll(By(DynamicEntity.UserId, userId))
  }

  override def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT] = {

    //to find exists dynamicEntity, if dynamicEntityId supplied, query by dynamicEntityId, or use entityName and dynamicEntityId to do query
    val existsDynamicEntity: Box[DynamicEntity] = dynamicEntity.dynamicEntityId match {
      case Some(id) if StringUtils.isNotBlank(id) => getByDynamicEntityId(id)
      case _ => Empty
    }
    val entityToPersist = existsDynamicEntity match {
      case _: EmptyBox => DynamicEntity.create
      case Full(dynamicEntity) => dynamicEntity
    }

    // §8.4: switching useRowLevelAccess on for an entity that already has rows makes those
    // rows admin-only (no backfill). Warn so the operator grants access deliberately.
    val wasRowLevel = existsDynamicEntity.map(_.useRowLevelAccess).getOrElse(false)
    if (!wasRowLevel && dynamicEntity.useRowLevelAccess) {
      // The data table stores the system space as the sentinel, never as a SQL NULL, so both spaces
      // are one comparison. Looking for NULL here counted zero rows for every system level entity,
      // which meant the warning below never fired for exactly the entities most likely to have data.
      val existingRowCount = code.DynamicData.DynamicData.count(
        By(code.DynamicData.DynamicData.DynamicEntityName, dynamicEntity.entityName),
        By(code.DynamicData.DynamicData.BankId, storedBankId(dynamicEntity.bankId)))
      if (existingRowCount > 0)
        logger.warn(s"createOrUpdate says: useRowLevelAccess switched on for entity '${dynamicEntity.entityName}' " +
          s"(bankId=${dynamicEntity.bankId.getOrElse("none")}) which already has $existingRowCount row(s); these are now " +
          s"admin-only until access is granted via the ACL (no backfill — see DYNAMIC_ENTITY_ROW_LEVEL_ACCESS.md §8.4).")
    }

    tryo{
      try {
        val saved = entityToPersist
          .EntityName(dynamicEntity.entityName)
          .MetadataJson(dynamicEntity.metadataJson)
          // Definition creator resolves to the on-behalf-of user (UserReference.DynamicEntity_UserId):
          // a consent user owns nothing durable. ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2.
          .UserId(code.users.Users.users.vend.attributedUserId(dynamicEntity.userId, code.users.UserReference.DynamicEntity_UserId).openOr(dynamicEntity.userId))
          .BankId(storedBankId(dynamicEntity.bankId))
          .HasPersonalEntity(dynamicEntity.hasPersonalEntity)
          .HasPublicAccess(dynamicEntity.hasPublicAccess)
          .HasCommunityAccess(dynamicEntity.hasCommunityAccess)
          .PersonalRequiresRole(dynamicEntity.personalRequiresRole)
          .UseRowLevelAccess(dynamicEntity.useRowLevelAccess)
          .AuthMode(DynamicEntityAuthMode.normalise(dynamicEntity.authMode))
          .saveMe()
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
    dynamicEntity match {
      case v: DynamicEntity => DynamicEntity.delete_!(v)
      // Anything that is not one of our own rows is matched by name, and a name identifies an entity
      // only within one space: two spaces may each hold one called country. Without the bank id this
      // deletes every space's copy. No caller reaches this branch today -- both pass a row this
      // provider just returned -- but the signature takes any DynamicEntityT, and DynamicEntityCommons
      // is what a caller naturally holds, so the branch is one call away from being reached.
      case v => DynamicEntity.bulkDelete_!!(
        By(DynamicEntity.BankId, storedBankId(v.bankId)),
        By(DynamicEntity.EntityName, v.entityName))
    }
  }

  private[this] def getByDynamicEntityId(dynamicEntityId: String): Box[DynamicEntity] = DynamicEntity.find(By(DynamicEntity.DynamicEntityId, dynamicEntityId))

}

class DynamicEntity extends DynamicEntityT with LongKeyedMapper[DynamicEntity] with IdPK with CreatedUpdated with CustomJsonFormats{

  override def getSingleton = DynamicEntity

  object DynamicEntityId extends MappedUUID(this)
  object EntityName extends MappedString(this, 255)

  object MetadataJson extends MappedText(this)
  object UserId extends MappedString(this, 255)
  object BankId extends MappedString(this, 255)
  object HasPersonalEntity extends MappedBoolean(this)
  object HasPublicAccess extends MappedBoolean(this)
  object HasCommunityAccess extends MappedBoolean(this)
  object PersonalRequiresRole extends MappedBoolean(this)
  object UseRowLevelAccess extends MappedBoolean(this)
  // Name of an EndpointAuthMode; null for rows created before the column existed (read as UserOnly).
  object AuthMode extends MappedString(this, 32)

  override def dynamicEntityId: Option[String] = Option(DynamicEntityId.get)
  override def entityName: String = EntityName.get
  override def metadataJson: String = MetadataJson.get
  override def userId: String = UserId.get
  // A system level definition stores the sentinel rather than a SQL NULL; it is filtered back out
  // here so every reader still sees None, exactly as before. Empty and null are still read as the
  // system space, so a row written before the sentinel existed reads correctly until it is moved.
  override def bankId: Option[String] =
    Option(BankId.get).filterNot(_.isEmpty).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
  override def hasPersonalEntity: Boolean = HasPersonalEntity.get
  override def hasPublicAccess: Boolean = HasPublicAccess.get
  override def hasCommunityAccess: Boolean = HasCommunityAccess.get
  override def personalRequiresRole: Boolean = PersonalRequiresRole.get
  override def useRowLevelAccess: Boolean = UseRowLevelAccess.get
  override def authMode: String = DynamicEntityAuthMode.normalise(AuthMode.get)
}

object DynamicEntity extends DynamicEntity with LongKeyedMetaMapper[DynamicEntity] {
  override def dbIndexes = UniqueIndex(DynamicEntityId) :: super.dbIndexes
}

