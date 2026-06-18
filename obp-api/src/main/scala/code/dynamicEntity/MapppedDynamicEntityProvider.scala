package code.dynamicEntity

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedDynamicEntityProvider extends DynamicEntityProvider with CustomJsonFormats with MdcLoggable {

  override def getById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntityT] = {
    if (bankId.isEmpty)//If bankId is empty, we only return the system level entities
      DynamicEntity.find(
        By(DynamicEntity.DynamicEntityId, dynamicEntityId),
        NullRef(DynamicEntity.BankId))
    else
      DynamicEntity.find(
        By(DynamicEntity.DynamicEntityId, dynamicEntityId),
        By(DynamicEntity.BankId, bankId.get))
  }

  override def getByEntityName(bankId: Option[String], entityName: String): Box[DynamicEntityT] =
    if (bankId.isEmpty)//If Bank id is empty, we only return  the system level entity
      DynamicEntity.find(
        By(DynamicEntity.EntityName, entityName),
        NullRef(DynamicEntity.BankId)
      )
    else
      DynamicEntity.find(
        By(DynamicEntity.BankId, bankId.get),
        By(DynamicEntity.EntityName, entityName)
      )
      

  override def getDynamicEntities(bankId: Option[String], returnBothBankAndSystemLevel: Boolean): List[DynamicEntity] = {
    if(returnBothBankAndSystemLevel)
      DynamicEntity.findAll()
    else if (bankId.isEmpty)//If Bank id is empty, we only return  the system level entity
      DynamicEntity.findAll(NullRef(DynamicEntity.BankId))
    else
      DynamicEntity.findAll(By(DynamicEntity.BankId, bankId.get))
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
      val existingRowCount = dynamicEntity.bankId match {
        case Some(b) => code.DynamicData.DynamicData.count(
          By(code.DynamicData.DynamicData.DynamicEntityName, dynamicEntity.entityName),
          By(code.DynamicData.DynamicData.BankId, b))
        case None => code.DynamicData.DynamicData.count(
          By(code.DynamicData.DynamicData.DynamicEntityName, dynamicEntity.entityName),
          NullRef(code.DynamicData.DynamicData.BankId))
      }
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
          .UserId(dynamicEntity.userId)
          .BankId(dynamicEntity.bankId.getOrElse(null))
          .HasPersonalEntity(dynamicEntity.hasPersonalEntity)
          .HasPublicAccess(dynamicEntity.hasPublicAccess)
          .HasCommunityAccess(dynamicEntity.hasCommunityAccess)
          .PersonalRequiresRole(dynamicEntity.personalRequiresRole)
          .UseRowLevelAccess(dynamicEntity.useRowLevelAccess)
          .saveMe()
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
    dynamicEntity match {
      case v: DynamicEntity => DynamicEntity.delete_!(v)
      case v => DynamicEntity.bulkDelete_!!(By(DynamicEntity.EntityName, v.entityName))
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

  override def dynamicEntityId: Option[String] = Option(DynamicEntityId.get)
  override def entityName: String = EntityName.get
  override def metadataJson: String = MetadataJson.get
  override def userId: String = UserId.get
  override def bankId: Option[String] = if (BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get)
  override def hasPersonalEntity: Boolean = HasPersonalEntity.get
  override def hasPublicAccess: Boolean = HasPublicAccess.get
  override def hasCommunityAccess: Boolean = HasCommunityAccess.get
  override def personalRequiresRole: Boolean = PersonalRequiresRole.get
  override def useRowLevelAccess: Boolean = UseRowLevelAccess.get
}

object DynamicEntity extends DynamicEntity with LongKeyedMetaMapper[DynamicEntity] {
  override def dbIndexes = UniqueIndex(DynamicEntityId) :: super.dbIndexes
}

