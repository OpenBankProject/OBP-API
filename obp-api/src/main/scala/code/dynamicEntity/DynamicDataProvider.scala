package code.DynamicData

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.json.JObject
import net.liftweb.util.SimpleInjector

object DynamicDataProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(buildOne _) {}

  def buildOne: MappedDynamicDataProvider.type = MappedDynamicDataProvider
}

trait DynamicDataT {
  def dynamicDataId: Option[String]
  def dynamicEntityName: String
  def dataJson: String
}

case class DynamicDataCommons(dynamicEntityName: String,
                                dataJson: String,
                                dynamicDataId: Option[String] = None
                               ) extends DynamicDataT with JsonFieldReName

object DynamicDataCommons extends Converter[DynamicDataT, DynamicDataCommons]


trait DynamicDataProvider {
  def save(entityName: String, requestBody: JObject): Box[DynamicDataT]
  def update(entityName: String, requestBody: JObject, id: String): Box[DynamicDataT]
  def get(entityName: String, id: String): Box[DynamicDataT]
  def getAllDataJson(entityName: String): List[JObject]
  def getAll(entityName: String): List[DynamicDataT]
  def delete(entityName: String, id: String): Box[Boolean]
  def existsData(dynamicEntityName: String): Boolean
}






