package code.regulatedentities


import com.openbankproject.commons.model.{RegulatedEntityTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object RegulatedEntityX extends SimpleInjector {
  val regulatedEntityProvider = new Inject(buildOne _) {}
  def buildOne: RegulatedEntityProvider = MappedRegulatedEntityProvider
}
/* For ProductFee */
trait RegulatedEntityProvider extends MdcLoggable {

  def getRegulatedEntities(): List[RegulatedEntityTrait]

  def getRegulatedEntityByEntityId(entityId: String): Box[RegulatedEntityTrait]

  def createRegulatedEntity(certificateAuthorityCaOwnerId: Option[String],
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
                           ): Box[RegulatedEntityTrait]

  def deleteRegulatedEntity(id: String): Box[Boolean]

}
