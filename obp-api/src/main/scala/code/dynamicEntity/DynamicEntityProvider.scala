package code.dynamicEntity

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object DynamicEntityProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicEntityProvider.type = MappedDynamicEntityProvider
}

trait DynamicEntityT {
  def dynamicEntityId: Option[String]
  def entityName: String
  def metadataJson: String
}

case class DynamicEntityCommons(entityName: String,
                                metadataJson: String,
                                dynamicEntityId: Option[String] = None,
                               ) extends DynamicEntityT with JsonFieldReName

object DynamicEntityCommons extends Converter[DynamicEntityT, DynamicEntityCommons]


trait DynamicEntityProvider {
  def getById(dynamicEntityId: String): Box[DynamicEntityT]

  def getDynamicEntities(): List[DynamicEntityT]

  def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT]

  def delete(dynamicEntityId: String):Box[Boolean]
}






