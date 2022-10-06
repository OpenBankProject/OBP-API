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
  def bankId: Option[String]
}

case class DynamicDataCommons(dynamicEntityName: String,
                                dataJson: String,
                                dynamicDataId: Option[String] = None,
                                bankId: Option[String]
                               ) extends DynamicDataT with JsonFieldReName

object DynamicDataCommons extends Converter[DynamicDataT, DynamicDataCommons]


trait DynamicDataProvider {
  def save(bankId: Option[String], entityName: String, requestBody: JObject): Box[DynamicDataT]
  def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String): Box[DynamicDataT]
  def get(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT]
  def getAllDataJson(bankId: Option[String], entityName: String): List[JObject]
  def getAll(bankId: Option[String], entityName: String): List[DynamicDataT]
  def delete(bankId: Option[String], entityName: String, id: String): Box[Boolean]
  def existsData(dbankId: Option[String], ynamicEntityName: String): Boolean
}






