package code.dynamicEntity

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import code.util.MappedUUID
import net.liftweb.common.{Box, Empty, EmptyBox, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object MappedDynamicEntityProvider extends DynamicEntityProvider with CustomJsonFormats with MdcLoggable {

  override def getById(dynamicEntityId: String): Box[DynamicEntityT] =  DynamicEntity.find(
    By(DynamicEntity.DynamicEntityId, dynamicEntityId)
  )

  override def getByEntityName(entityName: String): Box[DynamicEntityT] = DynamicEntity.find(
    By(DynamicEntity.EntityName, entityName)
  )

  override def getDynamicEntities(): List[DynamicEntity] = {
    DynamicEntity.findAll()
  }

  override def getDynamicEntitiesByBankId(bankId: String): List[DynamicEntity] = {
    DynamicEntity.findAll(By(DynamicEntity.BankId, bankId))
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

    tryo{
      try {
        entityToPersist
          .EntityName(dynamicEntity.entityName)
          .MetadataJson(dynamicEntity.metadataJson)
          .UserId(dynamicEntity.userId)
          .BankId(dynamicEntity.bankId.getOrElse(null))
          .saveMe()
      } catch {
        case e =>
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

  override def dynamicEntityId: Option[String] = Option(DynamicEntityId.get)
  override def entityName: String = EntityName.get
  override def metadataJson: String = MetadataJson.get
  override def userId: String = UserId.get
  override def bankId: Option[String] = if (BankId.get == null || BankId.get.isEmpty) None else Some(BankId.get)
}

object DynamicEntity extends DynamicEntity with LongKeyedMetaMapper[DynamicEntity] {
  override def dbIndexes = UniqueIndex(DynamicEntityId) :: super.dbIndexes
}

