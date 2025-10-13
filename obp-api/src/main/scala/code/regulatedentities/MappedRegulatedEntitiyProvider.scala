package code.regulatedentities

import code.regulatedentities.attribute.RegulatedEntityAttribute
import code.util.MappedUUID
import com.openbankproject.commons.model.{RegulatedEntityAttributeSimple, RegulatedEntityTrait}
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.mapper._

import scala.concurrent.Future

object MappedRegulatedEntityProvider extends RegulatedEntityProvider {
  def getRegulatedEntities(): List[RegulatedEntityTrait] = {
    MappedRegulatedEntity.findAll()
  }

  override def getRegulatedEntityByEntityId(entityId: String): Box[RegulatedEntityTrait] = {
    MappedRegulatedEntity.find(By(MappedRegulatedEntity.EntityId, entityId))
  }

  override def createRegulatedEntity(certificateAuthorityCaOwnerId: Option[String],
                                     entityCertificatePublicKey: Option[String],
                                     entityName: Option[String],
                                     entityCode: Option[String],
                                     entityType: Option[String],
                                     entityAddress: Option[String],
                                     entityTownCity: Option[String],
                                     entityPostCode: Option[String],
                                     entityCountry: Option[String],
                                     entityWebSite: Option[String],
                                     services: Option[String]
                                    ): Box[RegulatedEntityTrait] = {
    tryo {
      val entity = MappedRegulatedEntity.create
      certificateAuthorityCaOwnerId match {
        case Some(v) => entity.CertificateAuthorityCaOwnerId(v)
        case None =>
      }
      entityCertificatePublicKey match {
        case Some(v) => entity.EntityCertificatePublicKey(v)
        case None =>
      }
      entityName match {
        case Some(v) => entity.EntityName(v)
        case None =>
      }
      entityCode match {
        case Some(v) => entity.EntityCode(v)
        case None =>
      }
      entityType match {
        case Some(v) => entity.EntityType(v)
        case None =>
      }
      entityAddress match {
        case Some(v) => entity.EntityAddress(v)
        case None =>
      }
      entityTownCity match {
        case Some(v) => entity.EntityTownCity(v)
        case None =>
      }
      entityPostCode match {
        case Some(v) => entity.EntityPostCode(v)
        case None =>
      }
      entityCountry match {
        case Some(v) => entity.EntityCountry(v)
        case None =>
      }
      entityWebSite match {
        case Some(v) => entity.EntityWebSite(v)
        case None =>
      }
      services match {
        case Some(v) => entity.Services(v)
        case None =>
      }

      if (entity.validate.isEmpty) {
        entity.saveMe()
      } else {
        throw new Error(entity.validate.map(_.msg.toString()).mkString(";"))
      }
    }
  }

  override def deleteRegulatedEntity(id: String): Box[Boolean] = {
    tryo(
      MappedRegulatedEntity.bulkDelete_!!(By(MappedRegulatedEntity.EntityId, id))
    )
  }

}

class MappedRegulatedEntity extends RegulatedEntityTrait with LongKeyedMapper[MappedRegulatedEntity] with IdPK {
  override def getSingleton = MappedRegulatedEntity
  object EntityId extends MappedUUID(this)
  object CertificateAuthorityCaOwnerId extends MappedString(this, 256)
  object EntityName extends MappedString(this, 256)
  object EntityCode extends MappedString(this, 50)
  object EntityCertificatePublicKey extends MappedText(this)
  object EntityType extends MappedString(this, 50)
  object EntityAddress extends MappedString(this, 256)
  object EntityTownCity extends MappedString(this, 50)
  object EntityPostCode extends MappedString(this, 50)
  object EntityCountry extends MappedString(this, 50)
  object EntityWebSite extends MappedString(this, 256)
  object Services extends MappedText(this)


  override def entityId: String = EntityId.get
  override def certificateAuthorityCaOwnerId: String = CertificateAuthorityCaOwnerId.get
  override def entityName: String = EntityName.get
  override def entityCode: String = EntityCode.get
  override def entityCertificatePublicKey: String = EntityCertificatePublicKey.get
  override def entityType: String = EntityType.get
  override def entityAddress: String = EntityAddress.get
  override def entityTownCity: String = EntityTownCity.get
  override def entityPostCode: String = EntityPostCode.get
  override def entityCountry: String = EntityCountry.get
  override def entityWebSite: String = EntityWebSite.get
  override def services: String = Services.get
  override def attributes: Option[List[RegulatedEntityAttributeSimple]] = {
    Some(
      RegulatedEntityAttribute.findAll(
        By(RegulatedEntityAttribute.RegulatedEntityId_, EntityId.get)
      ).map(i => RegulatedEntityAttributeSimple(i.attributeType.toString, i.name, i.value))
    )
  }

}

object MappedRegulatedEntity extends MappedRegulatedEntity with LongKeyedMetaMapper[MappedRegulatedEntity]  {
  override def dbTableName = "RegulatedEntity" // define the DB table name
  override def dbIndexes = Index(CertificateAuthorityCaOwnerId) :: super.dbIndexes
}

