package code.regulatedentities

import code.api.util.{APIUtil, DoobieUtil}
import code.regulatedentities.attribute.DoobieRegulatedEntityAttributeProvider
import com.openbankproject.commons.model.{RegulatedEntityAttributeSimple, RegulatedEntityTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.common.Box.tryo

/**
 * A regulated entity (a PSD2 certificate holder).
 *
 * Every column is optional at the API but not nullable in practice: createRegulatedEntity only set
 * the fields it was given and left the rest at MappedString's "" default, so absent values are
 * stored as empty strings rather than NULL. That is preserved — the trait types them all as bare
 * Strings, and a caller reading back an omitted field has always seen "".
 */
case class MappedRegulatedEntity(
  entityId: String,
  certificateAuthorityCaOwnerId: String,
  entityName: String,
  entityCode: String,
  entityCertificatePublicKey: String,
  entityType: String,
  entityAddress: String,
  entityTownCity: String,
  entityPostCode: String,
  entityCountry: String,
  entityWebSite: String,
  services: String
) extends RegulatedEntityTrait {
  override def attributes: Option[List[RegulatedEntityAttributeSimple]] =
    Some(
      DoobieRegulatedEntityAttributeProvider.getRegulatedEntityAttributesSync(entityId)
        .map(i => RegulatedEntityAttributeSimple(i.attributeType.toString, i.name, i.value))
    )
}

object MappedRegulatedEntity {

  private val selectColumns =
    fr"""SELECT entityid, certificateauthoritycaownerid, entityname, entitycode,
                entitycertificatepublickey, entitytype, entityaddress, entitytowncity,
                entitypostcode, entitycountry, entitywebsite, services
         FROM regulatedentity"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedRegulatedEntity = row match {
    case (entityId, certificateAuthorityCaOwnerId, entityName, entityCode,
          entityCertificatePublicKey, entityType, entityAddress, entityTownCity, entityPostCode,
          entityCountry, entityWebSite, services) =>
      MappedRegulatedEntity(entityId.orNull, certificateAuthorityCaOwnerId.orNull,
        entityName.orNull, entityCode.orNull, entityCertificatePublicKey.orNull, entityType.orNull,
        entityAddress.orNull, entityTownCity.orNull, entityPostCode.orNull, entityCountry.orNull,
        entityWebSite.orNull, services.orNull)
  }

  private def query(condition: Fragment): List[MappedRegulatedEntity] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findAll(): List[MappedRegulatedEntity] = query(fr"ORDER BY id ASC")

  def findByEntityId(entityId: String): Box[MappedRegulatedEntity] =
    query(fr"WHERE entityid = $entityId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * Absent fields are stored as "" rather than NULL, matching what Mapper's untouched
   * MappedString defaults wrote.
   *
   * Mapper also ran `entity.validate` before saving and threw the collected messages when it was
   * non-empty. No validator was ever declared on this entity, so that check always passed; the
   * column widths are what actually reject an over-long value, and the caller's tryo turns that
   * into a Failure exactly as it did the thrown Error.
   */
  def insert(certificateAuthorityCaOwnerId: Option[String],
             entityCertificatePublicKey: Option[String],
             entityName: Option[String],
             entityCode: Option[String],
             entityType: Option[String],
             entityAddress: Option[String],
             entityTownCity: Option[String],
             entityPostCode: Option[String],
             entityCountry: Option[String],
             entityWebSite: Option[String],
             services: Option[String]): MappedRegulatedEntity = {
    val entityId = APIUtil.generateUUID()
    val row = MappedRegulatedEntity(
      entityId,
      certificateAuthorityCaOwnerId.getOrElse(""),
      entityName.getOrElse(""),
      entityCode.getOrElse(""),
      entityCertificatePublicKey.getOrElse(""),
      entityType.getOrElse(""),
      entityAddress.getOrElse(""),
      entityTownCity.getOrElse(""),
      entityPostCode.getOrElse(""),
      entityCountry.getOrElse(""),
      entityWebSite.getOrElse(""),
      services.getOrElse("")
    )
    DoobieUtil.runUpdate(
      sql"""INSERT INTO regulatedentity
            (entityid, certificateauthoritycaownerid, entityname, entitycode,
             entitycertificatepublickey, entitytype, entityaddress, entitytowncity, entitypostcode,
             entitycountry, entitywebsite, services)
            VALUES (${row.entityId}, ${row.certificateAuthorityCaOwnerId}, ${row.entityName},
             ${row.entityCode}, ${row.entityCertificatePublicKey}, ${row.entityType},
             ${row.entityAddress}, ${row.entityTownCity}, ${row.entityPostCode},
             ${row.entityCountry}, ${row.entityWebSite}, ${row.services})"""
        .update.run)
    row
  }

  def deleteByEntityId(entityId: String): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentity WHERE entityid = $entityId".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentity".update.run)
    ()
  }
}

object MappedRegulatedEntityProvider extends RegulatedEntityProvider {

  def getRegulatedEntities(): List[RegulatedEntityTrait] = MappedRegulatedEntity.findAll()

  override def getRegulatedEntityByEntityId(entityId: String): Box[RegulatedEntityTrait] =
    MappedRegulatedEntity.findByEntityId(entityId)

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
                                    ): Box[RegulatedEntityTrait] =
    tryo {
      MappedRegulatedEntity.insert(certificateAuthorityCaOwnerId, entityCertificatePublicKey,
        entityName, entityCode, entityType, entityAddress, entityTownCity, entityPostCode,
        entityCountry, entityWebSite, services)
    }

  override def deleteRegulatedEntity(id: String): Box[Boolean] =
    tryo(MappedRegulatedEntity.deleteByEntityId(id))
}
